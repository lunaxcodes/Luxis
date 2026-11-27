using System.Diagnostics;
using System.IO.Compression;
using System.Text.Json;

namespace Luxis;

public interface IMacHost
{
    void Minimize();
    void Maximize();
    void Close();
    void Post(string json);
    string[] PickFiles(string title, string filterName, string[] extensions, bool multiple);
}

public sealed class MacBridge
{
    private readonly IMacHost _host;
    private readonly AuthService _auth;
    private readonly MinecraftService _minecraft;
    private readonly ModrinthService _modrinth;
    private readonly MrpackService _mrpack;
    private readonly UpdateService _updates;
    private readonly JavaInstaller _java;

    public MacBridge(IMacHost host, AuthService auth, MinecraftService minecraft, ModrinthService modrinth,
        MrpackService mrpack, UpdateService updates, JavaInstaller java)
    {
        _host = host;
        _auth = auth;
        _minecraft = minecraft;
        _modrinth = modrinth;
        _mrpack = mrpack;
        _updates = updates;
        _java = java;
    }

    private static string NotOnMac(string feature) => Util.Json(new
    {
        ok = false,
        installed = false,
        version = "",
        wu = "Not on macOS",
        wuStart = "",
        ready = false,
        message = feature + " is not available on macOS."
    });

    public void minimize() => _host.Minimize();
    public void maximize() => _host.Maximize();
    public void close() => _host.Close();
    public void drag() { }

    public void openUrl(string url)
    {
        if (string.IsNullOrWhiteSpace(url)) return;
        try { Process.Start(new ProcessStartInfo("/usr/bin/open", url) { UseShellExecute = false, CreateNoWindow = true }); }
        catch { }
    }

    public void openFolder(string folder, string profileName)
    {
        var path = Path.Combine(_minecraft.InstanceDir(profileName), folder ?? "");
        Directory.CreateDirectory(path);
        try { Process.Start(new ProcessStartInfo("/usr/bin/open", path) { UseShellExecute = false, CreateNoWindow = true }); }
        catch { }
    }

    public string listNativeAccounts() => _auth.ListAccounts();
    public string activeNativeAccount() => _auth.ActiveId;
    public string createOfflineAccount(string username) => _auth.CreateOffline(username);
    public string setActiveNativeAccount(string id) { _auth.SetActive(id); return "{\"ok\":true}"; }
    public string removeNativeAccount(string id) { _auth.Remove(id); return "{\"ok\":true}"; }
    public string getMsClientId() => _auth.ClientId;
    public string mojangCapes(string id) => _auth.MojangCapes(id);
    public Task<string> setMojangCape(string id, string capeId) => _auth.SetActiveCape(id, capeId);
    public Task<string> getCapeTexture(string id, string capeId) => _auth.GetCapeTexture(id, capeId);

    public string javaStatus()
    {
        var majors = new[] { 8, 17, 21, 25 };
        return Util.Json(majors.ToDictionary(m => m.ToString(), m =>
        {
            var managed = _java.JavaExe(m);
            return string.IsNullOrEmpty(managed) ? _minecraft.DetectJava(m) : managed;
        }));
    }

    public async Task<string> installJava(int major)
    {
        var result = await _java.Install(major);
        _minecraft.ClearJavaCache();
        return result;
    }

    public string getUpdateUrl() => _updates.UpdateUrl;
    public string setUpdateUrl(string url) { _updates.SetUrl(url); return "{\"ok\":true}"; }
    public Task<string> checkUpdate() => _updates.Check();
    public Task<string> installUpdate(string manifestJson) => _updates.Install(manifestJson);
    public string getMsTenant() => _auth.Tenant;
    public string setMsTenant(string tenant) { _auth.SetTenant(tenant); return "{\"ok\":true}"; }
    public string getAuthDebug() => _auth.GetAuthDebug();
    public string setMsClientId(string id) { _auth.SetClientId(id); return "{\"ok\":true}"; }

    public Task<string> beginMicrosoftLogin() => _auth.BeginLogin();
    public Task<string> pollMicrosoftLogin(string flowId) => _auth.PollLogin(flowId);
    public string beginCodeLogin() => _auth.BeginCodeLogin();
    public Task<string> finishCodeLogin(string flowId, string pasted) => _auth.FinishCodeLogin(flowId, pasted);
    public Task<string> refreshMicrosoftAccount(string id) => _auth.Refresh(id);
    public Task<string> getAccountTexture(string id, string kind) => _auth.GetTexture(id, kind);

    public Task<string> launchProfile(string profileJson, string activeAccountId) => _minecraft.Launch(profileJson, activeAccountId);
    public string stopProfile(string profileId) => _minecraft.Stop(profileId);
    public string analyzeInstance(string profileName) => _minecraft.Analyze(profileName);
    public string toggleModFile(string profileName, string file, bool enabled) => _minecraft.ToggleMod(profileName, file, enabled);

