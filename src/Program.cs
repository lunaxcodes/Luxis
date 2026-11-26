using System.Diagnostics;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text.RegularExpressions;
using System.Text.Json;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace Luxis;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm());
    }
}

public sealed class MainForm : Form
{
    private readonly WebView2 _web = new();
    private readonly string _root;
    private readonly AuthService _auth;
    private readonly MinecraftService _minecraft;
    private readonly ModrinthService _modrinth;
    private readonly MrpackService _mrpack;
    private readonly UpdateService _updates;
    private readonly JavaInstaller _java;

    public MainForm()
    {
        _root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Luxis");
        MigrateLegacyData(_root);
        _auth = new AuthService(_root);
        _minecraft = new MinecraftService(_root, _auth, Post);
        _modrinth = new ModrinthService(_minecraft);
        _mrpack = new MrpackService(_minecraft);
        _updates = new UpdateService(_root);
        _java = new JavaInstaller(_root, Post);

        Text = "Luxis";
        Size = new Size(1440, 900);
        MinimumSize = new Size(1024, 680);
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.None;
        BackColor = Color.FromArgb(15, 12, 22);
        Controls.Add(_web);
        _web.Dock = DockStyle.Fill;
        _web.DefaultBackgroundColor = Color.FromArgb(15, 12, 22);
        Load += async (_, _) => await InitializeAsync();
    }

    private static void MigrateLegacyData(string root)
    {
        var legacy = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Lunax");
        if (string.Equals(legacy, root, StringComparison.OrdinalIgnoreCase)) return;
        if (!Directory.Exists(legacy) || Directory.Exists(root)) return;
        try { Directory.Move(legacy, root); return; }
        catch { }
        try
        {
            Directory.CreateDirectory(root);
            foreach (var dir in Directory.GetDirectories(legacy, "*", SearchOption.AllDirectories))
                Directory.CreateDirectory(dir.Replace(legacy, root));
            foreach (var file in Directory.GetFiles(legacy, "*", SearchOption.AllDirectories))
            {
                var dest = file.Replace(legacy, root);
                Directory.CreateDirectory(Path.GetDirectoryName(dest)!);
                File.Copy(file, dest, true);
            }
        }
        catch { }
    }

    private void Post(string json)
    {
        try { if (_web.CoreWebView2 != null) _web.CoreWebView2.PostWebMessageAsJson(json); }
        catch { /* window closed mid-operation */ }
    }

    private async Task InitializeAsync()
    {
        try
        {
            var userData = Path.Combine(_root, "WebView2");
            var appFiles = Path.Combine(_root, "app");
            Directory.CreateDirectory(userData);
            Directory.CreateDirectory(appFiles);
            Directory.CreateDirectory(Path.Combine(_root, "instances"));

            using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("Luxis.index.html")
                ?? throw new InvalidOperationException("Embedded UI resource is missing.");
            using var buffer = new MemoryStream();
            await stream.CopyToAsync(buffer);
            await File.WriteAllBytesAsync(Path.Combine(appFiles, "index.html"), buffer.ToArray());
            Textures.EnsureBundled(appFiles);
            Textures.Export(appFiles, Path.Combine(_root, "game"));

            var env = await CoreWebView2Environment.CreateAsync(userDataFolder: userData);
            await _web.EnsureCoreWebView2Async(env);
            _web.CoreWebView2.SetVirtualHostNameToFolderMapping("luxis.local", appFiles, CoreWebView2HostResourceAccessKind.Allow);
            _web.CoreWebView2.AddHostObjectToScript("bridge", new Bridge(this, _auth, _minecraft, _modrinth, _mrpack, _updates, _java, _root));
            await _web.CoreWebView2.AddScriptToExecuteOnDocumentCreatedAsync(
                "window.__luxis = window.chrome?.webview?.hostObjects?.bridge || null;" +
                "window.__luxisSync = window.chrome?.webview?.hostObjects?.sync?.bridge || null;");
            _web.NavigationCompleted += (_, args) =>
            {
                if (args.IsSuccess) File.WriteAllText(Path.Combine(appFiles, "ready.txt"), DateTimeOffset.UtcNow.ToString("O"));
            };
            _web.CoreWebView2.Navigate("https://luxis.local/index.html");
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, "Luxis could not start:\n\n" + ex.Message, "Luxis", MessageBoxButtons.OK, MessageBoxIcon.Error);
            Close();
        }
    }

    public void MinimizeWindow() => BeginInvoke(() => WindowState = FormWindowState.Minimized);
    public void MaximizeWindow() => BeginInvoke(() =>
        WindowState = WindowState == FormWindowState.Maximized ? FormWindowState.Normal : FormWindowState.Maximized);
    public void CloseWindow() => BeginInvoke(Close);

    public void StartDrag()
    {
        if (InvokeRequired) { BeginInvoke(StartDrag); return; }
        NativeMethods.ReleaseCapture();
        NativeMethods.SendMessage(Handle, 0x00A1, (IntPtr)0x0002, IntPtr.Zero); // WM_NCLBUTTONDOWN / HTCAPTION
    }
}

