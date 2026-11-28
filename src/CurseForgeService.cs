using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace Luxis;

/// <summary>
/// CurseForge's public API requires a personal key. The key is kept in settings.json,
/// protected with the same per-user DPAPI scheme used for Microsoft refresh tokens.
/// Results follow the same JSON contract as ModrinthService so the content UI can share cards.
/// </summary>
public sealed class CurseForgeService
{
    private const string Base = "https://api.curseforge.com/v1";
    private const int GameId = 432;
    private const int Mods = 6, ResourcePacks = 12, Shaders = 6552;
    private readonly MinecraftService _minecraft;
    private readonly string _settings;
    private string _key = "";

    public CurseForgeService(MinecraftService minecraft, string root)
    {
        _minecraft = minecraft;
        _settings = Path.Combine(root, "settings.json");
        try
        {
            using var doc = JsonDocument.Parse(File.ReadAllText(_settings));
            var saved = doc.RootElement.Str("curseforgeKey");
            if (!string.IsNullOrWhiteSpace(saved)) _key = Decrypt(saved);
        }
        catch { }
    }

    public bool Configured => !string.IsNullOrWhiteSpace(_key);

    public string Status() => Util.Json(new
    {
        configured = Configured,
        message = Configured ? "CurseForge is ready. Your key stays on this computer." :
                "Open Settings → Accounts & content to add a free CurseForge API key."
    });

    public string SetKey(string key)
    {
        key = (key ?? "").Trim();
        var map = new Dictionary<string, object>();
        try
        {
            using var doc = JsonDocument.Parse(File.ReadAllText(_settings));
            if (doc.RootElement.ValueKind == JsonValueKind.Object)
                foreach (var property in doc.RootElement.EnumerateObject()) map[property.Name] = property.Value.Clone();
        }
        catch { }
        if (key.Length == 0) { map.Remove("curseforgeKey"); _key = ""; }
        else { map["curseforgeKey"] = Encrypt(key); _key = key; }
        try { File.WriteAllText(_settings, Util.Json(map)); }
        catch (Exception e)
        {
            if (key.Length > 0) _key = "";
            return Util.Json(new { ok = false, message = e.Message });
        }
        return Util.Json(new { ok = true, configured = Configured });
    }

    private static string Encrypt(string value)
        => Convert.ToBase64String(ProtectedData.Protect(Encoding.UTF8.GetBytes(value), null, DataProtectionScope.CurrentUser));

    private static string Decrypt(string value)
        => Encoding.UTF8.GetString(ProtectedData.Unprotect(Convert.FromBase64String(value), null, DataProtectionScope.CurrentUser));

