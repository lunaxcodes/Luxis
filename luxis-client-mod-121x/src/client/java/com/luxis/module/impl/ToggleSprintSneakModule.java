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

public final class ToggleSprintSneakModule extends HudModule {
    public ToggleSprintSneakModule() {
        super("sprint", "Toggle Sprint / Sneak", "HUD indicator for sprint and sneak state.", Category.HUD, true, false);
        add(new Setting.ModeSetting("indicator", "Indicator", "Both", "Icon", "Text", "Both"));
        add(new Setting.ModeSetting("sprintInput", "Sprint input", "Toggle", "Toggle", "Hold", "Double tap"));
        add(new Setting.ModeSetting("sneakInput", "Sneak input", "Hold", "Hold", "Toggle"));
        add(new Setting.BooleanSetting("customText", "Custom labels", false));
        add(new Setting.BooleanSetting("alert", "Change alert", false));
        width = 170;
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.toggleSprint().set("Toggle".equals(get("sprintInput")));
        mc.options.toggleCrouch().set("Toggle".equals(get("sneakInput")));
    }

    @Override
    public void renderHud(GuiGraphics g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String text = (mc.player.isSprinting() ? "[Sprinting" : "[Walking") + (mc.options.toggleSprint().get() ? " (Toggled)]" : ")]");
        if (mc.player.isShiftKeyDown()) text += " [Sneaking]";
        drawBox(g, x, y, width, height, text, mc.player.isSprinting() ? 0xFF22C55E : 0xFFA78BFA, this);
    }
}
