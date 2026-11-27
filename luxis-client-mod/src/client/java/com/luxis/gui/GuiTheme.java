package com.luxis.gui;

import com.luxis.config.GuiConfig;

import java.util.List;

/** Stable names are stored in appearance.json, independently of palette ordering. */
public final class GuiTheme {
    public static final class Palette {
        public final String name;
        public final int accent, accent2, tint, text = 0xFFF5F8FF, muted;
        private final ThemePaint[] paints;

        Palette(String name, int accent, int accent2, int tint, int muted) {
            this.name = name;
            this.accent = accent;
            this.accent2 = accent2;
            this.tint = tint;
            this.muted = AnimationUtil.lerpColor(muted, 0xFFE8F0FF, 0.52F);
            this.paints = new ThemePaint[]{
                    ThemePaint.create(accent, accent2, tint, .30F),
                    ThemePaint.create(accent, accent2, tint, .65F),
                    ThemePaint.create(accent, accent2, tint, 1F)
            };
        }

        public ThemePaint paint() { return paints[GuiConfig.appearance().colorDepth.ordinal()]; }
    }

    public static final List<Palette> ALL = List.of(
        new Palette("Orchid", 0xFFC864FF, 0xFF7B5CFF, 0xFFE7CBFF, 0xFFB5A1CE),
        new Palette("Nebula", 0xFF38BDF8, 0xFF6366F1, 0xFFC0CDFF, 0xFFA3AED8),
        new Palette("Emerald", 0xFF22C55E, 0xFF0D9488, 0xFFC3FFDB, 0xFF91CDB1),
        new Palette("Crimson", 0xFFF43F5E, 0xFFBE123C, 0xFFFFC4CF, 0xFFCB9EAA),
        new Palette("Amber", 0xFFFBBF24, 0xFFEA580C, 0xFFFFDFB6, 0xFFCDB38B),
        new Palette("Monochrome", 0xFFE2E8F0, 0xFF94A3B8, 0xFFDDE4EC, 0xFFA6B0C1),
        new Palette("Glacier", 0xFF82EEFC, 0xFF5798CF, 0xFFC0F6FF, 0xFFA0CADA),
        new Palette("Matcha", 0xFFBBDF83, 0xFF82A858, 0xFFE2F2B9, 0xFFBFCDA1),
        new Palette("Rosewater", 0xFFFF9CCA, 0xFFE46D99, 0xFFFFCCE3, 0xFFE0A9C1),
        new Palette("Midnight", 0xFF9AA7FF, 0xFF6E71BC, 0xFFBFC5F8, 0xFFACB0D6),
        new Palette("Solar", 0xFFFFB76B, 0xFFFF7367, 0xFFFFD6B9, 0xFFDAB093),
        new Palette("Lagoon", 0xFF45E5CA, 0xFF419EC1, 0xFFBAF2EA, 0xFF9DCBC7),
        new Palette("Copper", 0xFFF4AA81, 0xFFD47956, 0xFFF7D0BD, 0xFFD2AA9B),
        new Palette("Slate", 0xFFA3BEDC, 0xFF708BAB, 0xFFD1DEE8, 0xFFACBCCB),
        new Palette("Sorbet", 0xFFFF9BB0, 0xFFFFC379, 0xFFFFD5E2, 0xFFDBADBB),
        new Palette("Aurora", 0xFFA5F8BF, 0xFF9D94F5, 0xFFDBD1FA, 0xFFADA8D8)
    );

    private GuiTheme() {}

    public static String legacyName(int index) { return ALL.get(Math.floorMod(index, 6)).name; }

    public static Palette named(String name) {
        for (Palette palette : ALL) if (palette.name.equalsIgnoreCase(name)) return palette;
        return ALL.get(3);
    }

    public static Palette current() { return named(GuiConfig.appearance().theme); }
}