    private async Task<JsonElement> Get(string path)
    {
        if (!Configured) throw new Exception("Add your CurseForge API key in Settings first.");
        using var request = new HttpRequestMessage(HttpMethod.Get, Base + path);
        request.Headers.TryAddWithoutValidation("x-api-key", _key);
        using var response = await Util.Http.SendAsync(request);
        var body = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode)
            throw new Exception($"CurseForge HTTP {(int)response.StatusCode}: {Util.Truncate(body, 140)}");
        using var doc = JsonDocument.Parse(body);
        return doc.RootElement.Clone();
    }

    private static string Encode(string value) => Uri.EscapeDataString(value ?? "");

    private static int ClassId(string projectType) => projectType switch
    {
        "resourcepack" => ResourcePacks,
        "shader" => Shaders,
        _ => Mods
    };

    private static int LoaderId(string loader) => (loader ?? "").ToLowerInvariant() switch
    {
        "forge" => 1,
        "fabric" => 4,
        "quilt" => 5,
        "neoforge" => 6,
        _ => 0
    };

    private static string Icon(JsonElement mod)
    {
        if (mod.ValueKind != JsonValueKind.Object) return "";
        var links = mod.GetProperty("links");
        var url = links.Str("iconUrl");
        if (!string.IsNullOrWhiteSpace(url)) return url;
        if (links.TryGetProperty("logo", out var logo)) return logo.Str("url");
        return mod.Str("iconUrl");
    }

    private static object Simplify(JsonElement mod)
    {
        var id = mod.Int("id").ToString();
        var categories = mod.Items("categories")
            .Take(3).Select(c => c.ValueKind == JsonValueKind.Number ? "Class " + c : c.Str("name", ""))
            .Where(x => !string.IsNullOrWhiteSpace(x)).ToArray();
        return new
        {
            id,
            slug = mod.Str("slug", id),
            title = mod.Str("name", "CurseForge project"),
            description = mod.Str("summary"),
            iconUrl = Icon(mod),
            downloads = mod.Int("downloadCount"),
            categories,
            projectType = "mod",
            followers = 0
        };
    }

    public async Task<string> Search(string query, string projectType, string loader, string gameVersion, string category, int offset)
    {
        if (!Configured) return Util.Json(new { hits = Array.Empty<object>(), total = 0, offset, error = "CurseForge needs an API key." });
        var url = $"/mods/search?gameId={GameId}&classId={ClassId(projectType)}&searchFilter={Encode(query)}" +
                  $"&sortField=6&sortOrder=desc&pageSize=24&index={Math.Max(0, offset)}";
        if (!string.IsNullOrWhiteSpace(gameVersion) && gameVersion != "All") url += "&gameVersion=" + Encode(gameVersion);
        if (projectType == "mod" && LoaderId(loader) > 0) url += "&modLoaderType=" + LoaderId(loader);
        var doc = await Get(url);
        var hits = doc.Items("data").Select(Simplify).ToArray();
        var total = doc.TryGetProperty("pagination", out var page) ? page.Int("totalCount", hits.Length) : hits.Length;
        return Util.Json(new { hits, total, offset });
    }

    private static string MajorVersion(string version)
    {
        var parts = (version ?? "").Split('.');
        return parts.Length < 2 ? version : string.Join('.', parts.Take(2));
    }

    private async Task<JsonElement[]> FetchFiles(string projectId, string gameVersion, string loader, bool relaxed)
    {
        int loaderId = LoaderId(loader);
        async Task<JsonElement[]> Ask(string version, int loaderFilter)
        {
            var url = $"/mods/{Encode(projectId)}/files?pageSize=50&index=0&sortField=1&sortOrder=desc";
            if (!string.IsNullOrWhiteSpace(version) && version != "All") url += "&gameVersion=" + Encode(version);
            if (loaderFilter > 0) url += "&modLoaderType=" + loaderFilter;
            var doc = await Get(url);
            return doc.Items("data").Select(x => x.Clone()).ToArray();
        }
        var files = await Ask(gameVersion, loaderId);
        if (files.Length == 0 && !string.IsNullOrWhiteSpace(gameVersion) && gameVersion != "All")
            files = await Ask(MajorVersion(gameVersion), loaderId);
        if (files.Length == 0 && loaderId > 0) files = await Ask("All", loaderId);
        if (files.Length == 0 && relaxed) files = await Ask("All", 0);
        return files;
    }

    private static string Hash(JsonElement file, int algorithm)
        => file.Items("hashes").FirstOrDefault(h => h.Int("algo") == algorithm).Str("value");

    private static object SimplifyFile(JsonElement file, string projectId)
    {
        var versions = file.Items("gameVersions").Select(v => v.GetString() ?? "").Where(x => x.Length > 0).ToArray();
        var id = file.Int("id").ToString();
        return new
        {
            id,
            versionNumber = file.Str("displayName", file.Str("fileName", "Release")),
            changelog = "",
            datePublished = file.Str("fileDate"),
            gameVersions = versions,
            fileId = id,
            files = new[]
            {
                new
                {
                    filename = file.Str("fileName"),
                    url = file.Str("downloadUrl"),
                    primary = true,
                    hashes = new { sha1 = Hash(file, 1), sha512 = "" }
                }
            }
        };
    }

    public async Task<string> Versions(string projectId, string gameVersion, string loader, bool relaxed = false)
    {
        var files = await FetchFiles(projectId, gameVersion, loader, relaxed);
        return Util.Json(files.Take(12).Select(f => SimplifyFile(f, projectId)).ToArray());
    }

    public async Task<string> GetProjectIcon(string projectId)
    {
        var doc = await Get($"/mods/{Encode(projectId)}");
        return Icon(doc.GetProperty("data"));
    }

    private static string SubFolder(string type) => type switch
    {
        "resourcepack" => "resourcepacks",
        "shader" => "shaderpacks",
        _ => "mods"
    };

    public async Task<string> Install(string projectJson, string profileJson)
    {
        using var request = JsonDocument.Parse(projectJson);
        using var profile = JsonDocument.Parse(profileJson);
        var p = request.RootElement;
        var profileData = profile.RootElement;
        if (!Configured) throw new Exception("Add your CurseForge API key in Settings first.");

        var id = p.Str("id");
        if (string.IsNullOrWhiteSpace(id)) throw new Exception("No CurseForge project was selected.");
        var gameVersion = profileData.Str("version", "26.3");
        var loader = profileData.Str("loader", "fabric");
        var type = p.Str("projectType", "mod");
        var name = profileData.Str("name", "Instance");

        var meta = await Get($"/mods/{Encode(id)}");
        var mod = meta.GetProperty("data");
        var title = mod.Str("name", p.Str("title", id));
        var icon = Icon(mod);
        var files = await FetchFiles(id, gameVersion, loader, type != "mod");
        if (files.Length == 0) throw new Exception($"No CurseForge file of {title} supports {gameVersion} / {loader}.");

        var installed = new List<object>();
        var warnings = new List<string>();
        await InstallFile(files[0], id, title, icon, type, gameVersion, loader, name, installed, warnings, 0);
        return Util.Json(new { installed, warnings });
    }

    private async Task InstallFile(JsonElement file, string projectId, string title, string icon, string type,
        string gameVersion, string loader, string profileName, List<object> installed, List<string> warnings, int depth)
    {
        var fileName = file.Str("fileName");
        if (string.IsNullOrWhiteSpace(fileName)) return;
        var fileId = file.Int("id").ToString();
        var sub = SubFolder(type);
        var dir = Path.Combine(_minecraft.InstanceDir(profileName), sub);
        Directory.CreateDirectory(dir);
        var target = Path.Combine(dir, Path.GetFileName(fileName));

        var url = file.Str("downloadUrl");
        if (string.IsNullOrWhiteSpace(url))
        {
            var link = await Get($"/mods/{Encode(projectId)}/files/{Encode(fileId)}/download-url");
            url = link.Str("data");
        }
        if (string.IsNullOrWhiteSpace(url)) throw new Exception("CurseForge did not return a download URL for " + fileName);
        await Util.Download(url, target, Hash(file, 1));

        installed.Add(new
        {
            id = projectId,
            projectId,
            title,
            file = Path.GetFileName(target),
            version = file.Str("displayName", "Release"),
            type = sub,
            icon,
            source = "curseforge",
            cfFileId = fileId,
            sha1 = Hash(file, 1),
            sha512 = ""
        });

        if (depth >= 1) return;
        foreach (var dependency in file.Items("dependencies"))
        {
            var relation = dependency.Int("relationType");
            var depId = dependency.Int("modId").ToString();
            if (relation == 2) { warnings.Add("Optional dependency available: " + depId); continue; }
            if (relation == 5) { warnings.Add("Incompatible dependency skipped: " + depId); continue; }
            if (relation != 3) continue;
            try
            {
                var meta = await Get($"/mods/{Encode(depId)}");
                var depMod = meta.GetProperty("data");
                var depFiles = await FetchFiles(depId, gameVersion, loader, false);
                if (depFiles.Length == 0) { warnings.Add("No compatible file for dependency " + depId); continue; }
                await InstallFile(depFiles[0], depId, depMod.Str("name", depId), Icon(depMod), "mod",
                    gameVersion, loader, profileName, installed, warnings, depth + 1);
            }
            catch (Exception e) { warnings.Add("Dependency " + depId + " failed: " + Util.Truncate(e.Message, 90)); }
        }
    }

    public async Task<string> CheckUpdate(string fileId, string projectId)
    {
        if (!Configured || string.IsNullOrWhiteSpace(projectId)) return Util.Json(new { update = false });
        try
        {
            var doc = await Get($"/mods/{Encode(projectId)}/files?pageSize=1&index=0&sortField=1&sortOrder=desc");
            var newest = doc.Items("data").FirstOrDefault();
            var newestId = newest.Int("id").ToString();
            return Util.Json(new
            {
                update = !string.IsNullOrWhiteSpace(newestId) && newestId != fileId,
                version = newest.Str("displayName", "")
            });
        }
        catch { return Util.Json(new { update = false }); }
    }
}
