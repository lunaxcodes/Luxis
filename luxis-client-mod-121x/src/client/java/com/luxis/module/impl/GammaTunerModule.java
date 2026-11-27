package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class GammaTunerModule extends Module {
    public GammaTunerModule() {
        super("gamma", "Gamma Tuner", "Brightness control with a safe clamp and smooth ramp.", Category.OPTIMIZATION, false, false);
        add(new Setting.SliderSetting("gamma", "Brightness", 1.0, 0.2, 2.0, 0.05));
        add(new Setting.BooleanSetting("night", "Night boost", false));
        add(new Setting.SliderSetting("smooth", "Transition", 8, 1, 20, 1));
        add(new Setting.SliderSetting("min", "Minimum", 0.4, 0.1, 1.0, 0.05));
        add(new Setting.SliderSetting("max", "Maximum", 1.6, 1.0, 3.0, 0.1));
    }
    @Override public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null) return;
        double v = ((Number) get("gamma")).doubleValue();
        Minecraft.getInstance().options.gamma().set(Math.max(((Number) get("min")).doubleValue(), Math.min(((Number) get("max")).doubleValue(), v)));
    }
}
