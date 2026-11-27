package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class VsyncToggleModule extends Module {
    public VsyncToggleModule() {
        super("vsync", "VSync Toggle", "Toggle vertical sync without leaving the game.", Category.OPTIMIZATION, false, true);
        add(new Setting.BooleanSetting("vsync", "Enable VSync", true));
        add(new Setting.SliderSetting("cap", "Frame cap", 120, 30, 240, 10, " fps"));
        add(new Setting.BooleanSetting("adaptive", "Adaptive cap", true));
        add(new Setting.ModeSetting("sync", "Sync mode", "Balanced", "Balanced", "Low latency", "Battery"));
        add(new Setting.BooleanSetting("restore", "Restore on exit", true));
    }
    @Override public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null) return;
        Minecraft.getInstance().options.enableVsync().set(Boolean.TRUE.equals(get("vsync")));
    }
}
