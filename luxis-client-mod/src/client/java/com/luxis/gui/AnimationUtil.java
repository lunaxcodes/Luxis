package com.luxis.gui;

import com.luxis.config.GuiConfig;

public final class AnimationUtil {
    private AnimationUtil() {}

    /** One sample per frame, shared by all controls on the screen. */
    public static final class Clock {
        private long last = System.nanoTime();

        public float nextFrame() {
            long now = System.nanoTime();
            float seconds = (now - last) / 1_000_000_000F;
            last = now;
            return Math.min(0.05F, seconds);
        }
    }

    public static float clamp(float v) {
        return Math.max(0F, Math.min(1F, v));
    }

    public static float easeOutCubic(float t) {
        float r = 1F - clamp(t);
        return 1F - r * r * r;
    }

    public static float smoothStep(float t) { t = clamp(t); return t * t * (3F - 2F * t); }

    /** Exponential decay is independent of how many frames split up the elapsed time. */
    public static float approach(float current, float target, float speed, float dt, float epsilon) {
        if (Math.abs(current - target) <= epsilon) return target;
        return target + (current - target) * (float) Math.exp(-speed * dt);
    }

    static float guiApproach(float current, float target, float speed, float dt, float epsilon) {
        return switch (GuiConfig.appearance().motion) {
            case Off -> target;
            case Reduced -> approach(current, target, speed * 1.65F, dt, epsilon);
            case Full -> approach(current, target, speed, dt, epsilon);
        };
    }

    static float reveal(float open, float dt, boolean closing, float openingSpeed, float closingSpeed) {
        var look = GuiConfig.appearance();
        var motion = look.motion;
        if (motion == GuiConfig.Motion.Off) return closing ? 0F : 1F;
        return clamp(open + dt * (closing ? -closingSpeed : openingSpeed) * (motion == GuiConfig.Motion.Reduced ? 1.65F : 1F)
                * look.animationSpeed / 100F);
    }

    static float slide(float distance) {
        var look = GuiConfig.appearance();
        return look.motion == GuiConfig.Motion.Full && look.transition != GuiConfig.Transition.Fade ? distance : 0F;
    }

    public static int lerpColor(int from, int to, float t) {
        t = clamp(t);
        return (ch(from >>> 24, to >>> 24, t) << 24)
                | (ch((from >>> 16) & 255, (to >>> 16) & 255, t) << 16)
                | (ch((from >>> 8) & 255, (to >>> 8) & 255, t) << 8)
                | ch(from & 255, to & 255, t);
    }

    private static int ch(int a, int b, float t) { return Math.round(a + (b - a) * t); }
}
