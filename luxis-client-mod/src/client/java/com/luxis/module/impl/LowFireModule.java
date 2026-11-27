package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.module.ModuleRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class LowFireModule extends Module {
    public LowFireModule() {
        super("lowfire", "Low Fire & Clear Water", "Keeps vision clear in combat and water.", Category.VISUAL, false, false);
        add(new Setting.SliderSetting("fire", "Fire height", 0.35, 0, 1, 0.05));
        add(new Setting.SliderSetting("clarity", "Water clarity", 0.6, 0, 1, 0.05));
        add(new Setting.BooleanSetting("lava", "Clear lava", true));
        add(new Setting.BooleanSetting("fog", "Reduce fog", true));
        add(new Setting.BooleanSetting("nightVision", "Night vision compatible", true));
    }

    @Override
    public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null || Minecraft.getInstance().level == null) return;
        Minecraft.getInstance().options.screenEffectScale().set(((Number) get("fire")).doubleValue());
    }

    @Override
    public void onDisable() {
        Minecraft.getInstance().options.screenEffectScale().set(1.0);
    }
}
