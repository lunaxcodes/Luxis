using System.Collections.Concurrent;
using System.Diagnostics;
using System.IO.Compression;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace Lunax;

public sealed class MinecraftService
{
    private readonly string _root, _game, _instances;
    private readonly AuthService _auth;
    private readonly Action<string> _emit;
    private readonly ConcurrentDictionary<string, Process> _processes = new();

    public MinecraftService(string root, AuthService auth, Action<string> emit)
    {
        _root = root; _game = Path.Combine(root, "game"); _instances = Path.Combine(root, "instances");
        _auth = auth; _emit = emit;
        Directory.CreateDirectory(_game); Directory.CreateDirectory(_instances);
    }

    private void Stage(string stage, int progress, string detail = "")
        => _emit(Util.Json(new { type = "launch", stage, progress, detail }));

    private readonly List<string> _logTail = new();

    private void Log(string line)
    {
        lock (_logTail) { _logTail.Add(line); while (_logTail.Count > 40) _logTail.RemoveAt(0); }
        _emit(Util.Json(new { type = "log", line }));
    }

    public string InstanceDir(string profileName) => Path.Combine(_instances, Util.Safe(profileName));

    public string ToggleMod(string profileName, string fileName, bool enabled)
    {
        var dir = Path.Combine(InstanceDir(profileName), "mods");
        Directory.CreateDirectory(dir);
        var name = Path.GetFileName(fileName);
        var onPath = Path.Combine(dir, name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) ? name : name + ".jar");
        var offPath = onPath + ".disabled";
        if (enabled)
        {
            if (File.Exists(offPath)) File.Move(offPath, onPath, true);
        }
        else if (File.Exists(onPath)) File.Move(onPath, offPath, true);
        return Util.Json(new { enabled, file = enabled ? Path.GetFileName(onPath) : Path.GetFileName(offPath) });
    }

    public async Task<string> Launch(string profileJson, string activeAccountId)
    {
        using var pdoc = JsonDocument.Parse(profileJson);
        var p = pdoc.RootElement;
        var name = p.Str("name", "Instance");
        var version = p.Str("version", "26.3");
        var loader = p.Str("loader", "fabric");
        var loaderVersion = p.Str("loaderVersion", "0.19.5");
        var ram = Math.Clamp(p.Int("ram", 6), 2, 16);
        var jvm = p.Str("jvm", "default");
        var res = p.Str("res", "1920x1080");
        var accountId = string.IsNullOrEmpty(p.Str("accountId")) ? activeAccountId : p.Str("accountId");

        Stage("Authenticating Session", 8, "Signing in with " + accountId);
        var session = await _auth.GetSession(accountId);

        var dir = InstanceDir(name);
        var natives = Path.Combine(dir, "natives");
        foreach (var sub in new[] { "mods", "resourcepacks", "shaderpacks", "config", "saves", "logs" })
            Directory.CreateDirectory(Path.Combine(dir, sub));
        BackupInstance(dir, name);

        Stage("Reading Version Manifest", 16, version);
        using var manifest = await Util.GetJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        var verInfo = manifest.RootElement.Items("versions").FirstOrDefault(v => v.Str("id") == version);
        if (verInfo.ValueKind == JsonValueKind.Undefined) throw new Exception("Version " + version + " is not in Mojang's manifest.");
        var versionUrl = verInfo.Str("url");

        var versionDir = Path.Combine(_game, "versions", Util.Safe(version));
        var versionJsonPath = Path.Combine(versionDir, version + ".json");
        await Util.Download(versionUrl, versionJsonPath);
        using var vdoc = JsonDocument.Parse(File.ReadAllText(versionJsonPath));
        var vj = vdoc.RootElement;

        JsonElement fabricProfile = default;
        if (loader == "fabric" || loader == "quilt")
        {
            Stage("Resolving " + char.ToUpper(loader[0]) + loader[1..] + " Loader", 24, loaderVersion);
            var metaUrl = loader == "fabric"
                ? $"https://meta.fabricmc.net/v2/versions/loader/{Uri.EscapeDataString(version)}/{Uri.EscapeDataString(loaderVersion)}/profile/json"
                : $"https://meta.quiltmc.org/v3/versions/loader/{Uri.EscapeDataString(version)}/{Uri.EscapeDataString(loaderVersion)}/profile/json";
            var raw = await Util.Get(metaUrl);
            fabricProfile = JsonDocument.Parse(raw).RootElement.Clone();
        }

        // ---- assets ----
        Stage("Verifying Assets", 40, "Starting asset index");
        var assetsIndexName = vj.Str("assets", version);
        var indexInfo = vj.GetProperty("assetIndex");
        var indexPath = Path.Combine(_game, "assets", "indexes", assetsIndexName + ".json");
        await Util.Download(indexInfo.Str("url"), indexPath, indexInfo.Str("sha1"));
        using var indexDoc = JsonDocument.Parse(File.ReadAllText(indexPath));
        // Several logical assets can share one content hash; download each blob only once.
        var objects = indexDoc.RootElement.GetProperty("objects").EnumerateObject()
            .Select(x => x.Value).GroupBy(x => x.Str("hash")).Select(g => g.First()).ToArray();
        int done = 0, total = objects.Length;
        using var cts = new CancellationTokenSource();
        var reporter = Task.Run(async () =>
        {
            while (!cts.IsCancellationRequested)
            {
                Stage("Verifying Assets", 40 + (int)(35d * Volatile.Read(ref done) / Math.Max(1, total)),
                    $"{Volatile.Read(ref done)} / {total} files");
                await Task.Delay(320);
            }
        });
        try
        {
            await Parallel.ForEachAsync(objects, new ParallelOptions { MaxDegreeOfParallelism = 16 }, async (o, ct) =>
            {
                var hash = o.Str("hash");
                if (string.IsNullOrEmpty(hash)) return;
                var target = Path.Combine(_game, "assets", "objects", hash[..2], hash);
                await Util.Download($"https://resources.download.minecraft.net/{hash[..2]}/{hash}", target, hash);
                Interlocked.Increment(ref done);
            });
        }
        finally { cts.Cancel(); await reporter; }
        Stage("Verifying Assets", 76, total + " files ready");

        // ---- libraries + jar ----
        Stage("Verifying Libraries", 80, "");
        var cp = new List<string>();
        var libs = vj.Items("libraries").Concat(fabricProfile.ValueKind == JsonValueKind.Object ? fabricProfile.Items("libraries") : Enumerable.Empty<JsonElement>());
        int libCount = 0;
        foreach (var lib in libs)
        {
            if (!Allowed(lib)) continue;
            var dl = lib.TryGetProperty("downloads", out var downloadsNode) ? downloadsNode : default;
            bool handled = false;
            if (dl.ValueKind == JsonValueKind.Object && dl.TryGetProperty("artifact", out var artifact) && artifact.ValueKind == JsonValueKind.Object)
            {
                var path = artifact.Str("path");
                if (!string.IsNullOrEmpty(path))
                {
                    var target = Path.Combine(_game, "libraries", path.Replace('/', Path.DirectorySeparatorChar));
                    await Util.Download(artifact.Str("url"), target, artifact.Str("sha1"));
                    cp.Add(target);
                    handled = true;
                }
            }
            // Fabric/Quilt profiles describe libraries in Maven notation instead of downloads.artifact.
            if (!handled && dl.ValueKind != JsonValueKind.Object && lib.TryGetProperty("name", out var nameNode) && nameNode.ValueKind == JsonValueKind.String)
            {
                var (url, path) = MavenLibrary(lib);
                var target = Path.Combine(_game, "libraries", path.Replace('/', Path.DirectorySeparatorChar));
                await Util.Download(url, target, lib.Str("sha1"));
                cp.Add(target);
            }
            if (dl.ValueKind == JsonValueKind.Object && dl.TryGetProperty("classifiers", out var classifiers) && classifiers.ValueKind == JsonValueKind.Object)
            {
                var native = classifiers.EnumerateObject().FirstOrDefault(x =>
                    x.Name.StartsWith("natives-windows") && !x.Name.Contains("-arm"));
                if (native.Value.ValueKind == JsonValueKind.Object)
                {
                    var target = Path.Combine(_game, "libraries", native.Value.Str("path").Replace('/', Path.DirectorySeparatorChar));
                    await Util.Download(native.Value.Str("url"), target, native.Value.Str("sha1"));
                    if (Directory.Exists(natives)) Directory.Delete(natives, true);
                    Directory.CreateDirectory(natives);
                    try
                    {
                        using var zip = ZipFile.OpenRead(target);
                        foreach (var entry in zip.Entries)
                        {
                            if (entry.FullName.StartsWith("META-INF/") || string.IsNullOrEmpty(entry.Name)) continue;
                            entry.ExtractToFile(Path.Combine(natives, entry.Name), true);
                        }
                    }
                    catch { /* native extraction is best-effort */ }
                }
            }
            libCount++;
        }
        var client = vj.GetProperty("downloads").GetProperty("client");
        var jarPath = Path.Combine(versionDir, version + ".jar");
        await Util.Download(client.Str("url"), jarPath, client.Str("sha1"));
        cp.Add(jarPath);
        Stage("Verifying Libraries", 90, libCount + " libraries ready");

        // ---- java ----
        Stage("Selecting Java Runtime", 93, "");
        var (java, major) = FindJava(version);
        var width = res.Split('x')[0]; var height = res.Split('x').Length > 1 ? res.Split('x')[1] : "1080";
        var dict = new Dictionary<string, string>
        {
            ["auth_player_name"] = session.Username,
            ["version_name"] = version,
            ["game_directory"] = dir,
            ["assets_root"] = Path.Combine(_game, "assets"),
            ["assets_index_name"] = assetsIndexName,
            ["auth_uuid"] = session.Uuid,
            ["auth_access_token"] = session.Token,
            ["user_type"] = session.Online ? "msa" : "mojang",
            ["version_type"] = verInfo.Str("type", "release"),
            ["resolution_width"] = width,
            ["resolution_height"] = height,
            ["game_assets"] = Path.Combine(_game, "assets", "virtual", assetsIndexName),
            ["natives_directory"] = natives,
            ["launcher_name"] = "Lunax",
            ["launcher_version"] = "1.0",
            ["classpath"] = string.Join(Path.PathSeparator, cp.Distinct()),
            ["clientid"] = AuthService.DefaultClientId,
            ["auth_session"] = session.Token,
            ["user_properties"] = "{}"
        };

        var mainClass = fabricProfile.ValueKind == JsonValueKind.Object ? fabricProfile.Str("mainClass") : vj.Str("mainClass");
        if (string.IsNullOrEmpty(mainClass)) mainClass = "net.minecraft.client.main.Main";

        var args = new List<string>();
        args.Add("-Xmx" + ram + "G");
        args.Add("-Xms" + Math.Min(2, ram) + "G");
        if (jvm == "aikar")
        {
            args.AddRange(new[] { "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200",
                "-XX:+UnlockExperimentalVMOptions", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch",
                "-XX:G1HeapRegionSize=4M", "-XX:G1ReservePercent=20", "-XX:InitiatingHeapOccupancyPercent=15" });
        }
        else if (jvm == "zgc")
        {
            args.AddRange(new[] { "-XX:+UseZGC", "-XX:+ZGenerational", "-XX:+UnlockExperimentalVMOptions" });
        }
        else if (jvm == "low") args.AddRange(new[] { "-XX:+UseSerialGC", "-XX:MaxGCPauseMillis=200" });
        if (fabricProfile.ValueKind == JsonValueKind.Object && fabricProfile.TryGetProperty("arguments", out var fargs)
            && fargs.TryGetProperty("jvm", out var fjvm))
        {
            foreach (var item in fjvm.EnumerateArray())
            {
                if (item.ValueKind == JsonValueKind.String) args.Add(Sub(item.GetString(), dict));
                else if (Allowed(item) && item.TryGetProperty("value", out var fval))
                {
                    if (fval.ValueKind == JsonValueKind.Array)
                        foreach (var v in fval.EnumerateArray()) args.Add(Sub(v.GetString(), dict));
                    else if (fval.ValueKind == JsonValueKind.String) args.Add(Sub(fval.GetString(), dict));
                }
            }
        }
        args.Add("-Djava.library.path=" + natives);
        args.Add("-Dfile.encoding=UTF-8");
        args.Add("-cp");
        args.Add(string.Join(Path.PathSeparator, cp.Distinct()));
        args.Add(mainClass);
        foreach (var a in GameArguments(vj, dict)) args.Add(a);

        Stage("Launching", 98, "Java " + major + " · " + mainClass);
        var psi = new ProcessStartInfo(java)
        {
            UseShellExecute = false,
            WorkingDirectory = dir,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            CreateNoWindow = true
        };
        foreach (var a in args) psi.ArgumentList.Add(a);
        var proc = Process.Start(psi) ?? throw new Exception("Java process did not start.");
        _processes[p.Str("id", name)] = proc;
        proc.Exited += (_, _) =>
        {
            string tail;
            lock (_logTail) tail = string.Join("\n", _logTail.TakeLast(14));
            _emit(Util.Json(new { type = "crash", instance = name, exitCode = proc.ExitCode, tail }));
        };
        _ = Task.Run(() => Tail(proc.StandardOutput));
        _ = Task.Run(() => Tail(proc.StandardError));
        Log($"[Lunax] {session.Username} launched {version} · Java {major} · {libCount} libraries");
        Stage("Running", 100, "PID " + proc.Id);
        return Util.Json(new { ok = true, pid = proc.Id, java, javaMajor = major, version, loader, mainClass });
    }

    public string Analyze(string profileName)
    {
        var dir = InstanceDir(profileName);
        var log = Path.Combine(dir, "logs", "latest.log");
        var crashDir = Path.Combine(dir, "crash-reports");
        var crash = Directory.Exists(crashDir)
            ? Directory.GetFiles(crashDir, "crash-*.txt").OrderByDescending(File.GetLastWriteTimeUtc).FirstOrDefault()
            : null;
        var logText = File.Exists(log) ? File.ReadAllText(log) : "";
        var crashText = crash != null ? File.ReadAllText(crash) : "";
        var all = crashText + "\n" + logText;
        var lines = logText.Split('\n').Where(x => !string.IsNullOrWhiteSpace(x)).TakeLast(16).ToArray();

        string cause, fix;
        if (all.Contains("Only one quick play option"))
        {
            cause = "Conflicting Quick Play flags were passed to Minecraft.";
            fix = "This is fixed in the current Lunax build. Delete the crash report and launch again.";
        }
        else if (all.Contains("UnsupportedClassVersionError") || all.Contains("class file has wrong version"))
        {
            cause = "Java version is too old for this Minecraft version.";
            fix = "Open Profile Settings → Java & Memory and select a newer Java (Java 21/25), or install Temurin.";
        }
        else if (all.Contains("OutOfMemoryError") || all.Contains("Java heap space"))
        {
            cause = "The game ran out of memory.";
            fix = "Lower the RAM allocation to 4–8 GB in Profile Settings → Java & Memory, or close other apps.";
        }
        else if (all.Contains("Could not find or load main class") || all.Contains("ClassNotFoundException"))
        {
            cause = "A Minecraft library or the version JAR is missing or damaged.";
            fix = "Delete game/versions/<version> in %LOCALAPPDATA%\\Lunax and launch again to re-download.";
        }
        else if (all.Contains("Invalid token") || all.Contains("authentication") || all.Contains("Not authenticated"))
        {
            cause = "The Microsoft session token is invalid or expired.";
            fix = "Open Settings → Accounts, refresh the token or sign in again with Microsoft.";
        }
        else if (all.Contains("Download failed") || all.Contains("verification failed") || all.Contains("SHA"))
        {
            cause = "A downloaded file failed its checksum verification.";
            fix = "Delete the affected cache file under game/ and launch again; Lunax will re-download it.";
        }
        else if (all.Contains("zip END header not found") || all.Contains("ZipException") || all.Contains("not a valid zip"))
        {
            cause = "A .jar file in the instance mods folder is corrupt or not a real mod jar.";
            fix = "Delete the named file from mods/ and reinstall the mod from Modrinth. Lunax now rejects invalid jars on import.";
        }
        else if (all.Contains("No Java installation") || all.Contains("java.exe"))
        {
            cause = "No compatible Java runtime was found.";
            fix = "Install Temurin Java 8 and 21/25, or set JAVA_HOME, then launch again.";
        }
        else if (crashText.Contains("Description:"))
        {
            cause = crashText.Split('\n').First(x => x.Contains("Description:")).Trim();
            fix = "Open the full crash report with the button below and check the mod list for incompatible versions.";
        }
        else
        {
            cause = logText.Length == 0
                ? "No game log was produced — the JVM never started."
                : "No obvious crash signature was found in the log.";
            fix = "Check that the profile has a valid account and Java runtime, then launch again.";
        }
        return Util.Json(new
        {
            ok = logText.Length > 0,
            cause, fix, lines,
            logPath = log,
            crashPath = crash ?? "",
            hasCrash = crash != null
        });
    }

    public void BackupInstance(string dir, string name)
    {
        try
        {
            var stamp = DateTime.Now.ToString("yyyyMMdd-HHmmss");
            var target = Path.Combine(_root, "backups", Util.Safe(name), stamp);
            Directory.CreateDirectory(target);
            var cfg = Path.Combine(dir, "config");
            if (Directory.Exists(cfg))
                foreach (var f in Directory.EnumerateFiles(cfg, "*", SearchOption.AllDirectories))
                {
                    var rel = Path.GetRelativePath(dir, f);
                    var dest = Path.Combine(target, rel);
                    Directory.CreateDirectory(Path.GetDirectoryName(dest)!);
                    File.Copy(f, dest, true);
                }
            var opts = Path.Combine(dir, "options.txt");
            if (File.Exists(opts)) File.Copy(opts, Path.Combine(target, "options.txt"), true);
        }
        catch { }
    }

    public string RestoreConfigs(string name)
    {
        var baseDir = Path.Combine(_root, "backups", Util.Safe(name));
        if (!Directory.Exists(baseDir)) throw new Exception("No config backup found for " + name + ".");
        var newest = Directory.GetDirectories(baseDir).OrderByDescending(x => x).FirstOrDefault();
        if (newest == null) throw new Exception("No config backup found for " + name + ".");
        var dir = InstanceDir(name);
        int count = 0;
        foreach (var f in Directory.EnumerateFiles(newest, "*", SearchOption.AllDirectories))
        {
            var rel = Path.GetRelativePath(newest, f);
            var dest = Path.Combine(dir, rel);
            Directory.CreateDirectory(Path.GetDirectoryName(dest)!);
            File.Copy(f, dest, true);
            count++;
        }
        return Util.Json(new { restored = count, from = newest });
    }

    public string Stop(string profileId)
    {
        if (!_processes.TryRemove(profileId, out var proc)) return Util.Json(new { stopped = false });
        try { if (!proc.HasExited) { proc.CloseMainWindow(); if (!proc.WaitForExit(4000)) proc.Kill(true); } } catch { }
        return Util.Json(new { stopped = true });
    }

    private async Task Tail(StreamReader reader)
    {
        try { while (!reader.EndOfStream) { var line = await reader.ReadLineAsync(); if (!string.IsNullOrWhiteSpace(line)) Log(line); } }
        catch { }
    }

    private static IEnumerable<string> GameArguments(JsonElement vj, Dictionary<string, string> dict)
    {
        var result = new List<string>();
        if (vj.TryGetProperty("arguments", out var arguments) && arguments.TryGetProperty("game", out var game))
        {
            foreach (var item in game.EnumerateArray())
            {
                if (item.ValueKind == JsonValueKind.String) { result.Add(Sub(item.GetString(), dict)); continue; }
                if (!Allowed(item)) continue;
                if (!item.TryGetProperty("value", out var val)) continue;
                if (val.ValueKind == JsonValueKind.Array)
                    foreach (var v in val.EnumerateArray()) result.Add(Sub(v.GetString(), dict));
                else if (val.ValueKind == JsonValueKind.String) result.Add(Sub(val.GetString(), dict));
            }
        }
        else if (vj.TryGetProperty("minecraftArguments", out var legacy))
        {
            foreach (var token in legacy.GetString().Split(' ', StringSplitOptions.RemoveEmptyEntries)) result.Add(Sub(token, dict));
        }
        return result;
    }

    private static (string url, string path) MavenLibrary(JsonElement lib)
    {
        var parts = lib.Str("name").Split(':');
        var group = parts[0];
        var artifact = parts.Length > 1 ? parts[1] : parts[0];
        var version = parts.Length > 2 ? parts[2] : "1.0";
        var classifier = "";
        var ext = "jar";
        if (parts.Length > 3)
        {
            var tail = parts[3];
            if (tail.Contains('@')) { var split = tail.Split('@'); classifier = split[0]; ext = split[1]; }
            else classifier = tail;
        }
        var path = $"{group.Replace('.', '/')}/{artifact}/{version}/{artifact}-{version}{(string.IsNullOrEmpty(classifier) ? "" : "-" + classifier)}.{ext}";
        var baseUrl = string.IsNullOrEmpty(lib.Str("url")) ? "https://maven.fabricmc.net/" : lib.Str("url");
        return (baseUrl.TrimEnd('/') + "/" + path, path);
    }

    private static string Sub(string template, Dictionary<string, string> dict)
    {
        if (string.IsNullOrEmpty(template)) return "";
        return Regex.Replace(template, @"\$\{(\w+)\}", m => dict.TryGetValue(m.Groups[1].Value, out var v) ? v : "");
    }

    private static bool Allowed(JsonElement element)
    {
        if (!element.TryGetProperty("rules", out var rules) || rules.ValueKind != JsonValueKind.Array) return true;
        bool allow = false;
        foreach (var r in rules.EnumerateArray())
        {
            bool match = true;
            if (r.TryGetProperty("os", out var os) && os.TryGetProperty("name", out var n) && n.GetString() != "windows") match = false;
            // Optional features (demo, quick play, custom resolution) are not enabled by Lunax.
            // Without this check every quick-play flag would be passed, and modern Minecraft
            // crashes with "Only one quick play option can be specified".
            if (r.TryGetProperty("features", out var features) && features.ValueKind == JsonValueKind.Object)
                foreach (var feature in features.EnumerateObject())
                    if (feature.Value.ValueKind == JsonValueKind.True) match = false;
            if (match) allow = r.Str("action") == "allow";
        }
        return allow;
    }

    private (string path, int major) FindJava(string version)
    {
        int required = version.StartsWith("26.") ? 25
            : version.StartsWith("1.21") ? 21
            : version.StartsWith("1.20") || version.StartsWith("1.19") || version.StartsWith("1.18") || version.StartsWith("1.17") ? 17
            : 8;
        var candidates = new List<string>();
        void Add(string exe) { if (!string.IsNullOrEmpty(exe) && File.Exists(exe)) candidates.Add(exe); }

        var home = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrEmpty(home)) Add(Path.Combine(home, "bin", "java.exe"));

        // Lunax-managed runtimes (installed by the one-click Java installer).
        var managed = Path.Combine(_root, "runtimes");
        if (Directory.Exists(managed))
            foreach (var f in Directory.EnumerateFiles(managed, "java.exe", SearchOption.AllDirectories)) Add(f);

        // Common vendor install locations.
        string[] roots = {
            @"C:\Program Files\Eclipse Adoptium", @"C:\Program Files\Java",
            @"C:\Program Files (x86)\Java", @"C:\Program Files\Microsoft",
            @"C:\Program Files\Amazon Corretto", @"C:\Program Files\BellSoft",
            @"C:\Program Files\Zulu", @"C:\Program Files\OpenJDK",
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "Eclipse Adoptium"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "Microsoft")
        };
        foreach (var root in roots)
        {
            if (!Directory.Exists(root)) continue;
            foreach (var d in Directory.GetDirectories(root))
            {
                Add(Path.Combine(d, "bin", "java.exe"));
                Add(Path.Combine(d, "bin", "java"));
            }
        }
        Add(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Java", "latest", "bin", "java.exe"));

        // Official Minecraft launcher keeps private runtimes under .minecraft\runtime.
        var mcRuntime = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".minecraft", "runtime");
        if (Directory.Exists(mcRuntime))
            foreach (var f in Directory.EnumerateFiles(mcRuntime, "java.exe", SearchOption.AllDirectories)) Add(f);

        // Anything on PATH.
        try
        {
            var psi = new ProcessStartInfo("cmd.exe", "/c where java") { RedirectStandardOutput = true, UseShellExecute = false, CreateNoWindow = true };
            using var proc = Process.Start(psi)!;
            var output = proc.StandardOutput.ReadToEnd();
            proc.WaitForExit(3000);
            foreach (var line in output.Split('\n')) Add(line.Trim());
        }
        catch { }

        candidates = candidates.Distinct(StringComparer.OrdinalIgnoreCase).ToList();
        var found = candidates.Select(path => (path, major: Major(path))).Where(x => x.major > 0)
            .OrderByDescending(x => x.major).ToList();
        if (found.Count == 0)
            throw new Exception("No Java " + required + "+ runtime found. Click 'Install Java automatically' or install Java 21/25 and restart Lunax.");
        var exact = found.FirstOrDefault(x => x.major == required);
        if (exact.path != null) return exact;
        var compatible = found.FirstOrDefault(x => x.major >= required && !(required == 8 && x.major > 8));
        return compatible.path != null ? compatible : found[0];
    }

    private static readonly Dictionary<int, string> JavaCache = new();

    public void ClearJavaCache() { lock (JavaCache) JavaCache.Clear(); }

    public string DetectJava(int major)
    {
        lock (JavaCache) if (JavaCache.TryGetValue(major, out var cached)) return cached;
        var version = major switch { 25 => "26.3", 21 => "1.21.11", 17 => "1.20.4", 8 => "1.8.9", _ => "1.21.11" };
        string result;
        try { result = FindJava(version).path; } catch { result = ""; }
        lock (JavaCache) JavaCache[major] = result;
        return result;
    }

    private static int Major(string java)
    {
        try
        {
            var psi = new ProcessStartInfo(java, "-version") { RedirectStandardError = true, UseShellExecute = false, CreateNoWindow = true };
            var proc = Process.Start(psi)!;
            var text = proc.StandardError.ReadToEnd();
            proc.WaitForExit(4000);
            var m = Regex.Match(text, "version \"([0-9]+)(?:\\.([0-9]+))?");
            if (!m.Success) return 0;
            var first = int.Parse(m.Groups[1].Value);
            return first == 1 && m.Groups[2].Success ? int.Parse(m.Groups[2].Value) : first;
        }
        catch { return 0; }
    }
}
