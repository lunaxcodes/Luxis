using System.Net;
using System.Reflection;
using System.Text;
using System.Text.Json;

namespace Luxis;

/// <summary>Loopback-only bridge that lets the web UI call native services synchronously or asynchronously.</summary>
public sealed class BridgeServer : IDisposable
{
    private readonly HttpListener _listener = new();
    private readonly MacBridge _bridge;
    private readonly string _appDir;
    private readonly CancellationTokenSource _stop = new();
    public int Port { get; }
    public string Origin => $"http://127.0.0.1:{Port}";

    public BridgeServer(MacBridge bridge, string appDir, int port)
    {
        _bridge = bridge;
        _appDir = appDir;
        Port = port;
        _listener.Prefixes.Add($"http://127.0.0.1:{port}/");
    }

    public void Start()
    {
        _listener.Start();
        _ = Task.Run(Loop);
    }

    private async Task Loop()
    {
        while (!_stop.IsCancellationRequested && _listener.IsListening)
        {
            HttpListenerContext context;
            try { context = await _listener.GetContextAsync(); }
            catch { break; }
            _ = Task.Run(() => Handle(context));
        }
    }

    private async Task Handle(HttpListenerContext context)
    {
        var path = context.Request.Url?.AbsolutePath ?? "/";
        try
        {
            if (path == "/call")
            {
                using var reader = new StreamReader(context.Request.InputStream, Encoding.UTF8);
                var body = await reader.ReadToEndAsync();
                using var doc = JsonDocument.Parse(body);
                var result = await Invoke(doc.RootElement.GetProperty("m").GetString()!,
                    doc.RootElement.TryGetProperty("a", out var a) ? a : default);
                await Respond(context, result, "application/json; charset=utf-8");
                return;
            }
            var relative = path.TrimStart('/');
            if (string.IsNullOrEmpty(relative)) relative = "index.html";
            if (relative.Contains("..")) { context.Response.StatusCode = 403; context.Response.Close(); return; }
            var file = Path.Combine(_appDir, relative.Replace('/', Path.DirectorySeparatorChar));
            if (!File.Exists(file)) { context.Response.StatusCode = 404; context.Response.Close(); return; }
            var type = Path.GetExtension(file).ToLowerInvariant() switch
            {
                ".html" => "text/html; charset=utf-8",
                ".png" => "image/png",
                ".json" => "application/json; charset=utf-8",
                ".txt" => "text/plain; charset=utf-8",
                _ => "application/octet-stream"
            };
            context.Response.ContentType = type;
            context.Response.Headers.Add("Cache-Control", "no-store");
            var bytes = await File.ReadAllBytesAsync(file);
            context.Response.ContentLength64 = bytes.Length;
            await context.Response.OutputStream.WriteAsync(bytes);
        }
        catch (Exception ex)
        {
            try { await Respond(context, "{\"ok\":false,\"error\":" + JsonSerializer.Serialize(ex.Message) + "}", "application/json"); }
            catch { }
        }
        finally { try { context.Response.Close(); } catch { } }
    }

    private static async Task Respond(HttpListenerContext context, string text, string type)
    {
        var bytes = Encoding.UTF8.GetBytes(text ?? "{}");
        context.Response.ContentType = type;
        context.Response.ContentLength64 = bytes.Length;
        await context.Response.OutputStream.WriteAsync(bytes);
    }

    private async Task<string> Invoke(string name, JsonElement args)
    {
        var method = typeof(MacBridge).GetMethods(BindingFlags.Instance | BindingFlags.Public | BindingFlags.DeclaredOnly)
            .FirstOrDefault(m => m.Name == name) ?? throw new MissingMethodException(name);
        var parameters = method.GetParameters();
        var values = new object?[parameters.Length];
        int count = args.ValueKind == JsonValueKind.Array ? args.GetArrayLength() : 0;
        for (int i = 0; i < parameters.Length; i++)
        {
            var parameter = parameters[i];
            if (i < count)
                values[i] = JsonSerializer.Deserialize(args[i].GetRawText(), parameter.ParameterType);
            else if (parameter.HasDefaultValue)
                values[i] = parameter.DefaultValue;
            else if (parameter.ParameterType == typeof(bool))
                values[i] = false;
            else if (parameter.ParameterType == typeof(int))
                values[i] = 0;
            else
                values[i] = null;
        }
        var result = method.Invoke(_bridge, values);
        if (result is Task<string> textTask) return await textTask;
        if (result is Task plainTask) { await plainTask; return "{}"; }
        return result?.ToString() ?? "{}";
    }

    public static int FreePort()
    {
        var listener = new System.Net.Sockets.TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port;
        listener.Stop();
        return port;
    }

    public void Dispose()
    {
        _stop.Cancel();
        try { _listener.Stop(); _listener.Close(); } catch { }
    }
}
