package com.luxis.gui;

/** Theme colors for material masks, not translucent overlays on top of a blue base. */
public record ThemePaint(int window, int card, int hover, int selected, int chip, int active,
                         int backdrop, int border, int track, int thumb) {
    static ThemePaint create(int primary, int secondary, int tint, float depth) {
        int hue = AnimationUtil.lerpColor(primary, secondary, .22F);
        return new ThemePaint(
                mix(0xFF24242B, hue, depth * .75F),
                mix(0xFF383841, hue, depth * .62F),
                mix(0xFF45454F, hue, depth * .86F),
                mix(0xFF42424C, secondary, depth * .82F),
                mix(0xFF373740, hue, depth * .55F),
                mix(0xFF505059, hue, depth * .90F),
                mix(0xFF08080C, secondary, depth * .22F),
                mix(0x477D7D89, primary, depth),
                mix(0x66767680, tint, depth * .78F),
                mix(0xFFFAFAFC, tint, depth * .75F));
    }

    private static int mix(int base, int color, float weight) {
        return (base & 0xFF000000) | (AnimationUtil.lerpColor(base, color, weight) & 0xFFFFFF);
    }
}
