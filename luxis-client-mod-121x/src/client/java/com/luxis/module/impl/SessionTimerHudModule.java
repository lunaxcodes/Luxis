package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class SessionTimerHudModule extends HudModule {
    private final long start = System.currentTimeMillis();
    public SessionTimerHudModule() {
        super("sessiontimer", "Session Timer", "Elapsed session time with clean formatting.", Category.HUD, false, true);
        add(new Setting.ModeSetting("format", "Format", "HH:MM:SS", "HH:MM:SS", "MM:SS", "Seconds"));
        add(new Setting.BooleanSetting("hours", "Show hours", true));
        add(new Setting.SliderSetting("refresh", "Refresh", 1, 1, 10, 1, " s"));
        add(new Setting.BooleanSetting("label", "Show label", true));
        width = 160;
    }
    @Override public void renderHud(GuiGraphics g, float tickDelta) {
        long sec = (System.currentTimeMillis() - start) / 1000;
        String t = "Seconds".equals(get("format")) ? sec + "s" : String.format("%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60);
        drawBox(g, x, y, width, height, (Boolean.TRUE.equals(get("label")) ? "SESSION " : "") + t, 0xFFA78BFA);
    }
}
