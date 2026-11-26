using System.Text.Json;

namespace Lunax;

public sealed class ModrinthService
{
    private const string Base = "https://api.modrinth.com/v2";
    private readonly MinecraftService _minecraft;

    public ModrinthService(MinecraftService minecraft) { _minecraft = minecraft; }

    public async Task<string> Search(string query, string projectType, string loader, string gameVersion, string category, int offset)
    {
        var facets = new List<List<string>> { new() { "project_type:" + (string.IsNullOrEmpty(projectType) ? "mod" : projectType) } };
        // Only mods carry a loader category on Modrinth; packs and shaders use their own categories.
        if (projectType == "mod" && !string.IsNullOrEmpty(loader) && loader != "All")
            facets.Add(new() { "categories:" + loader.ToLowerInvariant() });
        if (!string.IsNullOrEmpty(gameVersion) && gameVersion != "All") facets.Add(new() { "versions:" + gameVersion });
        if (!string.IsNullOrEmpty(category) && category != "All")
        {
            var cat = category.ToLowerInvariant();
            if (projectType == "shader")
                cat = cat.Contains("potato") || cat.Contains("low") ? "low" : cat.Contains("high") ? "high" : "medium";
            else if (projectType == "resourcepack")
            {
                if (cat.StartsWith("pvp")) cat = "pvp";
                else if (cat.Contains("ui")) cat = "gui";
                else if (cat.Contains("vanilla")) cat = "vanilla";
            }
            facets.Add(new() { "categories:" + cat });
        }
        var facetJson = System.Text.Json.JsonSerializer.Serialize(facets);
        var url = $"{Base}/search?query={Uri.EscapeDataString(query ?? "")}" +
                  $"&facets={Uri.EscapeDataString(facetJson)}&limit=24&offset={offset}&sort=downloads&index=downloads";
        using var doc = JsonDocument.Parse(await Util.Get(url));
        var hits = doc.RootElement.Items("hits").Select(Simplify).ToArray();
        return Util.Json(new { hits, total = doc.RootElement.Int("total_hits"), offset });
    }

    private static object Simplify(JsonElement h) => new
    {
        id = h.Str("project_id", h.Str("id")),
        slug = h.Str("slug"),
        title = h.Str("title"),
        description = h.Str("description"),
        iconUrl = h.Str("icon_url"),
        downloads = h.Int("downloads"),
        categories = h.Items("categories").Select(x => x.GetString()).Where(x => x != null).ToArray(),
        projectType = h.Str("project_type"),
        followers = h.Int("followers")
    };

    private static string VersionsUrl(string projectId, string gameVersion, string loader)
    {
        var q = new List<string>();
        if (!string.IsNullOrEmpty(loader) && loader != "vanilla")
            q.Add("loaders=" + Uri.EscapeDataString(Util.Json(new[] { loader })));
        if (!string.IsNullOrEmpty(gameVersion) && gameVersion != "All")
            q.Add("game_versions=" + Uri.EscapeDataString(Util.Json(new[] { gameVersion })));
        return $"{Base}/project/{Uri.EscapeDataString(projectId)}/version" + (q.Count > 0 ? "?" + string.Join("&", q) : "");
    }

    private static string MajorVersion(string version)
    {
        if (string.IsNullOrEmpty(version)) return "";
        var parts = version.Split('.');
        return parts.Length >= 2 ? string.Join('.', parts.Take(2)) : version;
    }

    private static async Task<JsonElement[]> Fetch(string url)
    {
        using var doc = JsonDocument.Parse(await Util.Get(url));
        return doc.RootElement.EnumerateArray().Select(x => x.Clone()).ToArray();
    }

    // Shaders and resource packs rarely list a loader, and often only a major game
    // version. Fall back from exact -> major -> any version, and for non-mods also
    // drop the loader filter as a last resort. Mods never ignore the loader.
    private static async Task<JsonElement[]> FetchVersions(string projectId, string gameVersion, string loader, bool relaxed = false)
    {
        var items = await Fetch(VersionsUrl(projectId, gameVersion, loader));
        if (items.Length == 0 && gameVersion != "All")
        {
            var major = MajorVersion(gameVersion);
            if (!string.IsNullOrEmpty(major) && major != gameVersion)
                items = await Fetch(VersionsUrl(projectId, major, loader));
            if (items.Length == 0) items = await Fetch(VersionsUrl(projectId, "All", loader));
        }
        if (items.Length == 0 && relaxed && !string.IsNullOrEmpty(loader))
            items = await Fetch(VersionsUrl(projectId, "All", ""));
        return items;
    }

    public async Task<string> GetProjectIcon(string projectId)
    {
        using var doc = await Util.GetJson($"{Base}/project/{Uri.EscapeDataString(projectId)}");
        return doc.RootElement.Str("icon_url");
    }

    public async Task<string> Versions(string projectId, string gameVersion, string loader, bool relaxed = false)
    {
        var items = await FetchVersions(projectId, gameVersion, loader, relaxed);
        return Util.Json(items.Select(v => new
        {
            id = v.Str("id"), versionNumber = v.Str("version_number"), changelog = v.Str("changelog", ""),
            datePublished = v.Str("date_published"), gameVersions = v.Items("game_versions").Select(x => x.GetString()).ToArray(),
            files = v.Items("files").Select(f => new
            {
                filename = f.Str("filename"), url = f.Str("url"),
                primary = f.TryGetProperty("primary", out var pv) && pv.ValueKind == JsonValueKind.True,
                sha512 = f.TryGetProperty("hashes", out var h512) ? h512.Str("sha512") : "",
                sha1 = f.TryGetProperty("hashes", out var h1) ? h1.Str("sha1") : ""
            }).ToArray()
        }).Take(12));
    }

