package com.luxis.gui;

import com.luxis.client.LuxisClientMod;
import com.luxis.module.Module;
import com.luxis.module.Setting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.luxis.gui.Draw.Face.*;
import static com.luxis.gui.Draw.Surface.*;

final class ModuleOptionsOverlay {
    private static final int[] PALETTE = {0xFFFFFFFF, 0xFFC864FF, 0xFF22C55E, 0xFFEF4444, 0xFF38BDF8, 0xFFFBBF24};
    private static final int ROW_HEIGHT = 29, GENERAL_ROWS = 3, SECTION_HEIGHT = 21;
    private static final Component GENERAL = Draw.label("GENERAL", STRONG);
    private static final Component SETTINGS = Draw.label("MODULE SETTINGS", STRONG);
    private static final Component RESET = Draw.label("Reset defaults", STRONG);
    private static final Component DONE = Draw.label("Done", STRONG);

    private static final class Row {
        final Component label;
        final Setting<?> setting;
        Component renderedValue;
        Object value;
        int valueWidth;
        float motion = Float.NaN;

        Row(String label, Setting<?> setting) {
            this.label = Draw.label(label, BODY);
            this.setting = setting;
        }

        float animate(float target, float dt) {
            motion = Float.isNaN(motion) ? target : AnimationUtil.guiApproach(motion, target, 22F, dt, 0.003F);
            return motion;
        }
    }

    private final Module module;
    private final Font font;
    private final List<Row> rows = new ArrayList<>();
    private Component title, description;
    private int w, h, x, y, clipTop, clipBottom, maxScroll, controlX, controlW;
    private float open, offsetY, scrollTarget, scroll;
    private boolean closing, capturing, dirty;
    private Setting.SliderSetting dragging;
    private Setting.TextSetting typing;

    ModuleOptionsOverlay(Module module, Font font) {
        this.module = module;
        this.font = font;
        rows.add(new Row("Enable module", null));
        rows.add(new Row("Keybind", null));
        rows.add(new Row("Keybind mode", null));
        for (Setting<?> s : module.settings) rows.add(new Row(s.label, s));
    }

    boolean draw(GuiGraphics g, int px, int py, int pw, int ph, float dt, GuiTheme.Palette th) {
        open = AnimationUtil.reveal(open, dt, closing, 6F, 8F);
        if (closing && open <= 0F) return false;
        float fade = AnimationUtil.easeOutCubic(open);
        offsetY = AnimationUtil.slide(12F * (1F - fade));
        int newW = Math.min(490, pw - 20);
        if (newW != w) {
            w = newW;
            title = Draw.fit(font, module.name, TITLE, w - 32);
            description = Draw.fit(font, module.description, BODY, w - 32);
        }
        int content = rows.size() * ROW_HEIGHT + SECTION_HEIGHT * (rows.size() > GENERAL_ROWS ? 2 : 1);
        h = Math.min(ph - 18, Math.min(510, content + 112));
        x = px + (pw - w) / 2;
        y = py + (ph - h) / 2;
        clipTop = y + 63;
        clipBottom = y + h - 45;
        controlW = Math.min(152, w / 2 - 26);
        controlX = x + w - controlW - 22;
        maxScroll = Math.max(0, content - (clipBottom - clipTop));
        scrollTarget = Math.min(scrollTarget, maxScroll);
        scroll = AnimationUtil.guiApproach(Math.min(scroll, maxScroll), scrollTarget, 23F, dt, 0.05F);

        g.fill(0, 0, g.guiWidth(), g.guiHeight(), Draw.alpha(0xC0000000 | (th.paint().backdrop() & 0xFFFFFF), fade));
        g.pose().pushMatrix().translate(0F, offsetY);
        Draw.surface(g, SHADOW, x - 7, y - 4, w + 14, h + 15, Draw.alpha(0xA8000000, fade));
        Draw.window(g, x, y, w, h, fade, th);
        text(g, title, x + 16, y + 21, th.text, fade);
        text(g, description, x + 16, y + 42, th.muted, fade);
        if (clipBottom > clipTop) {
            g.enableScissor(x + 12, clipTop, x + w - 12, clipBottom);
            int base = clipTop - Math.round(scroll);
            g.pose().pushMatrix().translate(0F, Math.round(scroll) - scroll);
            text(g, GENERAL, x + 16, base + 4, th.accent, fade);
            if (rows.size() > GENERAL_ROWS) {
                text(g, SETTINGS, x + 16, base + SECTION_HEIGHT + GENERAL_ROWS * ROW_HEIGHT + 4, th.accent, fade);
            }
            for (int i = 0; i < rows.size(); i++) {
                int ry = base + rowOffset(i);
                if (ry + ROW_HEIGHT <= clipTop || ry >= clipBottom + 1) continue;
                drawRow(g, rows.get(i), i, ry, dt, fade, th);
            }
            g.pose().popMatrix();
            g.disableScissor();
        }
        int by = y + h - 35;
        LuxisModScreen.chip(g, x + 16, by, 100, 25, false, fade, th);
        LuxisModScreen.chip(g, x + w - 86, by, 70, 25, true, fade, th);
        text(g, RESET, x + 26, by + 7, th.text, fade);
        text(g, DONE, x + w - 51 - font.width(DONE) / 2, by + 7, th.text, fade);
        g.pose().popMatrix();
        return true;
    }

