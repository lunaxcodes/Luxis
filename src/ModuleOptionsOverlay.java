package com.luxis.gui;

import com.luxis.client.LuxisClientMod;
import com.luxis.module.Module;
import com.luxis.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public final class ModuleOptionsOverlay {
    private static final int[] PALETTE = {0xFFFFFFFF, 0xFFC864FF, 0xFF22C55E, 0xFFEF4444, 0xFF38BDF8, 0xFFFBBF24};
    private final List<ClickRegion> regions = new ArrayList<>();
    private int x, y, w, h, scroll;
    private GuiTheme.Palette th;

    public void draw(GuiGraphicsExtractor g, int px, int py, int pw, int ph, double mx, double my, Module m, float dt) {
        regions.clear();
        float anim = AnimationUtil.approach("options_open", 1F, 14F, dt);
        float scale = 0.9F + 0.1F * AnimationUtil.easeOutCubic(anim);
        w = Math.min(520, pw - 60); h = Math.min(ph - 50, 560);
        x = px + (pw - w) / 2; y = py + (ph - h) / 2;
        int cx = x + (int) (w * (1 - scale) / 2), cy = y + (int) (h * (1 - scale) / 2);
        int cw = (int) (w * scale), ch = (int) (h * scale);
        g.fill(cx - 2000, cy - 2000, cx + 2000, cy + 2000, 0x99000000);
        th = GuiTheme.current();
        Draw.rounded(g, cx, cy, cw, ch, 14, th.bg);
        Draw.rounded(g, cx, cy, cw, ch, 14, 0x22000000);
        Draw.rounded(g, cx + 1, cy + 1, cw - 2, 2, 1, th.accent);
        text(g, m.name, cx + 20, cy + 16, 0xFFFFFFFF);
        text(g, m.description, cx + 20, cy + 34, 0xFF9B93A8);

        int yy = cy + 58 - scroll;
        section(g, cx + 20, yy, "GENERAL"); yy += 16;
        yy = toggle(g, cx + 20, yy, cw - 60, "Enable module", m.enabled, "enabled");
        yy = keybind(g, cx + 20, yy, cw - 60, m);
        yy = cycle(g, cx + 20, yy, cw - 60, "Trigger mode", m.trigger.name(), new String[]{"TOGGLE", "HOLD"}, "trigger");
        section(g, cx + 20, yy + 6, "MODULE SETTINGS"); yy += 22;
        for (Setting<?> s : m.settings) {
            if (s instanceof Setting.BooleanSetting b) yy = toggle(g, cx + 20, yy, cw - 60, b.label, b.value, b.key);
            else if (s instanceof Setting.SliderSetting sl) yy = slider(g, cx + 20, yy, cw - 60, sl);
            else if (s instanceof Setting.ModeSetting md) yy = cycle(g, cx + 20, yy, cw - 60, md.label, md.value, md.options.toArray(String[]::new), md.key);
            else if (s instanceof Setting.ColorSetting col) yy = color(g, cx + 20, yy, cw - 60, col);
            if (yy > cy + ch - 60) break;
        }
        button(g, cx + 20, cy + ch - 44, 120, 28, "RESET DEFAULTS", "reset");
        button(g, cx + cw - 140, cy + ch - 44, 120, 28, "SAVE", "save");
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int c) { Draw.text(g, Minecraft.getInstance().font, s, x, y, c, false); }
    private void section(GuiGraphicsExtractor g, int x, int y, String s) { text(g, s, x, y, th.accent); }

    private int toggle(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean on, String key) {
        text(g, label, x, y, th.muted);
        int bx = x + w - 44;
        Draw.rounded(g, bx, y - 3, 40, 16, 8, on ? th.accent : 0x33FFFFFF);
        Draw.rounded(g, on ? bx + 22 : bx + 2, y - 1, 16, 12, 6, 0xFFFFFFFF);
        regions.add(new ClickRegion(bx - 6, y - 8, 52, 26, "toggle", key));
        return y + 22;
    }

    private int keybind(GuiGraphicsExtractor g, int x, int y, int w, Module m) {
        text(g, "Keybind", x, y, 0xFFD8D2DE);
        String name = LuxisClientMod.capturing == m ? "PRESS A KEY..." : (m.key == null ? "NONE" : m.key.getName());
        int bx = x + w - 130;
        Draw.rounded(g, bx, y - 4, 126, 18, 6, 0x33000000);
        Draw.rounded(g, bx, y - 4, 126, 18, 6, LuxisClientMod.capturing == m ? th.accent : 0x22FFFFFF);
        text(g, name, bx + 6, y, 0xFFFFFFFF);
        regions.add(new ClickRegion(bx, y - 6, 126, 24, "keybind", m.id));
        return y + 22;
    }

    private int cycle(GuiGraphicsExtractor g, int x, int y, int w, String label, String value, String[] options, String key) {
        text(g, label, x, y, th.muted);
        int bx = x + w - 130;
        Draw.rounded(g, bx, y - 4, 126, 18, 6, 0x33000000);
        Draw.rounded(g, bx, y - 4, 126, 18, 6, 0x22FFFFFF);
        text(g, value, bx + 6, y, 0xFFFFFFFF);
        regions.add(new ClickRegion(bx, y - 6, 126, 24, "cycle", key));
        return y + 22;
    }

    private int slider(GuiGraphicsExtractor g, int x, int y, int w, Setting.SliderSetting s) {
        text(g, s.label + "  " + (Math.round(s.value * 10) / 10.0) + s.unit, x, y, 0xFFD8D2DE);
        int bx = x + w - 150, by = y + 8;
        Draw.rounded(g, bx, by, 146, 4, 2, 0x44FFFFFF);
        int pos = bx + (int) (146 * (s.value - s.min) / Math.max(0.0001, s.max - s.min));
        Draw.rounded(g, bx, by, pos - bx, 4, 2, th.accent);
        Draw.rounded(g, pos - 2, by - 3, 4, 10, 2, 0xFFFFFFFF);
        regions.add(new ClickRegion(bx - 4, by - 5, 154, 15, "slider", s.key));
        return y + 26;
    }

    private int color(GuiGraphicsExtractor g, int x, int y, int w, Setting.ColorSetting s) {
        text(g, s.label, x, y, 0xFFD8D2DE);
        int bx = x + w - 40;
        Draw.rounded(g, bx, y - 4, 32, 18, 5, s.value);
        Draw.rounded(g, bx, y - 4, 32, 18, 5, 0x44FFFFFF);
        regions.add(new ClickRegion(bx, y - 6, 32, 24, "color", s.key));
        return y + 24;
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, String action) {
        Draw.rounded(g, x, y, w, h, 7, 0x22FFFFFF);
        Draw.rounded(g, x, y, w, h, 7, th.accent);
        text(g, label, x + 8, y + 7, 0xFFFFFFFF);
        regions.add(new ClickRegion(x, y, w, h, action, ""));
    }

    public boolean click(double mx, double my, Module m) {
        for (ClickRegion r : regions) {
            if (!r.contains(mx, my)) continue;
            switch (r.action()) {
                case "toggle" -> {
                    if (r.data().equals("enabled")) m.enabled = !m.enabled;
                    else { Setting<?> s = m.setting(r.data()); if (s instanceof Setting.BooleanSetting b) b.value = !b.value; }
                }
                case "keybind" -> LuxisClientMod.capturing = m;
                case "cycle" -> {
                    if (r.data().equals("trigger")) m.trigger = m.trigger == Module.Trigger.TOGGLE ? Module.Trigger.HOLD : Module.Trigger.TOGGLE;
                    else {
                        Setting<?> s = m.setting(r.data());
                        if (s instanceof Setting.ModeSetting md) {
                            int i = md.options.indexOf(md.value);
                            md.value = md.options.get((i + 1) % md.options.size());
                        }
                    }
                }
                case "slider" -> {
                    Setting<?> s = m.setting(r.data());
                    if (s instanceof Setting.SliderSetting sl) {
                        double t = Math.max(0, Math.min(1, (mx - (r.x() + 4)) / Math.max(1, r.w() - 8)));
                        sl.value = Math.round((sl.min + (sl.max - sl.min) * t) / sl.step) * sl.step;
                    }
                }
                case "color" -> {
                    Setting<?> s = m.setting(r.data());
                    if (s instanceof Setting.ColorSetting c) {
                        int i = 0;
                        for (int j = 0; j < PALETTE.length; j++) if (PALETTE[j] == c.value) i = j;
                        c.value = PALETTE[(i + 1) % PALETTE.length];
                    }
                }
                case "reset" -> { m.resetDefaults(); LuxisClientMod.save(); }
                case "save" -> { LuxisClientMod.save(); LuxisClientMod.options = null; }
                default -> { return false; }
            }
            return true;
        }
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public void scroll(double amount) { scroll = Math.max(0, scroll + (int) amount * 12); }
}
