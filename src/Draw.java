package com.luxis.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.joml.Matrix3x2f;
import java.util.HashMap;
import java.util.Map;

/** Soft rounded drawing helpers — no raw OpenGL, no sharp blocky panels. */
public final class Draw {
    private Draw() {}

    private static final Map<Integer, int[]> INSETS = new HashMap<>();
    private static int[] insets(int r) {
        return INSETS.computeIfAbsent(r, rr -> {
            int[] a = new int[rr];
            for (int i = 0; i < rr; i++) a[i] = rr - (int) Math.round(Math.sqrt(rr * rr - (rr - i) * (rr - i)));
            return a;
        });
    }

    public static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(1, Math.min(r, Math.min(w, h) / 2));
        int a = (color >>> 24) & 255;
        int softA = Math.min(255, a + 34);
        int soft = (softA << 24) | (color & 0x00FFFFFF);
        int[] ins = insets(r);
        for (int i = 0; i < r; i++) {
            int inset = ins[i];
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
            if (inset > 1 && a > 8) {
                g.fill(x + inset - 1, y + i, x + inset, y + i + 1, soft);
                g.fill(x + w - inset, y + i, x + w - inset + 1, y + i + 1, soft);
            }
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    public static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        rounded(g, x - 2, y - 2, w + 4, h + 4, r + 2, (0x18 << 24) | (color & 0xFFFFFF));
        rounded(g, x - 1, y - 1, w + 2, h + 2, r + 1, (0x10 << 24) | (color & 0xFFFFFF));
    }

    /** Layered translucent panel with highlight, sheen and themed border. */
    public static void glass(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int base, int accent) {
        rounded(g, x, y, w, h, r, base);
        rounded(g, x, y, w, h, r, 0x0C000000);
        for (int i = 0; i < 3; i++) {
            int a = 34 - i * 9;
            g.fill(x + r, y + i, x + w - r, y + i + 1, (a << 24) | 0xFFFFFF);
        }
        g.fill(x + r, y + h - 2, x + w - r, y + h - 1, 0x1A000000);
        g.fill(x + 12, y + 5, x + w / 2, y + 6, 0x0DFFFFFF);
        softBorder(g, x, y, w, h, r, (0x3A << 24) | (accent & 0xFFFFFF));
    }

    public static void textScaled(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String value,
                                  int x, int y, int color, float scale, boolean bold) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        styled(g, font, value, 0, 0, color, bold, false);
        g.pose().popMatrix();
    }

    public static void styled(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String value,
                              int x, int y, int color, boolean bold, boolean shadow) {
        Component c = Component.literal(value);
        if (bold) c = c.copy().withStyle(ChatFormatting.BOLD);
        g.text(font, c, x, y, color, shadow);
    }

    public static void softBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        int[] ins = insets(r);
        for (int i = 0; i < r; i++) {
            int inset = ins[i];
            g.fill(x + inset, y + i, x + inset + 1, y + i + 1, color);
            g.fill(x + w - inset - 1, y + i, x + w - inset, y + i + 1, color);
        }
    }

    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        rounded(g, x - 2, y + 1, w + 4, h + 4, r + 2, 0x2A000000);
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
