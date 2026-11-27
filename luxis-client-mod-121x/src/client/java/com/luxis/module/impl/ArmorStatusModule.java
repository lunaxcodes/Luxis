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
import net.minecraft.world.entity.EquipmentSlot;

public final class ArmorStatusModule extends HudModule {
    public ArmorStatusModule() {
        super("armor", "Armor Status", "Durability for equipped armor and held items.", Category.HUD, false, false);
        add(new Setting.ModeSetting("layout", "Layout", "Horizontal", "Horizontal", "Vertical"));
        add(new Setting.ModeSetting("value", "Value style", "Both", "Bar", "Percent", "Both"));
        add(new Setting.SliderSetting("warn", "Warning threshold", 20, 5, 50, 5, "%"));
        add(new Setting.BooleanSetting("offhand", "Off-hand slot", true));
        add(new Setting.BooleanSetting("icons", "Show icons", true));
        width = 190;
    }

    @Override
    public void renderHud(GuiGraphics g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var player = mc.player;
        var tracked = new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        int yOff = y;
        String[] names = {"HELM", "CHEST", "LEGS", "BOOTS", "MAIN", "OFF"};
        boolean vertical = "Vertical".equals(get("layout"));
        var summary = new StringBuilder();
        int lowest = 100;
        for (int i = 0; i < tracked.length; i++) {
            if (i == 5 && !Boolean.TRUE.equals(get("offhand"))) continue;
            var stack = player.getItemBySlot(tracked[i]);
            if (stack.isEmpty() || !stack.isDamageableItem()) continue;
            int pct = 100 * (stack.getMaxDamage() - stack.getDamageValue()) / Math.max(1, stack.getMaxDamage());
            String text = names[i] + " " + pct + "%";
            int color = pct < ((Number) get("warn")).intValue() ? 0xFFFF6B6B : 0xFF22C55E;
            if (vertical) { drawBox(g, x, yOff, Math.min(width, 120), 18, text, color); yOff += 20; }
            else {
                if (!summary.isEmpty()) summary.append("  ");
                summary.append(text);
            }
            lowest = Math.min(lowest, pct);
        }
        if (!vertical || yOff == y)
            drawBox(g, x, y, width, 20, summary.isEmpty() ? "Armor · no damageable gear" : summary.toString(),
                    lowest < ((Number) get("warn")).intValue() ? 0xFFFF6B6B : 0xFF22C55E);
    }
}
