package com.luxis.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.luxis.gui.GuiTheme;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Menu-only preferences; never edits the user's module profiles or Minecraft video settings. */
public final class GuiConfig {
    public enum Density { Auto, Compact, Comfortable }
    public enum Width {
        Narrow(460), Balanced(730), Wide(1000);
        public final int limit;
        Width(int limit) { this.limit = limit; }
    }
    public enum Corners {
        Soft(0.65F), Rounded(1F), Extra(1.4F);
        public final float scale;
        Corners(float scale) { this.scale = scale; }
    }
    public enum Motion { Full, Reduced, Off }
    public enum ColorDepth { Subtle, Balanced, Rich }
    public enum Transition { Glide, Zoom, Fade }
    public enum Particles { Off, Drift, Stars, Comets }
    public enum Glass {
        Clear(203), Frosted(235), Solid(255);
        public final int alpha;
        Glass(int alpha) { this.alpha = alpha; }
    }
    public enum Backdrop {
        Light(72), Medium(136), Dark(192);
        public final int alpha;
        Backdrop(int alpha) { this.alpha = alpha; }
    }
    public enum Sort {
        Category("By category"), Name("A–Z"), Enabled("Enabled first");
        public final String label;
        Sort(String label) { this.label = label; }
    }

    public static final class Appearance {
        public String theme = "Crimson";
        public ColorDepth colorDepth = ColorDepth.Rich;
        public Density density = Density.Auto;
        private Width width; // Old presets used presets with this name. Normalize once on load.
        public int windowWidth = 78, windowHeight = 80;
        public Corners corners = Corners.Rounded;
        public Motion motion = Motion.Full;
        public Transition transition = Transition.Glide;
        public int animationSpeed = 100;
        public Particles particles = Particles.Drift;
        public int particleCount = 20, particleSpeed = 45, particleOpacity = 45;
        public Glass glass = Glass.Frosted;
        public Backdrop backdrop = Backdrop.Medium;
        public boolean descriptions = true;
        public boolean hudSnap = true, hudGuides = false;

        void normalize() {
            theme = GuiTheme.named(theme).name;
            if (colorDepth == null) colorDepth = ColorDepth.Rich;
            if (density == null) density = Density.Auto;
            if (width != null) {
                windowWidth = switch (width) { case Narrow -> 65; case Balanced -> 75; case Wide -> 82; };
                width = null;
            }
            windowWidth = Math.clamp(windowWidth, 55, 92);
            windowHeight = Math.clamp(windowHeight, 62, 92);
            if (corners == null) corners = Corners.Rounded;
            if (motion == null) motion = Motion.Full;
            if (transition == null) transition = Transition.Glide;
            animationSpeed = Math.clamp(animationSpeed, 50, 150);
            if (particles == null) particles = Particles.Drift;
            particleCount = Math.clamp(particleCount, 0, 52);
            particleSpeed = Math.clamp(particleSpeed, 0, 100);
            particleOpacity = Math.clamp(particleOpacity, 15, 85);
            if (glass == null) glass = Glass.Frosted;
            if (backdrop == null) backdrop = Backdrop.Medium;
        }
    }

