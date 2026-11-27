using System.Reflection;
using Photino.NET;

namespace Luxis;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        var root = Platform.Root;
        var appDir = Path.Combine(root, "app");
        Directory.CreateDirectory(root);
        Directory.CreateDirectory(appDir);
        Directory.CreateDirectory(Path.Combine(root, "instances"));
        Directory.CreateDirectory(Path.Combine(root, "game"));

        var window = new PhotinoWindow
        {
            Title = "Luxis",
            Width = 1440,
            Height = 900,
            MinWidth = 1024,
            MinHeight = 680,
            Centered = true,
            UseOsDefaultLocation = false,
            UseOsDefaultSize = false,
            WebSecurityEnabled = false,
            DevToolsEnabled = false,
            LogVerbosity = 0
        };
        var host = new MacHost(window);
        Action<string> emit = json => host.Post(json);

        var auth = new AuthService(root);
        var minecraft = new MinecraftService(root, auth, emit);
        var modrinth = new ModrinthService(minecraft);
        var mrpack = new MrpackService(minecraft);
        var updates = new UpdateService(root);
        var java = new JavaInstaller(root, emit);
        var bridge = new MacBridge(host, auth, minecraft, modrinth, mrpack, updates, java);

        var server = new BridgeServer(bridge, appDir, BridgeServer.FreePort());
        server.Start();

        var html = Platform.ResourceText("Luxis.index.html");
        var shim = JSBridge.Generate(bridge, server.Port);
        html = html.Replace("<head>", "<head><script>" + shim + "</script>");
        File.WriteAllText(Path.Combine(appDir, "index.html"), html);
        Textures.EnsureBundled(appDir);
        Textures.Export(appDir, Path.Combine(root, "game"));

        window.Load(server.Origin + "/");
        window.WaitForClose();
        server.Dispose();
    }
}

internal sealed class MacHost : IMacHost
{
    private readonly PhotinoWindow _window;
    public MacHost(PhotinoWindow window) { _window = window; }

    public void Post(string json)
    {
        try { _window.SendWebMessage(json); }
        catch { }
    }

    public void Minimize() { try { _window.Invoke(() => _window.Minimized = true); } catch { } }
    public void Maximize() { try { _window.Invoke(() => _window.Maximized = !_window.Maximized); } catch { } }
    public void Close() { try { _window.Invoke(() => _window.Close()); } catch { } }

    public string[] PickFiles(string title, string filterName, string[] extensions, bool multiple)
    {
        string[]? picked = null;
        using var done = new ManualResetEventSlim(false);
        try
        {
            _window.Invoke(() =>
            {
                try { picked = _window.ShowOpenFile(title, "*." + extensions[0], multiple, new[] { (filterName, extensions) }); }
                finally { done.Set(); }
            });
            done.Wait();
        }
        catch { return Array.Empty<string>(); }
        return picked ?? Array.Empty<string>();
    }
}
