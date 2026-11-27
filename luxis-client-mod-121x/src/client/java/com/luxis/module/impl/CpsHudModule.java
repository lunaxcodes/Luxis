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

public final class CpsHudModule extends HudModule {
    public static int leftCount, rightCount;
    private static long lastReset = System.currentTimeMillis();
    private static int leftTotal, rightTotal, leftPeak, rightPeak;

    public static void click(int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) leftTotal++;
        else if (button == InputConstants.MOUSE_BUTTON_RIGHT) rightTotal++;
    }

    public static void resetTick() {
        long now = System.currentTimeMillis();
        if (now - lastReset < 1000) return;
        leftPeak = Math.max(leftPeak, leftTotal);
        rightPeak = Math.max(rightPeak, rightTotal);
        leftCount = leftTotal; rightCount = rightTotal;
        leftTotal = 0; rightTotal = 0; lastReset = now;
    }

    public CpsHudModule() {
        super("cps", "CPS Counter", "Displays click rate. Never clicks for you.", Category.HUD, false, false);
        add(new Setting.ModeSetting("display", "Display", "Both", "Both", "Left only", "Right only"));
        add(new Setting.SliderSetting("smoothing", "Smoothing", 4, 1, 10, 1));
        add(new Setting.SliderSetting("history", "History", 20, 5, 60, 5));
        add(new Setting.BooleanSetting("peak", "Show peak", true));
        add(new Setting.BooleanSetting("decimals", "Show decimals", false));
        width = 140;
    }

    @Override
    public void renderHud(GuiGraphics g, float tickDelta) {
        resetTick();
        String display = get("display");
        String text = "L " + leftCount + "  R " + rightCount;
        if ("Left only".equals(display)) text = "L " + leftCount;
        if ("Right only".equals(display)) text = "R " + rightCount;
        if (Boolean.TRUE.equals(get("peak"))) text += "  PEAK " + Math.max(leftPeak, rightPeak);
        drawBox(g, x, y, width, height, text, 0xFFC864FF, this);
    }
}
