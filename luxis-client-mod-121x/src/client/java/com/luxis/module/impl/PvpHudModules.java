package com.luxis.module.impl;

import com.luxis.gui.Draw;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/** Client-observed PvP readouts. No automated inputs, packets, or changes to combat. */
public final class PvpHudModules {
    private static final long REFRESH_NS = 85_000_000L;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] ARMOR_NAMES = {"Helmet", "Chestplate", "Leggings", "Boots"};

    private PvpHudModules() {}

    public static List<HudModule> create() {
        return List.of(
            pvp("attacktiming", "Attack Timing", "Your vanilla attack recharge, as a readout only.", "ATTACK  87%", 0,
                    (hud, p) -> "ATTACK  " + percent(p.getAttackStrengthScale(0F)) + "%"),
            pvp("shieldstatus", "Shield Status", "Your own shield: ready, raised, on cooldown, or not held.", "SHIELD  Ready", 1, PvpHudModules::shield),
            pvp("pearlstatus", "Pearl Status", "Your pearl cooldown and available pearl count.", "PEARLS  8 · Ready", 2,
                    (hud, p) -> "PEARLS  " + Stock.get(p, Items.ENDER_PEARL, hud.now) + " · " + cooldown(p, Items.ENDER_PEARL)),
            pvp("totemstock", "Totem Stock", "Counts your totems and shows whether one is in your offhand.", "TOTEMS  4 · Offhand", 3,
                    (hud, p) -> "TOTEMS  " + Stock.get(p, Items.TOTEM_OF_UNDYING, hud.now)
                            + (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING) ? " · Offhand" : "")),
            pvp("crystalstock", "Crystal Stock", "Counts your crystals and obsidian; no placement automation.", "CRYSTALS  32 · OBS  64", 4,
                    (hud, p) -> "CRYSTALS  " + Stock.get(p, Items.END_CRYSTAL, hud.now)
                            + " · OBS  " + Stock.get(p, Items.OBSIDIAN, hud.now)),
            pvp("anchorstock", "Anchor Supplies", "Respawn anchor and glowstone counts from your own inventory.", "ANCHORS  12 · GLOW  48", 5,
                    (hud, p) -> "ANCHORS  " + Stock.get(p, Items.RESPAWN_ANCHOR, hud.now)
                            + " · GLOW  " + Stock.get(p, Items.GLOWSTONE, hud.now)),
            pvp("healingstock", "Golden Apple Stock", "Counts regular and enchanted golden apples separately.", "GAPPLES  8 · ENCH  2", 6,
                    (hud, p) -> "GAPPLES  " + Stock.get(p, Items.GOLDEN_APPLE, hud.now)
                            + " · ENCH  " + Stock.get(p, Items.ENCHANTED_GOLDEN_APPLE, hud.now)),
            pvp("arrowstock", "Arrow Stock", "Counts normal, spectral, and tipped arrows in your inventory and offhand.", "ARROWS  42", 7,
                    (hud, p) -> "ARROWS  " + (Stock.get(p, Items.ARROW, hud.now) + Stock.get(p, Items.SPECTRAL_ARROW, hud.now)
                            + Stock.get(p, Items.TIPPED_ARROW, hud.now))),
            pvp("potionstock", "Potion Stock", "Counts drinkable, splash, and lingering potions separately.", "POTIONS  2 / 4 / 1", 8,
                    (hud, p) -> "POTIONS  " + Stock.get(p, Items.POTION, hud.now) + " drink · "
                            + Stock.get(p, Items.SPLASH_POTION, hud.now) + " splash · " + Stock.get(p, Items.LINGERING_POTION, hud.now) + " linger"),
            pvp("armorwarning", "Armor Warning", "Shows your most worn armor piece and flags low durability.", "ARMOR  Boots · 18% LOW", 9, PvpHudModules::armor)
                    .slider("warning", "Low durability threshold", 25, 5, 75, 5, "%"),
            pvp("helddurability", "Held Durability", "Shows remaining durability on your main-hand item.", "HELD  114 / 2031", 10,
                    (hud, p) -> {
                        var item = p.getMainHandItem();
                        return item.isDamageableItem() ? "HELD  " + (item.getMaxDamage() - item.getDamageValue()) + " / " + item.getMaxDamage() : "HELD  —";
                    }),
            pvp("combathealth", "Combat Health", "Your health and absorption in hearts.", "HEARTS  8.5 / 10.0 + 2.0", 11,
                    (hud, p) -> "HEARTS  " + decimal(p.getHealth() / 2F) + " / " + decimal(p.getMaxHealth() / 2F)
                            + (p.getAbsorptionAmount() > 0 ? " + " + decimal(p.getAbsorptionAmount() / 2F) : "")),
            pvp("targetdistance", "Target Distance", "Reads the distance to the entity directly under your crosshair; no tracking.", "TARGET  2.6 b", 12,
                    (hud, p) -> Minecraft.getInstance().hitResult instanceof EntityHitResult hit
                            ? "TARGET  " + decimal((float) p.getEyePosition().distanceTo(hit.getLocation())) + " b" : "TARGET  —"),
            pvp("pvpping", "PvP Ping", "Your server-reported latency. Sends no additional traffic.", "PING  27 ms", 13,
                    (hud, p) -> {
                        var connection = Minecraft.getInstance().getConnection();
                        var info = connection == null ? null : connection.getPlayerInfo(p.getUUID());
                        return info == null || Minecraft.getInstance().isLocalServer() ? "PING  —" : "PING  " + info.getLatency() + " ms";
                    }),
             new LastDamage(),
             pvp("bowdraw", "Bow Draw", "Shows the draw strength of the bow you're using.", "BOW  92%", 15,
                     (hud, p) -> p.isUsingItem() && p.getUseItem().is(Items.BOW)
                             ? "BOW  " + percent(net.minecraft.world.item.BowItem.getPowerForTime(p.getTicksUsingItem())) + "%" : "BOW  —"),
             pvp("xpstock", "XP Bottle Stock", "Counts experience bottles in your inventory and offhand.", "XP BOTTLES  32", 16,
                     (hud, p) -> "XP BOTTLES  " + Stock.get(p, Items.EXPERIENCE_BOTTLE, hud.now)),
             pvp("shieldwear", "Shield Durability", "Shows remaining durability on your equipped shield.", "SHIELD  206 / 336", 17,
                     (hud, p) -> {
                         var stack = p.getOffhandItem().is(Items.SHIELD) ? p.getOffhandItem() : p.getMainHandItem();
                         return stack.is(Items.SHIELD) && stack.isDamageableItem()
                                 ? "SHIELD  " + (stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage() : "SHIELD  —";
                     })
        );
    }

    private static Readout pvp(String id, String title, String desc, String preview, int slot,
                               BiFunction<Readout, LocalPlayer, String> sample) {
        return new Readout(id, title, desc, preview, slot) {
            @Override String sample(LocalPlayer player) { return sample.apply(this, player); }
        };
    }

    private abstract static class Readout extends HudModule {
        private final Component preview;
        private Component label = Draw.label("—", Draw.Face.BODY);
        private String lastValue;
        private LocalPlayer lastPlayer;
        long now;

        Readout(String id, String title, String desc, String example, int slot) {
            super(id, title, desc, Category.PVP, false, true);
             x = slot >= 15 ? 194 : slot < 8 ? 164 : 12;
             y = 24 + (slot >= 15 ? slot - 12 : slot % 8) * 21;
            width = 144;
            height = 18;
            preview = Draw.label(example, Draw.Face.BODY);
        }

        Readout slider(String id, String title, int current, int min, int max, int step, String suffix) {
            add(new Setting.SliderSetting(id, title, current, min, max, step, suffix));
            return this;
        }

        abstract String sample(LocalPlayer player);

        @Override public void onEnable() { lastPlayer = null; now = 0; }

        @Override
        public final void renderHud(GuiGraphics g, float tickDelta) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) return;
            long time = System.nanoTime();
            if (lastPlayer != p || time - now >= REFRESH_NS) {
                lastPlayer = p;
                now = time;
                String value = sample(p);
                if (!Objects.equals(value, lastValue)) {
                    lastValue = value;
                    label = Draw.label(value, Draw.Face.BODY);
                }
            }
            drawBox(g, x, y, width, height, label, 0xFFFFFFFF);
        }

        @Override
        public final void renderDummyHud(GuiGraphics g, float tickDelta) {
            drawBox(g, x, y, width, height, preview, 0xFFFFFFFF);
        }
    }

    private static String shield(Readout hud, LocalPlayer p) {
        ItemStack item = p.getMainHandItem().is(Items.SHIELD) ? p.getMainHandItem() : p.getOffhandItem();
        if (!item.is(Items.SHIELD)) return "SHIELD  Not held";
        if (p.isBlocking()) return "SHIELD  Raised";
        return "SHIELD  " + cooldown(p, item);
    }

    private static String armor(Readout hud, LocalPlayer p) {
        int worst = 101, slot = -1;
        for (int i = 0; i < ARMOR.length; i++) {
            var stack = p.getItemBySlot(ARMOR[i]);
            if (!stack.isDamageableItem()) continue;
            int remaining = percent(1F - (float) stack.getDamageValue() / stack.getMaxDamage());
            if (remaining < worst) { slot = i; worst = remaining; }
        }
        if (slot < 0) return "ARMOR  —";
        return "ARMOR  " + ARMOR_NAMES[slot] + " · " + worst + "%" + (worst <= ((Number) hud.get("warning")).intValue() ? " LOW" : "");
    }

    private static String cooldown(LocalPlayer p, Item item) { return cooldown(p, item.getDefaultInstance()); }
    private static String cooldown(LocalPlayer p, ItemStack item) {
        float remaining = p.getCooldowns().getCooldownPercent(item, 0F);
        return remaining > 0 ? percent(1F - remaining) + "% charged" : "Ready";
    }
    private static int percent(float fraction) { return Math.clamp(Math.round(fraction * 100F), 0, 100); }
    private static String decimal(float value) {
        int tenths = Math.max(0, Math.round(value * 10F));
        return (tenths / 10) + "." + (tenths % 10);
    }

    private static final class LastDamage extends Readout {
        private long lastDamage;
        private LocalPlayer player;
        private float hearts;
        LastDamage() {
            super("damagetimer", "Last Damage", "Seconds since your health or absorption last dropped.", "DAMAGE  2.3 s ago", 14);
        }
        @Override public void onEnable() { super.onEnable(); player = null; }
        @Override String sample(LocalPlayer current) {
            float value = current.getHealth() + current.getAbsorptionAmount();
            if (player != current) { player = current; lastDamage = 0; }
            else if (value < hearts) lastDamage = now;
            hearts = value;
            return lastDamage == 0 ? "DAMAGE  —" : "DAMAGE  " + decimal((now - lastDamage) / 1_000_000_000F) + " s ago";
        }
    }

    /** All stock readouts share one cached inventory pass; no external data or container scans. */
    private static final class Stock {
        private static final Item[] ITEMS = {Items.ENDER_PEARL, Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.OBSIDIAN,
                Items.RESPAWN_ANCHOR, Items.GLOWSTONE, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
                Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW, Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION,
                Items.EXPERIENCE_BOTTLE};
        private static final Map<Item, Integer> INDEX = new IdentityHashMap<>();
        private static final int[] COUNTS = new int[ITEMS.length];
        private static LocalPlayer lastPlayer;
        private static long last;
        static { for (int i = 0; i < ITEMS.length; i++) INDEX.put(ITEMS[i], i); }

        static int get(LocalPlayer player, Item item, long now) {
            if (lastPlayer != player || now - last >= REFRESH_NS) {
                lastPlayer = player;
                last = now;
                Arrays.fill(COUNTS, 0);
                var inventory = player.getInventory();
                for (int i = 0; i < Math.min(36, inventory.getContainerSize()); i++) add(inventory.getItem(i));
                add(player.getOffhandItem());
            }
            Integer index = INDEX.get(item);
            return index == null ? 0 : COUNTS[index];
        }
        private static void add(ItemStack stack) {
            if (stack.isEmpty()) return;
            Integer index = INDEX.get(stack.getItem());
            if (index != null) COUNTS[index] += stack.getCount();
        }
    }
}
