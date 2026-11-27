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

public final class FogAndWeatherOptimizerModule extends Module {
    public FogAndWeatherOptimizerModule() {
        super("weather", "Fog & Weather Optimizer", "Clearer visibility and lighter weather effects.", Category.OPTIMIZATION, false, false);
        add(new Setting.BooleanSetting("fog", "Tune fog", true));
        add(new Setting.SliderSetting("weather", "Weather density", 8, 1, 32, 1));
        add(new Setting.SliderSetting("fade", "Chunk fade", 4, 0, 20, 1));
        add(new Setting.BooleanSetting("clearWater", "Clear water", true));
        add(new Setting.BooleanSetting("rain", "Simplify rain", true));
    }

    @Override
    public void onEnable() { apply(); }
    @Override
    public void onTick() { if (enabled) apply(); }

    private void apply() {
        var o = Minecraft.getInstance().options;
        o.weatherRadius().set(((Number) get("weather")).intValue());
        o.chunkSectionFadeInTime().set(((Number) get("fade")).doubleValue());
    }
}
