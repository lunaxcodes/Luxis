package com.luxis.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Soft rounded drawing helpers — no raw OpenGL, no sharp blocky panels. */
public final class Draw {
    private Draw() {}

    public static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(1, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            int inset = r - (int) Math.sqrt(r * r - (r - i) * (r - i));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        for (int i = 3; i >= 1; i--) {
            int a = Math.max(0, 20 - i * 4);
            rounded(g, x - i, y - i + 3, w + i * 2, h + i * 2, r + i, (a << 24));
        }
    }

    public static void band(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
        for (int i = 0; i < h; i++) {
            float t = h <= 1 ? 0F : (float) i / (h - 1);
            g.fill(x, y + i, x + w, y + i + 1, AnimationUtil.lerpColor(top, bottom, t));
        }
    }

    public static void text(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.gui.Font font,
                            String value, int x, int y, int color, boolean shadow) {
        g.text(font, value, x, y, color, shadow);
    }
}
