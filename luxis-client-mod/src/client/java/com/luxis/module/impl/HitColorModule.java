package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.module.ModuleRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class HitColorModule extends Module {
    public HitColorModule() {
        super("hitcolor", "Hit Color & Damage Flash", "Subtle visual damage feedback.", Category.VISUAL, false, false);
        add(new Setting.SliderSetting("intensity", "Intensity", 45, 0, 100, 5, "%"));
        add(new Setting.ColorSetting("color", "Tint color", 0xFFEF4444));
        add(new Setting.SliderSetting("duration", "Duration", 220, 50, 500, 10, "ms"));
        add(new Setting.ModeSetting("marker", "Hit marker", "Off", "Off", "Cross", "X"));
        add(new Setting.BooleanSetting("fade", "Fade out", true));
    }

    @Override
    public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null || Minecraft.getInstance().level == null) return;
        Minecraft.getInstance().options.damageTiltStrength().set(((Number) get("intensity")).doubleValue() / 100.0);
    }

    @Override
    public void onDisable() {
        Minecraft.getInstance().options.damageTiltStrength().set(1.0);
    }
}
