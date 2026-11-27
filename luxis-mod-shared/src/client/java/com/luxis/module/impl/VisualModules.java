package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.module.Setting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.util.List;

/** Opt-in visual tools; the world effects share one pass and leave Minecraft's UI ungraded. */
public final class VisualModules {
    public static final Identifier EFFECT = Identifier.fromNamespaceAndPath("luxis", "visual_lens");
    public static final String UNIFORM = "LuxisLens";
    public static final int UNIFORM_BYTES = 28 * Float.BYTES;
    private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");

    public static final Visual GRADE = visual("colorgrading", "Color Grading",
            "Grade the world colors without tinting chat or menus. Skipped with Iris.")
            .mode("preset", "Base look", "Neutral", "Neutral", "Cinema", "Vintage", "Black & white", "Cool")
            .slider("strength", "Blend", 100, 0, 100, 1, "%")
            .slider("brightness", "Brightness", 0, -40, 40, 1, "%")
            .slider("saturation", "Saturation", 100, 0, 200, 1, "%")
            .slider("contrast", "Contrast", 100, 40, 160, 1, "%")
            .slider("exposure", "Exposure (EV)", 0, -2, 2, 0.05, "")
            .slider("gamma", "Midtones (gamma)", 1, 0.5, 2, 0.025, "")
            .slider("temperature", "Warmth", 0, -100, 100, 1, "%")
            .slider("hue", "Hue shift", 0, -180, 180, 1, "°")
            .slider("tint", "Green / magenta", 0, -100, 100, 1, "%")
            .slider("fade", "Lift shadows", 0, 0, 40, 1, "%")
            .slider("red", "Red gain", 100, 50, 150, 1, "%")
            .slider("green", "Green gain", 100, 50, 150, 1, "%")
            .slider("blue", "Blue gain", 100, 50, 150, 1, "%");
    private static final Visual VIGNETTE = lens("lensvignette", "Lens Vignette", "Adds an adjustable lens falloff to frame the world.")
            .slider("strength", "Strength", 45, 0, 90, 1, "%")
            .slider("radius", "Clear center", 60, 20, 95, 1, "%");
    private static final Visual GRAIN = lens("filmgrain", "Film Grain", "Adds a static fine-grain texture without flicker.")
            .slider("strength", "Strength", 12, 0, 45, 1, "%")
            .slider("size", "Grain size", 1, 1, 5, 1, " px");
    private static final Visual PALETTE = lens("palettesteps", "Palette Steps", "Posterizes the world into a limited range of color levels.")
            .slider("levels", "Levels per channel", 8, 3, 24, 1, "")
            .slider("strength", "Blend", 70, 0, 100, 1, "%");
    private static final Visual FRINGE = lens("colorfringe", "Color Fringe", "Separates color channels slightly near the screen edges.")
            .slider("strength", "Edge offset", 2, 0, 6, 0.25, " px");
    private static final Visual PIXEL = lens("pixellens", "Pixel Lens", "Pixelates the world while keeping the UI crisp.")
            .slider("size", "Pixel size", 3, 1, 12, 1, " px");
    private static final Visual SHARPEN = lens("clarity", "Clarity", "Applies subtle edge sharpening to the world.")
            .slider("strength", "Strength", 18, 0, 65, 1, "%");
    private static final Visual BLOOM = lens("highlightbloom", "Highlight Bloom", "Adds a soft glow to bright areas.")
            .slider("strength", "Strength", 25, 0, 65, 1, "%")
            .slider("threshold", "Threshold", 72, 30, 95, 1, "%")
            .slider("radius", "Radius", 3, 1, 8, 1, " px");

