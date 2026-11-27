package com.luxis.gui;

import com.luxis.config.GuiConfig;
import net.minecraft.client.gui.GuiGraphics;

import java.util.SplittableRandom;

/** Repeatable, bounded ambient animation. No entity particles, world scans, or per-frame allocation. */
final class MenuParticles {
    private static final int MAX = 52;
    private final float[] px = new float[MAX], py = new float[MAX], speed = new float[MAX], phase = new float[MAX];
    private float elapsed;

    MenuParticles() {
        var random = new SplittableRandom();
        for (int i = 0; i < MAX; i++) {
            px[i] = (float) random.nextDouble();
            py[i] = (float) random.nextDouble();
            speed[i] = 0.45F + (float) random.nextDouble();
            phase[i] = (float) random.nextDouble() * 6.283F;
        }
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, float dt, float fade, GuiTheme.Palette theme) {
        var look = GuiConfig.appearance();
        if (look.particles == GuiConfig.Particles.Off || look.particleCount == 0 || w <= 30 || h <= 30) return;
        boolean animated = look.motion == GuiConfig.Motion.Full && look.particleSpeed > 0;
        if (animated) elapsed += dt * look.particleSpeed / 100F;
        int count = Math.min(MAX, look.particleCount);
        g.enableScissor(x + 8, y + 8, x + w - 8, y + h - 8);
        for (int i = 0; i < count; i++) {
            float fx = wrap(px[i] + elapsed * speed[i] * (look.particles == GuiConfig.Particles.Comets ? .21F : .045F));
            float fy = wrap(py[i] - elapsed * speed[i] * .075F);
            float edge = Math.min(1F, Math.min(Math.min(fx, 1F - fx), Math.min(fy, 1F - fy)) * 12F);
            float shimmer = animated && look.particles == GuiConfig.Particles.Stars
                    ? .65F + .25F * (float) Math.sin(elapsed * 1.8F + phase[i]) : .8F;
            int color = Draw.alpha((i & 1) == 0 ? theme.accent : theme.accent2,
                    fade * edge * shimmer * (look.particleOpacity / 100F) * .55F);
            int size = (i % 7 == 0) ? 3 : 2;
            float sx = x + 9 + fx * (w - 18), sy = y + 9 + fy * (h - 18);
            Draw.pill(g, sx, sy, look.particles == GuiConfig.Particles.Comets ? size + 4 : size, size, color);
        }
        g.disableScissor();
    }

    private static float wrap(float value) { return value - (float) Math.floor(value); }
}