    private static JsonElement PrimaryFile(JsonElement version)
    {
        var files = version.Items("files").ToArray();
        return files.FirstOrDefault(f => f.TryGetProperty("primary", out var p) && p.GetBoolean()).ValueKind != JsonValueKind.Undefined
            ? files.First(f => f.GetProperty("primary").GetBoolean()) : files.FirstOrDefault();
    }

    public async Task<string> Install(string projectJson, string profileJson)
    {
        using var pdoc = JsonDocument.Parse(projectJson);
        using var prof = JsonDocument.Parse(profileJson);
        var p = pdoc.RootElement;
        var profile = prof.RootElement;
        var id = p.Str("id");
        var type = p.Str("projectType", p.Str("type", "mod"));
        var gameVersion = profile.Str("version", "26.3");
        var loader = profile.Str("loader", "fabric");

        using var meta = JsonDocument.Parse(await Util.Get($"{Base}/project/{Uri.EscapeDataString(id)}"));
        var projectTitle = meta.RootElement.Str("title", p.Str("title", id));
        type = meta.RootElement.Str("project_type", type);
        var versions = await FetchVersions(id, gameVersion, type == "mod" ? loader : "", type != "mod");
        if (versions.Length == 0) throw new Exception($"No version of {projectTitle} is compatible with {gameVersion} / {loader}.");

        var installed = new List<object>();
        var warnings = new List<string>();
        var visited = new HashSet<string>();
        await InstallVersion(versions[0], meta.RootElement, type, gameVersion, loader, profile, installed, warnings, visited, 0);
        return Util.Json(new { installed, warnings });
    }

    private async Task InstallVersion(JsonElement version, JsonElement project, string type, string gameVersion,
        string loader, JsonElement profile, List<object> installed, List<string> warnings, HashSet<string> visited, int depth)
    {
        var versionId = version.Str("id");
        if (!visited.Add(versionId)) return;
        var file = PrimaryFile(version);
        if (file.ValueKind == JsonValueKind.Undefined) return;
        var filename = file.Str("filename", versionId + ".jar");
        var sub = type switch { "resourcepack" => "resourcepacks", "shader" => "shaderpacks", _ => "mods" };
        var dir = Path.Combine(_minecraft.InstanceDir(profile.Str("name", "Instance")), sub);
        var target = Path.Combine(dir, Path.GetFileName(filename));
        var hashes = file.TryGetProperty("hashes", out var h) ? h : default;
        await Util.Download(file.Str("url"), target, hashes.ValueKind == JsonValueKind.Object ? hashes.Str("sha1") : null,
            hashes.ValueKind == JsonValueKind.Object ? hashes.Str("sha512") : null);
        installed.Add(new
        {
            id = project.Str("id"), projectId = project.Str("id"), title = project.Str("title"),
            file = Path.GetFileName(target), version = version.Str("version_number"), type = sub,
            icon = project.Str("icon_url"),
            sha512 = hashes.ValueKind == JsonValueKind.Object ? hashes.Str("sha512") : ""
        });

        if (depth >= 2) return;
        foreach (var dep in version.Items("dependencies"))
        {
            var depType = dep.Str("dependency_type", dep.Str("type", "required"));
            var depId = dep.Str("project_id");
            if (string.IsNullOrEmpty(depId)) continue;
            if (depType == "optional") { warnings.Add("Optional dependency available: " + depId); continue; }
            if (depType == "incompatible") { warnings.Add("Incompatible dependency skipped: " + depId); continue; }
            try
            {
                using var depMeta = JsonDocument.Parse(await Util.Get($"{Base}/project/{Uri.EscapeDataString(depId)}"));
                var depVersions = await FetchVersions(dep.Str("project_id"), gameVersion, type == "mod" ? loader : "", type != "mod");
                if (depVersions.Length == 0) { warnings.Add("No compatible version for dependency " + depId); continue; }
                await InstallVersion(depVersions[0], depMeta.RootElement, "mod", gameVersion, loader, profile,
                    installed, warnings, visited, depth + 1);
            }
            catch (Exception ex) { warnings.Add("Dependency " + depId + " failed: " + Util.Truncate(ex.Message, 90)); }
        }
    }

    public async Task<string> CheckUpdate(string fileSha512, string projectId)
    {
        try
        {
            var body = await Util.PostJson($"{Base}/version_file", new { hash = fileSha512, algorithm = "sha512" });
            using var doc = JsonDocument.Parse(body);
            var currentId = doc.RootElement.Str("id");
            if (string.IsNullOrEmpty(projectId) || string.IsNullOrEmpty(currentId))
                return Util.Json(new { update = false });
            using var versions = JsonDocument.Parse(await Util.Get($"{Base}/project/{Uri.EscapeDataString(projectId)}/version?limit=1"));
            var newest = versions.RootElement.EnumerateArray().FirstOrDefault();
            if (newest.ValueKind == JsonValueKind.Undefined) return Util.Json(new { update = false });
            return Util.Json(new { update = newest.Str("id") != currentId, version = newest.Str("version_number") });
        }
        catch { return Util.Json(new { update = false }); }
    }
}
