package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class RenderDistanceModule extends Module {
    public RenderDistanceModule() {
        super("renderdist", "Render Distance Tuner", "Fine-grained render and simulation distance presets.", Category.OPTIMIZATION, false, true);
        add(new Setting.SliderSetting("distance", "Render distance", 12, 2, 32, 1, " chunks"));
        add(new Setting.SliderSetting("simulation", "Simulation distance", 8, 2, 32, 1, " chunks"));
        add(new Setting.BooleanSetting("auto", "Auto performance", false));
        add(new Setting.SliderSetting("min", "Minimum", 6, 2, 16, 1));
        add(new Setting.SliderSetting("max", "Maximum", 24, 8, 32, 1));
    }
    @Override public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null || Minecraft.getInstance().level == null) return;
        Minecraft.getInstance().options.renderDistance().set(((Number) get("distance")).intValue());
        Minecraft.getInstance().options.simulationDistance().set(((Number) get("simulation")).intValue());
    }
}
