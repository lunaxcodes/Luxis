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

public final class BlockOutlineModule extends Module {
    public BlockOutlineModule() {
        super("outline", "Block Outline", "Custom targeted-block outline.", Category.VISUAL, false, false);
        add(new Setting.SliderSetting("thickness", "Thickness", 2, 1, 6, 0.5));
        add(new Setting.ColorSetting("color", "Color", 0xFFFFFFFF));
        add(new Setting.SliderSetting("alpha", "Opacity", 100, 10, 100, 5, "%"));
        add(new Setting.BooleanSetting("chroma", "Chroma", false));
        add(new Setting.BooleanSetting("corners", "Corners only", false));
    }

    @Override
    public void onEnable() { Minecraft.getInstance().options.highContrastBlockOutline().set(true); }
    @Override
    public void onDisable() { Minecraft.getInstance().options.highContrastBlockOutline().set(false); }
}
