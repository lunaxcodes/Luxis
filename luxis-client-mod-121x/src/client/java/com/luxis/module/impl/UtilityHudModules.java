package com.luxis.module.impl;

import com.luxis.gui.Draw;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Read-only overlays. Disabled by default, and no frame work is performed unless enabled. */
public final class UtilityHudModules {
    private UtilityHudModules() {}

    public static List<HudModule> create() {
        return List.of(new FrameTime(), new Memory(), new Clock(), new Compass(),
                new BlockInspector(), new ChunkPosition(), new MovementSpeed(), new Experience(),
                new WorldClock(), new BiomeName(), new InventorySpace());
    }

    private abstract static class Readout extends HudModule {
        private final Component preview;
        private Component line;

        Readout(String id, String name, String description, Category category, int row, int width, String sample) {
            super(id, name, description, category, false, true);
            x = 16;
            y = 26 + 22 * row;
            this.width = width;
            height = 18;
            preview = line = Draw.label(sample, Draw.Face.BODY);
        }

        void line(String text) { line = Draw.label(text, Draw.Face.BODY); }

        abstract void sample(long now);

        @Override
        public final void renderHud(GuiGraphics g, float tickDelta) {
            sample(System.nanoTime());
            drawBox(g, x, y, width, height, line, 0xFFFFFFFF);
        }

        @Override
        public final void renderDummyHud(GuiGraphics g, float tickDelta) {
            drawBox(g, x, y, width, height, preview, 0xFFFFFFFF);
        }
    }

    private abstract static class SlowReadout extends Readout {
        private long next;
        private String previous;
        SlowReadout(String id, String name, String desc, int row, String sample) {
            super(id, name, desc, Category.HUD, row, 165, sample);
        }
        abstract String read(Minecraft mc);
        @Override public void onEnable() { next = 0; }
        @Override void sample(long now) {
            if (next != 0 && now - next < 0) return;
            next = now + 250_000_000L;
            String text = read(Minecraft.getInstance());
            if (!text.equals(previous)) { previous = text; line(text); }
        }
    }

    private static final class WorldClock extends SlowReadout {
        WorldClock() { super("worldclock", "World Clock", "Shows Minecraft time in the Overworld.", 0, "WORLD  17:42"); }
        @Override String read(Minecraft mc) {
            if (mc.level == null || mc.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return "WORLD  —";
            int minutes = (int) (Math.floorMod(mc.level.getDayTime() + 6000L, 24000) * 1440L / 24000L);
            return String.format(Locale.ROOT, "WORLD  %02d:%02d", minutes / 60, minutes % 60);
        }
    }

    private static final class BiomeName extends SlowReadout {
        BiomeName() { super("biomename", "Current Biome", "Shows the biome at your position.", 1, "BIOME  Cherry Grove"); }
        @Override String read(Minecraft mc) {
            if (mc.level == null || mc.player == null) return "BIOME  —";
            return mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(key -> {
                var id = key.identifier();
                return "BIOME  " + Component.translatableWithFallback(
                        "biome." + id.getNamespace() + "." + id.getPath().replace('/', '.'),
                        id.getPath().replace('_', ' ')).getString();
            }).orElse("BIOME  —");
        }
    }

    private static final class InventorySpace extends SlowReadout {
        InventorySpace() { super("inventoryspace", "Inventory Space", "Counts empty inventory and hotbar slots.", 2, "SPACE  12 / 36 free"); }
        @Override String read(Minecraft mc) {
            if (mc.player == null) return "SPACE  —";
            var items = mc.player.getInventory().getNonEquipmentItems();
            int free = 0;
            for (var stack : items) if (stack.isEmpty()) free++;
            return "SPACE  " + free + " / " + items.size() + " free";
        }
    }

    private static final class FrameTime extends Readout {
        private long last, since, sum, peak;
        private int count;

