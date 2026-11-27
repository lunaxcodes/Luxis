package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.Setting;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.server.level.ParticleStatus;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Client-side rendering budgets. Compose changes once, then restore the user's settings on disable. */
public final class ClientTuning {
    private static final class Tune extends Module {
        final BiConsumer<Tune, Options> rule;
        Tune(String id, String name, String desc, Category category, BiConsumer<Tune, Options> rule) {
            super(id, name, desc, category, false, true);
            this.rule = rule;
        }
        Tune flag(String id, String label, boolean initial) { add(new Setting.BooleanSetting(id, label, initial)); return this; }
        Tune slider(String id, String label, int initial, int min, int max, int step, String unit) {
            add(new Setting.SliderSetting(id, label, initial, min, max, step, unit)); return this;
        }
        Tune mode(String id, String label, String initial, String... list) {
            add(new Setting.ModeSetting(id, label, initial, list)); return this;
        }
        boolean on(String id) { return Boolean.TRUE.equals(get(id)); }
        int number(String id) { return ((Number) get(id)).intValue(); }
        @Override public void onEnable() { changed(); }
        @Override public void onDisable() { changed(); }
    }

    private record Lease<T>(OptionInstance<T> option, T original, T applied) {
        void restore() { if (option.get().equals(applied) && !applied.equals(original)) option.set(original); }
    }
    private static final Map<OptionInstance<?>, Lease<?>> ACTIVE = new IdentityHashMap<>();
    private static final Map<OptionInstance<?>, Object> DESIRED = new IdentityHashMap<>();
    private static final List<Tune> TUNERS = new ArrayList<>();
    private static boolean dirty = true, focused = true;
    private static int explosions, emitters;

    private static final Tune CRYSTAL = performance("crystalbudget", "Crystal Optimizer",
            "Simplifies crystal bases and beams and cuts explosion clutter.", (m, o) -> {})
            .flag("base", "Hide decorative base", true).flag("beam", "Hide beam", false).flag("explosions", "Trim explosion particles", true);
    private static final Tune EXPLOSION = performance("explosionbudget", "Explosion Budget",
            "Caps rendered explosion particles; keeps sounds, damage and knockback unchanged.", (m, o) -> {})
            .slider("count", "Particles per tick", 8, 1, 30, 1, "").slider("emitters", "Emitters per tick", 2, 1, 10, 1, "");
    private static final Tune HAND = visual("handview", "Hand Viewmodel",
            "Adjusts first-person item size and position without changing interactions.", (m, o) -> {})
            .slider("horizontal", "Horizontal offset", 0, -40, 40, 2, "%").slider("vertical", "Vertical offset", 0, -40, 40, 2, "%")
            .slider("size", "Hand size", 100, 60, 140, 5, "%").flag("offhand", "Hide offhand viewmodel", false);
    private static final Tune BLUR = visual("turnblur", "Motion Blur",
            "Directional camera-turn blur. Off by default; skipped with Iris.", (m, o) -> {})
            .mode("strength", "Maximum strength", "Low", "Low", "Medium", "High");
    private static final Tune RADIUS = visual("reachradius", "Radius",
            "Shows the real hit radius at your feet. Display only; does not increase reach.", (m, o) -> {})
            .mode("kind", "Range", "Entity", "Entity", "Block").slider("opacity", "Ring opacity", 68, 15, 100, 5, "%");

