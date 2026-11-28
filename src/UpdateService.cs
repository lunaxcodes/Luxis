using System.Diagnostics;
using System.Reflection;
using System.Security.Cryptography;
using System.Text.Json;

namespace Luxis;

public sealed class UpdateService
{
    private readonly string _root;
    private readonly Action<string> _emit;
    public const string DefaultManifest = "https://github.com/lunaxcodes/Luxis/releases/latest/download/version.json";
    public string UpdateUrl { get; private set; } = DefaultManifest;

    public UpdateService(string root, Action<string> emit = null)
    {
        _root = root;
        _emit = emit;
        UpdateUrl = DefaultManifest;
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

    /// <summary>
    /// Downloads a verified update and starts a detached helper that waits for this launcher
    /// to exit, replaces the executable, restores version.txt and relaunches it.
    /// The UI must close the launcher after this returns, otherwise Windows keeps Luxis.exe locked.
    /// </summary>
    public async Task<string> Install(string manifestJson)
    {
        using var doc = JsonDocument.Parse(manifestJson);
        var r = doc.RootElement;
        var url = r.Str("url");
        var sha = r.Str("sha256");
        var version = r.Str("version", "0.0.0");
        if (string.IsNullOrEmpty(url)) throw new Exception("Update URL missing.");
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps)
            throw new Exception("Update URL must be HTTPS.");
        if (!Version.TryParse(version, out _)) throw new Exception("Invalid update version.");

        var target = Process.GetCurrentProcess().MainModule?.FileName;
        if (string.IsNullOrEmpty(target) || !target.EndsWith("Luxis.exe", StringComparison.OrdinalIgnoreCase))
            throw new Exception("Could not locate the running Luxis.exe. Use the installer or portable build.");
        var versionFile = Path.Combine(Path.GetDirectoryName(target)!, "version.txt");

        var tmp = Path.Combine(Path.GetTempPath(), "luxis-update-" + Guid.NewGuid().ToString("N") + ".exe");
        var lastPercent = -1;
        _emit?.Invoke(Util.Json(new { type = "update-progress", stage = "Downloading", percent = 0 }));
        await Util.Download(url, tmp, progress: (done, total) =>
        {
            var percent = total > 0 ? (int)(100d * done / total) : 0;
            if (percent == lastPercent) return;
            lastPercent = percent;
            _emit?.Invoke(Util.Json(new { type = "update-progress", stage = "Downloading",
                downloaded = done, total, percent }));
        });
        _emit?.Invoke(Util.Json(new { type = "update-progress", stage = "Verifying", percent = 100 }));
        using (var stream = File.OpenRead(tmp))
        {
            var actual = Convert.ToHexString(SHA256.HashData(stream)).ToLowerInvariant();
            if (!string.IsNullOrEmpty(sha) && actual != sha.ToLowerInvariant())
            {
                TryDelete(tmp);
                throw new Exception("Update failed SHA-256 verification.");
            }
        }

        _emit?.Invoke(Util.Json(new { type = "update-progress", stage = "Installing", percent = 100 }));
        var pid = Environment.ProcessId;
        var script = Path.Combine(Path.GetTempPath(), "luxis-update-" + Guid.NewGuid().ToString("N") + ".ps1");
        File.WriteAllText(script, UpdateScript(pid, tmp, target, versionFile, version));
        try
        {
            var start = new ProcessStartInfo("powershell.exe")
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                WindowStyle = ProcessWindowStyle.Hidden
            };
            foreach (var arg in new[] { "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script })
                start.ArgumentList.Add(arg);
            Process.Start(start)?.Dispose();
        }
        catch
        {
            TryDelete(script);
            TryDelete(tmp);
            throw;
        }
        return Util.Json(new { started = true, pid });
    }

    internal static string UpdateScript(int pid, string source, string target, string versionFile, string version)
    {
        // Single-quoted PowerShell literals: only apostrophes need doubling.
        static string Q(string value) => "'" + value.Replace("'", "''") + "'";
        return "$ErrorActionPreference = 'Stop'\n" +
               "$luxisPid = " + pid + "\n" +
               "$source = " + Q(source) + "\n" +
               "$target = " + Q(target) + "\n" +
               "$versionFile = " + Q(versionFile) + "\n" +
               "$version = " + Q(version) + "\n" +
               "# Wait for the launcher to release its own executable.\n" +
               "for ($i = 0; $i -lt 60; $i++) {\n" +
               "  if (-not (Get-Process -Id $luxisPid -ErrorAction SilentlyContinue)) { break }\n" +
               "  Start-Sleep -Milliseconds 500\n" +
               "}\n" +
               "$replaced = $false\n" +
               "for ($i = 0; $i -lt 30; $i++) {\n" +
               "  try {\n" +
               "    Copy-Item -LiteralPath $source -Destination $target -Force -ErrorAction Stop\n" +
               "    $replaced = $true\n" +
               "    break\n" +
               "  } catch { Start-Sleep -Milliseconds 500 }\n" +
               "}\n" +
               "if ($replaced) {\n" +
               "  [IO.File]::WriteAllText($versionFile, $version)\n" +
               "  Start-Process -FilePath $target\n" +
               "}\n" +
               "Remove-Item -LiteralPath $source -Force -ErrorAction SilentlyContinue\n" +
               "Remove-Item -LiteralPath $PSCommandPath -Force -ErrorAction SilentlyContinue\n";
    }

    private static void TryDelete(string path)
    {
        try { File.Delete(path); } catch { }
    }
}