[ComVisible(true)]
public sealed class Bridge
{
    private readonly MainForm _form;
    private readonly AuthService _auth;
    private readonly MinecraftService _minecraft;
    private readonly ModrinthService _modrinth;
    private readonly MrpackService _mrpack;
    private readonly UpdateService _updates;
    private readonly JavaInstaller _java;
    private readonly string _root;

    public Bridge(MainForm form, AuthService auth, MinecraftService minecraft, ModrinthService modrinth, MrpackService mrpack, UpdateService updates, JavaInstaller java, string root)
    { _form = form; _auth = auth; _minecraft = minecraft; _modrinth = modrinth; _mrpack = mrpack; _updates = updates; _java = java; _root = root; }

    // ---- native window ----
    public void minimize() => _form.MinimizeWindow();
    public void maximize() => _form.MaximizeWindow();
    public void close() => _form.CloseWindow();
    public void drag() => _form.StartDrag();

    public void openUrl(string url)
    {
        if (string.IsNullOrWhiteSpace(url)) return;
        Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
    }

    public void openFolder(string folder, string profileName)
    {
        var path = Path.Combine(_minecraft.InstanceDir(profileName), folder ?? "");
        Directory.CreateDirectory(path);
        Process.Start(new ProcessStartInfo("explorer.exe", path) { UseShellExecute = true });
    }

    // ---- accounts ----
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

    // ---- game ----
    public Task<string> launchProfile(string profileJson, string activeAccountId) => _minecraft.Launch(profileJson, activeAccountId);
    public string stopProfile(string profileId) => _minecraft.Stop(profileId);
    public string analyzeInstance(string profileName) => _minecraft.Analyze(profileName);
    public string toggleModFile(string profileName, string file, bool enabled) => _minecraft.ToggleMod(profileName, file, enabled);

    // ---- bundled in-game Luxis mod ----
    public string luxisModStatus(string profileName)
    {
        var mods = Path.Combine(_minecraft.InstanceDir(profileName), "mods");
        var jar = Path.Combine(mods, "LuxisClientMod.jar");
        var fabric = Directory.Exists(mods) && Directory.GetFiles(mods, "fabric-api*.jar").Length > 0;
        return Util.Json(new { installed = File.Exists(jar) || File.Exists(jar + ".disabled"), enabled = File.Exists(jar), fabricApi = fabric });
    }

    public string installLuxisMod(string profileName)
    {
        var mods = Path.Combine(_minecraft.InstanceDir(profileName), "mods");
        Directory.CreateDirectory(mods);
        if (Directory.GetFiles(mods, "fabric-api*.jar").Length == 0)
            return Util.Json(new { ok = false, error = "Install Fabric API 0.167.0+26.3 in this instance first." });
        using var src = Assembly.GetExecutingAssembly().GetManifestResourceStream("Luxis.LuxisClientMod.jar");
        if (src == null) return Util.Json(new { ok = false, error = "Bundled mod file is missing." });
        var jar = Path.Combine(mods, "LuxisClientMod.jar");
        using (var dst = File.Create(jar)) src.CopyTo(dst);
        var off = jar + ".disabled";
        if (File.Exists(off)) File.Delete(off);
        return Util.Json(new { ok = true, file = "LuxisClientMod.jar" });
    }

    public string removeLuxisMod(string profileName)
    {
        var mods = Path.Combine(_minecraft.InstanceDir(profileName), "mods");
        try
        {
            File.Delete(Path.Combine(mods, "LuxisClientMod.jar"));
            File.Delete(Path.Combine(mods, "LuxisClientMod.jar.disabled"));
        }
        catch { }
        return Util.Json(new { ok = true });
    }

