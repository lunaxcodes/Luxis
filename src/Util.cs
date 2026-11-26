using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace Lunax;

internal static class Util
{
    public static readonly HttpClient Http = new() { Timeout = TimeSpan.FromMinutes(8) };

    static Util()
    {
        Http.DefaultRequestHeaders.UserAgent.ParseAdd("Lunax/1.0 (desktop launcher; local build)");
        Http.DefaultRequestHeaders.Accept.ParseAdd("application/json");
    }

    public static async Task<string> Get(string url, string bearer = null)
    {
        using var req = new HttpRequestMessage(HttpMethod.Get, url);
        if (!string.IsNullOrEmpty(bearer)) req.Headers.Authorization = new("Bearer", bearer);
        using var res = await Http.SendAsync(req);
        var body = await res.Content.ReadAsStringAsync();
        if (!res.IsSuccessStatusCode) throw new Exception($"HTTP {(int)res.StatusCode} from {new Uri(url).Host}: {Truncate(body)}");
        return body;
    }

    public static async Task<JsonDocument> GetJson(string url, string bearer = null)
        => JsonDocument.Parse(await Get(url, bearer));

    public static async Task<string> Post(string url, string content, string contentType, string bearer = null)
    {
        using var req = new HttpRequestMessage(HttpMethod.Post, url);
        req.Content = new StringContent(content, Encoding.UTF8, contentType);
        if (!string.IsNullOrEmpty(bearer)) req.Headers.Authorization = new("Bearer", bearer);
        using var res = await Http.SendAsync(req);
        var body = await res.Content.ReadAsStringAsync();
        return body; // callers inspect error bodies (OAuth needs this)
    }

    public static Task<string> PostForm(string url, Dictionary<string, string> fields)
        => Post(url, string.Join("&", fields.Select(kv =>
            Uri.EscapeDataString(kv.Key) + "=" + Uri.EscapeDataString(kv.Value ?? ""))),
            "application/x-www-form-urlencoded");

    public static async Task<string> PostJson(string url, object payload, string bearer = null)
        => await Post(url, JsonSerializer.Serialize(payload), "application/json", bearer);

    public static async Task<string> PostJsonWithHeaders(string url, object payload, Dictionary<string, string> headers)
    {
        using var req = new HttpRequestMessage(HttpMethod.Post, url)
        {
            Content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json")
        };
        foreach (var h in headers) req.Headers.TryAddWithoutValidation(h.Key, h.Value);
        using var res = await Http.SendAsync(req);
        return await res.Content.ReadAsStringAsync();
    }

    public static JsonDocument ParseJson(string body, string context, bool emptyIsObject = false)
    {
        if (string.IsNullOrWhiteSpace(body))
        {
            if (emptyIsObject) return JsonDocument.Parse("{}");
            throw new Exception(context + " returned an empty response.");
        }
        try { return JsonDocument.Parse(body); }
        catch (JsonException)
        {
            if (emptyIsObject) return JsonDocument.Parse("{}");
            throw new Exception(context + " returned invalid JSON: " + Truncate(body, 160));
        }
    }

    public static string Truncate(string s, int n = 300)
        => string.IsNullOrEmpty(s) ? "" : (s.Length > n ? s[..n] + "…" : s);

    public static string Safe(string name)
    {
        var s = Regex.Replace(name ?? "", @"[^a-zA-Z0-9._-]", "-");
        return string.IsNullOrWhiteSpace(s) ? "instance" : s;
    }

    public static string Quote(string s) => "\"" + (s ?? "").Replace("\"", "\\\"") + "\"";

    public static string Str(this JsonElement e, string key, string fallback = "")
        => e.ValueKind == JsonValueKind.Object && e.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.String
            ? v.GetString() ?? fallback : fallback;

    public static int Int(this JsonElement e, string key, int fallback = 0)
        => e.ValueKind == JsonValueKind.Object && e.TryGetProperty(key, out var v) && v.TryGetInt32(out var n) ? n : fallback;

    public static IEnumerable<JsonElement> Items(this JsonElement e, string key)
        => e.ValueKind == JsonValueKind.Object && e.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.Array
            ? v.EnumerateArray() : Enumerable.Empty<JsonElement>();

    public static string HashFile(string path, string algorithm)
    {
        using var stream = File.OpenRead(path);
        using HashAlgorithm hash = algorithm.Equals("sha1", StringComparison.OrdinalIgnoreCase) ? (HashAlgorithm)SHA1.Create() : SHA512.Create();
        return Convert.ToHexString(hash.ComputeHash(stream)).ToLowerInvariant();
    }

    public static async Task Download(string url, string path, string sha1 = null, string sha512 = null,
        Action<long, long> progress = null)
    {
        if (File.Exists(path) &&
            (string.IsNullOrEmpty(sha1) || HashFile(path, "sha1").Equals(sha1, StringComparison.OrdinalIgnoreCase)) &&
            (string.IsNullOrEmpty(sha512) || HashFile(path, "sha512").Equals(sha512, StringComparison.OrdinalIgnoreCase)))
            return;

        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var tmp = path + ".part";
        using (var res = await Http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead))
        {
            if (!res.IsSuccessStatusCode)
                throw new Exception($"Download failed ({(int)res.StatusCode}) {url}");
            var total = res.Content.Headers.ContentLength ?? 0;
            await using var src = await res.Content.ReadAsStreamAsync();
            await using var dst = new FileStream(tmp, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true);
            var buffer = new byte[81920];
            long done = 0;
            int read;
            while ((read = await src.ReadAsync(buffer)) > 0)
            {
                await dst.WriteAsync(buffer.AsMemory(0, read));
                done += read;
                progress?.Invoke(done, total);
            }
        }
        if (!string.IsNullOrEmpty(sha1) && !HashFile(tmp, "sha1").Equals(sha1, StringComparison.OrdinalIgnoreCase))
            throw new Exception($"SHA-1 verification failed for {Path.GetFileName(path)}");
        if (!string.IsNullOrEmpty(sha512) && !HashFile(tmp, "sha512").Equals(sha512, StringComparison.OrdinalIgnoreCase))
            throw new Exception($"SHA-512 verification failed for {Path.GetFileName(path)}");
        File.Move(tmp, path, true);
    }

    public static string Json(object obj) => JsonSerializer.Serialize(obj);
}
