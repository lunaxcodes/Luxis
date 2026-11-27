package com.luxis.module;

import com.luxis.gui.Draw;
import com.luxis.gui.GuiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public abstract class HudModule extends Module {
    public enum Anchor { TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }

    public int x = 40, y = 40, width = 170, height = 64;
    public float scale = 1.0F;
    public Anchor anchor = Anchor.TOP_LEFT;
    public boolean dummyVisible = true;

    protected HudModule(String id, String name, String description, Category category, boolean enabled, boolean isNew) {
        super(id, name, description, category, enabled, isNew);
        add(new Setting.BooleanSetting("shadow", "Text shadow", true));
        add(new Setting.TextSetting("textPrefix", "Custom label"));
        add(new Setting.BooleanSetting("bgBox", "Background box", true));
        add(new Setting.BooleanSetting("bgOutline", "Box outline", true));
        add(new Setting.SliderSetting("bgOpacity", "Background opacity", 85, 0, 100, 5, "%"));
        add(new Setting.ModeSetting("textStyle", "Text style", "Theme", "Theme", "Module", "Custom"));
        add(new Setting.ColorSetting("textColor", "Text color", 0xFFFFFFFF));
    }

    public abstract void renderHud(GuiGraphics graphics, float tickDelta);

    public void renderDummyHud(GuiGraphics graphics, float tickDelta) { renderHud(graphics, tickDelta); }

    protected void drawBox(GuiGraphics g, int x, int y, int w, int h, String text, int color) {
        drawBox(g, x, y, w, h, Draw.label(text, Draw.Face.BODY), color);
    }

    protected void drawBox(GuiGraphics g, int x, int y, int w, int h, Component text, int color) {
        Minecraft mc = Minecraft.getInstance();
        var theme = GuiTheme.current();
        int height = Math.max(16, Math.min(h, 22));
        int width = Math.max(1, Math.min(w, g.guiWidth() - x));
        if (Boolean.TRUE.equals(get("bgBox"))) {
            int a = Math.max(0, Math.min(255, ((Number) get("bgOpacity")).intValue() * 255 / 100));
            Draw.surface(g, Draw.Surface.CARD, x, y, width, height, (a << 24) | (theme.paint().card() & 0xFFFFFF));
        }
        if (Boolean.TRUE.equals(get("bgOutline")))
            Draw.softBorder(g, x, y, width, height, 6, Draw.alpha(theme.accent, .77F));
        String prefix = get("textPrefix");
        if (!prefix.isBlank()) text = Draw.fit(mc.font, prefix + " · " + text.getString(), Draw.Face.BODY, width - 12);
        else if (mc.font.width(text) > width - 12) text = Draw.fit(mc.font, text.getString(), Draw.Face.BODY, width - 12);
        if ("Custom".equals(get("textStyle"))) color = ((Number) get("textColor")).intValue();
        else if (!"Module".equals(get("textStyle"))) color = theme.tint;
        g.drawString(mc.font, text, x + 6, y + (height - 9) / 2, color, !Boolean.FALSE.equals(get("shadow")));
    }

    protected void drawBox(GuiGraphics g, int x, int y, int w, int h, String text, int color, HudModule ignored) {
        drawBox(g, x, y, w, h, text, color);
    }

    public void applyAnchor(int screenWidth, int screenHeight) {
        int m = 24;
        switch (anchor) {
            case TOP_LEFT -> { x = m; y = m; }
            case TOP_CENTER -> { x = (screenWidth - width) / 2; y = m; }
            case TOP_RIGHT -> { x = screenWidth - width - m; y = m; }
            case BOTTOM_LEFT -> { x = m; y = screenHeight - height - m; }
            case BOTTOM_CENTER -> { x = (screenWidth - width) / 2; y = screenHeight - height - m; }
            case BOTTOM_RIGHT -> { x = screenWidth - width - m; y = screenHeight - height - m; }
        }
    }
}