    private static final Visual BARS = guide("letterbox", "Letterbox", "Adds cinema bars; this doesn't crop the screenshot file.")
            .mode("ratio", "Frame ratio", "2.39:1", "2.39:1", "2.00:1", "16:9", "4:3")
            .slider("opacity", "Opacity", 100, 15, 100, 5, "%");
    private static final Visual GRID = guide("compositiongrid", "Composition Grid", "Lines for lining up screenshots and builds.")
            .mode("grid", "Layout", "Thirds", "Thirds", "Golden ratio", "Center")
            .slider("opacity", "Opacity", 45, 10, 100, 5, "%");
    private static final Visual SAFE = guide("safearea", "Safe Area", "Outlines margins where captions or highlights fit in a recording.")
            .slider("margin", "Margin", 10, 3, 25, 1, "%")
            .slider("opacity", "Opacity", 40, 10, 100, 5, "%");
    private static final Visual LEVEL = guide("cameralevel", "Camera Level", "Shows whether your view is level with the horizon.")
            .slider("tolerance", "Level tolerance", 2, 1, 8, 1, "°");

    public static final Visual BOSS = visual("hidebossbar", "Clean Boss Bar", "Hides the vanilla boss bar. Boss behavior is unchanged.");
    public static final Visual SCOREBOARD = visual("hidesidebar", "Clean Scoreboard", "Hides the on-screen sidebar scoreboard.");
    public static final Visual TITLES = visual("hidetitles", "Quiet Titles", "Hides large on-screen titles and their subtitles.");
    public static final Visual ACTION = visual("hideactionbar", "Quiet Action Bar", "Hides temporary messages above the hotbar.");
    public static final Visual ITEM = visual("hideheldlabel", "Quiet Held Item", "Hides the temporary name when you switch held items.");
    public static final Visual EFFECTS = visual("hidepotionicons", "Quiet Potion Icons", "Hides the corner icons. Active effects are unchanged.");

    private static final List<Visual> SHADER_MODULES = List.of(GRADE, VIGNETTE, GRAIN, PALETTE, FRINGE, PIXEL, SHARPEN, BLOOM);

    private VisualModules() {}

    public static List<Module> modules() {
        return List.of(GRADE, VIGNETTE, GRAIN, PALETTE, FRINGE, PIXEL, SHARPEN, BLOOM,
                BARS, GRID, SAFE, LEVEL, BOSS, SCOREBOARD, TITLES, ACTION, ITEM, EFFECTS);
    }

    public static boolean hasWorldEffect() {
        if (IRIS) return false;
        for (Visual module : SHADER_MODULES) if (module.enabled) return true;
        return false;
    }

    public static void writeUniform(ByteBuffer out) {
        String preset = GRADE.choice("preset");
        float saturation = GRADE.percent("saturation"), contrast = GRADE.percent("contrast");
        float warmth = GRADE.percent("temperature"), fade = GRADE.percent("fade");
        switch (preset) {
            case "Cinema" -> { saturation *= .86F; contrast *= 1.14F; warmth -= .12F; }
            case "Vintage" -> { saturation *= .65F; warmth += .30F; fade += .08F; }
            case "Black & white" -> saturation = 0;
            case "Cool" -> warmth -= .26F;
            default -> { }
        }
        out.putFloat(GRADE.active("strength")).putFloat(GRADE.percent("brightness")).putFloat(saturation).putFloat(contrast);
        out.putFloat(GRADE.number("exposure")).putFloat(GRADE.number("gamma")).putFloat(warmth).putFloat(GRADE.percent("tint"));
        out.putFloat((float) Math.toRadians(GRADE.number("hue"))).putFloat(fade)
                .putFloat(VIGNETTE.active("strength")).putFloat(VIGNETTE.percent("radius"));
        out.putFloat(GRADE.percent("red")).putFloat(GRADE.percent("green")).putFloat(GRADE.percent("blue"))
                .putFloat(SHARPEN.active("strength"));
        out.putFloat(GRAIN.active("strength")).putFloat(GRAIN.number("size"))
                .putFloat(PALETTE.number("levels")).putFloat(PALETTE.active("strength"));
        out.putFloat(FRINGE.enabled ? FRINGE.number("strength") : 0F).putFloat(PIXEL.enabled ? PIXEL.number("size") : 1F)
                .putFloat(BLOOM.active("strength")).putFloat(BLOOM.percent("threshold"));
        out.putFloat(BLOOM.number("radius")).putFloat(0F).putFloat(0F).putFloat(0F);
    }