        FrameTime() {
            super("frametime", "Frame Time", "Average and slowest frame time in a recent window.", Category.HUD,
                    0, 172, "FRAME  16.7 ms · peak 21.4 ms");
            add(new Setting.BooleanSetting("peak", "Show peak", true));
        }

        @Override public void onEnable() { last = 0; line("FRAME  Measuring…"); }

        @Override
        void sample(long now) {
            if (!Minecraft.getInstance().isWindowActive()) { last = 0; return; }
            long elapsed = now - last;
            if (last == 0 || elapsed > 2_000_000_000L) {
                since = now;
                sum = peak = count = 0;
                last = now;
                return;
            }
            last = now;
            count++;
            sum += elapsed;
            peak = Math.max(peak, elapsed);
            if (now - since >= 1_000_000_000L) {
                String text = "FRAME  " + millis(sum / count) + " ms";
                if (Boolean.TRUE.equals(get("peak"))) text += " · peak " + millis(peak) + " ms";
                line(text);
                since = now;
                sum = peak = count = 0;
            }
        }

        private static String millis(long nanos) {
            long tenths = (nanos + 50_000) / 100_000;
            return tenths / 10 + "." + tenths % 10;
        }
    }

    private static final class Memory extends Readout {
        private long refreshedAt;

        Memory() {
            super("heap", "Memory Monitor", "Shows Java heap use, not total PC memory.", Category.HUD,
                    1, 151, "HEAP  1620 / 4096 MiB");
            add(new Setting.ModeSetting("display", "Display", "MiB", "MiB", "Percent"));
        }

        @Override
        void sample(long now) {
            if (refreshedAt != 0 && now - refreshedAt < 1_000_000_000L) return;
            refreshedAt = now;
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory(), max = runtime.maxMemory();
            if ("Percent".equals(get("display"))) line("HEAP  " + Math.round(100.0 * used / Math.max(1, max)) + "%");
            else line("HEAP  " + (used >> 20) + " / " + (max >> 20) + " MiB");
        }
    }

    private static final class Clock extends Readout {
        private static final DateTimeFormatter TWELVE = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);
        private static final DateTimeFormatter TWENTY_FOUR = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
        private long refreshedAt;

        Clock() {
            super("localclock", "Local Clock", "Shows your computer’s local time without leaving the game.", Category.HUD,
                    2, 106, "LOCAL  21:30");
            add(new Setting.ModeSetting("format", "Clock format", "24-hour", "24-hour", "12-hour"));
        }

        @Override
        void sample(long now) {
            if (refreshedAt != 0 && now - refreshedAt < 1_000_000_000L) return;
            refreshedAt = now;
            line("LOCAL  " + LocalTime.now().format("12-hour".equals(get("format")) ? TWELVE : TWENTY_FOUR));
        }
    }

    private static final class Compass extends Readout {
        private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        private int lastValue = -1;
        private boolean lastDegrees;

        Compass() {
            super("compass", "Compass", "Displays your camera heading. No radar or tracking.", Category.HUD,
                    3, 106, "HEADING  NW · 315°");
            add(new Setting.BooleanSetting("degrees", "Show degrees", true));
        }

