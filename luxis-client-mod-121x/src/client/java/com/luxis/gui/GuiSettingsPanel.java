package com.luxis.gui;

import com.luxis.config.GuiConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.luxis.gui.Draw.Face.*;
import static com.luxis.gui.Draw.Surface.*;

/** Settings pages share one clipped, scrollable viewport, even at large Minecraft GUI scales. */
final class GuiSettingsPanel {
    private static final String[] PAGES = {"Themes", "Look", "Motion", "Library", "Presets", "Files"};
    private static final int ROW = 29;
    private final Component[] labels = new Component[PAGES.length];
    private final Component[] themes = new Component[GuiTheme.ALL.size()];
    private int page, top, bottom, maxScroll;
    private float scroll, target, reveal = 1F;

    GuiSettingsPanel() {
        for (int i = 0; i < PAGES.length; i++) labels[i] = Draw.label(PAGES[i], STRONG);
        for (int i = 0; i < themes.length; i++) themes[i] = Draw.label(GuiTheme.ALL.get(i).name, BODY);
    }

    void draw(GuiGraphics g, Font font, List<ClickRegion> regions, int x, int y, int w,
              int end, float dt, float fade, GuiTheme.Palette theme) {
        int tx = x, ty = y;
        for (int i = 0; i < PAGES.length; i++) {
            int tw = font.width(labels[i]) + 16;
            if (tx != x && tx + tw > x + w) { tx = x; ty += 24; }
            LuxisModScreen.chip(g, tx, ty, tw, 21, i == page, fade, theme);
            Draw.text(g, font, labels[i], tx + 8, ty + 6, Draw.alpha(i == page ? theme.text : theme.muted, fade));
            regions.add(new ClickRegion(tx, ty, tw, 21, "settings_page", Integer.toString(i)));
            tx += tw + 5;
        }

        top = ty + 26;
        bottom = end;
        if (bottom <= top) return;
        int innerW = w - 8;
        int cols = Math.max(1, Math.min(4, (innerW + 6) / 105));
        int bodyH = switch (page) {
            case 0 -> ((themes.length + cols - 1) / cols) * 29;
            case 1 -> 8 * ROW;
            case 2 -> 7 * ROW;
            case 3 -> 3 * ROW;
            case 4 -> 4 * ROW;
            default -> 6 * ROW;
        };
        int viewport = bottom - top;
        maxScroll = Math.max(0, bodyH - viewport);
        target = Math.max(0, Math.min(maxScroll, target));
        scroll = AnimationUtil.guiApproach(Math.max(0, Math.min(maxScroll, scroll)), target, 23F, dt, 0.05F);
        int start = top - Math.round(scroll);
        reveal = AnimationUtil.reveal(reveal, dt, false, 6F, 6F);
        fade *= AnimationUtil.easeOutCubic(reveal);

        g.enableScissor(x, top, x + w, bottom);
        switch (page) {
            case 0 -> {
                int cw = (innerW - (cols - 1) * 6) / cols;
                for (int i = 0; i < themes.length; i++) {
                    GuiTheme.Palette preset = GuiTheme.ALL.get(i);
                    int px = x + (i % cols) * (cw + 6), py = start + (i / cols) * 29;
                    if (py + 24 <= top || py >= bottom) continue;
                    boolean selected = preset == theme;
                    LuxisModScreen.chip(g, px, py, cw, 24, selected, fade, preset);
                    Draw.pill(g, px + 6, py + 7, 9, 9, Draw.alpha(preset.accent, fade));
                    Draw.pill(g, px + 11, py + 7, 9, 9, Draw.alpha(preset.accent2, fade));
                    Draw.text(g, font, themes[i], px + 24, py + 8, Draw.alpha(theme.text, fade));
                    region(regions, px, py, cw, 24, "theme", preset.name);
                }
            }
            case 1 -> {
                var look = GuiConfig.appearance();
                step(g, font, regions, x, start, innerW, "Window width", look.windowWidth + "%", "window_width", fade, theme);
                step(g, font, regions, x, start + ROW, innerW, "Window height", look.windowHeight + "%", "window_height", fade, theme);
                control(g, font, regions, x, start + ROW * 2, innerW, "Card density", look.density.name(), "look", "density", fade, theme);
                control(g, font, regions, x, start + ROW * 3, innerW, "Panel corners", look.corners.name(), "look", "corners", fade, theme);
                control(g, font, regions, x, start + ROW * 4, innerW, "Glass opacity", look.glass.name(), "look", "glass", fade, theme);
                control(g, font, regions, x, start + ROW * 5, innerW, "Background dimming", look.backdrop.name(), "look", "backdrop", fade, theme);
                control(g, font, regions, x, start + ROW * 6, innerW, "Card descriptions", look.descriptions ? "Shown" : "Hidden", "look", "descriptions", fade, theme);
                control(g, font, regions, x, start + ROW * 7, innerW, "Theme intensity", look.colorDepth.name(), "look", "color_depth", fade, theme);
            }
            case 2 -> {
                var look = GuiConfig.appearance();
                control(g, font, regions, x, start, innerW, "Animations", look.motion.name(), "look", "motion", fade, theme);
                control(g, font, regions, x, start + ROW, innerW, "Transition style", look.transition.name(), "look", "transition", fade, theme);
                step(g, font, regions, x, start + ROW * 2, innerW, "Animation speed", look.animationSpeed + "%", "animation_speed", fade, theme);
                control(g, font, regions, x, start + ROW * 3, innerW, "Menu particles", look.particles.name(), "look", "particles", fade, theme);
                step(g, font, regions, x, start + ROW * 4, innerW, "Particle count", Integer.toString(look.particleCount), "particle_count", fade, theme);
                step(g, font, regions, x, start + ROW * 5, innerW, "Particle speed", look.particleSpeed + "%", "particle_speed", fade, theme);
                step(g, font, regions, x, start + ROW * 6, innerW, "Particle opacity", look.particleOpacity + "%", "particle_opacity", fade, theme);
            }
            case 3 -> {
                control(g, font, regions, x, start, innerW, "Sort modules", GuiConfig.sort().label, "sort", "", fade, theme);
                info(g, font, x, start + ROW + 5, innerW, "Pinned modules: " + GuiConfig.favoriteCount(), fade, theme);
                info(g, font, x, start + ROW * 2 + 5, innerW, "Use the star on a card to pin or unpin it.", fade, theme);
            }
            case 4 -> {
                info(g, font, x, start + 5, innerW, "Save your current look, then load it whenever you like.", fade, theme);
                for (int i = 0; i < 3; i++) {
                    int py = start + (i + 1) * ROW;
                    if (py + 24 <= top || py >= bottom) continue;
                    String preset = GuiConfig.presetTheme(i);
                    String label = "Layout " + (i + 1) + " · " + (preset == null ? "Empty" : preset);
                    Draw.guiSurface(g, CARD, x, py, innerW, 24, Draw.alpha(theme.paint().card(), fade));
                    Draw.text(g, font, label, BODY, x + 8, py + 8, Draw.alpha(theme.text, fade));
                    int saveX = x + innerW - 44, loadX = saveX - 45;
                    small(g, font, regions, saveX, py + 2, "Save", "preset_save", Integer.toString(i), true, fade, theme);
                    small(g, font, regions, loadX, py + 2, "Load", "preset_load", Integer.toString(i), preset != null, fade, theme);
                }
            }
            default -> {
                info(g, font, x, start + 5, innerW, "Appearance → config/luxis/gui/appearance.json", fade, theme);
                info(g, font, x, start + ROW + 5, innerW, "Favorites & sorting → config/luxis/gui/browser.json", fade, theme);
                info(g, font, x, start + ROW * 2 + 5, innerW, "Layout presets → config/luxis/gui/presets/", fade, theme);
                info(g, font, x, start + ROW * 3 + 5, innerW, "Module and HUD profiles → config/luxis/profiles/", fade, theme);
                control(g, font, regions, x, start + ROW * 4, innerW, "Appearance only", "Reset", "reset_look", "", fade, theme);
                control(g, font, regions, x, start + ROW * 5, innerW, "Sorting only (keeps pins)", "Reset", "reset_sort", "", fade, theme);
            }
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int thumb = Math.max(12, viewport * viewport / bodyH);
            Draw.pill(g, x + w - 3, top, 3, viewport, Draw.alpha(theme.paint().border(), fade));
            Draw.pill(g, x + w - 3, top + (viewport - thumb) * scroll / maxScroll, 3, thumb, Draw.alpha(theme.accent, fade));
        }
    }

