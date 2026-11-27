package com.luxis.gui;

import com.luxis.config.GuiConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/** Shared, bilinear-filtered surfaces: at most nine quads per panel, independent of size. */
public final class Draw {
    private static final Identifier ATLAS = id("textures/gui/surfaces.png");
    private static final int ATLAS_SIZE = 256, TEXELS = 4;

    public enum Surface {
        WINDOW(2, 2, 40, 17), CARD(164, 2, 22, 9), CHIP(164, 92, 20, 7),
        OUTLINE(84, 164, 22, 9), SHADOW(174, 174, 20, 8);

        final int u, v, size, border;

        Surface(int u, int v, int size, int border) {
            this.u = u;
            this.v = v;
            this.size = size * TEXELS;
            this.border = border * TEXELS;
        }
    }

    public enum Face {
        BODY("ui"), STRONG("ui_strong"), TITLE("ui_title");

        final Style style;

        Face(String name) {
            style = Style.EMPTY.withFont(new FontDescription.Resource(id(name)));
        }
    }

    private Draw() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("luxis", path);
    }

    public static void surface(GuiGraphics g, Surface s, int x, int y, int w, int h, int color) {
        surface(g, s, x, y, w, h, s.border / TEXELS, color);
    }

    static void guiSurface(GuiGraphics g, Surface s, int x, int y, int w, int h, int color) {
        surface(g, s, x, y, w, h, Math.round((s.border / TEXELS) * GuiConfig.appearance().corners.scale), color);
    }

    static void window(GuiGraphics g, int x, int y, int w, int h, float fade, GuiTheme.Palette palette) {
        int color = (GuiConfig.appearance().glass.alpha << 24) | (palette.paint().window() & 0xFFFFFF);
        guiSurface(g, Surface.WINDOW, x, y, w, h, alpha(color, fade));
        int radius = Math.round((Surface.WINDOW.border / TEXELS) * GuiConfig.appearance().corners.scale);
        softBorder(g, x, y, w, h, radius, alpha(palette.paint().border(), fade));
    }

    private static void surface(GuiGraphics g, Surface s, int x, int y, int w, int h, int inset, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int bx = Math.min(inset, w / 2), by = Math.min(inset, h / 2);
        int mid = s.size - s.border * 2;
        row(g, s, x, y, w, by, bx, s.v, s.border, color);
        row(g, s, x, y + by, w, h - by * 2, bx, s.v + s.border, mid, color);
        row(g, s, x, y + h - by, w, by, bx, s.v + s.size - s.border, s.border, color);
    }

    private static void row(GuiGraphics g, Surface s, int x, int y, int w, int h,
                            int bx, int v, int sh, int color) {
        if (h == 0) return;
        blit(g, x, y, bx, h, s.u, v, s.border, sh, color);
        blit(g, x + bx, y, w - bx * 2, h, s.u + s.border, v, s.size - 2 * s.border, sh, color);
        blit(g, x + w - bx, y, bx, h, s.u + s.size - s.border, v, s.border, sh, color);
    }

    public static void pill(GuiGraphics g, float x, float y, int w, int h, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        boolean fractional = x != ix || y != iy;
        if (fractional) g.pose().pushMatrix().translate(x - ix, y - iy);
        if (w == h) {
            blit(g, ix, iy, w, h, 2, 164, 80, 80, color);
        } else if (h > w) {
            int end = (w + 1) / 2;
            blit(g, ix, iy, w, end, 2, 164, 80, 40, color);
            blit(g, ix, iy + end, w, h - 2 * end, 2, 203, 80, 2, color);
            blit(g, ix, iy + h - end, w, end, 2, 204, 80, 40, color);
        } else {
            int end = (h + 1) / 2;
            blit(g, ix, iy, end, h, 2, 164, 40, 80, color);
            blit(g, ix + end, iy, w - 2 * end, h, 41, 164, 2, 80, color);
            blit(g, ix + w - end, iy, end, h, 42, 164, 40, 80, color);
        }
        if (fractional) g.pose().popMatrix();
    }

    public static void softBorder(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        surface(g, Surface.OUTLINE, x, y, w, h, radius, color);
    }

    private static void blit(GuiGraphics g, int x, int y, int w, int h, int u, int v,
                             int sw, int sh, int color) {
        if (w > 0 && h > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x, y, u, v, w, h, sw, sh, ATLAS_SIZE, ATLAS_SIZE, color);
        }
    }

    public static int alpha(int color, float opacity) {
        return (Math.round((color >>> 24) * opacity) << 24) | (color & 0xFFFFFF);
    }

    public static Component label(String value, Face face) {
        return Component.literal(value).setStyle(face.style);
    }

    public static Component fit(Font font, String value, Face face, int width) {
        Component full = label(value, face);
        if (font.width(full) <= width) return full;
        Component best = label("…", face);
        int lo = 1, hi = value.codePointCount(0, value.length()) - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            Component truncated = label(value.substring(0, value.offsetByCodePoints(0, mid)).stripTrailing() + "…", face);
            if (font.width(truncated) <= width) { best = truncated; lo = mid + 1; }
            else hi = mid - 1;
        }
        return best;
    }

    public static void text(GuiGraphics g, Font font, String value, Face face, int x, int y, int color) {
        text(g, font, label(value, face), x, y, color);
    }

    public static void text(GuiGraphics g, Font font, Component value, int x, int y, int color) {
        // Minecraft treats colors with near-zero alpha as opaque.
        if ((color >>> 24) >= 4) g.drawString(font, value, x, y, color, false);
    }
}
