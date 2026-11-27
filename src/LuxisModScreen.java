package com.luxis.gui;

import com.luxis.client.LuxisClientMod;
import com.luxis.module.Module;
import com.luxis.module.ModuleRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Rounded windowed menu inspired by the reference — no ClickGUI columns. */
public final class LuxisModScreen extends Screen {
    private static final String[] TABS = {"MODS", "SETTINGS", "WAYPOINTS"};
    private static final String[] FILTERS = {"ALL", "NEW", "HUD", "OPTIMIZATION", "PVP", "VISUAL"};
    private final List<ClickRegion> regions = new ArrayList<>();
    private int wx, wy, ww, wh, scroll;
    private String tab = "MODS", filter = "ALL", search = "";

    public LuxisModScreen() { super(Component.literal("LUNAX MODS")); }

    @Override
    protected void init() {
        ww = Math.min(1100, width - 50);
        wh = Math.min(620, height - 50);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;
        AnimationUtil.set("window_open", 0F);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float tickDelta) {
        float dt = AnimationUtil.deltaSeconds();
        float open = AnimationUtil.approach("window_open", 1F, 13F, dt);
        float eased = AnimationUtil.easeOutCubic(open);
        int sw = (int) (ww * (0.97F + 0.03F * eased));
        int sh = (int) (wh * (0.96F + 0.04F * eased));
        int px = wx + (ww - sw) / 2, py = wy + (wh - sh) / 2;
        GuiTheme.Palette th = GuiTheme.current();
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 0.0025));

        g.fill(0, 0, width, height, 0x99050709);
        Draw.shadow(g, px, py, sw, sh, 22);
        Draw.rounded(g, px, py, sw, sh, 22, th.bg);
        Draw.rounded(g, px, py, sw, sh, 22, 0x18000000);
        Draw.rounded(g, px + 1, py + 1, sw - 2, 1, 1, AnimationUtil.lerpColor(th.accent, th.accent2, pulse));

        regions.clear();
        drawHeader(g, px, py, sw, th, pulse);
        int cx = px + 34, cy = py + 118, cw = sw - 68, ch = sh - 150;
        if (tab.equals("SETTINGS")) drawSettings(g, cx, cy, cw, ch, th);
        else if (tab.equals("WAYPOINTS")) drawWaypoints(g, cx, cy, th);
        else drawMods(g, cx, cy, cw, ch, mx, my, dt, th);
        label(g, "RIGHT SHIFT   Close", px + 34, py + sh - 28, th.muted);

        if (LuxisClientMod.options != null)
            LuxisClientMod.options.draw(g, px, py, sw, sh, mx, my, LuxisClientMod.optionsModule, dt);
    }

    private void drawHeader(GuiGraphicsExtractor g, int px, int py, int w, GuiTheme.Palette th, float pulse) {
        String title = switch (tab) {
            case "SETTINGS" -> "Settings";
            case "WAYPOINTS" -> "Waypoints";
            default -> "Mods";
        };
        label(g, "LUNAX CLIENT", px + 34, py + 26, th.accent);
        label(g, title, px + 32, py + 40, th.text);

        int sx = px + w - 340;
        Draw.rounded(g, sx, py + 30, 306, 34, 10, 0x22FFFFFF);
        Draw.rounded(g, sx, py + 30, 306, 34, 10, 0x11000000);
        label(g, "⌕   Search effects", sx + 14, py + 39, th.muted);
        regions.add(new ClickRegion(sx, py + 30, 306, 34, "search", ""));

        int tx = px + 34, ty = py + 88;
        for (String t : TABS) {
            int tw = t.length() * 8 + 28;
            boolean on = t.equals(tab);
            if (on) {
                float ind = AnimationUtil.approach("tab_ind", tx, 12F, AnimationUtil.deltaSeconds());
                Draw.rounded(g, (int) ind + 8, ty + 24, tw - 16, 2, 1, AnimationUtil.lerpColor(th.accent, th.accent2, pulse));
            }
            label(g, t, tx + 10, ty + 4, on ? th.text : th.muted);
            regions.add(new ClickRegion(tx, ty, tw, 28, "tab", t));
            tx += tw + 18;
        }
        g.fill(px + 32, py + 120, px + w - 32, py + 121, 0x2AFFFFFF);
    }

    private void drawMods(GuiGraphicsExtractor g, int cx, int cy, int cw, int ch, double mx, double my,
                          float dt, GuiTheme.Palette th) {
        int fx = cx, fy = cy;
        for (String f : FILTERS) {
            int fw = f.length() * 7 + 24, on = f.equals(filter) ? 1 : 0;
            Draw.rounded(g, fx, fy, fw, 22, 9, on == 1 ? AnimationUtil.lerpColor(th.accent, th.accent2, 0.35F) : 0x14FFFFFF);
            label(g, f, fx + 9, fy + 5, on == 1 ? 0xFFFFFFFF : th.muted);
            regions.add(new ClickRegion(fx, fy, fw, 22, "filter", f));
            fx += fw + 8;
        }

        List<Module> list = new ArrayList<>();
        for (Module m : ModuleRegistry.all()) {
            if (!filter.equals("ALL") && !filter.equals("NEW")) {
                String cat = m.category == Module.Category.PVP ? "PVP" : m.category.name();
                if (!cat.equals(filter)) continue;
            }
            if (filter.equals("NEW") && !m.isNew) continue;
            if (!search.isEmpty() && !(m.name + " " + m.description).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) continue;
            list.add(m);
        }

        int gx = cx, gy = cy + 34, gw = cw, cols = 3, gap = 12;
        int cardW = (gw - gap * (cols - 1)) / cols, cardH = 62;
        int rows = (list.size() + cols - 1) / cols;
        int max = Math.max(0, rows * (cardH + gap) - (ch - 34));
        scroll = Math.max(0, Math.min(scroll, max));
        g.enableScissor(gx, gy, gx + gw, gy + ch - 34);
        for (int i = 0; i < list.size(); i++) {
            int col = i % cols, row = i / cols;
            int x = gx + col * (cardW + gap), y = gy + row * (cardH + gap) - scroll;
            if (y + cardH < gy || y > cy + ch) continue;
            drawCard(g, list.get(i), x, y, cardW, cardH, mx, my, dt, th, i);
        }
        g.disableScissor();
        int sbX = gx + gw - 4, sbTop = gy, sbH = ch - 34;
        g.fill(sbX, sbTop, sbX + 3, sbTop + sbH, 0x22FFFFFF);
        int thumb = Math.max(28, sbH * Math.max(1, ch) / Math.max(1, rows * (cardH + gap)));
        g.fill(sbX, sbTop + (int) (scroll / Math.max(1f, max) * (sbH - thumb)), sbX + 3,
               sbTop + (int) (scroll / Math.max(1f, max) * (sbH - thumb)) + thumb, th.accent);
    }

    private void drawCard(GuiGraphicsExtractor g, Module m, int x, int y, int w, int h, double mx, double my,
                          float dt, GuiTheme.Palette th, int index) {
        float stagger = AnimationUtil.approach("card_" + index, 1F, 10F, dt);
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        float lift = (float) (-3 * AnimationUtil.approach("hover_" + m.id, hover ? 1F : 0F, 16F, dt) * stagger);
        Draw.shadow(g, x, (int) (y + lift), w, h, 10);
        Draw.rounded(g, x, (int) (y + lift), w, h, 10,
                hover ? AnimationUtil.lerpColor(0x18FFFFFF, th.accent, 0.10F) : 0x10FFFFFF);
        Draw.rounded(g, x, (int) (y + lift), w, h, 10, 0x14000000);
        String name = m.name.length() > 22 ? m.name.substring(0, 21) + "..." : m.name;
        String desc = m.description.length() > 30 ? m.description.substring(0, 29) + "..." : m.description;
        label(g, name, x + 14, (int) (y + lift + 10), th.text);
        label(g, desc, x + 14, (int) (y + lift + 30), th.muted);
        drawSwitch(g, x + w - 52, (int) (y + lift + 20), m.enabled, dt, th, m.id);
        regions.add(new ClickRegion(x, (int) (y + lift), w - 60, h, "options_zone", m.id));
        regions.add(new ClickRegion(x + w - 56, (int) (y + lift), 56, h, "toggle_zone", m.id));
    }

    private void drawSwitch(GuiGraphicsExtractor g, int x, int y, boolean on, float dt, GuiTheme.Palette th, String key) {
        float v = AnimationUtil.approach("switch_" + key, on ? 1F : 0F, 14F, dt);
        Draw.rounded(g, x, y, 38, 20, 10, AnimationUtil.lerpColor(0x33FFFFFF, th.accent, v));
        int knob = x + 2 + (int) (18 * AnimationUtil.easeOutCubic(v));
        Draw.rounded(g, knob, y + 2, 16, 16, 8, 0xFFFFFFFF);
    }

    private void drawSettings(GuiGraphicsExtractor g, int cx, int cy, int cw, int ch, GuiTheme.Palette th) {
        label(g, "Theme", cx, cy, th.text);
        Draw.rounded(g, cx, cy + 20, 240, 34, 10, 0x22FFFFFF);
        label(g, th.name + "   click to cycle", cx + 14, cy + 29, th.text);
        regions.add(new ClickRegion(cx, cy + 20, 240, 34, "theme", ""));
        label(g, "Smooth cubic-bezier motion, staggered cards, spring modals.", cx, cy + 80, th.muted);
        label(g, "Fair-play only — no cheats, auto-clickers, hitboxes or unfair combat automation.", cx, cy + 102, th.muted);
        label(g, "Profiles and settings persist to config/luxis.", cx, cy + 124, th.muted);
    }

    private void drawWaypoints(GuiGraphicsExtractor g, int cx, int cy, GuiTheme.Palette th) {
        label(g, "Waypoints", cx, cy, th.text);
        label(g, "Display-only bookmarks. No radar or entity tracking.", cx, cy + 24, th.muted);
    }

    private void label(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        Draw.text(g, font, s, x, y, color, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        if (LuxisClientMod.options != null) {
            LuxisClientMod.options.click(mx, my, LuxisClientMod.optionsModule);
            return true;
        }
        for (ClickRegion r : regions) if (r.contains(mx, my)) { handle(r); return true; }
        return true;
    }

    private void handle(ClickRegion r) {
        switch (r.action()) {
            case "tab" -> tab = r.data();
            case "filter" -> { filter = r.data(); scroll = 0; }
            case "theme" -> GuiTheme.cycle();
            case "search" -> { }
            case "options_zone" -> {
                Module m = ModuleRegistry.byId(r.data());
                if (m != null) { LuxisClientMod.options = new ModuleOptionsOverlay(); LuxisClientMod.optionsModule = m; }
            }
            case "toggle_zone" -> {
                Module m = ModuleRegistry.byId(r.data());
                if (m != null) {
                    m.enabled = !m.enabled;
                    if (m.enabled) m.onEnable(); else m.onDisable();
                    LuxisClientMod.save();
                }
            }
            case "options" -> {
                Module m = ModuleRegistry.byId(r.data());
                if (m != null) { LuxisClientMod.options = new ModuleOptionsOverlay(); LuxisClientMod.optionsModule = m; }
            }
            default -> { }
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double ax, double ay) {
        if (LuxisClientMod.options != null) LuxisClientMod.options.scroll(ay);
        else scroll -= (int) ay * 20;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (LuxisClientMod.capturing != null) {
            if (event.key() != InputConstants.KEY_ESCAPE) LuxisClientMod.capturing.key = InputConstants.getKey(event);
            LuxisClientMod.capturing = null; LuxisClientMod.save(); return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) { onClose(); return true; }
        if (!search.isEmpty() && event.key() == InputConstants.KEY_BACKSPACE) search = search.substring(0, search.length() - 1);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (search.length() < 40 && event.isAllowedChatCharacter()) search += event.codepointAsString();
        return true;
    }

    @Override
    public void onClose() { LuxisClientMod.save(); super.onClose(); }

    @Override
    public boolean isPauseScreen() { return false; }
}
