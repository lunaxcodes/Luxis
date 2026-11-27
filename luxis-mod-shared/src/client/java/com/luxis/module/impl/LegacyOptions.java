package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.ModuleRegistry;
import net.minecraft.client.Options;

import java.util.Set;

/** Older cards used to write options from an uncalled onTick. Reuse the shared option leases. */
final class LegacyOptions {
    private static final Set<String> WIRED = Set.of("vsync", "ao", "entityshadows", "bobbing", "gamma", "renderdist",
            "framecap", "unfocused", "weatherdensity", "chunkfade", "fov", "vignette", "chatopacity");
    private LegacyOptions() {}

    static boolean wired(Module m) { return WIRED.contains(m.id); }

    static void apply(Options options, boolean focused) {
        for (Module m : ModuleRegistry.all()) {
            if (!m.enabled || !wired(m)) continue;
            switch (m.id) {
                case "vsync" -> ClientTuning.set(options.enableVsync(), flag(m, "vsync"));
                case "ao" -> ClientTuning.set(options.ambientOcclusion(), flag(m, "ao"));
                case "entityshadows" -> ClientTuning.set(options.entityShadows(), flag(m, "shadows"));
                case "bobbing" -> ClientTuning.set(options.bobView(), flag(m, "bob"));
                case "gamma" -> ClientTuning.set(options.gamma(), Math.clamp(number(m, "gamma"), 0D, 1D));
                case "renderdist" -> ClientTuning.set(options.renderDistance(), (int) number(m, "distance"));
                case "framecap" -> ClientTuning.cap(options.framerateLimit(), Math.max(10, (int) number(m, focused ? "cap" : "unfocused")));
                case "unfocused" -> { if (!focused) ClientTuning.cap(options.framerateLimit(), Math.max(10, (int) number(m, "unfocused"))); }
                case "weatherdensity" -> {
                    ClientTuning.cap(options.weatherRadius(), Math.clamp((int) number(m, "radius"), 3, 10));
                    if (flag(m, "thunder")) ClientTuning.set(options.hideLightningFlash(), true);
                }
                case "chunkfade" -> ClientTuning.set(options.chunkSectionFadeInTime(), number(m, "fade") / 20D);
                case "fov" -> { if (!flag(m, "dynamic")) ClientTuning.set(options.fov(), (int) number(m, "fov")); }
                case "vignette" -> ClientTuning.set(options.vignette(), flag(m, "vignette"));
                case "chatopacity" -> ClientTuning.set(options.chatOpacity(), number(m, "opacity") / 100D);
                default -> { }
            }
        }
    }

    private static boolean flag(Module m, String key) { return Boolean.TRUE.equals(m.get(key)); }
    private static double number(Module m, String key) { return ((Number) m.get(key)).doubleValue(); }
}