    private static bool IsValidJar(string path)
    {
        try { using var zip = ZipFile.OpenRead(path); return zip.Entries.Count > 0; }
        catch { return false; }
    }

    public Task<string> pickJarFiles(string profileName)
    {
        var tcs = new TaskCompletionSource<string>();
        _form.BeginInvoke(new Action(() =>
        {
            try
            {
                using var dialog = new OpenFileDialog
                {
                    Title = "Install mods into " + profileName,
                    Filter = "Minecraft mods (*.jar)|*.jar|All files (*.*)|*.*",
                    Multiselect = true,
                    CheckFileExists = true
                };
                if (dialog.ShowDialog(_form) != DialogResult.OK) { tcs.SetResult("{\"files\":[]}"); return; }
                var dir = Path.Combine(_minecraft.InstanceDir(profileName), "mods");
                Directory.CreateDirectory(dir);
                var names = new List<string>();
                var rejected = new List<string>();
                foreach (var path in dialog.FileNames)
                {
                    var name = Path.GetFileName(path);
                    if (!IsValidJar(path)) { rejected.Add(name); continue; }
                    File.Copy(path, Path.Combine(dir, name), true);
                    names.Add(name);
                }
                tcs.SetResult(Util.Json(new { files = names, rejected }));
            }
            catch (Exception ex) { tcs.SetException(ex); }
        }));
        return tcs.Task;
    }

    public string importJarFiles(string pathsJson, string profileName)
    {
        string[] paths;
        try { paths = System.Text.Json.JsonSerializer.Deserialize<string[]>(pathsJson) ?? Array.Empty<string>(); }
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
        long bytes = 0; int files = 0;
        if (Directory.Exists(dir))
            foreach (var f in Directory.EnumerateFiles(dir, "*", SearchOption.AllDirectories))
            { try { bytes += new FileInfo(f).Length; files++; } catch { } }
        return Util.Json(new { bytes, files });
    }

    // ---- modrinth ----
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

    public string bedrockStatus()
    {
        try
        {
            var psi = new ProcessStartInfo("powershell",
                "-NoProfile -Command \"(Get-AppxPackage -Name Microsoft.MinecraftUWP | Select-Object -First 1 -ExpandProperty Version)\"")
            { RedirectStandardOutput = true, UseShellExecute = false, CreateNoWindow = true };
            using var proc = Process.Start(psi)!;
            var version = proc.StandardOutput.ReadToEnd().Trim();
            proc.WaitForExit(5000);
            return Util.Json(new { installed = !string.IsNullOrEmpty(version), version });
        }
        catch { return Util.Json(new { installed = false, version = "" }); }
    }

    public string launchBedrock()
    {
        using var status = JsonDocument.Parse(bedrockStatus());
        if (!status.RootElement.GetProperty("installed").GetBoolean())
            throw new Exception("Minecraft Bedrock is not installed on this PC.");
        // Windows UWP activation is brittle when invoked from another process. Opening the
        // Minecraft Launcher (same Microsoft account) is the reliable way to reach Bedrock.
        Process.Start(new ProcessStartInfo("explorer.exe", @"shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft")
        { UseShellExecute = true });
        return Util.Json(new { ok = true, edition = "bedrock", via = "Minecraft Launcher" });
    }

    public Task<string> importMrpack(string path) => _mrpack.Import(path);

    public Task<string> pickMrpack()
    {
        var tcs = new TaskCompletionSource<string>();
        _form.BeginInvoke(new Action(() =>
        {
            try
            {
                using var dialog = new OpenFileDialog
                {
                    Title = "Import Modrinth modpack",
                    Filter = "Modrinth modpack (*.mrpack;*.zip)|*.mrpack;*.zip",
                    CheckFileExists = true
                };
                if (dialog.ShowDialog(_form) != DialogResult.OK) { tcs.SetResult("{\"cancelled\":true}"); return; }
                _ = _mrpack.Import(dialog.FileName).ContinueWith(t =>
                {
                    if (t.IsFaulted) tcs.SetException(t.Exception!.InnerException ?? t.Exception);
                    else tcs.SetResult(t.Result);
                });
            }
            catch (Exception ex) { tcs.SetException(ex); }
        }));
        return tcs.Task;
    }
}

internal static class NativeMethods
{
    [DllImport("user32.dll")]
    internal static extern bool ReleaseCapture();

    [DllImport("user32.dll", CharSet = CharSet.Auto)]
    internal static extern IntPtr SendMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
}
