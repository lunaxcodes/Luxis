package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class SaturationHudModule extends HudModule {
    public SaturationHudModule() {
        super("saturation", "Saturation HUD", "Compact saturation readout with a low-warning color.", Category.HUD, false, true);
        add(new Setting.ModeSetting("style", "Style", "Compact", "Compact", "Detailed", "Bar"));
        add(new Setting.SliderSetting("decimals", "Decimals", 1, 0, 2, 1));
        add(new Setting.SliderSetting("warn", "Warning threshold", 2, 0, 10, 1));
        add(new Setting.BooleanSetting("icon", "Show icon", true));
        width = 140;
    }
    @Override public void renderHud(GuiGraphicsExtractor g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float s = mc.player.getFoodData().getSaturationLevel();
        int color = s < ((Number) get("warn")).intValue() ? 0xFFFF6B6B : 0xFFFBBF24;
        drawBox(g, x, y, width, height, "SATURATION " + Math.round(s * 10) / 10.0, color);
    }
}
