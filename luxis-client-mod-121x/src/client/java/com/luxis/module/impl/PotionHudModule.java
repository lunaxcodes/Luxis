package com.luxis.module.impl;

import com.luxis.gui.Draw;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.module.ModuleRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class PotionHudModule extends HudModule {
    public PotionHudModule() {
        super("potions", "Potion & Effect HUD", "Active effects with readable timers.", Category.HUD, false, false);
        add(new Setting.SliderSetting("iconSize", "Icon size", 20, 12, 32, 2));
        add(new Setting.SliderSetting("blink", "Blink threshold", 5, 1, 10, 1));
        add(new Setting.ModeSetting("orient", "Orientation", "Vertical", "Vertical", "Horizontal"));
        add(new Setting.BooleanSetting("ambient", "Ambient effects", true));
        add(new Setting.ModeSetting("timeFormat", "Time format", "mm:ss", "mm:ss", "seconds"));
        width = 180;
    }

    @Override
    public void renderHud(GuiGraphics g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int yy = y;
        for (var effect : mc.player.getActiveEffects()) {
            if (!Boolean.TRUE.equals(get("ambient")) && effect.isAmbient()) continue;
            String name = effect.getEffect().value().getDisplayName().getString();
            int seconds = effect.getDuration() / 20;
            String time = "mm:ss".equals(get("timeFormat")) ? String.format("%02d:%02d", seconds / 60, seconds % 60) : seconds + "s";
            drawBox(g, x, yy, width, 18, name + " " + time, 0xFFA78BFA);
            yy += 20;
        }
        if (yy == y) drawBox(g, x, y, width, 18, "No active effects", 0xFFA78BFA);
    }
}
