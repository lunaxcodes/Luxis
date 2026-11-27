using System.IO.Compression;
using System.Reflection;
using System.Security.Cryptography;
using System.Text.Json;

namespace Luxis;

internal static class BundledLuxisMod
{
    private const string Legacy = "LuxisClientMod-1.21x.jar";
    private const string Modern = "LuxisClientMod.jar";
    private const string PendingSuffix = ".update-pending";
    private const string InUseMessage = "Minecraft is still using Luxis Mod in this profile. Close the game window and try again.";
    private static readonly object UpdateLock = new();
    private sealed record Build(byte[] Bytes, byte[] Hash, string Version);
    private sealed record Change(string File = null, bool Enabled = false, string Version = null, bool Queued = false, string Error = null);
    private static readonly Lazy<Build> LegacyBuild = new(() => Load(Legacy));
    private static readonly Lazy<Build> ModernBuild = new(() => Load(Modern));

    private static string FileFor(string version) => version switch
    {
        "1.21.11" => Legacy,
        "26.3" => Modern,
        _ => null
    };

    private static Build Bundle(string file) => file == Legacy ? LegacyBuild.Value : ModernBuild.Value;

    private static Build Load(string file)
    {
        using var resource = Assembly.GetExecutingAssembly().GetManifestResourceStream("Luxis." + file)
            ?? throw new IOException("The bundled Luxis Mod build is missing.");
        using var content = new MemoryStream();
        resource.CopyTo(content);
        var bytes = content.ToArray();
        content.Position = 0;
        return new Build(bytes, SHA256.HashData(bytes), ReadVersion(content));
    }

    private static string ReadVersion(Stream jar)
    {
        using var zip = new ZipArchive(jar, ZipArchiveMode.Read, leaveOpen: true);
        using var manifest = zip.GetEntry("fabric.mod.json")?.Open();
        if (manifest == null) return null;
        using var json = JsonDocument.Parse(manifest);
        return json.RootElement.Str("version", null);
    }

    private static bool HasFabricApi(string dir) =>
        Directory.Exists(dir) && Directory.EnumerateFiles(dir, "fabric-api*.jar").Any();

    private static bool HasWrongBuild(string dir, string file) =>
        file == null
            ? File.Exists(Path.Combine(dir, Legacy)) || File.Exists(Path.Combine(dir, Modern))
            : File.Exists(Path.Combine(dir, file == Legacy ? Modern : Legacy));

    private static bool SharingViolation(IOException ex) => (ex.HResult & 0xFFFF) is 32 or 33;

