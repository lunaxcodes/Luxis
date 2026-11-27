using System.Diagnostics;
using System.Reflection;
using System.Security.Cryptography;
using System.Text.Json;

namespace Luxis;

public sealed class UpdateService
{
    private readonly string _root;
#if MAC
    public const string DefaultManifest = "https://github.com/lunaxcodes/Luxis/releases/latest/download/version-mac.json";
#else
    public const string DefaultManifest = "https://github.com/lunaxcodes/Luxis/releases/latest/download/version.json";
#endif
    public string UpdateUrl { get; private set; } = DefaultManifest;

    private static string MacManifest()
    {
#if MAC
        var arch = System.Runtime.InteropServices.RuntimeInformation.ProcessArchitecture
            == System.Runtime.InteropServices.Architecture.Arm64 ? "arm64" : "x64";
        return $"https://github.com/lunaxcodes/Luxis/releases/latest/download/version-mac-{arch}.json";
#else
        return DefaultManifest;
#endif
    }

    public UpdateService(string root)
    {
        _root = root;
        UpdateUrl = MacManifest();
        try
        {
            var settings = JsonDocument.Parse(File.ReadAllText(Path.Combine(root, "settings.json")));
            var saved = settings.RootElement.Str("updateUrl");
            if (!string.IsNullOrWhiteSpace(saved)) UpdateUrl = saved;
        }
        catch { }
    }

    public void SetUrl(string url)
    {
        UpdateUrl = string.IsNullOrWhiteSpace(url) ? DefaultManifest : url.Trim();
        var path = Path.Combine(_root, "settings.json");
        JsonElement settings = default;
        try { settings = JsonDocument.Parse(File.ReadAllText(path)).RootElement.Clone(); } catch { }
        var map = new Dictionary<string, object>();
        if (settings.ValueKind == JsonValueKind.Object)
            foreach (var p in settings.EnumerateObject()) map[p.Name] = p.Value.Clone();
        map["updateUrl"] = UpdateUrl;
        File.WriteAllText(path, Util.Json(map));
    }

    private string CurrentVersion()
    {
        try { return File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "version.txt")).Trim(); }
        catch { }
        try
        {
            using var resource = Assembly.GetExecutingAssembly().GetManifestResourceStream("Luxis.version.txt");
            if (resource != null)
            {
                using var reader = new StreamReader(resource);
                var value = reader.ReadToEnd().Trim();
                if (!string.IsNullOrEmpty(value)) return value;
            }
        }
        catch { }
        return "0.0.0";
    }

    public async Task<string> Check()
    {
        if (string.IsNullOrWhiteSpace(UpdateUrl))
            return Util.Json(new { available = false, current = CurrentVersion(), latest = "", message = "No update URL configured." });
        try
        {
            using var doc = await Util.GetJson(UpdateUrl);
            var r = doc.RootElement;
            var latest = r.Str("version", "0.0.0");
            var current = CurrentVersion();
            var available = Version.TryParse(latest, out var lv) && Version.TryParse(current, out var cv) && lv > cv;
            var exe = r.Str("executable", "Luxis.exe");
            var url = r.Str("url");
            if (string.IsNullOrEmpty(url))
            {
                var idx = UpdateUrl.LastIndexOf("version.json", StringComparison.OrdinalIgnoreCase);
                url = idx >= 0 ? UpdateUrl[..idx] + exe : UpdateUrl.Substring(0, UpdateUrl.LastIndexOf('/') + 1) + Uri.EscapeDataString(exe);
            }
            return Util.Json(new { available, current, latest, url, sha256 = r.Str("sha256"), size = r.Int("size"), message = "" });
        }
        catch (Exception ex) { return Util.Json(new { available = false, current = CurrentVersion(), latest = "", message = ex.Message }); }
    }

    public async Task<string> Install(string manifestJson)
    {
        using var doc = JsonDocument.Parse(manifestJson);
        var r = doc.RootElement;
        var url = r.Str("url");
        var sha = r.Str("sha256");
        var version = r.Str("version", "0.0.0");
        if (string.IsNullOrEmpty(url)) throw new Exception("Update URL missing.");
        var target = Process.GetCurrentProcess().MainModule?.FileName ?? Path.Combine(AppContext.BaseDirectory, "Luxis.exe");
        var versionFile = Path.Combine(Path.GetDirectoryName(target) ?? AppContext.BaseDirectory, "version.txt");
        var tmp = Path.Combine(Path.GetTempPath(), "luxis-update-" + Guid.NewGuid().ToString("N") + ".exe");
        // The manifest hash is SHA-256; Util.Download's 4th argument is SHA-512.
        // Download unchecked here and verify with SHA-256 below.
        await Util.Download(url, tmp);
        using var stream = File.OpenRead(tmp);
        var actual = Convert.ToHexString(SHA256.HashData(stream)).ToLowerInvariant();
        if (!string.IsNullOrEmpty(sha) && actual != sha.ToLowerInvariant())
            throw new Exception("Update failed SHA-256 verification.");

#if MAC
        var script = Path.Combine(Path.GetTempPath(), "luxis-update-" + Guid.NewGuid().ToString("N") + ".sh");
        File.WriteAllText(script,
            "#!/bin/bash\n" +
            "sleep 3\n" +
            $"cp -f \"{tmp}\" \"{target}\"\n" +
            $"printf '%s' \"{version}\" > \"{versionFile}\"\n" +
            $"open \"{target}\"\n" +
            "rm -f \"$0\"\n");
        Process.Start(new ProcessStartInfo("/bin/bash", "\"" + script + "\"") { UseShellExecute = false, CreateNoWindow = true });
#else
        var bat = Path.Combine(Path.GetTempPath(), "luxis-update-" + Guid.NewGuid().ToString("N") + ".bat");
        File.WriteAllText(bat,
            "@echo off\r\n" +
            "timeout /t 3 /nobreak >nul\r\n" +
            "set /a n=0\r\n" +
            ":retry\r\n" +
            $"copy /Y \"{tmp}\" \"{target}\" >nul\r\n" +
            $"echo {version}> \"{versionFile}\"\r\n" +
            "if errorlevel 1 (\r\n" +
            "  set /a n+=1\r\n" +
            "  if %n% LSS 20 (timeout /t 1 /nobreak >nul & goto retry)\r\n" +
            ")\r\n" +
            $"start \"\" \"{target}\"\r\n" +
            "del \"%~f0\"\r\n");
        Process.Start(new ProcessStartInfo("cmd.exe", "/c \"" + bat + "\"") { UseShellExecute = false, CreateNoWindow = true });
#endif
        return Util.Json(new { started = true });
    }
}
