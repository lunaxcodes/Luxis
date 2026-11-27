package com.luxis.gui;

import com.luxis.client.LuxisClientMod;
import com.luxis.module.HudModule;
import com.luxis.module.ModuleRegistry;
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

/** Live HUD layout editor over the game world. */
public final class HudEditorScreen extends Screen {
    private final List<ClickRegion> toolbar = new ArrayList<>();
    private final Deque<String> undo = new ArrayDeque<>();
    private HudModule dragging;
    private boolean resizing;
    private double dragX, dragY;
    private int origX, origY, origW, origH;
    private boolean snap = true, margins = false;

    public HudEditorScreen() { super(Component.literal("HUD LAYOUT EDITOR")); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float tickDelta) {
        float dt = AnimationUtil.deltaSeconds();
        g.fill(0, 0, width, height, 0x44000000);
        if (margins) g.outline(24, 24, width - 24, height - 24, 0x44FFFFFF);
        toolbar.clear();
        String[] labels = {"SNAP", "MARGINS", "UNDO", "RESET", "DONE"};
        String[] acts = {"snap", "margins", "undo", "reset", "done"};
        int tx = width / 2 - 260;
        for (int i = 0; i < labels.length; i++) {
            int w = labels[i].length() * 8 + 24;
            int c = switch (acts[i]) {
                case "snap" -> snap ? 0x55C864FF : 0x22FFFFFF;
                case "margins" -> margins ? 0x55C864FF : 0x22FFFFFF;
                case "done" -> 0xFFC864FF;
                default -> 0x22FFFFFF;
            };
            g.fill(tx, 16, tx + w, 42, c);
            g.outline(tx, 16, tx + w, 42, 0x66FFFFFF);
            text(g, labels[i], tx + 10, 23, 0xFFFFFFFF);
            toolbar.add(new ClickRegion(tx, 16, w, 26, acts[i], ""));
            tx += w + 8;
        }
        float alpha = AnimationUtil.approach("editor_open", 1F, 12F, dt);

        for (HudModule m : ModuleRegistry.hudModules()) {
            if (!m.enabled || !m.dummyVisible) continue;
            m.renderDummyHud(g, tickDelta);
            int x = m.x, y = m.y, w = (int) (m.width * m.scale), h = (int) (m.height * m.scale);
            g.outline(x, y, x + w, y + h, 0xFFC864FF);
            g.fill(x, y, x + w, y + 12, 0x88000000);
            text(g, m.name, x + 4, y + 1, 0xFFC864FF);
            g.fill(x, y, x + 8, y + h, 0x44C864FF);
            g.fill(x + w - 10, y + h - 10, x + w, y + h, 0xFFC864FF);
            if (Math.abs(x + w - width / 2) < 8) { g.fill(width / 2, 50, width / 2 + 1, height - 50, 0x88FFFFFF); }
            if (Math.abs(y - height / 2) < 8) { g.fill(50, height / 2, width - 50, height / 2 + 1, 0x88FFFFFF); }
        }
        text(g, "DRAG TO MOVE  -  BLUE CORNER TO RESIZE  -  RIGHT SHIFT TO SAVE", width / 2 - 210, height - 32, 0xAAFFFFFF);
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int c) {
        g.text(Minecraft.getInstance().font, Component.literal(s), x, y, c, false);
    }

    private void pushUndo() {
        StringBuilder sb = new StringBuilder();
        for (HudModule m : ModuleRegistry.hudModules()) sb.append(m.id).append(':').append(m.x).append(',').append(m.y).append(',').append(m.width).append(',').append(m.height).append(';');
        undo.push(sb.toString());
    }

    private void applyUndo() {
        String s = undo.poll();
        if (s == null) return;
        for (String part : s.split(";")) {
            String[] p = part.split(":");
            if (p.length < 2) continue;
            HudModule m = (HudModule) ModuleRegistry.byId(p[0]);
            if (m == null) continue;
            String[] v = p[1].split(",");
            m.x = Integer.parseInt(v[0]); m.y = Integer.parseInt(v[1]);
            m.width = Integer.parseInt(v[2]); m.height = Integer.parseInt(v[3]);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        for (ClickRegion r : toolbar) {
            if (!r.contains(mx, my)) continue;
            switch (r.action()) {
                case "snap" -> snap = !snap;
                case "margins" -> margins = !margins;
                case "undo" -> applyUndo();
                case "reset" -> {
                    pushUndo();
                    for (HudModule m : ModuleRegistry.hudModules()) {
                        m.x = 40; m.y = 40; m.width = 170; m.height = 64;
                    }
                }
                case "done" -> done();
                default -> { }
            }
            return true;
        }
        for (HudModule m : ModuleRegistry.hudModules()) {
            if (!m.enabled) continue;
            int w = (int) (m.width * m.scale), h = (int) (m.height * m.scale);
            if (mx >= m.x && mx <= m.x + w && my >= m.y && my <= m.y + h) {
                pushUndo();
                dragging = m;
                resizing = mx >= m.x + w - 12 && my >= m.y + h - 12;
                dragX = mx; dragY = my;
                origX = m.x; origY = m.y; origW = m.width; origH = m.height;
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null) return true;
        int nx = (int) (origX + event.x() - dragX), ny = (int) (origY + event.y() - dragY);
        if (snap) { nx = Math.round(nx / 8F) * 8; ny = Math.round(ny / 8F) * 8; }
        if (Math.abs(nx + dragging.width - width / 2) < 10) nx = width / 2 - dragging.width;
        if (Math.abs(ny - height / 2) < 10) ny = height / 2;
        if (resizing) {
            dragging.width = Math.max(32, origW + (int) (event.x() - dragX));
            dragging.height = Math.max(24, origH + (int) (event.y() - dragY));
        } else {
            dragging.x = nx;
            dragging.y = ny;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) { dragging = null; LuxisClientMod.save(); }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT) {
            done(); return true;
        }
        return true;
    }

    private void done() {
        LuxisClientMod.save();
        Minecraft.getInstance().gui.setScreen(new LuxisModScreen());
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
