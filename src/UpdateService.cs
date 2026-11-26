using System.Diagnostics;
using System.Security.Cryptography;
using System.Text.Json;

namespace Lunax;

public sealed class UpdateService
{
    private readonly string _root;
    public const string DefaultManifest = "https://github.com/lunaxcodes/Lunax/releases/latest/download/version.json";
    public string UpdateUrl { get; private set; } = DefaultManifest;

    public UpdateService(string root)
    {
        _root = root;
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
        catch { return "0.0.0"; }
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
            var exe = r.Str("executable", "Lunax.exe");
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
        if (string.IsNullOrEmpty(url)) throw new Exception("Update URL missing.");
        var target = Process.GetCurrentProcess().MainModule?.FileName ?? Path.Combine(AppContext.BaseDirectory, "Lunax.exe");
        var tmp = Path.Combine(Path.GetTempPath(), "lunax-update-" + Guid.NewGuid().ToString("N") + ".exe");
        await Util.Download(url, tmp, null, string.IsNullOrEmpty(sha) ? null : sha);
        using var stream = File.OpenRead(tmp);
        var actual = Convert.ToHexString(SHA256.HashData(stream)).ToLowerInvariant();
        if (!string.IsNullOrEmpty(sha) && actual != sha.ToLowerInvariant())
            throw new Exception("Update failed SHA-256 verification.");

        var bat = Path.Combine(Path.GetTempPath(), "lunax-update-" + Guid.NewGuid().ToString("N") + ".bat");
        File.WriteAllText(bat,
            "@echo off\r\n" +
            "timeout /t 3 /nobreak >nul\r\n" +
            "set /a n=0\r\n" +
            ":retry\r\n" +
            $"copy /Y \"{tmp}\" \"{target}\" >nul\r\n" +
            "if errorlevel 1 (\r\n" +
            "  set /a n+=1\r\n" +
            "  if %n% LSS 20 (timeout /t 1 /nobreak >nul & goto retry)\r\n" +
            ")\r\n" +
            $"start \"\" \"{target}\"\r\n" +
            "del \"%~f0\"\r\n");
        Process.Start(new ProcessStartInfo("cmd.exe", "/c \"" + bat + "\"") { UseShellExecute = false, CreateNoWindow = true });
        return Util.Json(new { started = true });
    }
}
