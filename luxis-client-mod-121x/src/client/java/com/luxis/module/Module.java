package com.luxis.module;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public enum Category { HUD, OPTIMIZATION, PVP, VISUAL }
    public enum Trigger { TOGGLE, HOLD }

    public final String id, name, description;
    public final Category category;
    public final boolean isNew;
    public boolean enabled;
    public InputConstants.Key key;
    public Trigger trigger = Trigger.TOGGLE;
    public final List<Setting<?>> settings = new ArrayList<>();

    protected Module(String id, String name, String description, Category category, boolean enabled, boolean isNew) {
        this.id = id; this.name = name; this.description = description;
        this.category = category; this.enabled = enabled; this.isNew = isNew;
        this.key = InputConstants.UNKNOWN;
    }

    protected <T extends Setting<?>> T add(T setting) { settings.add(setting); return setting; }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        for (Setting<?> s : settings) if (s.key.equals(key)) return (T) s.value;
        return null;
    }

    public Setting<?> setting(String key) {
        for (Setting<?> s : settings) if (s.key.equals(key)) return s;
        return null;
    }

    public void resetDefaults() { settings.forEach(Setting::reset); trigger = Trigger.TOGGLE; }
    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
    public boolean isHud() { return this instanceof HudModule; }
}
