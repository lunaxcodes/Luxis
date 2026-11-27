package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ChatOpacityModule extends Module {
    public ChatOpacityModule() {
        super("chatopacity", "Chat Opacity", "Clean chat readability control for PvP servers.", Category.VISUAL, false, false);
        add(new Setting.SliderSetting("opacity", "Opacity", 100, 0, 100, 5, "%"));
        add(new Setting.SliderSetting("width", "Width", 100, 40, 160, 5, "%"));
        add(new Setting.SliderSetting("scale", "Scale", 100, 50, 150, 5, "%"));
        add(new Setting.BooleanSetting("background", "Background", true));
        add(new Setting.BooleanSetting("focus", "Focus only", false));
    }
    @Override public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null) return;
        Minecraft.getInstance().options.chatOpacity().set(((Number) get("opacity")).doubleValue() / 100.0);
    }
}
