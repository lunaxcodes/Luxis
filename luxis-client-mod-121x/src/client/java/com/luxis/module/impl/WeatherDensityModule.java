package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class WeatherDensityModule extends Module {
    public WeatherDensityModule() {
        super("weatherdensity", "Weather Density", "Lighter rain and snow for clearer combat visibility.", Category.OPTIMIZATION, false, false);
        add(new Setting.SliderSetting("radius", "Weather radius", 8, 1, 32, 1, " blocks"));
        add(new Setting.BooleanSetting("rain", "Simplify rain", true));
        add(new Setting.BooleanSetting("snow", "Simplify snow", true));
        add(new Setting.BooleanSetting("thunder", "Reduce flashes", false));
        add(new Setting.SliderSetting("fade", "Fade", 4, 0, 20, 1));
    }
    @Override public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null || Minecraft.getInstance().level == null) return;
        Minecraft.getInstance().options.weatherRadius().set(((Number) get("radius")).intValue());
    }
}