    private int rowOffset(int i) {
        return i * ROW_HEIGHT + SECTION_HEIGHT * (i < GENERAL_ROWS ? 1 : 2);
    }

    private void text(GuiGraphics g, Component value, int x, int y, int color, float fade) {
        Draw.text(g, font, value, x, y, Draw.alpha(color, fade));
    }

    private void drawRow(GuiGraphics g, Row row, int index, int y, float dt, float fade, GuiTheme.Palette th) {
        text(g, row.label, x + 16, y + 8, th.text, fade);
        Setting<?> s = row.setting;
        int right = controlX + controlW;
        if (index == 0 || s instanceof Setting.BooleanSetting) {
            boolean on = index == 0 ? module.enabled : ((Setting.BooleanSetting) s).value;
            LuxisModScreen.drawSwitch(g, right - 36, y + 3, row.animate(on ? 1F : 0F, dt), fade, th);
        } else if (s instanceof Setting.ColorSetting color) {
            Draw.pill(g, right - 33, y + 3, 33, 18, Draw.alpha(th.muted, fade));
            Draw.pill(g, right - 32, y + 4, 31, 16, Draw.alpha(color.value, fade));
        } else if (s instanceof Setting.SliderSetting slider) {
            updateValue(row, slider.value, controlW - 14,
                    () -> BigDecimal.valueOf(slider.value).stripTrailingZeros().toPlainString() + slider.unit);
            text(g, row.renderedValue, controlX, y + 3, th.muted, fade);
            float target = AnimationUtil.clamp((float) ((slider.value - slider.min) / (slider.max - slider.min)));
            float pos = controlX + row.animate(target, dt) * (controlW - 8);
            Draw.pill(g, controlX, y + 18, controlW, 4, Draw.alpha(th.paint().track(), fade));
            Draw.pill(g, controlX, y + 18, Math.round(pos - controlX) + 4, 4, Draw.alpha(th.accent, fade));
            Draw.pill(g, pos, y + 16, 8, 8, Draw.alpha(th.paint().thumb(), fade));
        } else {
            String value = switch (index) {
                case 1 -> capturing ? "Press a key…" : module.key == null || module.key == InputConstants.UNKNOWN
                        ? "Unbound" : module.key.getDisplayName().getString();
                case 2 -> module.trigger == Module.Trigger.TOGGLE ? "Toggle" : "Hold";
                default -> s instanceof Setting.TextSetting input
                        ? (input.value.isEmpty() ? (typing == input ? "Type a label…" : "Click to edit") : input.value + (typing == input ? "|" : ""))
                        : ((Setting.ModeSetting) s).value;
            };
            updateValue(row, value, controlW - 16, () -> value);
            LuxisModScreen.chip(g, controlX, y + 1, controlW, 23, index == 1 && capturing || typing != null && typing == s, fade, th);
            text(g, row.renderedValue, controlX + 8, y + 8, th.text, fade);
        }
    }

