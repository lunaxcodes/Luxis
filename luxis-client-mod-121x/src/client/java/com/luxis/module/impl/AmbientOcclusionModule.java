package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class AmbientOcclusionModule extends Module {
    public AmbientOcclusionModule() {
        super("ao", "Ambient Occlusion", "Soft corner shading with a smooth low-cost toggle.", Category.OPTIMIZATION, false, false);
        add(new Setting.BooleanSetting("ao", "Ambient occlusion", true));
        add(new Setting.SliderSetting("strength", "Strength", 60, 0, 100, 5, "%"));
        add(new Setting.BooleanSetting("edges", "Block edges", true));
        add(new Setting.BooleanSetting("smooth", "Smooth lighting", true));
        add(new Setting.BooleanSetting("debug", "Debug overlay", false));
    }
    @Override public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null) return;
        Minecraft.getInstance().options.ambientOcclusion().set(Boolean.TRUE.equals(get("ao")));
    }
}
