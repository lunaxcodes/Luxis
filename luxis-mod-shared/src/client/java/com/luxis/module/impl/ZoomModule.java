package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.Setting;
import com.mojang.blaze3d.platform.InputConstants;

public final class ZoomModule extends Module {
    public ZoomModule() {
        super("zoom", "Zoom", "Hold C by default. Set the keybind mode to Hold or Toggle.", Category.PVP, true, false);
        key = InputConstants.getKey("key.keyboard.c");
        trigger = Trigger.HOLD;
        add(new Setting.SliderSetting("level", "Zoom level", 3, 1.5, 8, 0.5, "x"));
        add(new Setting.SliderSetting("speed", "Transition speed", 8, 1, 20, 1));
        add(new Setting.BooleanSetting("scroll", "Scroll to zoom", true));
        add(new Setting.BooleanSetting("overlay", "Overlay indicator", true));
    }

    @Override public void onDisable() { ViewControls.reset(); }
    @Override public void resetDefaults() { super.resetDefaults(); trigger = Trigger.HOLD; }
}
