using System.IO.Compression;

namespace Lunax;

/// <summary>
/// Exports genuine Minecraft block textures from the cached client JAR into the
/// virtual-host folder so the UI can use real game art for profile icons/banners.
/// </summary>
internal static class Textures
{
    private static readonly string[] Keys =
    {
        "diamond_block", "netherite_block", "emerald_block", "amethyst_block",
        "stone", "cobblestone", "deepslate_bricks", "grass_block_top",
        "tnt_top", "obsidian", "gold_block", "redstone_block", "oak_planks", "bricks"
    };

    public static void Export(string appFiles, string gameRoot)
    {
        var outDir = Path.Combine(appFiles, "textures");
        Directory.CreateDirectory(outDir);
        var jars = Directory.Exists(Path.Combine(gameRoot, "versions"))
            ? Directory.GetFiles(Path.Combine(gameRoot, "versions"), "*.jar", SearchOption.AllDirectories)
            : Array.Empty<string>();
        if (jars.Length == 0) return;
        var jar = jars.OrderByDescending(f =>
            f.Contains("26.3") ? 3 : f.Contains("1.21") ? 2 : 1).First();

        try
        {
            using var zip = ZipFile.OpenRead(jar);
            foreach (var key in Keys)
            {
                var entry = zip.GetEntry("assets/minecraft/textures/block/" + key + ".png")
                         ?? zip.GetEntry("assets/minecraft/textures/blocks/" + key + ".png");
                if (entry == null) continue;
                var target = Path.Combine(outDir, key + ".png");
                using var src = entry.Open();
                using var dst = File.Create(target);
                src.CopyTo(dst);
            }
        }
        catch { /* corrupt jar -> UI falls back to gradient art */ }
    }
}
