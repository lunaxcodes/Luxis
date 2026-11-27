package com.luxis.gui;

import com.luxis.config.WaypointStore;
import com.luxis.config.WaypointStore.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.util.List;
import java.util.Locale;

import static com.luxis.gui.Draw.Face.*;
import static com.luxis.gui.Draw.Surface.*;

/** Saved coordinate notes, not a radar or an overlay on the world. */
final class WaypointPanel {
    private static final int ROW = 38;
    private static final Component NAME = Draw.label("Name (optional)", BODY);
    private static final Component SAVE = Draw.label("Save here", STRONG);
    private static final Component EMPTY = Draw.label("Save this spot to remember it later.", BODY);
    private static final Component NO_WORLD = Draw.label("Join a world or server to save places.", BODY);
    private static final Component LIMIT = Draw.label("Waypoint limit reached (250).", BODY);
    private static final Component FULL = Draw.label("Limit reached", BODY);
    private static final Component COPY = Draw.label("Copy", BODY);
    private static final Component COPIED = Draw.label("Copied", BODY);
    private static final Component DELETE = Draw.label("×", STRONG);
    private record Entry(Waypoint waypoint, Component name, Component info) {}

    private List<Entry> entries = List.of();
    private String scope, draft = "", copied;
    private long copiedAt;
    private Component draftLabel = NAME;
    private boolean focused, refresh = true;
    private int lastWidth, maxScroll;
    private float scroll, target;

    private static String currentScope(Minecraft client) {
        if (client.level == null) return null;
        var world = client.getSingleplayerServer();
        if (world != null) {
            return WaypointStore.scopeKey("save:" + world.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
        }
        var remote = client.getCurrentServer();
        return remote == null || remote.ip == null ? null
                : WaypointStore.scopeKey("server:" + remote.ip.strip().toLowerCase(Locale.ROOT));
    }

    void enter() {
        scope = currentScope(Minecraft.getInstance());
        focused = false;
        scroll = target = 0;
        refresh = true;
    }

    void draw(GuiGraphics g, Font font, List<ClickRegion> regions, int x, int y, int w,
              int bottom, float dt, float fade, GuiTheme.Palette theme) {
        int inputW = w - 95;
        if (refresh || lastWidth != w) {
            entries = WaypointStore.forWorld(scope).stream().map(place -> new Entry(place,
                    Draw.fit(font, place.name(), STRONG, w - 110),
                    Draw.fit(font, place.coordinates() + " · " + place.dimension().replace("minecraft:", ""), BODY, w - 110))).toList();
            lastWidth = w;
            updateDraft(font);
            refresh = false;
        }
        LuxisModScreen.chip(g, x, y, inputW, 24, focused, fade, theme);
        Draw.text(g, font, draftLabel, x + 8, y + 8, Draw.alpha(draft.isEmpty() ? theme.muted : theme.text, fade));
        boolean canSave = scope != null && !maxed;
        LuxisModScreen.chip(g, x + w - 87, y, 87, 24, canSave, fade, theme);
        Draw.text(g, font, maxed ? FULL : SAVE, x + w - 76, y + 7, Draw.alpha(canSave ? theme.text : theme.muted, fade));
        if (scope != null) regions.add(new ClickRegion(x, y, inputW, 24, "place_name", ""));
        if (canSave) regions.add(new ClickRegion(x + w - 87, y, 87, 24, "place_save", ""));

        int top = y + 30;
        int viewport = bottom - top;
        if (viewport <= 0) return;
        maxScroll = Math.max(0, entries.size() * ROW - viewport);
        target = Math.max(0, Math.min(target, maxScroll));
        scroll = AnimationUtil.guiApproach(Math.min(scroll, maxScroll), target, 24F, dt, 0.02F);
        g.enableScissor(x, top, x + w, bottom);
        if (entries.isEmpty()) Draw.text(g, font, maxed ? LIMIT : scope == null ? NO_WORLD : EMPTY,
                x + 4, top + 6, Draw.alpha(theme.muted, fade));
        else {
            int pos = Math.round(scroll), first = pos / ROW, end = Math.min(entries.size(), (pos + viewport + ROW - 1) / ROW);
            for (int i = first; i < end; i++) {
                Entry entry = entries.get(i);
                int py = top + i * ROW - pos;
                Draw.guiSurface(g, CARD, x, py, w - 8, ROW - 4, Draw.alpha(theme.paint().card(), fade));
                Draw.text(g, font, entry.name, x + 9, py + 7, Draw.alpha(theme.text, fade));
                Draw.text(g, font, entry.info, x + 9, py + 21, Draw.alpha(theme.muted, fade));
                LuxisModScreen.chip(g, x + w - 88, py + 6, 47, 22, false, fade, theme);
                LuxisModScreen.chip(g, x + w - 37, py + 6, 22, 22, false, fade, theme);
                boolean justCopied = entry.waypoint.id().equals(copied) && System.nanoTime() - copiedAt < 1_500_000_000L;
                Draw.text(g, font, justCopied ? COPIED : COPY, x + w - 82, py + 13, Draw.alpha(theme.text, fade));
                Draw.text(g, font, DELETE, x + w - 29, py + 12, Draw.alpha(theme.muted, fade));
                addVisible(regions, x + w - 88, py + 6, 47, top, bottom, "place_copy", entry.waypoint.id());
                addVisible(regions, x + w - 37, py + 6, 22, top, bottom, "place_delete", entry.waypoint.id());
            }
        }
        g.disableScissor();
    }

    private boolean maxed;

    void save() {
        var client = Minecraft.getInstance();
        if (scope == null || client.player == null || client.level == null) return;
        var pos = client.player.blockPosition();
        maxed = !WaypointStore.add(scope, draft, client.level.dimension().identifier().toString(),
                pos.getX(), pos.getY(), pos.getZ());
        if (maxed) return;
        draft = "";
        focused = false;
        scroll = target = 0;
        refresh = true;
    }

    void copy(String id) {
        for (Entry entry : entries) {
            Waypoint place = entry.waypoint;
            if (!place.id().equals(id)) continue;
            Minecraft.getInstance().keyboardHandler.setClipboard(place.coordinates() + " [" + place.dimension() + "]");
            copied = id;
            copiedAt = System.nanoTime();
            return;
        }
    }

    void remove(String id) {
        if (WaypointStore.remove(scope, id)) {
            maxed = false;
            refresh = true;
        }
    }

    private static void addVisible(List<ClickRegion> regions, int x, int y, int w, int top, int bottom,
                                   String action, String id) {
        int from = Math.max(y, top), to = Math.min(y + 22, bottom);
        if (to > from) regions.add(new ClickRegion(x, from, w, to - from, action, id));
    }

    void scroll(double amount) { target = Math.max(0, Math.min(maxScroll, target - (float) amount * 33F)); }
    void focus(boolean focus) { focused = focus; }
    boolean focused() { return focused; }

    void type(String text, Font font) {
        if (draft.length() >= 36) return;
        draft += text;
        updateDraft(font);
    }

    void backspace(Font font) {
        if (draft.isEmpty()) return;
        draft = draft.substring(0, draft.offsetByCodePoints(draft.length(), -1));
        updateDraft(font);
    }

    private void updateDraft(Font font) {
        draftLabel = draft.isEmpty() ? NAME : Draw.fit(font, draft, BODY, Math.max(30, lastWidth - 114));
    }
}
