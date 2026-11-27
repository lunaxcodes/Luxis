package com.luxis.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.luxis.module.HudModule;
import com.luxis.module.Module;
import com.luxis.module.ModuleKeys;
import com.luxis.module.Setting;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ProfileManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path root, profilesDir, activeFile;
    private final Map<String, Profile> cache = new LinkedHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Luxis profile writer");
        thread.setDaemon(true);
        return thread;
    });
    private String activeName = "default";

    public ProfileManager() {
        root = FabricLoader.getInstance().getGameDir().resolve("config").resolve("luxis");
        profilesDir = root.resolve("profiles");
        activeFile = root.resolve("active.json");
        try {
            Files.createDirectories(profilesDir);
            if (Files.exists(activeFile)) {
                Active a = GSON.fromJson(Files.readString(activeFile, StandardCharsets.UTF_8), Active.class);
                if (a != null && a.name != null) activeName = a.name;
            }
        } catch (IOException | JsonParseException ignored) {}
    }

    private static final class Active {
        String name;
        Active() {}
        Active(String name) { this.name = name; }
    }

    public String activeName() { return activeName; }

    public List<String> names() {
        List<String> result = new ArrayList<>();
        try (var s = Files.list(profilesDir)) {
            s.filter(p -> p.toString().endsWith(".json"))
             .forEach(p -> { String n = p.getFileName().toString(); result.add(n.substring(0, n.length() - 5)); });
        } catch (IOException ignored) {}
        if (!result.contains("default")) result.add(0, "default");
        return result;
    }

    private static String safe(String n) { return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-"); }
    private Path file(String n) { return profilesDir.resolve(safe(n) + ".json"); }

    public Profile load(String name) {
        Profile p = cache.get(name);
        if (p != null) return p;
        try {
            Path f = file(name);
            if (Files.exists(f)) p = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Profile.class);
        } catch (IOException | JsonParseException ignored) {}
        if (p == null) p = new Profile(name);
        if (p.modules == null) p.modules = new LinkedHashMap<>();
        cache.put(name, p);
        return p;
    }

    public void setActive(String name) {
        activeName = name;
        try { Files.writeString(activeFile, GSON.toJson(new Active(activeName)), StandardCharsets.UTF_8); }
        catch (IOException ignored) {}
    }

    private Profile snapshot(String name, List<Module> modules) {
        Profile p = new Profile(name);
        for (Module m : modules) {
            Profile.ModuleState s = new Profile.ModuleState();
            s.enabled = ModuleKeys.persistentEnabled(m);
            s.key = m.key == null ? "" : m.key.getName();
            s.trigger = m.trigger.name();
            if (m instanceof HudModule h) {
                s.x = h.x; s.y = h.y; s.width = h.width; s.height = h.height;
                s.scale = h.scale; s.anchor = h.anchor.name();
            }
            for (Setting<?> setting : m.settings) s.values.put(setting.key, setting.value);
            p.modules.put(m.id, s);
        }
        cache.put(name, p);
        return p;
    }

    private void writeProfile(Path path, Profile snapshot) {
        try { Files.writeString(path, GSON.toJson(snapshot), StandardCharsets.UTF_8); }
        catch (IOException ignored) {}
    }

    /** Capture game-owned state on the client thread; serialize and write in order off-thread. */
    public void saveAsync(String name, List<Module> modules) {
        if (writer.isShutdown()) return;
        Profile snapshot = snapshot(name, modules);
        Path path = file(name);
        writer.execute(() -> writeProfile(path, snapshot));
    }

    public void finishWrites() {
        writer.shutdown();
        try { writer.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public void apply(String name, List<Module> modules) {
        Profile p = load(name);
        activeName = name;
        Profile.ModuleState oldWeather = p.modules.get("weather");
        boolean sharedWeatherState = oldWeather != null && oldWeather.values != null && oldWeather.values.containsKey("radius");
        for (Module m : modules) {
            Profile.ModuleState s = p.modules.get(m.id);
            // Earlier builds shared an ID between Fog Optimizer and Weather Density.
            if (sharedWeatherState && m.id.equals("weather")) s = null;
            if (sharedWeatherState && s == null && m.id.equals("weatherdensity")) s = oldWeather;
            if (s == null) continue;
            m.enabled = s.enabled;
            boolean bound = s.key != null && !s.key.isEmpty() && !s.key.endsWith("unknown");
            if (bound) {
                InputConstants.Key k = InputConstants.getKey(s.key);
                if (k != null) m.key = k;
            }
            if (bound || !m.id.equals("zoom"))
                try { m.trigger = Module.Trigger.valueOf(s.trigger); } catch (IllegalArgumentException | NullPointerException ignored) {}
            if (m instanceof HudModule h) {
                h.x = s.x; h.y = s.y; h.width = s.width; h.height = s.height; h.scale = s.scale;
                try { h.anchor = HudModule.Anchor.valueOf(s.anchor); } catch (Exception ignored) {}
            }
            if (s.values == null) continue;
            for (Setting<?> setting : m.settings) {
                Object v = s.values.get(setting.key);
                if (v == null) continue;
                if (setting instanceof Setting.BooleanSetting b && v instanceof Boolean bool) b.value = bool;
                else if (setting instanceof Setting.SliderSetting sl && v instanceof Number n && Double.isFinite(n.doubleValue())) sl.value = Math.clamp(n.doubleValue(), sl.min, sl.max);
                else if (setting instanceof Setting.ModeSetting md && v instanceof String str && md.options.contains(str)) md.value = str;
                else if (setting instanceof Setting.ColorSetting c && v instanceof Number n) c.value = n.intValue();
                else if (setting instanceof Setting.TextSetting t && v instanceof String str) t.set(str);
            }
        }
    }
}
