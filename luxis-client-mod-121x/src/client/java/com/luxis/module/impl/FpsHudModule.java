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

public final class FpsHudModule extends HudModule {
    private int minFps = Integer.MAX_VALUE;
    private final java.util.ArrayDeque<Integer> samples = new java.util.ArrayDeque<>();

    public FpsHudModule() {
        super("fps", "FPS Counter", "Current, minimum and average frame rate.", Category.HUD, true, false);
        add(new Setting.ModeSetting("mode", "Display mode", "Detailed", "Compact", "Detailed", "Graph"));
        add(new Setting.ModeSetting("colors", "Color style", "Gradient", "Gradient", "Static", "Monochrome"));
        add(new Setting.SliderSetting("low", "Low FPS warning", 60, 20, 120, 5));
        add(new Setting.SliderSetting("avg", "Average window", 60, 5, 120, 5));
        add(new Setting.BooleanSetting("decimals", "Show decimals", false));
        width = 150;
    }

    @Override
    public void renderHud(GuiGraphics g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        int fps = mc.getFps();
        minFps = Math.min(minFps, fps);
        samples.addLast(fps);
        int window = get("avg") == null ? 60 : ((Number) get("avg")).intValue();
        while (samples.size() > window) samples.removeFirst();
        int avg = samples.stream().mapToInt(Integer::intValue).sum() / Math.max(1, samples.size());
        String mode = get("mode");
        String text = "FPS " + fps;
        if (!"Compact".equals(mode)) text += "  MIN " + minFps + "  AVG " + avg;
        int color = fps < ((Number) get("low")).intValue() ? 0xFFFF6B6B : 0xFF22C55E;
        drawBox(g, x, y, width, height, text, color, this);
    }
}