    private void updateValue(Row row, Object value, int width, java.util.function.Supplier<String> text) {
        if (row.renderedValue == null || width != row.valueWidth || !Objects.equals(row.value, value)) {
            row.renderedValue = Draw.fit(font, text.get(), BODY, width);
            row.value = value;
            row.valueWidth = width;
        }
    }

    void click(double mx, double my) {
        if (closing || open < 0.15F) return;
        typing = null;
        my -= offsetY;
        if (mx < x || mx >= x + w || my < y || my >= y + h) { close(); return; }
        if (my >= y + h - 35 && my < y + h - 10) {
            if (mx >= x + 16 && mx < x + 116) { module.resetDefaults(); dirty = true; }
            else if (mx >= x + w - 86 && mx < x + w - 16) close();
            return;
        }
        if (my < clipTop || my >= clipBottom || mx < controlX || mx >= controlX + controlW) return;
        double rel = my - clipTop + scroll;
        for (int i = 0; i < rows.size(); i++) {
            int top = rowOffset(i);
            if (rel < top || rel >= top + ROW_HEIGHT) continue;
            Setting<?> s = rows.get(i).setting;
            switch (i) {
                case 0 -> {
                    module.enabled = !module.enabled;
                    if (module.enabled) module.onEnable(); else module.onDisable();
                }
                case 1 -> { capturing = true; return; }
                case 2 -> module.trigger = module.trigger == Module.Trigger.TOGGLE ? Module.Trigger.HOLD : Module.Trigger.TOGGLE;
                default -> {
                    if (s instanceof Setting.TextSetting text) { typing = text; capturing = false; return; }
                    if (s instanceof Setting.BooleanSetting b) b.value = !b.value;
                    else if (s instanceof Setting.ModeSetting mode) {
                        int next = (mode.options.indexOf(mode.value) + 1) % mode.options.size();
                        mode.value = mode.options.get(next);
                    } else if (s instanceof Setting.SliderSetting slider) {
                        dragging = slider;
                        drag(mx);
                    } else if (s instanceof Setting.ColorSetting color) {
                        int next = 0;
                        for (int j = 0; j < PALETTE.length; j++) if (PALETTE[j] == color.value) next = (j + 1) % PALETTE.length;
                        color.value = PALETTE[next];
                    }
                }
            }
            dirty = true;
            return;
        }
    }

    boolean drag(double mx) {
        if (closing || dragging == null) return false;
        double t = Math.clamp((mx - controlX - 4) / (controlW - 8), 0D, 1D);
        long steps = Math.round((dragging.max - dragging.min) * t / dragging.step);
        dragging.value = t == 1D ? dragging.max : Math.min(dragging.max, BigDecimal.valueOf(dragging.min)
                .add(BigDecimal.valueOf(dragging.step).multiply(BigDecimal.valueOf(steps))).doubleValue());
        dirty = true;
        return true;
    }

    boolean release() {
        if (dragging == null) return false;
        dragging = null;
        return true;
    }

    boolean captureKey(KeyEvent event) {
        if (typing != null) {
            if (event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_RETURN
                    || event.key() == InputConstants.KEY_NUMPADENTER) typing = null;
            else if (event.key() == InputConstants.KEY_BACKSPACE && !typing.value.isEmpty())
                typeValue(typing.value.substring(0, typing.value.offsetByCodePoints(typing.value.length(), -1)));
            else if (event.key() == InputConstants.KEY_DELETE) typeValue("");
            return true;
        }
        if (!capturing) return false;
        capturing = false;
        if (event.key() != InputConstants.KEY_ESCAPE) {
            module.key = InputConstants.getKey(event);
            dirty = true;
        }
        return true;
    }

    boolean type(String text) {
        if (typing == null) return false;
        typeValue(typing.value + text);
        return true;
    }

    private void typeValue(String value) { typing.set(value); dirty = true; }

    void scroll(double amount) {
        if (!closing) scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) amount * 32F));
    }

    void commit() {
        if (dirty) {
            LuxisClientMod.save();
            dirty = false;
        }
    }

    void close() {
        closing = true;
        capturing = false;
        dragging = null;
        typing = null;
        commit();
    }
}