    private static bool InUse(string path)
    {
        if (!File.Exists(path)) return false;
        try
        {
            using var handle = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.None);
            return false;
        }
        catch (FileNotFoundException) { return false; }
        catch (IOException ex) when (SharingViolation(ex)) { return true; }
    }

    internal static string Status(string dir, string minecraftVersion)
    {
        try
        {
            var file = FileFor(minecraftVersion);
            var path = file == null ? null : Path.Combine(dir, file);
            var enabled = path != null && File.Exists(path);
            var installed = enabled || (path != null && File.Exists(path + ".disabled"));
            var bundle = file == null ? null : Bundle(file);
            var current = false;
            var inUse = false;
            string installedVersion = null;
            if (installed)
            {
                var installedPath = enabled ? path : path + ".disabled";
                inUse = InUse(installedPath);
                using var jar = new FileStream(installedPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
                current = jar.Length == bundle.Bytes.Length && SHA256.HashData(jar).SequenceEqual(bundle.Hash);
                if (current) installedVersion = bundle.Version;
                else
                {
                    jar.Position = 0;
                    try { installedVersion = ReadVersion(jar); }
                    catch (InvalidDataException) { }
                    catch (JsonException) { }
                }
            }
            return Util.Json(new { supported = file != null, installed, enabled, current, installedVersion,
                inUse, pendingUpdate = installed && !current && File.Exists(path + PendingSuffix),
                bundledVersion = bundle?.Version, fabricApi = HasFabricApi(dir), wrongBuild = HasWrongBuild(dir, file) });
        }
        catch (IOException) { return Fail("Could not read the mod files. Close Minecraft and refresh the list."); }
        catch (UnauthorizedAccessException) { return Fail("The launcher does not have permission to read this profile’s mod files."); }
    }

    internal static string Install(string dir, string minecraftVersion, bool enable)
    {
        var file = FileFor(minecraftVersion);
        if (file == null) return Fail("This bundle supports Minecraft 1.21.11 and 26.3.");
        lock (UpdateLock)
        {
            var result = ChangeFiles(dir, file, enable, allowQueue: !enable);
            return result.Error == null
                ? Util.Json(new { ok = true, file = result.File, enabled = result.Enabled, version = result.Version, queued = result.Queued })
                : Fail(result.Error);
        }
    }

    /// <summary>Finish a user-requested update before starting a new Minecraft process.</summary>
    internal static string ApplyPendingUpdate(string dir, string minecraftVersion)
    {
        var file = FileFor(minecraftVersion);
        if (file == null) return null;
        lock (UpdateLock)
        {
            var path = Path.Combine(dir, file);
            if (!File.Exists(path + PendingSuffix)) return null;
            if (!File.Exists(path) && !File.Exists(path + ".disabled"))
            {
                TryDelete(path + PendingSuffix);
                return null;
            }
            return ChangeFiles(dir, file, enable: false, allowQueue: false).Error;
        }
    }

    private static Change ChangeFiles(string dir, string file, bool enable, bool allowQueue)
    {
        var path = Path.Combine(dir, file);
        var disabled = path + ".disabled";
        try
        {
            if (!HasFabricApi(dir)) return new Change(Error: "Install the matching Fabric API in this profile first.");
            if (HasWrongBuild(dir, file)) return new Change(Error: "Remove the other Luxis Mod build from this profile first.");
            var active = enable || File.Exists(path);
            if (!enable && !active && !File.Exists(disabled)) return new Change(Error: "Luxis Mod is not installed in this profile.");
            var target = active ? path : disabled;
            var bundle = Bundle(file);
            Change HandleLock()
            {
                if (!allowQueue) return new Change(Error: InUseMessage);
                File.WriteAllText(path + PendingSuffix, bundle.Version);
                return new Change(file, active, bundle.Version, Queued: true);
            }
            if (InUse(target) || (enable && InUse(disabled))) return HandleLock();
            if (File.Exists(target) && File.GetAttributes(target).HasFlag(FileAttributes.ReadOnly))
                return new Change(Error: "The installed Luxis Mod JAR is read-only. Make that file writable, then try again.");
            var stage = Path.Combine(dir, "." + Guid.NewGuid().ToString("N") + ".tmp");
            try
            {
                File.WriteAllBytes(stage, bundle.Bytes);
                if (File.Exists(target)) File.Replace(stage, target, null);
                else File.Move(stage, target);
                if (enable) TryDelete(disabled);
            }
            catch (IOException ex) when (SharingViolation(ex)) { return HandleLock(); }
            catch (UnauthorizedAccessException) when (InUse(target)) { return HandleLock(); }
            finally { TryDelete(stage); }
            TryDelete(path + PendingSuffix);
            return new Change(file, active, bundle.Version);
        }
        catch (IOException) { return new Change(Error: "The mod could not be written. Close Minecraft and check that the profile’s mods folder is writable."); }
        catch (UnauthorizedAccessException) { return new Change(Error: "Windows denied access to the mod file or the profile’s mods folder."); }
    }

    internal static string Remove(string dir)
    {
        lock (UpdateLock) return RemoveFiles(dir);
    }

    private static string RemoveFiles(string dir)
    {
        if (!Directory.Exists(dir)) return Util.Json(new { ok = true });
        try
        {
            foreach (var file in new[] { Modern, Legacy })
            {
                var path = Path.Combine(dir, file);
                if (InUse(path) || InUse(path + ".disabled")) return Fail(InUseMessage);
            }
            foreach (var file in new[] { Modern, Legacy })
            {
                var path = Path.Combine(dir, file);
                File.Delete(path);
                File.Delete(path + ".disabled");
                TryDelete(path + PendingSuffix);
            }
            return Util.Json(new { ok = true });
        }
        catch (IOException) { return Fail("Close Minecraft before removing the mod."); }
        catch (UnauthorizedAccessException) { return Fail("The launcher does not have permission to remove this mod."); }
    }

    private static void TryDelete(string path)
    {
        try { File.Delete(path); }
        catch (IOException) { }
        catch (UnauthorizedAccessException) { }
    }

    private static string Fail(string error) => Util.Json(new { ok = false, error });
}