    @FunctionalInterface
    public interface Fill { void rect(int x1, int y1, int x2, int y2, int color); }

    public static void drawGuides(int width, int height, float pitch, Fill fill) {
        int top = 0, left = 0;
        if (BARS.enabled) {
            float ratio = switch (BARS.choice("ratio")) {
                case "2.00:1" -> 2F;
                case "16:9" -> 16F / 9F;
                case "4:3" -> 4F / 3F;
                default -> 2.39F;
            };
            top = Math.max(0, (height - Math.round(width / ratio)) / 2);
            left = Math.max(0, (width - Math.round(height * ratio)) / 2);
            int color = Math.round(BARS.percent("opacity") * 255F) << 24;
            if (top > 0) {
                fill.rect(0, 0, width, top, color);
                fill.rect(0, height - top, width, height, color);
            } else if (left > 0) {
                fill.rect(0, 0, left, height, color);
                fill.rect(width - left, 0, width, height, color);
            }
        }
        int w = width - 2 * left, h = height - 2 * top;
        if (w < 12 || h < 12) return;
        if (GRID.enabled) {
            int color = white(GRID.percent("opacity"));
            switch (GRID.choice("grid")) {
                case "Center" -> grid(fill, left, top, w, h, .5F, color);
                case "Golden ratio" -> { grid(fill, left, top, w, h, .382F, color); grid(fill, left, top, w, h, .618F, color); }
                default -> { grid(fill, left, top, w, h, 1F / 3F, color); grid(fill, left, top, w, h, 2F / 3F, color); }
            }
        }
        if (SAFE.enabled) {
            int x = left + Math.max(1, Math.round(w * SAFE.percent("margin")));
            int y = top + Math.max(1, Math.round(h * SAFE.percent("margin")));
            int color = white(SAFE.percent("opacity"));
            fill.rect(x, y, width - x, y + 1, color);
            fill.rect(x, height - y - 1, width - x, height - y, color);
            fill.rect(x, y + 1, x + 1, height - y - 1, color);
            fill.rect(width - x - 1, y + 1, width - x, height - y - 1, color);
        }
        if (LEVEL.enabled && w >= 56) {
            int x = width / 2, y = height / 2, offset = Math.round(Math.clamp(-pitch, -10F, 10F));
            int color = Math.abs(pitch) <= LEVEL.number("tolerance") ? 0xDD90FFAD : 0xDDFFCE80;
            fill.rect(x - 26, y + offset, x - 11, y + offset + 1, color);
            fill.rect(x + 12, y + offset, x + 27, y + offset + 1, color);
        }
    }

    private static void grid(Fill fill, int x, int y, int w, int h, float fraction, int color) {
        int vx = x + Math.round(w * fraction), hy = y + Math.round(h * fraction);
        fill.rect(vx, y, vx + 1, y + h, color);
        fill.rect(x, hy, x + w, hy + 1, color);
    }

    private static int white(float opacity) { return Math.round(255F * opacity) << 24 | 0xFFFFFF; }
    private static Visual lens(String id, String name, String desc) { return visual(id, name, desc + " Skipped with Iris."); }
    private static Visual guide(String id, String name, String desc) { return visual(id, name, desc); }
    private static Visual visual(String id, String name, String desc) { return new Visual(id, name, desc); }

    public static final class Visual extends Module {
        private Visual(String id, String name, String desc) { super(id, name, desc, Category.VISUAL, false, true); }
        private Visual slider(String key, String label, double initial, double min, double max, double step, String unit) {
            add(new Setting.SliderSetting(key, label, initial, min, max, step, unit));
            return this;
        }
        private Visual mode(String key, String label, String initial, String... values) {
            add(new Setting.ModeSetting(key, label, initial, values));
            return this;
        }
        private float number(String key) { return ((Number) get(key)).floatValue(); }
        private float percent(String key) { return number(key) * .01F; }
        private float active(String key) { return enabled ? percent(key) : 0F; }
        private String choice(String key) { return get(key); }
    }
}
