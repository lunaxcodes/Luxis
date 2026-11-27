package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.module.ModuleRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class EntityRenderDistanceModule extends Module {
    public EntityRenderDistanceModule() {
        super("entitydist", "Entity Render Distance", "Distance cutoffs for passive entities and items.", Category.OPTIMIZATION, true, false);
        add(new Setting.SliderSetting("passive", "Passive distance", 64, 16, 128, 8));
        add(new Setting.SliderSetting("items", "Item distance", 32, 8, 96, 8));
        add(new Setting.SliderSetting("armorStands", "Armor stand distance", 48, 8, 128, 8));
        add(new Setting.BooleanSetting("players", "Always render players", true));
        add(new Setting.BooleanSetting("hostiles", "Always render hostiles", true));
    }

    public static boolean allow(net.minecraft.world.entity.Entity entity, double distance) {
        EntityRenderDistanceModule m = (EntityRenderDistanceModule) ModuleRegistry.byId("entitydist");
        if (m == null || !m.enabled) return true;
        if (Boolean.TRUE.equals(m.get("players")) && entity instanceof net.minecraft.world.entity.player.Player) return true;
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity) return distance <= ((Number) m.get("items")).doubleValue();
        if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand) return distance <= ((Number) m.get("armorStands")).doubleValue();
        if (entity instanceof net.minecraft.world.entity.animal.Animal) return distance <= ((Number) m.get("passive")).doubleValue();
        if (Boolean.TRUE.equals(m.get("hostiles")) && entity instanceof net.minecraft.world.entity.monster.Monster) return true;
        return true;
    }
}