    private void control(GuiGraphics g, Font font, List<ClickRegion> regions, int x, int y, int w,
                         String text, String value, String action, String data, float fade, GuiTheme.Palette theme) {
        if (y + 24 <= top || y >= bottom) return;
        Draw.guiSurface(g, CARD, x, y, w, 24, Draw.alpha(theme.paint().card(), fade));
        Draw.text(g, font, text, BODY, x + 8, y + 8, Draw.alpha(theme.text, fade));
        int bw = Math.min(104, w / 2 - 6), bx = x + w - bw - 3;
        LuxisModScreen.chip(g, bx, y + 2, bw, 20, true, fade, theme);
        Draw.text(g, font, value, BODY, bx + 8, y + 7, Draw.alpha(theme.text, fade));
        region(regions, bx, y + 2, bw, 20, action, data);
    }

    private void info(GuiGraphics g, Font font, int x, int y, int w, String value, float fade, GuiTheme.Palette theme) {
        if (y + 12 < top || y >= bottom) return;
        g.enableScissor(x, top, x + w, bottom);
        Draw.text(g, font, value, BODY, x + 3, y, Draw.alpha(theme.muted, fade));
        g.disableScissor();
    }

    private void step(GuiGraphics g, Font font, List<ClickRegion> regions, int x, int y, int w,
                      String label, String value, String data, float fade, GuiTheme.Palette theme) {
        if (y + 24 <= top || y >= bottom) return;
        Draw.guiSurface(g, CARD, x, y, w, 24, Draw.alpha(theme.paint().card(), fade));
        Draw.text(g, font, label, BODY, x + 8, y + 8, Draw.alpha(theme.text, fade));
        int bx = x + w - 91;
        LuxisModScreen.chip(g, bx, y + 2, 22, 20, false, fade, theme);
        LuxisModScreen.chip(g, bx + 66, y + 2, 22, 20, true, fade, theme);
        Draw.text(g, font, "−", STRONG, bx + 7, y + 7, Draw.alpha(theme.muted, fade));
        Draw.text(g, font, value, BODY, bx + 30, y + 8, Draw.alpha(theme.text, fade));
        Draw.text(g, font, "+", STRONG, bx + 73, y + 7, Draw.alpha(theme.text, fade));
        region(regions, bx, y + 2, 22, 20, "adjust_down", data);
        region(regions, bx + 66, y + 2, 22, 20, "adjust_up", data);
    }

    private void region(List<ClickRegion> regions, int x, int y, int w, int h, String action, String data) {
        int from = Math.max(top, y), to = Math.min(bottom, y + h);
        if (to > from) regions.add(new ClickRegion(x, from, w, to - from, action, data));
    }

    private void small(GuiGraphics g, Font font, List<ClickRegion> regions, int x, int y, String label,
                       String action, String data, boolean enabled, float fade, GuiTheme.Palette theme) {
        LuxisModScreen.chip(g, x, y, 40, 20, enabled, fade, theme);
        Draw.text(g, font, label, BODY, x + 8, y + 6, Draw.alpha(enabled ? theme.text : theme.muted, fade));
        if (enabled) region(regions, x, y, 40, 20, action, data);
    }

    void select(int page) {
        if (this.page == page) return;
        this.page = page;
        scroll = target = reveal = 0F;
    }

    void scroll(double amount) { target = Math.max(0, Math.min(maxScroll, target - (float) amount * 31F)); }
}
