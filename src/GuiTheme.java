package com.luxis.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Launcher-matching palettes for the in-game menu. */
public final class GuiTheme {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Palette {
        public final String name;
        public final int accent, accent2, bg, panel, text, muted;
        Palette(String name, int accent, int accent2, int bg, int panel, int text, int muted) {
            this.name = name; this.accent = accent; this.accent2 = accent2;
            this.bg = bg; this.panel = panel; this.text = text; this.muted = muted;
        }
    }

    public static final Palette[] ALL = {
        new Palette("Orchid", 0xFFC864FF, 0xFF7B5CFF, 0xE60E0F14, 0x14FFFFFF, 0xFFFFFFFF, 0xFF9B93A8),
        new Palette("Nebula", 0xFF38BDF8, 0xFF6366F1, 0xE608101C, 0x12FFFFFF, 0xFFFFFFFF, 0xFF90A4B8),
        new Palette("Emerald", 0xFF22C55E, 0xFF0D9488, 0xE6081410, 0x12FFFFFF, 0xFFFFFFFF, 0xFF8FB8A3),
        new Palette("Crimson", 0xFFF43F5E, 0xFFBE123C, 0xE614080C, 0x14FFFFFF, 0xFFFFFFFF, 0xFFB78D98),
        new Palette("Amber", 0xFFFBBF24, 0xFFEA580C, 0xE6141008, 0x14FFFFFF, 0xFFFFFFFF, 0xFFB8A98D),
        new Palette("Monochrome", 0xFFE2E8F0, 0xFF94A3B8, 0xE60B0B10, 0x12FFFFFF, 0xFFFFFFFF, 0xFF94A3B8)
    };

    private static int index = 3;
    private static Path file;

    public static void init() {
        try {
            file = FabricLoader.getInstance().getGameDir().resolve("config").resolve("luxis").resolve("theme.json");
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                String value = Files.readString(file, StandardCharsets.UTF_8).trim();
                index = Math.floorMod(Integer.parseInt(value), ALL.length);
            }
        } catch (Exception ignored) { index = 3; }
    }

    public static Palette current() { return ALL[Math.floorMod(index, ALL.length)]; }

    public static void cycle() {
        index = (index + 1) % ALL.length;
        try { if (file != null) Files.writeString(file, String.valueOf(index), StandardCharsets.UTF_8); }
        catch (IOException ignored) { }
    }
}
