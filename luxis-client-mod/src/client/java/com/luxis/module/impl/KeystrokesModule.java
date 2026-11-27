package com.luxis.module.impl;

import com.luxis.module.Module;
import com.luxis.gui.Draw;
import com.luxis.gui.GuiTheme;
import com.luxis.module.HudModule;
import com.luxis.module.Setting;
import com.luxis.module.ModuleRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class KeystrokesModule extends HudModule {
    public KeystrokesModule() {
        super("keys", "Keystrokes", "Animated WASD, Space and mouse indicators.", Category.HUD, false, false);
        add(new Setting.SliderSetting("size", "Key size", 34, 24, 56, 2));
        add(new Setting.SliderSetting("spacing", "Spacing", 3, 0, 8, 1));
        add(new Setting.BooleanSetting("showCps", "Show CPS", true));
        add(new Setting.BooleanSetting("showSpace", "Show space", true));
        add(new Setting.BooleanSetting("outline", "Outline", true));
        width = 130; height = 110;
    }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float tickDelta) {
        int s = ((Number) get("size")).intValue(), gap = ((Number) get("spacing")).intValue();
        int color = GuiTheme.current().accent;
        key(g, "W", x + s + gap, y, s, InputConstants.isKeyDown(InputConstants.KEY_W), color);
        key(g, "A", x, y + s + gap, s, InputConstants.isKeyDown(InputConstants.KEY_A), color);
        key(g, "S", x + s + gap, y + s + gap, s, InputConstants.isKeyDown(InputConstants.KEY_S), color);
        key(g, "D", x + (s + gap) * 2, y + s + gap, s, InputConstants.isKeyDown(InputConstants.KEY_D), color);
        if (Boolean.TRUE.equals(get("showSpace"))) key(g, "SPACE", x, y + (s + gap) * 2, s * 3 + gap * 2, InputConstants.isKeyDown(InputConstants.KEY_SPACE), color);
        if (Boolean.TRUE.equals(get("showCps")) || !((String) get("textPrefix")).isBlank())
            drawBox(g, x, y + (s + gap) * (Boolean.TRUE.equals(get("showSpace")) ? 3 : 2),
                    s * 3 + gap * 2, 20, Boolean.TRUE.equals(get("showCps"))
                            ? "LMB " + CpsHudModule.leftCount + "  RMB " + CpsHudModule.rightCount : "Keys", color);
    }

    private void key(GuiGraphicsExtractor g, String label, int x, int y, int w, boolean down, int color) {
        g.fill(x, y, x + w, y + w, down ? color : 0x55000000);
        if (Boolean.TRUE.equals(get("outline"))) Draw.softBorder(g, x, y, w, w, 5, down ? 0xFFFFFFFF : GuiTheme.current().paint().border());
        g.centeredText(Minecraft.getInstance().font, Component.literal(label), x + w / 2, y + w / 3, 0xFFFFFFFF);
    }
}