    private static final class Library {
        Sort sort = Sort.Category;
        Set<String> favorites = new LinkedHashSet<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Luxis GUI config writer");
        thread.setDaemon(true);
        return thread;
    });
    private static Appearance appearance = new Appearance();
    private static final Appearance[] PRESETS = new Appearance[3];
    private static Library library = new Library();
    private static Path directory;

    private GuiConfig() {}

    public static void init(Path luxisDirectory) {
        directory = luxisDirectory.resolve("gui");
        Path look = directory.resolve("appearance.json"), browser = directory.resolve("browser.json");
        Appearance fromDisk = read(look, Appearance.class);
        if (fromDisk != null) appearance = fromDisk;
        else if (Files.notExists(look)) {
            // Original releases saved a numeric palette index. Leave their file intact.
            Integer legacy = read(luxisDirectory.resolve("theme.json"), Integer.class);
            if (legacy != null) appearance.theme = GuiTheme.legacyName(legacy);
            saveAppearance();
        }
        appearance.normalize();
        for (int i = 0; i < PRESETS.length; i++) {
            PRESETS[i] = read(presetPath(i), Appearance.class);
            if (PRESETS[i] != null) PRESETS[i].normalize();
        }
        Library preferences = read(browser, Library.class);
        if (preferences != null) {
            library = preferences;
            if (library.sort == null) library.sort = Sort.Category;
            if (library.favorites == null) library.favorites = new LinkedHashSet<>();
            library.favorites.remove(null);
        } else if (Files.notExists(browser)) saveBrowser();
    }

    static <T> T read(Path path, Class<T> type) {
        try { return GSON.fromJson(Files.readString(path), type); }
        catch (IOException | JsonParseException ignored) { return null; }
    }

    public static Appearance appearance() { return appearance; }
    public static Sort sort() { return library.sort; }
    public static boolean favorite(String id) { return library.favorites.contains(id); }
    public static int favoriteCount() { return library.favorites.size(); }

    public static void toggleFavorite(String id) {
        if (!library.favorites.remove(id)) library.favorites.add(id);
        saveBrowser();
    }

    public static void cycleSort() {
        library.sort = next(library.sort);
        saveBrowser();
    }

    public static void setTheme(String theme) {
        appearance.theme = GuiTheme.named(theme).name;
        saveAppearance();
    }

    public static void change(String key) {
        switch (key) {
            case "color_depth" -> appearance.colorDepth = next(appearance.colorDepth);
            case "density" -> appearance.density = next(appearance.density);
            case "corners" -> appearance.corners = next(appearance.corners);
            case "motion" -> appearance.motion = next(appearance.motion);
            case "transition" -> appearance.transition = next(appearance.transition);
            case "particles" -> appearance.particles = next(appearance.particles);
            case "glass" -> appearance.glass = next(appearance.glass);
            case "backdrop" -> appearance.backdrop = next(appearance.backdrop);
            case "descriptions" -> appearance.descriptions = !appearance.descriptions;
            case "hud_snap" -> appearance.hudSnap = !appearance.hudSnap;
            case "hud_guides" -> appearance.hudGuides = !appearance.hudGuides;
            default -> { return; }
        }
        saveAppearance();
    }

    public static void adjust(String key, int direction) {
        int sign = Integer.signum(direction);
        switch (key) {
            case "window_width" -> appearance.windowWidth = Math.clamp(appearance.windowWidth + sign * 4, 55, 92);
            case "window_height" -> appearance.windowHeight = Math.clamp(appearance.windowHeight + sign * 4, 62, 92);
            case "animation_speed" -> appearance.animationSpeed = Math.clamp(appearance.animationSpeed + sign * 10, 50, 150);
            case "particle_count" -> appearance.particleCount = Math.clamp(appearance.particleCount + sign * 4, 0, 52);
            case "particle_speed" -> appearance.particleSpeed = Math.clamp(appearance.particleSpeed + sign * 10, 0, 100);
            case "particle_opacity" -> appearance.particleOpacity = Math.clamp(appearance.particleOpacity + sign * 10, 15, 85);
            default -> { return; }
        }
        saveAppearance();
    }

    private static <E extends Enum<E>> E next(E current) {
        E[] values = current.getDeclaringClass().getEnumConstants();
        return values[(current.ordinal() + 1) % values.length];
    }

    public static void resetAppearance() {
        appearance = new Appearance();
        saveAppearance();
    }

    public static void resetSort() {
        library.sort = Sort.Category;
        saveBrowser();
    }

    public static String presetTheme(int slot) {
        Appearance preset = PRESETS[slot];
        return preset == null ? null : preset.theme;
    }

    public static void savePreset(int slot) {
        PRESETS[slot] = copy(appearance);
        write(presetPath(slot), PRESETS[slot]);
    }

    public static boolean loadPreset(int slot) {
        if (PRESETS[slot] == null) return false;
        appearance = copy(PRESETS[slot]);
        saveAppearance();
        return true;
    }

    private static Appearance copy(Appearance source) {
        return GSON.fromJson(GSON.toJsonTree(source), Appearance.class);
    }

    private static Path presetPath(int slot) { return directory.resolve("presets").resolve("layout-" + (slot + 1) + ".json"); }

    private static void saveAppearance() { save("appearance.json", appearance); }
    private static void saveBrowser() { save("browser.json", library); }

    private static void save(String name, Object settings) {
        if (directory != null) write(directory.resolve(name), settings);
    }

    static void write(Path path, Object settings) {
        if (WRITER.isShutdown()) return;
        String snapshot = GSON.toJson(settings);
        WRITER.execute(() -> {
            Path pending = path.resolveSibling(path.getFileName() + ".tmp");
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(pending, snapshot);
                try { Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) {
                    Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) {
                try { Files.deleteIfExists(pending); } catch (IOException ignoredAgain) { }
            }
        });
    }

    public static void finishWrites() {
        WRITER.shutdown();
        try { WRITER.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
