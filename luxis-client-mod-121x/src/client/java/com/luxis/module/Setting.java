package com.luxis.module;

import java.util.List;

public abstract class Setting<T> {
    public final String key;
    public final String label;
    public final T defaultValue;
    public T value;

    protected Setting(String key, String label, T defaultValue) {
        this.key = key;
        this.label = label;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public abstract String type();

    public void reset() { value = defaultValue; }

    public static final class BooleanSetting extends Setting<Boolean> {
        public BooleanSetting(String key, String label, boolean def) { super(key, label, def); }
        @Override public String type() { return "toggle"; }
    }

    public static final class SliderSetting extends Setting<Double> {
        public final double min, max, step;
        public final String unit;
        public SliderSetting(String key, String label, double def, double min, double max, double step) { this(key, label, def, min, max, step, ""); }
        public SliderSetting(String key, String label, double def, double min, double max, double step, String unit) {
            super(key, label, def); this.min = min; this.max = max; this.step = step; this.unit = unit;
        }
        @Override public String type() { return "slider"; }
    }

    public static final class ModeSetting extends Setting<String> {
        public final List<String> options;
        public ModeSetting(String key, String label, String def, String... options) {
            super(key, label, def); this.options = List.of(options);
        }
        @Override public String type() { return "mode"; }
    }

    public static final class TextSetting extends Setting<String> {
        public TextSetting(String key, String label) { super(key, label, ""); }
        public void set(String text) {
            var clean = new StringBuilder();
            text.codePoints().filter(cp -> !Character.isISOControl(cp) && cp != 0xA7).limit(30).forEach(clean::appendCodePoint);
            value = clean.toString();
        }
        @Override public String type() { return "text"; }
    }

    public static final class ColorSetting extends Setting<Integer> {
        public boolean chroma;
        public ColorSetting(String key, String label, int argb) { super(key, label, argb); }
        @Override public String type() { return "color"; }
    }
}
