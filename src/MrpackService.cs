using System.IO.Compression;
using System.Text.Json;

namespace Luxis;

/// <summary>Imports real Modrinth .mrpack archives (ZIP + modrinth.index.json).</summary>
public sealed class MrpackService
{
    private readonly MinecraftService _minecraft;
    public MrpackService(MinecraftService minecraft) { _minecraft = minecraft; }

    private static void D(string message)
    {
        try { File.AppendAllText(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Luxis", "mrpack-debug.log"), $"[{DateTime.UtcNow:O}] {message}\n"); }
        catch { }
    }

    public async Task<string> Import(string path)
    {
        try { D("START " + path); return await ImportInner(path); }
        catch (Exception ex) { D("ERROR " + ex); throw; }
    }

    private async Task<string> ImportInner(string path)
    {
        if (!File.Exists(path)) throw new Exception("Modpack file not found.");
        using var zip = ZipFile.OpenRead(path);
        var indexEntry = zip.GetEntry("modrinth.index.json")
                         ?? zip.Entries.FirstOrDefault(e => e.FullName.EndsWith("modrinth.index.json", StringComparison.OrdinalIgnoreCase));
        if (indexEntry == null) throw new Exception("Not a Modrinth modpack: modrinth.index.json is missing.");

        D("index entry found: " + indexEntry.FullName);
        using var indexStream = indexEntry.Open();
        using var doc = await JsonDocument.ParseAsync(indexStream);
        var root = doc.RootElement;
        var name = root.Str("name", Path.GetFileNameWithoutExtension(path));
        var deps = root.TryGetProperty("dependencies", out var d) && d.ValueKind == JsonValueKind.Object ? d : default;
        var version = root.Str("versionId", "");
        if (string.IsNullOrEmpty(version) || version == "1.0.0")
            version = deps.ValueKind == JsonValueKind.Object ? deps.Str("minecraft") : "";
        if (string.IsNullOrEmpty(version) && root.TryGetProperty("game", out var gameNode))
            version = gameNode.ValueKind == JsonValueKind.String ? gameNode.GetString() ?? "" : gameNode.EnumerateArray().FirstOrDefault().GetString() ?? "";
        if (string.IsNullOrEmpty(version)) version = "1.21.11";

        string loader = "fabric", loaderVersion = "0.19.5";
        if (deps.ValueKind == JsonValueKind.Object)
        {
            if (deps.TryGetProperty("fabric-loader", out var f)) { loader = "fabric"; loaderVersion = f.GetString() ?? loaderVersion; }
            else if (deps.TryGetProperty("quilt-loader", out var q)) { loader = "quilt"; loaderVersion = q.GetString() ?? loaderVersion; }
            else if (deps.TryGetProperty("neoforge", out var n)) { loader = "neoforge"; loaderVersion = n.GetString() ?? loaderVersion; }
            else if (deps.TryGetProperty("forge", out var g)) { loader = "forge"; loaderVersion = g.GetString() ?? loaderVersion; }
        }

        D($"parsed name={name} version={version} loader={loader}/{loaderVersion} files={root.Items("files").Count()}");
        var dir = _minecraft.InstanceDir(name);
        Directory.CreateDirectory(dir);
        var files = new List<object>();
        int done = 0;
        foreach (var file in root.Items("files"))
        {
            var rel = file.Str("path");
            if (string.IsNullOrEmpty(rel)) continue;
            var target = Path.Combine(dir, rel.Replace('/', Path.DirectorySeparatorChar));
            var hashes = file.TryGetProperty("hashes", out var h) ? h : default;
            var sha1 = hashes.ValueKind == JsonValueKind.Object ? hashes.Str("sha1") : "";
            var sha512 = hashes.ValueKind == JsonValueKind.Object ? hashes.Str("sha512") : "";
            var url = file.Items("downloads").Select(x => x.GetString()).FirstOrDefault(x => !string.IsNullOrEmpty(x));
            D($"file {done+1}: {rel} url={(string.IsNullOrEmpty(url)?"none":url.Substring(0, Math.Min(60,url.Length)))}");
            if (!string.IsNullOrEmpty(url))
                await Util.Download(url, target, string.IsNullOrEmpty(sha1) ? null : sha1,
                    string.IsNullOrEmpty(sha512) ? null : sha512);
            files.Add(new { path = rel, sha1, sha512, size = file.Int("fileSize") });
            done++;
        }

        // Apply overrides/ (config, resourcepacks, shaderpacks, saves, options.txt).
        int overrides = 0;
        foreach (var entry in zip.Entries)
        {
            if (!entry.FullName.StartsWith("overrides/", StringComparison.OrdinalIgnoreCase)) continue;
            if (string.IsNullOrEmpty(entry.Name)) continue;
            var rel = entry.FullName["overrides/".Length..].Replace('/', Path.DirectorySeparatorChar);
            var target = Path.Combine(dir, rel);
            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            using var src = entry.Open();
            using var dst = File.Create(target);
            src.CopyTo(dst);
            overrides++;
        }

        D($"DONE files={done} overrides={overrides}");
        return Util.Json(new { name, version, loader, loaderVersion, files, overrides, fileCount = done });
    }
}
