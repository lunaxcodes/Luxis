package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class VignetteToggleModule extends Module {
    public VignetteToggleModule() {
        super("vignette", "Vignette Toggle", "Soft screen-edge vignette with adjustable strength.", Category.VISUAL, false, false);
        add(new Setting.BooleanSetting("vignette", "Vignette", true));
        add(new Setting.SliderSetting("strength", "Strength", 50, 0, 100, 5, "%"));
        add(new Setting.BooleanSetting("pulse", "Damage pulse", false));
        add(new Setting.SliderSetting("fade", "Fade", 8, 1, 20, 1));
        add(new Setting.BooleanSetting("restore", "Restore on exit", true));
    }
    @Override public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null) return;
        Minecraft.getInstance().options.vignette().set(Boolean.TRUE.equals(get("vignette")));
    }
}