        @Override
        void sample(long now) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            int heading = Math.floorMod(Math.round(player.getYRot() + 180F), 360);
            int sector = ((heading + 22) / 45) % 8;
            boolean degrees = Boolean.TRUE.equals(get("degrees"));
            int value = degrees ? heading : sector;
            if (value == lastValue && lastDegrees == degrees) return;
            lastValue = value;
            lastDegrees = degrees;
            line("HEADING  " + DIRECTIONS[sector] + (degrees ? " · " + heading + "°" : ""));
        }
    }

    private static final class BlockInspector extends Readout {
        private Object lastState;
        private BlockPos lastPos;
        private boolean lastCoordinates;
        private long lastSample;

        BlockInspector() {
            super("blockinspector", "Block Inspector", "Names the block your crosshair is actually pointing at.",
                    Category.HUD, 4, 190, "BLOCK  Stone · 32, 64, 109");
            add(new Setting.BooleanSetting("coordinates", "Show block coordinates", false));
        }

        @Override
        void sample(long now) {
            if (lastSample != 0 && now - lastSample < 100_000_000L) return;
            lastSample = now;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                if (lastState != null || lastPos == null) line("BLOCK  —");
                lastState = null;
                lastPos = BlockPos.ZERO;
                return;
            }
            BlockPos pos = hit.getBlockPos();
            var block = mc.level.getBlockState(pos).getBlock();
            boolean coords = Boolean.TRUE.equals(get("coordinates"));
            if (block == lastState && coords == lastCoordinates && (!coords || pos.equals(lastPos))) return;
            lastState = block;
            lastPos = pos;
            lastCoordinates = coords;
            String label = "BLOCK  " + block.getName().getString();
            if (coords) label += " · " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            line(label);
        }
    }

    private static final class ChunkPosition extends Readout {
        private BlockPos last;
        private boolean lastLocal;

        ChunkPosition() {
            super("chunkposition", "Chunk Position", "Shows chunk and within-chunk coordinates for builds.",
                    Category.HUD, 5, 172, "CHUNK  -2, 7 · local 10, 4");
            add(new Setting.BooleanSetting("local", "Show position within chunk", true));
        }

        @Override
        void sample(long now) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            BlockPos pos = player.blockPosition();
            boolean local = Boolean.TRUE.equals(get("local"));
            if (last != null && local == lastLocal && pos.getX() == last.getX() && pos.getZ() == last.getZ()) return;
            last = pos;
            lastLocal = local;
            int bx = pos.getX(), bz = pos.getZ();
            String label = "CHUNK  " + (bx >> 4) + ", " + (bz >> 4);
            if (local) label += " · local " + (bx & 15) + ", " + (bz & 15);
            line(label);
        }
    }

    private static final class MovementSpeed extends Readout {
        private long previous;
        private double px, py, pz;
        private int lastTenths = -1;

        MovementSpeed() {
            super("movespeed", "Movement Speed", "Measures your motion in blocks per second.", Category.HUD,
                    6, 115, "SPEED  5.6 blocks/s");
            add(new Setting.BooleanSetting("vertical", "Include vertical movement", false));
        }

        @Override public void onEnable() { previous = 0; lastTenths = -1; }

        @Override
        void sample(long now) {
            long elapsed = now - previous;
            if (previous != 0 && elapsed < 250_000_000L) return;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            double x = player.getX(), y = player.getY(), z = player.getZ(), dy = y - py;
            double distance = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz)
                    + (Boolean.TRUE.equals(get("vertical")) ? dy * dy : 0));
            int tenths = previous == 0 || elapsed > 1_000_000_000L || distance > 64
                    ? 0 : (int) Math.round(distance * 10_000_000_000D / elapsed);
            previous = now;
            px = x; py = y; pz = z;
            if (lastTenths == tenths) return;
            lastTenths = tenths;
            line("SPEED  " + tenths / 10 + "." + tenths % 10 + " blocks/s");
        }
    }

    private static final class Experience extends Readout {
        private int lastLevel = -1, lastPercent = -1;

        Experience() {
            super("xpreadout", "XP Progress", "Shows your level and progress to the next one.", Category.HUD,
                    7, 106, "XP  Lv 32 · 48%");
            add(new Setting.BooleanSetting("percent", "Show progress percentage", true));
        }

        @Override
        void sample(long now) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            int level = player.experienceLevel;
            int percent = Boolean.TRUE.equals(get("percent")) ? Math.round(player.experienceProgress * 100F) : -1;
            if (level == lastLevel && percent == lastPercent) return;
            lastLevel = level;
            lastPercent = percent;
            line("XP  Lv " + level + (percent >= 0 ? " · " + percent + "%" : ""));
        }
    }
}