    static {
        visual("gentledamage", "Damage Tilt", "Adjust the camera tilt when you take damage.",
                (m, o) -> set(o.damageTiltStrength(), m.number("strength") / 100D))
                .slider("strength", "Tilt strength", 35, 0, 100, 5, "%");
        visual("steadyfov", "Dynamic FOV", "Controls the speed and sprint FOV effect, not base FOV.",
                (m, o) -> set(o.fovEffectScale(), m.number("strength") / 100D))
                .slider("strength", "FOV effects", 35, 0, 100, 5, "%");
        visual("enchantlook", "Enchantment Glint", "Control the strength and speed of enchantment shimmer.",
                (m, o) -> { set(o.glintStrength(), m.number("strength") / 100D); set(o.glintSpeed(), m.number("speed") / 100D); })
                .slider("strength", "Strength", 50, 0, 100, 5, "%").slider("speed", "Speed", 50, 0, 100, 5, "%");
        visual("steadyview", "Steady View", "Tone down screen distortion and optionally switch off view bobbing.",
                (m, o) -> { set(o.screenEffectScale(), m.number("distortion") / 100D); if (m.on("bob")) set(o.bobView(), false); })
                .slider("distortion", "Distortion strength", 30, 0, 100, 5, "%").flag("bob", "Disable view bobbing", true);
        visual("outlinecontrast", "High Contrast Outline", "Uses Minecraft's extra-visible targeted-block outline.",
                (m, o) -> set(o.highContrastBlockOutline(), true));
        visual("cleanview", "Clean View", "Optionally hide vignette shading and flashing lightning overlays.",
                (m, o) -> { if (m.on("vignette")) set(o.vignette(), false); if (m.on("flash")) set(o.hideLightningFlash(), true); })
                .flag("vignette", "Hide vignette", true).flag("flash", "Hide lightning flashes", true);

        visual("quietsaveicon", "Quiet Autosave Icon", "Hides the saving indicator without disabling autosave.",
                (m, o) -> set(o.showAutosaveIndicator(), false));
        visual("chatlayout", "Chat Layout", "Adjusts chat text size, width and line spacing.",
                (m, o) -> { set(o.chatScale(), m.number("scale") / 100D); set(o.chatWidth(), m.number("width") / 100D);
                    set(o.chatLineSpacing(), m.number("spacing") / 100D); })
                .slider("scale", "Text scale", 85, 45, 100, 5, "%")
                .slider("width", "Chat width", 80, 20, 100, 5, "%")
                .slider("spacing", "Line spacing", 10, 0, 100, 5, "%");

        performance("biomeblendbudget", "Biome Blend Budget", "Caps biome color blending; lower values use fewer samples.",
                (m, o) -> cap(o.biomeBlendRadius(), m.number("radius")))
                .slider("radius", "Blend radius cap", 2, 0, 7, 1, "");
        performance("clouddistancebudget", "Cloud Distance", "Caps how far clouds draw without changing their style.",
                (m, o) -> cap(o.cloudRange(), m.number("distance")))
                .slider("distance", "Maximum cloud range", 32, 16, 64, 4, "");
        performance("menublurbudget", "Menu Blur Budget", "Limits vanilla background blur while screens are open.",
                (m, o) -> cap(o.menuBackgroundBlurriness(), m.number("level")))
                .slider("level", "Maximum blur", 0, 0, 10, 1, "");

        performance("fpsbooster", "FPS Booster", "Uses faster leaves, simpler transparency and optional lighter shadows.",
                (m, o) -> { set(o.cutoutLeaves(), false); set(o.improvedTransparency(), false);
                    if (m.on("lighting")) set(o.ambientOcclusion(), false);
                    if (m.on("shadows")) set(o.entityShadows(), false); })
                .flag("lighting", "Simplify ambient lighting", false).flag("shadows", "Remove entity shadows", true);
        performance("pvpoptimizer", "PvP Optimizer", "Cuts background weather, cloud and shadow rendering. Keeps hit cues.",
                (m, o) -> { cap(o.weatherRadius(), m.number("weather")); set(o.entityShadows(), false);
                    if (m.on("clouds")) set(o.cloudStatus(), CloudStatus.OFF); })
                .slider("weather", "Weather radius", 4, 3, 10, 1, " blocks").flag("clouds", "Hide clouds", true);
        performance("cloudbudget", "Cloud Budget", "Switches fancy clouds to fast clouds or removes them.",
                (m, o) -> { if ("Off".equals(m.get("style"))) set(o.cloudStatus(), CloudStatus.OFF);
                    else if (effective(o.cloudStatus()) == CloudStatus.FANCY) set(o.cloudStatus(), CloudStatus.FAST); })
                .mode("style", "Cloud rendering", "Fast", "Fast", "Off");
        performance("weatherbudget", "Weather Budget", "Reduces how far rain and snow are drawn.",
                (m, o) -> cap(o.weatherRadius(), m.number("radius")))
                .slider("radius", "Maximum weather radius", 5, 3, 10, 1, " blocks");
        performance("particlebudget", "Particle Budget", "Uses Minecraft's reduced or minimal particle setting.",
                (m, o) -> set(o.particles(), "Minimal".equals(m.get("mode")) || effective(o.particles()) == ParticleStatus.MINIMAL
                        ? ParticleStatus.MINIMAL : ParticleStatus.DECREASED))
                .mode("mode", "Particle mode", "Decreased", "Decreased", "Minimal");
        performance("chunkschedule", "Chunk Scheduling", "Reduces chunk updates prioritized on the render thread.",
                (m, o) -> set(o.prioritizeChunkUpdates(), "All deferred".equals(m.get("policy"))
                        ? PrioritizeChunkUpdates.NONE : PrioritizeChunkUpdates.PLAYER_AFFECTED))
                .mode("policy", "Scheduling", "All deferred", "All deferred", "Player changes first");
        performance("entitybudget", "Entity Draw Budget", "Lowers distant entity rendering; can shorten player visibility.",
                (m, o) -> set(o.entityDistanceScaling(), Math.min(effective(o.entityDistanceScaling()), m.number("limit") / 100D)))
                .slider("limit", "Entity distance cap", 75, 50, 100, 5, "%");
        performance("terrainbudget", "Terrain Budget", "Caps visible chunks without changing simulation distance.",
                (m, o) -> cap(o.renderDistance(), m.number("chunks")))
                .slider("chunks", "Visible chunk cap", 10, 5, 24, 1, "");
        performance("backgroundbudget", "Background FPS", "Caps rendering only while the game window is unfocused.",
                (m, o) -> { if (!focused) cap(o.framerateLimit(), m.number("fps")); })
                .slider("fps", "Unfocused cap", 30, 10, 60, 10, " FPS");
    }

