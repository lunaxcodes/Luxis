package com.luxis.gui;

import java.util.HashMap;
import java.util.Map;

public final class AnimationUtil {
    private static final Map<String, Float> VALUES = new HashMap<>();
    private static final Map<String, Float> VELOCITY = new HashMap<>();
    private static long last = System.nanoTime();

    private AnimationUtil() {}

    public static float deltaSeconds() {
        long now = System.nanoTime();
        float dt = (now - last) / 1_000_000_000F;
        last = now;
        return Math.min(0.1F, Math.max(0F, dt));
    }

    public static float easeOutCubic(float t) {
        t = Math.max(0F, Math.min(1F, t));
        return 1F - (float) Math.pow(1F - t, 3);
    }

    public static float spring(String key, float target, float dt, float stiffness) {
        float v = VALUES.getOrDefault(key, 0F), vel = VELOCITY.getOrDefault(key, 0F);
        vel += (target - v) * stiffness * dt;
        vel *= (float) Math.exp(-8F * dt);
        v += vel * dt;
        VALUES.put(key, v); VELOCITY.put(key, vel);
        return v;
    }

    public static float approach(String key, float target, float speed, float dt) {
        float v = VALUES.getOrDefault(key, 0F);
        v += (target - v) * (1F - (float) Math.exp(-Math.max(0.01F, speed) * dt));
        VALUES.put(key, v);
        return v;
    }

    public static float get(String key) { return VALUES.getOrDefault(key, 0F); }
    public static void set(String key, float value) { VALUES.put(key, value); }
    public static void reset(String key) { VALUES.remove(key); VELOCITY.remove(key); }

    public static int lerpColor(int from, int to, float t) {
        t = Math.max(0F, Math.min(1F, t));
        int a = ch((from >>> 24) & 255, (to >>> 24) & 255, t);
        int r = ch((from >>> 16) & 255, (to >>> 16) & 255, t);
        int g = ch((from >>> 8) & 255, (to >>> 8) & 255, t);
        int b = ch(from & 255, to & 255, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int ch(int a, int b, float t) { return (int) (a + (b - a) * t); }
}
