package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class SmoothCameraModule extends Module {
    public SmoothCameraModule() {
        super("smoothcamera", "Smooth Camera", "Vanilla smooth-camera toggle with a clean HUD state.", Category.OPTIMIZATION, false, false);
        add(new Setting.BooleanSetting("smooth", "Smooth camera", false));
        add(new Setting.SliderSetting("damping", "Damping", 60, 0, 100, 5, "%"));
        add(new Setting.SliderSetting("speed", "Speed", 50, 0, 100, 5, "%"));
        add(new Setting.BooleanSetting("indicator", "Show indicator", true));
        add(new Setting.BooleanSetting("restore", "Restore on exit", true));
    }
    @Override public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null) return;
        Minecraft.getInstance().options.smoothCamera = Boolean.TRUE.equals(get("smooth"));
    }
}
