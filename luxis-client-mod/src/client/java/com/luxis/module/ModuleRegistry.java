package com.luxis.module;

import com.luxis.module.impl.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ModuleRegistry {
    private static final List<Module> MODULES = new ArrayList<>();
    private static final List<HudModule> LIVE_UTILITIES = liveUtilityCatalog();
    private static final List<HudModule> HUD_MODULES;
    private static final Map<HudModule, HudDefaults> HUD_DEFAULTS;
    public record HudDefaults(int x, int y, int width, int height, float scale, HudModule.Anchor anchor) {
        private HudDefaults(HudModule hud) { this(hud.x, hud.y, hud.width, hud.height, hud.scale, hud.anchor); }
        public void restore(HudModule hud) {
            hud.x = x; hud.y = y; hud.width = width; hud.height = height; hud.scale = scale; hud.anchor = anchor;
        }
    }
    static {
        MODULES.add(new FpsHudModule());
        MODULES.add(new CpsHudModule());
        MODULES.add(new ToggleSprintSneakModule());
        MODULES.add(new ArmorStatusModule());
        MODULES.add(new PotionHudModule());
        MODULES.add(new CoordinatesPingModule());
        MODULES.add(new KeystrokesModule());
        MODULES.add(new ParticleLimiterModule());
        MODULES.add(new EntityRenderDistanceModule());
        MODULES.add(new UnfocusedFpsLimiterModule());
        MODULES.add(new FogAndWeatherOptimizerModule());
        MODULES.add(new ZoomModule());
        MODULES.add(new LowFireModule());
        MODULES.add(new BlockOutlineModule());
        MODULES.add(new HitColorModule());
        MODULES.add(new CrosshairModule());

        MODULES.add(new VsyncToggleModule());
        MODULES.add(new AmbientOcclusionModule());
        MODULES.add(new EntityShadowsModule());
        MODULES.add(new ViewBobbingModule());
        MODULES.add(new SmoothCameraModule());
        MODULES.add(new GammaTunerModule());
        MODULES.add(new RenderDistanceModule());
        MODULES.add(new FrameCapModule());
        MODULES.add(new WeatherDensityModule());
        MODULES.add(new ChunkFadeModule());

        MODULES.add(new SaturationHudModule());
        MODULES.add(new SessionTimerHudModule());
        MODULES.add(new FovTunerModule());
        MODULES.add(new VignetteToggleModule());
        MODULES.add(new ChatOpacityModule());
        MODULES.addAll(ClientTuning.modules());
        MODULES.addAll(VisualModules.modules());
        MODULES.addAll(ViewControls.modules());
        MODULES.addAll(LIVE_UTILITIES);
        HUD_MODULES = MODULES.stream().filter(HudModule.class::isInstance).map(HudModule.class::cast).toList();
        int row = 0;
        for (HudModule hud : HUD_MODULES) if (hud.x == 40 && hud.y == 40) {
            hud.x = 10; hud.y = 8 + 24 * row++;
            if (!hud.id.equals("keys") && !hud.id.equals("armor")) hud.height = 20;
        }
        HUD_DEFAULTS = HUD_MODULES.stream().collect(Collectors.toUnmodifiableMap(hud -> hud, HudDefaults::new));
    }
    public static List<Module> all() { return MODULES; }
    public static Module byId(String id) { for (Module m : MODULES) if (m.id.equals(id)) return m; return null; }
    public static List<HudModule> hudModules() { return HUD_MODULES; }
    public static List<HudModule> liveUtilities() { return HUD_MODULES; }
    public static void resetHudLayout(HudModule hud) { HUD_DEFAULTS.get(hud).restore(hud); }
    private static List<HudModule> liveUtilityCatalog() {
        var result = new ArrayList<>(UtilityHudModules.create());
        result.addAll(PvpHudModules.create());
        return List.copyOf(result);
    }
    public static void applyEnabledStates() {
        for (Module m : MODULES) { if (m.enabled) m.onEnable(); else m.onDisable(); }
    }
}
