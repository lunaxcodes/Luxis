package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class EntityShadowsModule extends Module {
    public EntityShadowsModule() {
        super("entityshadows", "Entity Shadows", "Disable entity shadow rendering for crowded scenes.", Category.OPTIMIZATION, false, false);
        add(new Setting.BooleanSetting("shadows", "Entity shadows", true));
        add(new Setting.SliderSetting("distance", "Shadow distance", 32, 8, 96, 8, " blocks"));
        add(new Setting.BooleanSetting("self", "Self shadows", true));
        add(new Setting.ModeSetting("quality", "Quality", "Balanced", "Off", "Balanced", "High"));
        add(new Setting.BooleanSetting("debug", "Debug", false));
    }
    @Override public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null) return;
        Minecraft.getInstance().options.entityShadows().set(Boolean.TRUE.equals(get("shadows")));
    }
}
