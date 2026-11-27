package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.module.ModuleRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class CrosshairModule extends Module {
    public CrosshairModule() {
        super("crosshair", "Crosshair Customizer", "Gap, length, thickness and dot.", Category.VISUAL, true, false);
        add(new Setting.SliderSetting("gap", "Gap", 4, 0, 8, 1));
        add(new Setting.SliderSetting("length", "Length", 6, 2, 14, 1));
        add(new Setting.SliderSetting("thickness", "Thickness", 2, 1, 5, 0.5));
        add(new Setting.BooleanSetting("dot", "Center dot", false));
        add(new Setting.BooleanSetting("outline", "Outline", true));
        add(new Setting.ColorSetting("color", "Color", 0xFFFFFFFF));
        add(new Setting.BooleanSetting("dynamic", "Dynamic gap", false));
    }

    public void render(GuiGraphicsExtractor g, float tickDelta) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int gap = ((Number) get("gap")).intValue(), len = ((Number) get("length")).intValue();
        int t = Math.max(1, ((Number) get("thickness")).intValue());
        int color = ((Number) get("color")).intValue();
        int outline = Boolean.TRUE.equals(get("outline")) ? 0xFF000000 : color;
        g.fill(cx - gap - len, cy - t / 2, cx - gap, cy + t / 2, outline);
        g.fill(cx + gap, cy - t / 2, cx + gap + len, cy + t / 2, outline);
        g.fill(cx - t / 2, cy - gap - len, cx + t / 2, cy - gap, outline);
        g.fill(cx - t / 2, cy + gap, cx + t / 2, cy + gap + len, outline);
        g.fill(cx - gap - len, cy - t / 2 - 1, cx - gap, cy - t / 2, color);
        g.fill(cx + gap, cy - t / 2 - 1, cx + gap + len, cy - t / 2, color);
        g.fill(cx - t / 2 - 1, cy - gap - len, cx - t / 2, cy - gap, color);
        g.fill(cx - t / 2 - 1, cy + gap, cx - t / 2, cy + gap + len, color);
        if (Boolean.TRUE.equals(get("dot"))) g.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
    }
}
