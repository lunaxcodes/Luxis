package com.luxis.gui;

import com.luxis.client.LuxisClientMod;
import com.luxis.config.GuiConfig;
import com.luxis.module.HudModule;
import com.luxis.module.ModuleRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static com.luxis.gui.Draw.Face.*;
import static com.luxis.gui.Draw.Surface.*;

/** Live HUD layout editor over the game world. */
public final class HudEditorScreen extends Screen {
    private static final String[] ACTIONS = {"snap", "guides", "undo", "reset", "done"};
    private static final Component[] LABELS = {
            Draw.label("Snap", STRONG), Draw.label("Guides", STRONG), Draw.label("Undo", STRONG),
            Draw.label("Reset", STRONG), Draw.label("Done", STRONG)
    };
    private static final Component HINT = Draw.label("Drag or use arrows · Ctrl+Z undo · Right Shift save", BODY);
    private final List<ClickRegion> toolbar = new ArrayList<>();
    private final Deque<List<Snapshot>> undo = new ArrayDeque<>();
    private final AnimationUtil.Clock clock = new AnimationUtil.Clock();
    private HudModule dragging, selected;
    private float open;
    private boolean resizing;
    private double dragX, dragY;
    private int origX, origY, origW, origH;
    private boolean dirty;
    private record Snapshot(HudModule hud, int x, int y, int w, int h, float scale, HudModule.Anchor anchor) {
        Snapshot(HudModule hud) { this(hud, hud.x, hud.y, hud.width, hud.height, hud.scale, hud.anchor); }
        void restore() { hud.x = x; hud.y = y; hud.width = w; hud.height = h; hud.scale = scale; hud.anchor = anchor; }
    }

