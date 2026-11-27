package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.ModuleRegistry;
import com.luxis.module.Setting;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;

/** Camera-only controls; never changes the saved FOV, player rotation or light levels. */
public final class ViewControls {
    private static final Module FREELOOK = new Module("freelook", "Freelook",
            "Look around without turning your player. Enable here, then use your Freelook key.",
            Module.Category.VISUAL, false, true) {};
    private static final Module FULLBRIGHT = new Module("fullbright", "Fullbright",
            "Brightens the world lightmap on your client. Use only where server rules permit.",
            Module.Category.VISUAL, false, true) {
        { add(new Setting.SliderSetting("strength", "Brightness", 100, 25, 100, 5, "%")); }
    };
    static {
        FREELOOK.key = InputConstants.getKey("key.keyboard.v");
        FREELOOK.trigger = Module.Trigger.HOLD;
    }

    private static boolean zoom, freelook;
    private static float scrollFactor = 1, currentScale = 1, yaw, pitch;
    private static long lastFrame;

    private ViewControls() {}
    public static List<Module> modules() { return List.of(FREELOOK, FULLBRIGHT); }
    public static boolean isViewKey(Module m) { return m.id.equals("zoom") || m == FREELOOK; }
    public static boolean isZoomActive() { return zoom && ModuleRegistry.byId("zoom").enabled; }
    public static boolean isFreelookActive() { return freelook && FREELOOK.enabled; }
    public static float fullbright() { return FULLBRIGHT.enabled ? ((Number) FULLBRIGHT.get("strength")).floatValue() * .01F : 0; }

    public static void key(Module m, boolean down, float playerYaw, float playerPitch) {
        if (!m.enabled) return;
        if (m.id.equals("zoom")) {
            if (down) { zoom = m.trigger == Module.Trigger.HOLD || !zoom; scrollFactor = 1; }
            else if (m.trigger == Module.Trigger.HOLD) zoom = false;
        } else if (m == FREELOOK) {
            if (down) {
                freelook = m.trigger == Module.Trigger.HOLD || !freelook;
                if (freelook) { yaw = playerYaw; pitch = playerPitch; }
            } else if (m.trigger == Module.Trigger.HOLD) freelook = false;
        }
    }

    public static void reset() { zoom = freelook = false; scrollFactor = currentScale = 1; lastFrame = 0; }
    public static float adjustFov(float fov) {
        var m = ModuleRegistry.byId("zoom");
        float magnification = ((Number) m.get("level")).floatValue();
        float speed = ((Number) m.get("speed")).floatValue();
        float target = isZoomActive() ? 1F / Math.clamp(magnification * scrollFactor, 1.25F, 12F) : 1F;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 1F / 60F : Math.clamp((now - lastFrame) * 1e-9F, 0F, .1F);
        lastFrame = now;
        currentScale += (target - currentScale) * (1F - (float) Math.exp(-(speed * 2 + 5) * dt));
        if (Math.abs(target - currentScale) < .0005F) currentScale = target;
        return fov * currentScale;
    }

    public static boolean scroll(double amount) {
        if (amount == 0 || !isZoomActive() || !Boolean.TRUE.equals(ModuleRegistry.byId("zoom").get("scroll"))) return false;
        scrollFactor = Math.clamp(scrollFactor * (float) Math.pow(1.18, amount), .3F, 4F);
        return true;
    }

    public static boolean move(double dx, double dy) {
        if (!isFreelookActive()) return false;
        yaw = (yaw + (float) dx * .15F) % 360F;
        pitch = Math.clamp(pitch + (float) dy * .15F, -90F, 90F);
        return true;
    }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }
}
