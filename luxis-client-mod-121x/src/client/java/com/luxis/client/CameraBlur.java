package com.luxis.client;

import com.luxis.module.impl.ClientTuning;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Two-pass directional blur, active only while turning; no retained frame buffers. */
public final class CameraBlur {
    private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
    private static final Identifier[][] EFFECTS = new Identifier[2][3];
    static {
        for (int axis = 0; axis < 2; axis++) {
            for (int i = 0; i < 3; i++) EFFECTS[axis][i] = Identifier.fromNamespaceAndPath("luxis", "turn_" + axis + "_" + (i + 1));
        }
    }
    private static long previous;
    private static float yaw, pitch;

    private CameraBlur() {}

    public static Identifier select(Minecraft mc) {
        int max = ClientTuning.blurLimit();
        if (max == 0 || IRIS || mc.player == null || mc.screen != null || mc.getCameraEntity() != mc.player) {
            previous = 0;
            return null;
        }
        long now = System.nanoTime(), elapsed = now - previous;
        float y = mc.player.getYRot(), p = mc.player.getXRot();
        float dx = Math.abs(Mth.wrapDegrees(y - yaw)), dy = Math.abs(p - pitch);
        yaw = y; pitch = p;
        boolean reset = previous == 0 || elapsed > 200_000_000L;
        previous = now;
        if (reset || Math.max(dx, dy) > 110F) return null;
        float speed = Math.max(dx, dy) * 1_000_000_000F / Math.max(1_000_000L, elapsed);
        if (speed < 28F) return null;
        int level = speed < 110F ? 1 : speed < 240F ? 2 : 3;
        return EFFECTS[dx >= dy ? 0 : 1][Math.min(max, level) - 1];
    }
}
