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

public final class CoordinatesPingModule extends HudModule {
    public CoordinatesPingModule() {
        super("coords", "Ping & Coordinates", "Position, facing direction and latency.", Category.HUD, false, false);
        add(new Setting.ModeSetting("order", "Order", "XYZ", "XYZ", "XZY", "YXZ"));
        add(new Setting.SliderSetting("decimals", "Decimals", 1, 0, 3, 1));
        add(new Setting.ModeSetting("facing", "Facing", "Compass", "Degrees", "Compass", "Both"));
        add(new Setting.BooleanSetting("ping", "Show ping", true));
        add(new Setting.BooleanSetting("compact", "Compact", false));
        width = 200;
    }

    @Override
    public void renderHud(GuiGraphics g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int d = ((Number) get("decimals")).intValue();
        String fmt = "%." + d + "f";
        String coords = String.format(fmt + " / " + fmt + " / " + fmt, mc.player.getX(), mc.player.getY(), mc.player.getZ());
        float yaw = Math.floorMod((long) mc.player.getYRot(), 360);
        String dir = yaw < 45 || yaw >= 315 ? "S" : yaw < 135 ? "W" : yaw < 225 ? "N" : "E";
        String text = "XYZ " + coords + "  " + dir;
        if (Boolean.TRUE.equals(get("ping"))) {
            long ping = mc.getCurrentServer() == null ? -1 : mc.getCurrentServer().ping;
            text += "  " + (ping < 0 ? "--" : ping) + "ms";
        }
        drawBox(g, x, y, width, height, text, 0xFF67E8F9, this);
    }
}
