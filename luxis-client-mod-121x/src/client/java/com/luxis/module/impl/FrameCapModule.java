package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class FrameCapModule extends Module {
    public FrameCapModule() {
        super("framecap", "Frame Rate Capper", "Smoothly cap foreground and background frame rates.", Category.OPTIMIZATION, true, true);
        add(new Setting.SliderSetting("cap", "Foreground cap", 120, 30, 240, 10, " fps"));
        add(new Setting.SliderSetting("unfocused", "Unfocused cap", 30, 1, 60, 1, " fps"));
        add(new Setting.BooleanSetting("smooth", "Smooth cap", true));
        add(new Setting.SliderSetting("spike", "Spike guard", 120, 20, 200, 10, " ms"));
        add(new Setting.BooleanSetting("restore", "Restore on exit", true));
    }
    @Override public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null) return;
        int cap = Minecraft.getInstance().isWindowActive() ? ((Number) get("cap")).intValue() : ((Number) get("unfocused")).intValue();
        Minecraft.getInstance().options.framerateLimit().set(cap);
    }
}
