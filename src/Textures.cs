using System.IO.Compression;

namespace Luxis;

/// <summary>
/// Exports genuine Minecraft block textures from the cached client JAR into the
/// virtual-host folder so the UI can use real game art for profile icons/banners.
/// </summary>
internal static class Textures
{
    private static readonly string[] Keys =
    {
        "diamond_block","netherite_block","emerald_block","amethyst_block","gold_block","redstone_block","lapis_block","iron_block","coal_block","copper_block",
        "stone","cobblestone","mossy_cobblestone","stone_bricks","cracked_stone_bricks","deepslate","deepslate_bricks","deepslate_tiles","polished_deepslate",
        "blackstone","polished_blackstone","andesite","diorite","granite","tuff","calcite","dripstone_block","bedrock","obsidian","netherrack","crimson_nylium",
        "warped_nylium","soul_sand","soul_soil","glowstone","quartz_block","smooth_quartz","sandstone","red_sandstone","bricks","grass_block_top","dirt",
        "coarse_dirt","gravel","sand","red_sand","clay","ice","blue_ice","packed_ice","snow_block","oak_planks","spruce_planks","birch_planks","jungle_planks",
        "acacia_planks","dark_oak_planks","mangrove_planks","cherry_planks","bamboo_planks","crimson_planks","warped_planks","oak_log","spruce_log","birch_log",
        "jungle_log","acacia_log","dark_oak_log","mangrove_log","cherry_log","stripped_oak_log","black_wool","blue_wool","brown_wool","cyan_wool","gray_wool",
        "green_wool","light_blue_wool","lime_wool","magenta_wool","orange_wool","pink_wool","purple_wool","red_wool","white_wool","yellow_wool","black_terracotta",
        "blue_terracotta","cyan_terracotta","gray_terracotta","green_terracotta","orange_terracotta","purple_terracotta","red_terracotta","white_terracotta",
        "yellow_terracotta","black_concrete","blue_concrete","cyan_concrete","gray_concrete","green_concrete","orange_concrete","purple_concrete","red_concrete",
        "white_concrete","yellow_concrete","sea_lantern","shroomlight","sponge","hay_block","melon","pumpkin","carved_pumpkin","jack_o_lantern","prismarine",
        "dark_prismarine","sculk_block","resin_block","azalea_leaves","cherry_leaves","bamboo_block","brain_coral_block","tnt_top"
    };

    /// Writes the bundled fallback block textures so the icon picker works before any game is downloaded.
    public static void EnsureBundled(string appFiles)
    {
        var outDir = Path.Combine(appFiles, "textures");
        Directory.CreateDirectory(outDir);
        var asm = typeof(Textures).Assembly;
        foreach (var name in asm.GetManifestResourceNames())
        {
            if (!name.StartsWith("tex_") || !name.EndsWith(".png")) continue;
            var target = Path.Combine(outDir, name[4..]);
            if (File.Exists(target)) continue;
            try
            {
                using var src = asm.GetManifestResourceStream(name);
                if (src == null) continue;
                using var dst = File.Create(target);
                src.CopyTo(dst);
            }
            catch { }
        }
    }

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
