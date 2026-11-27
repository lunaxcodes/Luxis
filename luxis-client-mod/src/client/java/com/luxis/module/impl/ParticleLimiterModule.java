package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.module.ModuleRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ParticleLimiterModule extends Module {
    public ParticleLimiterModule() {
        super("particles", "Particle Optimizer", "Caps excessive particle spawns for steadier frames.", Category.OPTIMIZATION, false, false);
        add(new Setting.SliderSetting("max", "Max per tick", 1200, 0, 4000, 100));
        add(new Setting.ModeSetting("mode", "Mode", "Cap", "Cap", "Filter", "Off"));
        add(new Setting.BooleanSetting("explosions", "Filter explosions", true));
        add(new Setting.BooleanSetting("crit", "Filter crits", false));
        add(new Setting.BooleanSetting("smoke", "Filter smoke", true));
    }

    public static int max() {
        ParticleLimiterModule m = (ParticleLimiterModule) ModuleRegistry.byId("particles");
        if (m == null || !m.enabled || "Off".equals(m.get("mode"))) return Integer.MAX_VALUE;
        return ((Number) m.get("max")).intValue();
    }
}
