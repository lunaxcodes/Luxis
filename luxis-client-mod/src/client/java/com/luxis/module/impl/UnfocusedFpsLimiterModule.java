package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.module.ModuleRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class UnfocusedFpsLimiterModule extends Module {
    private int originalLimit = -1;

    public UnfocusedFpsLimiterModule() {
        super("unfocused", "Dynamic FPS / Limiter", "Lowers frame rate when the window is unfocused.", Category.OPTIMIZATION, true, true);
        add(new Setting.SliderSetting("unfocused", "Unfocused FPS", 30, 1, 60, 1));
        add(new Setting.SliderSetting("min", "Minimum FPS", 60, 10, 120, 5));
        add(new Setting.BooleanSetting("smoothing", "Frame smoothing", true));
        add(new Setting.SliderSetting("spike", "Spike threshold", 120, 20, 200, 10));
        add(new Setting.BooleanSetting("battery", "Battery saver", false));
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.options == null || mc.level == null || mc.player == null) return;
        int wanted = ((Number) get("unfocused")).intValue();
        if (!mc.isWindowActive()) {
            if (originalLimit < 0) originalLimit = mc.options.framerateLimit().get();
            mc.options.framerateLimit().set(Math.min(originalLimit, wanted));
        } else if (originalLimit >= 0) {
            mc.options.framerateLimit().set(originalLimit);
            originalLimit = -1;
        }
    }

    @Override
    public void onDisable() {
        if (originalLimit >= 0) Minecraft.getInstance().options.framerateLimit().set(originalLimit);
        originalLimit = -1;
    }
}
