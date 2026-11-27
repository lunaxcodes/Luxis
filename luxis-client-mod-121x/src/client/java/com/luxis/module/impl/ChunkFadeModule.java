package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class ChunkFadeModule extends Module {
    public ChunkFadeModule() {
        super("chunkfade", "Chunk Fade Tuner", "Smooth chunk loading with configurable fade timing.", Category.OPTIMIZATION, false, true);
        add(new Setting.SliderSetting("fade", "Fade time", 4, 0, 20, 1));
        add(new Setting.ModeSetting("priority", "Priority", "Balanced", "Smooth", "Balanced", "Performance"));
        add(new Setting.BooleanSetting("culling", "Cull hidden chunks", true));
        add(new Setting.SliderSetting("rebuild", "Rebuild delay", 2, 0, 20, 1));
        add(new Setting.BooleanSetting("debug", "Debug", false));
    }
    @Override public void onTick() {
        if (!enabled || Minecraft.getInstance().options == null || Minecraft.getInstance().level == null) return;
        Minecraft.getInstance().options.chunkSectionFadeInTime().set(((Number) get("fade")).doubleValue());
    }
}
