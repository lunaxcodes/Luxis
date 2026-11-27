package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ViewBobbingModule extends Module {
    public ViewBobbingModule() {
        super("bobbing", "View Bobbing", "Customize or calm the first-person camera bob.", Category.OPTIMIZATION, false, false);
        add(new Setting.BooleanSetting("bob", "View bobbing", true));
        add(new Setting.SliderSetting("intensity", "Intensity", 50, 0, 100, 5, "%"));
        add(new Setting.SliderSetting("frequency", "Frequency", 50, 0, 100, 5, "%"));
        add(new Setting.BooleanSetting("sprint", "Sprint bob", true));
        add(new Setting.BooleanSetting("sea", "Sea bob", true));
    }
    @Override public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null) return;
        Minecraft.getInstance().options.bobView().set(Boolean.TRUE.equals(get("bob")));
    }
}
