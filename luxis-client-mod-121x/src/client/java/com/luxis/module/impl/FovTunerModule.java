package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class FovTunerModule extends Module {
    public FovTunerModule() {
        super("fov", "FOV Tuner", "Field-of-view tuning that respects zoom modules.", Category.VISUAL, false, false);
        add(new Setting.SliderSetting("fov", "Field of view", 70, 30, 110, 1, " deg"));
        add(new Setting.BooleanSetting("dynamic", "Dynamic FOV", false));
        add(new Setting.BooleanSetting("sprintFov", "Sprint FOV", true));
        add(new Setting.SliderSetting("smooth", "Transition", 8, 1, 20, 1));
        add(new Setting.BooleanSetting("restore", "Restore on exit", true));
    }
    @Override public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null || Minecraft.getInstance().level == null) return;
        if (!Boolean.TRUE.equals(get("dynamic"))) Minecraft.getInstance().options.fov().set(((Number) get("fov")).intValue());
    }
}