    public HudEditorScreen() { super(Component.literal("HUD LAYOUT EDITOR")); }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float tickDelta) {
        // Keep the world sharp for positioning HUD elements.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float tickDelta) {
        open = AnimationUtil.reveal(open, clock.nextFrame(), false, 6F, 6F);
        float fade = AnimationUtil.easeOutCubic(open);
        GuiTheme.Palette th = GuiTheme.current();
        g.fill(0, 0, width, height, Draw.alpha(0x50000000 | (th.paint().backdrop() & 0xFFFFFF), fade));
        if (GuiConfig.appearance().hudGuides) {
            Draw.surface(g, OUTLINE, 24, 24, width - 48, height - 48, Draw.alpha(0x55FFFFFF, fade));
            g.fill(width / 2, 46, width / 2 + 1, height - 36, 0x34FFFFFF);
            g.fill(18, height / 2, width - 18, height / 2 + 1, 0x34FFFFFF);
        }

        for (HudModule m : ModuleRegistry.hudModules()) {
            if (!m.enabled || !m.dummyVisible) continue;
            m.renderDummyHud(g, tickDelta);
            int x = m.x, y = m.y, w = (int) (m.width * m.scale), h = (int) (m.height * m.scale);
            Draw.surface(g, OUTLINE, x, y, w, h, Draw.alpha(m == selected ? th.accent : th.paint().border(), fade));
            Draw.surface(g, CHIP, x, y - 19, w, 17, Draw.alpha(th.paint().chip(), fade));
            g.enableScissor(x + 6, y - 19, x + w - 5, y - 2);
            Draw.text(g, font, m.name, BODY, x + 6, y - 15, Draw.alpha(th.text, fade));
            g.disableScissor();
            Draw.pill(g, x + w - 10, y + h - 10, 8, 8, Draw.alpha(th.accent, fade));
            if (m == dragging && Math.abs(x + w / 2 - width / 2) < 3) g.fill(width / 2, 45, width / 2 + 1, height - 36, th.accent);
            if (m == dragging && Math.abs(y + h / 2 - height / 2) < 3) g.fill(18, height / 2, width - 18, height / 2 + 1, th.accent);
        }
        drawToolbar(g, mx, my, th, fade);
        int hintW = font.width(HINT), hx = (width - hintW) / 2;
        Draw.surface(g, CHIP, hx - 10, height - 34, hintW + 20, 23, Draw.alpha(th.paint().chip(), fade));
        Draw.text(g, font, HINT, hx, height - 27, Draw.alpha(th.text, fade));
    }

    private void drawToolbar(GuiGraphicsExtractor g, int mx, int my, GuiTheme.Palette th, float fade) {
        int total = (LABELS.length - 1) * 5;
        for (Component label : LABELS) total += font.width(label) + 16;
        int x = (width - total) / 2, y = 12 + Math.round(AnimationUtil.slide(5F * (1F - fade)));
        Draw.window(g, x - 6, y - 5, total + 12, 36, fade, th);
        toolbar.clear();
        for (int i = 0; i < LABELS.length; i++) {
            int w = font.width(LABELS[i]) + 16;
            ClickRegion region = new ClickRegion(x, y, w, 25, ACTIONS[i], "");
            boolean on = (i == 0 && GuiConfig.appearance().hudSnap) || (i == 1 && GuiConfig.appearance().hudGuides)
                    || i == 4 || region.contains(mx, my);
            LuxisModScreen.chip(g, x, y, w, 25, on, fade, th);
            Draw.text(g, font, LABELS[i], x + 8, y + 7, Draw.alpha(th.text, fade));
            toolbar.add(region);
            x += w + 5;
        }
    }

    private void pushUndo() {
        if (undo.size() == 40) undo.removeLast();
        undo.push(ModuleRegistry.hudModules().stream().map(Snapshot::new).toList());
    }

    private void applyUndo() {
        List<Snapshot> s = undo.poll();
        if (s == null) return;
        s.forEach(Snapshot::restore);
        dirty = true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) return true;
        double mx = event.x(), my = event.y();
        for (ClickRegion r : toolbar) {
            if (!r.contains(mx, my)) continue;
            switch (r.action()) {
                case "snap" -> GuiConfig.change("hud_snap");
                case "guides" -> GuiConfig.change("hud_guides");
                case "undo" -> applyUndo();
                case "reset" -> {
                    pushUndo();
                    for (HudModule m : ModuleRegistry.hudModules()) if (m.enabled) ModuleRegistry.resetHudLayout(m);
                    dirty = true;
                }
                case "done" -> done();
                default -> { }
            }
            return true;
        }
        for (HudModule m : ModuleRegistry.hudModules().reversed()) {
            if (!m.enabled || !m.dummyVisible) continue;
            int w = (int) (m.width * m.scale), h = (int) (m.height * m.scale);
            if (mx >= m.x && mx <= m.x + w && my >= m.y - 19 && my <= m.y + h) {
                pushUndo();
                dragging = selected = m;
                resizing = mx >= m.x + w - 12 && my >= m.y + h - 12;
                dragX = mx; dragY = my;
                origX = m.x; origY = m.y; origW = m.width; origH = m.height;
                return true;
            }
        }
        selected = null;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null) return true;
        int nx = (int) (origX + event.x() - dragX), ny = (int) (origY + event.y() - dragY);
        if (resizing) {
            float scale = Math.max(0.1F, dragging.scale);
            int maxW = Math.max(32, (int) ((width - dragging.x) / scale));
            int maxH = Math.max(18, (int) ((height - dragging.y) / scale));
            dragging.width = Math.clamp(origW + (int) ((event.x() - dragX) / scale), 32, maxW);
            dragging.height = Math.clamp(origH + (int) ((event.y() - dragY) / scale), 18, maxH);
        } else {
            if (GuiConfig.appearance().hudSnap) {
                nx = Math.round(nx / 8F) * 8; ny = Math.round(ny / 8F) * 8;
                int w = (int) (dragging.width * dragging.scale), h = (int) (dragging.height * dragging.scale);
                if (Math.abs(nx + w / 2 - width / 2) < 7) nx = width / 2 - w / 2;
                if (Math.abs(ny + h / 2 - height / 2) < 7) ny = height / 2 - h / 2;
            }
            move(dragging, nx, ny);
        }
        dirty = true;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_RSHIFT) {
            done(); return true;
        }
        if (event.hasControlDown() && event.key() == InputConstants.KEY_Z) { applyUndo(); return true; }
        if (selected != null) {
            int step = event.hasShiftDown() ? 8 : 1;
            int dx = event.key() == InputConstants.KEY_LEFT ? -step : event.key() == InputConstants.KEY_RIGHT ? step : 0;
            int dy = event.key() == InputConstants.KEY_UP ? -step : event.key() == InputConstants.KEY_DOWN ? step : 0;
            if (dx != 0 || dy != 0) {
                pushUndo();
                move(selected, selected.x + dx, selected.y + dy);
                dirty = true;
            }
        }
        return true;
    }

    private void done() {
        Minecraft.getInstance().gui.setScreen(new LuxisModScreen());
    }

    private void move(HudModule hud, int x, int y) {
        hud.x = Math.clamp(x, 0, Math.max(0, width - (int) (hud.width * hud.scale)));
        hud.y = Math.clamp(y, 0, Math.max(0, height - (int) (hud.height * hud.scale)));
    }

    @Override
    public void removed() {
        if (dirty) LuxisClientMod.save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