    public string listModFiles(string profileName)
    {
        var dir = Path.Combine(_minecraft.InstanceDir(profileName), "mods");
        if (!Directory.Exists(dir)) return "[]";
        var names = Directory.GetFiles(dir)
            .Select(Path.GetFileName)
            .Where(n => n != null && (n.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || n.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase)))
            .ToArray();
        return Util.Json(names);
    }

    private string ModsDir(string profileName) => Path.Combine(_minecraft.InstanceDir(profileName), "mods");
    public string luxisModStatus(string profileName, string version) => BundledLuxisMod.Status(ModsDir(profileName), version);
    public string installLuxisMod(string profileName, string version) => BundledLuxisMod.Install(ModsDir(profileName), version, enable: true);
    public string updateLuxisMod(string profileName, string version) => BundledLuxisMod.Install(ModsDir(profileName), version, enable: false);
    public string removeLuxisMod(string profileName) => BundledLuxisMod.Remove(ModsDir(profileName));

    private static bool IsValidJar(string path)
    {
        try { using var zip = ZipFile.OpenRead(path); return zip.Entries.Count > 0; }
        catch { return false; }
    }

    public Task<string> pickJarFiles(string profileName)
    {
        var paths = _host.PickFiles("Install mods into " + profileName, "Minecraft mods", new[] { "jar" }, true);
        var dir = Path.Combine(_minecraft.InstanceDir(profileName), "mods");
        Directory.CreateDirectory(dir);
        var names = new List<string>();
        var rejected = new List<string>();
        foreach (var path in paths ?? Array.Empty<string>())
        {
            var name = Path.GetFileName(path);
            if (!IsValidJar(path)) { rejected.Add(name); continue; }
            File.Copy(path, Path.Combine(dir, name), true);
            names.Add(name);
        }
        return Task.FromResult(Util.Json(new { files = names, rejected }));
    }

    public string importJarFiles(string pathsJson, string profileName)
    {
        string[] paths;
        try { paths = JsonSerializer.Deserialize<string[]>(pathsJson) ?? Array.Empty<string>(); }
        catch { paths = Array.Empty<string>(); }
        var dir = Path.Combine(_minecraft.InstanceDir(profileName), "mods");
        Directory.CreateDirectory(dir);
        var names = new List<string>();
        var rejected = new List<string>();
        foreach (var path in paths)
        {
            if (!File.Exists(path)) continue;
            var name = Path.GetFileName(path);
            if (!name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)) continue;
            if (!IsValidJar(path)) { rejected.Add(name); continue; }
            File.Copy(path, Path.Combine(dir, name), true);
            names.Add(name);
        }
        return Util.Json(new { files = names, rejected });
    }

    public string instancePath(string profileName) => _minecraft.InstanceDir(profileName);

    public string instanceStats(string profileName)
    {
        var dir = _minecraft.InstanceDir(profileName);
        long bytes = 0;
        int files = 0;
        if (Directory.Exists(dir))
            foreach (var f in Directory.EnumerateFiles(dir, "*", SearchOption.AllDirectories))
            { try { bytes += new FileInfo(f).Length; files++; } catch { } }
        return Util.Json(new { bytes, files });
    }

    public Task<string> searchModrinth(string query, string type, string loader, string version, string category, int offset)
        => _modrinth.Search(query, type, loader, version, category, offset);
    public Task<string> modrinthVersions(string projectId, string version, string loader, bool relaxed = false)
        => _modrinth.Versions(projectId, version, loader, relaxed);
    public Task<string> installModrinth(string projectJson, string profileJson)
        => _modrinth.Install(projectJson, profileJson);
    public Task<string> checkModrinthUpdate(string sha512, string projectId)
        => _modrinth.CheckUpdate(sha512, projectId);
    public Task<string> getProjectIcon(string projectId) => _modrinth.GetProjectIcon(projectId);

    public string restoreConfigs(string profileName) => _minecraft.RestoreConfigs(profileName);
    public string deleteModFile(string profileName, string fileName) => _minecraft.DeleteModFile(profileName, fileName);

    public string bedrockStatus() => NotOnMac("Minecraft Bedrock");
    public string fixWindowsUpdate() => NotOnMac("Windows Update repair");
    public string launchBedrock() => NotOnMac("Minecraft Bedrock");

    public Task<string> importMrpack(string path) => _mrpack.Import(path);

    public Task<string> pickMrpack()
    {
        var paths = _host.PickFiles("Import Modrinth modpack", "Modpack", new[] { "mrpack", "zip" }, false);
        if (paths == null || paths.Length == 0) return Task.FromResult("{\"cancelled\":true}");
        return _mrpack.Import(paths[0]);
    }
}