    private ClientTuning() {}
    private static Tune performance(String id, String name, String desc, BiConsumer<Tune, Options> rule) {
        return add(id, name, desc, Module.Category.OPTIMIZATION, rule);
    }
    private static Tune visual(String id, String name, String desc, BiConsumer<Tune, Options> rule) {
        return add(id, name, desc, Module.Category.VISUAL, rule);
    }
    private static Tune add(String id, String name, String desc, Module.Category category, BiConsumer<Tune, Options> rule) {
        Tune module = new Tune(id, name, desc, category, rule);
        TUNERS.add(module);
        return module;
    }
    public static List<Module> modules() { return List.copyOf(TUNERS); }
    public static boolean isTuner(Module module) { return module instanceof Tune || LegacyOptions.wired(module); }
    public static void changed() { dirty = true; }
    public static void restore() {
        ACTIVE.values().forEach(Lease::restore);
        ACTIVE.clear();
        DESIRED.clear();
        dirty = true;
    }

    public static void tick(Minecraft client) {
        explosions = emitters = 0;
        boolean now = client.isWindowActive();
        if (focused != now) { focused = now; dirty = true; }
        if (!dirty || client.options == null) return;
        dirty = false;
        DESIRED.clear();
        LegacyOptions.apply(client.options, focused);
        for (Tune module : TUNERS) if (module.enabled) module.rule.accept(module, client.options);
        ACTIVE.entrySet().removeIf(entry -> {
            if (DESIRED.containsKey(entry.getKey())) return false;
            entry.getValue().restore();
            return true;
        });
        DESIRED.forEach(ClientTuning::apply);
    }

    @SuppressWarnings("unchecked")
    private static <T> T effective(OptionInstance<T> option) {
        if (DESIRED.containsKey(option)) return (T) DESIRED.get(option);
        Lease<T> lease = (Lease<T>) ACTIVE.get(option);
        return lease != null && option.get().equals(lease.applied) ? lease.original : option.get();
    }
    static <T> void set(OptionInstance<T> option, T value) { DESIRED.put(option, value); }
    static void cap(OptionInstance<Integer> option, int max) { set(option, Math.min(effective(option), max)); }
    @SuppressWarnings("unchecked")
    private static <T> void apply(OptionInstance<T> option, Object target) {
        Lease<T> previous = (Lease<T>) ACTIVE.get(option);
        T original = previous != null && option.get().equals(previous.applied) ? previous.original : option.get();
        if (!option.get().equals(target)) option.set((T) target);
        ACTIVE.put(option, new Lease<>(option, original, option.get()));
    }

    public static boolean trimCrystalBase() { return CRYSTAL.enabled && CRYSTAL.on("base"); }
    public static boolean trimCrystalBeam() { return CRYSTAL.enabled && CRYSTAL.on("beam"); }
    public static boolean dropExplosion(boolean emitter) {
        boolean trim = CRYSTAL.enabled && CRYSTAL.on("explosions");
        if (!trim && !EXPLOSION.enabled) return false;
        int limit = EXPLOSION.enabled ? EXPLOSION.number(emitter ? "emitters" : "count") : 30;
        if (trim) limit = Math.min(limit, emitter ? 2 : 10);
        return (emitter ? ++emitters : ++explosions) > limit;
    }
    public static boolean handEnabled() { return HAND.enabled; }
    public static boolean hideOffhand() { return HAND.on("offhand"); }
    public static float handX() { return HAND.number("horizontal") / 100F; }
    public static float handY() { return HAND.number("vertical") / 100F; }
    public static float handScale() { return HAND.number("size") / 100F; }
    public static int blurLimit() {
        if (!BLUR.enabled) return 0;
        return switch ((String) BLUR.get("strength")) { case "High" -> 3; case "Medium" -> 2; default -> 1; };
    }
    public static boolean radiusEnabled() { return RADIUS.enabled; }
    public static boolean blockRadius() { return "Block".equals(RADIUS.get("kind")); }
    public static float radiusOpacity() { return RADIUS.number("opacity") / 100F; }
}
