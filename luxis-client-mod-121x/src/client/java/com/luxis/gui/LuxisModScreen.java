package com.luxis.gui;

import com.luxis.client.LuxisClientMod;
import com.luxis.config.GuiConfig;
import com.luxis.module.Module;
import com.luxis.module.ModuleRegistry;
import com.luxis.module.impl.ClientTuning;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.luxis.gui.Draw.Face.*;
import static com.luxis.gui.Draw.Surface.*;

/** Luxis's glass module browser; only visible cards are submitted for rendering. */
public final class LuxisModScreen extends Screen {
    private static final String[] TABS = {"Mods", "Settings", "Waypoints"};
    private static final String[] FILTERS = {"All", "Pinned", "New", "HUD", "Performance", "Visual", "PvP", "Enabled"};
    private static final int GAP = 8;
    private static final Component TITLE_TEXT = Draw.label("Luxis Client", TITLE);
    private static final Component HINT = Draw.label("Right Shift to close", BODY);
    private static final Component EMPTY = Draw.label("No modules match your search.", BODY);
    private static final Component EMPTY_PINNED = Draw.label("Pin modules with the star on a card.", BODY);
    private static final Component STAR = Draw.label("★", BODY);
    private static final Comparator<Card> BY_NAME = Comparator.comparing(card -> card.module.name, String.CASE_INSENSITIVE_ORDER);

    private final AnimationUtil.Clock clock = new AnimationUtil.Clock();
    private final List<ClickRegion> regions = new ArrayList<>();
    private final List<Card> library = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private final List<Card> visible = new ArrayList<>();
    private final GuiSettingsPanel settings = new GuiSettingsPanel();
    private final WaypointPanel waypoints = new WaypointPanel();
    private final MenuParticles particles = new MenuParticles();
    private final float[] filterSelection = new float[FILTERS.length];
    private Component[] tabLabels, filterLabels;
    private int[] tabWidths, filterWidths;
    private Component queryLabel;
    private int wx, wy, ww, wh, cx, cw, cols, cardW, cardH, rowH, gridTop, gridBottom;
    private int tab, filter, maxScroll;
    private float scroll, scrollTarget, open, offsetY, indicatorX = Float.NaN, indicatorW;
    private float windowScale = 1F, categoryReveal = 1F, tabReveal = 1F;
    private int categoryDirection = 1;
    private String search = "";
    private boolean compact, searchFocused, closing;
    private ModuleOptionsOverlay options;

    private static final class Card {
        final Module module;
        final String searchText;
        Component name, desc;
        float hover, enabled;
        int x, y;

        Card(Module module) {
            this.module = module;
            searchText = (module.name + " " + module.description).toLowerCase(Locale.ROOT);
            enabled = module.enabled ? 1F : 0F;
        }
    }

    public LuxisModScreen() { super(Component.literal("Luxis Client")); }

    @Override
    protected void init() {
        var look = GuiConfig.appearance();
        ww = Math.min(width - 18, Math.max(250, Math.min(1000, width * look.windowWidth / 100)));
        wh = Math.min(height - 18, Math.max(169, Math.min(640, height * look.windowHeight / 100)));
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;
        int inset = ww >= 520 ? 22 : 14;
        cx = wx + inset;
        cw = ww - inset * 2;
        cols = Math.max(1, Math.min(3, (cw + GAP) / 184));
        cardW = (cw - 9 - GAP * (cols - 1)) / cols;
        compact = wh < 290 || look.density == GuiConfig.Density.Compact;
        cardH = !look.descriptions ? 36 : compact ? 42 : look.density == GuiConfig.Density.Comfortable ? 66 : 58;
        rowH = cardH + GAP;
        tabLabels = labels(TABS);
        filterLabels = labels(FILTERS);
        tabWidths = widths(tabLabels, 20);
        filterWidths = widths(filterLabels, compact ? 10 : 16);
        indicatorX = Float.NaN;
        updateQueryLabel();
        library.clear();
        for (Module module : ModuleRegistry.all()) library.add(new Card(module));
        rebuildCards();
    }

    private static Component[] labels(String[] values) {
        Component[] result = new Component[values.length];
        for (int i = 0; i < values.length; i++) result[i] = Draw.label(values[i], STRONG);
        return result;
    }

    private int[] widths(Component[] labels, int padding) {
        int[] result = new int[labels.length];
        for (int i = 0; i < labels.length; i++) result[i] = font.width(labels[i]) + padding;
        return result;
    }

    private void updateQueryLabel() {
        queryLabel = Draw.fit(font, search.isEmpty() ? "Search modules" : search, BODY, searchWidth() - 23);
    }

    private int searchWidth() { return Math.min(234, cw / 2 - 6); }

    private void rebuildCards() {
        cards.clear();
        visible.clear();
        String query = search.toLowerCase(Locale.ROOT);
        for (Card card : library) {
            Module m = card.module;
            boolean matches = switch (filter) {
                case 1 -> GuiConfig.favorite(m.id);
                case 2 -> m.isNew;
                case 3 -> m.category == Module.Category.HUD;
                case 4 -> m.category == Module.Category.OPTIMIZATION && ClientTuning.isTuner(m);
                case 5 -> m.category == Module.Category.VISUAL;
                case 6 -> m.category == Module.Category.PVP;
                case 7 -> m.enabled;
                default -> true;
            };
            if (!matches) continue;
            if (card.searchText.contains(query)) cards.add(card);
        }
        cards.sort(switch (GuiConfig.sort()) {
            case Name -> BY_NAME;
            case Enabled -> Comparator.comparing((Card card) -> !card.module.enabled).thenComparing(BY_NAME);
            case Category -> Comparator.comparing((Card card) -> card.module.category).thenComparing(BY_NAME);
        });
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float tickDelta) {
        // The screen draws its own tinted scrim; skip Minecraft's full-screen blur pass.
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float tickDelta) {
        float dt = clock.nextFrame();
        open = AnimationUtil.reveal(open, dt, closing, 3.35F, 4.25F);
        if (closing && open <= 0F) {
            super.onClose();
            return;
        }
        float fade = AnimationUtil.smoothStep(open);
        var look = GuiConfig.appearance();
        offsetY = AnimationUtil.slide((1F - fade) * (look.transition == GuiConfig.Transition.Zoom ? 4F : 15F));
        float zoom = look.transition == GuiConfig.Transition.Zoom ? .13F : .035F;
        windowScale = 1F - AnimationUtil.slide((1F - fade) * zoom);
        float centerX = wx + ww / 2F, centerY = wy + wh / 2F;
        float mouseX = (float) ((mx - centerX) / windowScale + centerX);
        float mouseY = (float) ((my - offsetY - centerY) / windowScale + centerY);
        GuiTheme.Palette th = GuiTheme.current();
        g.fill(0, 0, width, height, Draw.alpha((look.backdrop.alpha << 24) | (th.paint().backdrop() & 0xFFFFFF), fade));
        g.pose().pushMatrix().translate(centerX, centerY + offsetY).scale(windowScale).translate(-centerX, -centerY);
        Draw.surface(g, SHADOW, wx - 8, wy - 4, ww + 16, wh + 16, Draw.alpha(0xA0000000, fade));
        Draw.window(g, wx, wy, ww, wh, fade, th);
        particles.draw(g, wx, wy, ww, wh, dt, fade, th);

        regions.clear();
        visible.clear();
        drawHeader(g, dt, fade, th);
        tabReveal = AnimationUtil.reveal(tabReveal, dt, false, 5F, 5F);
        float contentFade = fade * (.18F + .82F * AnimationUtil.easeOutCubic(tabReveal));
        if (tab == 0) drawMods(g, mouseX, mouseY, dt, contentFade, th);
        else if (tab == 1) {
            settings.draw(g, font, regions, cx, wy + (compact ? 61 : 89), cw, wy + wh - 33, dt, contentFade, th);
        }
        else waypoints.draw(g, font, regions, cx, wy + (compact ? 61 : 89), cw, wy + wh - 33, dt, contentFade, th);
        drawFooter(g, mouseX, mouseY, fade, th);
        g.pose().popMatrix();

        if (options != null) {
            g.nextStratum();
            if (!options.draw(g, wx, wy, ww, wh, dt, th)) options = null;
        }
    }

    private void drawHeader(GuiGraphics g, float dt, float fade, GuiTheme.Palette th) {
        Draw.text(g, font, TITLE_TEXT, cx, wy + (compact ? 13 : 21), Draw.alpha(th.text, fade));
        if (tab == 0) {
            int sw = searchWidth(), sx = cx + cw - sw, sy = wy + (compact ? 8 : 17);
            chip(g, sx, sy, sw, 28, searchFocused, fade, th);
            Draw.text(g, font, queryLabel, sx + 11, sy + 9, Draw.alpha(search.isEmpty() ? th.muted : th.text, fade));
            regions.add(new ClickRegion(sx, sy, sw, 28, "search", ""));
        }
        int tx = cx, ty = wy + (compact ? 34 : 55);
        for (int i = 0; i < tabLabels.length; i++) {
            Component label = tabLabels[i];
            int w = tabWidths[i];
            if (i == tab) {
                float targetX = tx + 7, targetW = w - 14;
                if (Float.isNaN(indicatorX)) {
                    indicatorX = targetX;
                    indicatorW = targetW;
                }
                indicatorX = AnimationUtil.guiApproach(indicatorX, targetX, 20F, dt, 0.05F);
                indicatorW = AnimationUtil.guiApproach(indicatorW, targetW, 20F, dt, 0.05F);
            }
            chip(g, tx, ty, w, 21, i == tab, fade, th);
            Draw.text(g, font, label, tx + 10, ty + 5, Draw.alpha(i == tab ? th.text : th.muted, fade));
            regions.add(new ClickRegion(tx, ty, w, 24, "tab", TABS[i]));
            tx += w + 6;
        }
        g.fill(cx, ty + 24, cx + cw, ty + 25, Draw.alpha(th.paint().border(), fade));
        Draw.pill(g, indicatorX, ty + 23, Math.round(indicatorW), 2, Draw.alpha(th.accent, fade));
    }

    private void drawMods(GuiGraphics g, float mx, float my, float dt, float fade, GuiTheme.Palette th) {
        int fx = cx, fy = wy + (compact ? 61 : 89);
        int filterH = compact ? 19 : 22;
        for (int i = 0; i < filterLabels.length; i++) {
            Component label = filterLabels[i];
            int fw = filterWidths[i];
            if (fx != cx && fx + fw > cx + cw) { fx = cx; fy += filterH + 3; }
            float weight = filterSelection[i] = AnimationUtil.guiApproach(filterSelection[i], i == filter ? 1F : 0F, 20F, dt, .01F);
            int panel = AnimationUtil.lerpColor(th.paint().chip(), th.paint().active(), weight);
            Draw.guiSurface(g, CHIP, fx, fy, fw, filterH, Draw.alpha(panel, fade));
            if (weight > 0.02F) Draw.pill(g, fx + 7, fy + filterH - 3, Math.round((fw - 14) * weight), 1, Draw.alpha(th.accent, fade * weight));
            Draw.text(g, font, label, fx + (compact ? 5 : 8), fy + (compact ? 4 : 6),
                    Draw.alpha(AnimationUtil.lerpColor(th.muted, th.text, weight), fade));
            regions.add(new ClickRegion(fx, fy, fw, filterH, "filter", FILTERS[i]));
            fx += fw + (compact ? 3 : 6);
        }

        gridTop = fy + filterH + 6;
        gridBottom = wy + wh - (compact ? 31 : 42);
        int viewport = gridBottom - gridTop;
        if (viewport <= 0) { maxScroll = 0; return; }
        int rows = (cards.size() + cols - 1) / cols;
        int content = Math.max(0, rows * rowH - GAP);
        maxScroll = Math.max(0, content - viewport);
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
        scroll = AnimationUtil.guiApproach(Math.min(scroll, maxScroll), scrollTarget, 23F, dt, 0.05F);
        if (cards.isEmpty()) Draw.text(g, font, filter == 1 && search.isEmpty() ? EMPTY_PINNED : EMPTY,
                cx + 2, gridTop + 8, Draw.alpha(th.muted, fade));

        int firstRow = (int) (scroll / rowH);
        int end = Math.min(cards.size(), ((int) Math.ceil((scroll + viewport) / rowH)) * cols);
        categoryReveal = AnimationUtil.reveal(categoryReveal, dt, false, 4.3F, 4.3F);
        g.enableScissor(cx, gridTop, cx + cw, gridBottom);
        int roundedScroll = Math.round(scroll);
        float fraction = roundedScroll - scroll;
        g.pose().pushMatrix().translate(0F, fraction);
        for (int i = firstRow * cols; i < end; i++) {
            Card card = cards.get(i);
            float stagger = Math.min(3, Math.max(0, i / cols - firstRow)) * .085F;
            float arrival = AnimationUtil.easeOutCubic((categoryReveal - stagger) / (1F - stagger));
            card.x = cx + (i % cols) * (cardW + GAP)
                    + Math.round(AnimationUtil.slide((1F - arrival) * categoryDirection * 16F));
            card.y = gridTop + (i / cols) * rowH - roundedScroll;
            if (card.y + cardH + fraction <= gridTop) continue;
            boolean hover = options == null && mx >= card.x && mx < card.x + cardW && my >= gridTop && my < gridBottom
                    && my >= card.y + fraction && my < card.y + cardH + fraction;
            drawCard(g, card, hover, dt, fade * (.20F + .80F * arrival), th);
            visible.add(card);
        }
        g.pose().popMatrix();
        g.disableScissor();

        if (maxScroll > 0) {
            int thumb = Math.min(viewport, Math.max(18, viewport * viewport / content));
            int sx = cx + cw - 4;
            Draw.pill(g, sx, gridTop, 3, viewport, Draw.alpha(th.paint().border(), fade));
            Draw.pill(g, sx, gridTop + scroll / maxScroll * (viewport - thumb), 3, thumb, Draw.alpha(th.accent, fade));
        }
    }

    private void drawCard(GuiGraphics g, Card card, boolean hover, float dt, float fade, GuiTheme.Palette th) {
        int x = card.x, y = card.y;
        boolean descriptions = GuiConfig.appearance().descriptions;
        // Measure each module only when it enters the viewport. Filters and sorting reuse that work.
        if (card.name == null) card.name = Draw.fit(font, card.module.name, STRONG, cardW - 83);
        if (descriptions && card.desc == null) card.desc = Draw.fit(font, card.module.description, BODY, cardW - 24);
        card.hover = AnimationUtil.guiApproach(card.hover, hover ? 1F : 0F, 19F, dt, 0.003F);
        card.enabled = AnimationUtil.guiApproach(card.enabled, card.module.enabled ? 1F : 0F, 21F, dt, 0.003F);
        float lift = AnimationUtil.slide(card.hover * -1.5F);
        if (lift != 0F) g.pose().pushMatrix().translate(0F, lift);
        int base = AnimationUtil.lerpColor(th.paint().card(), th.paint().selected(), card.enabled);
        base = AnimationUtil.lerpColor(base, th.paint().hover(), card.hover);
        Draw.guiSurface(g, CARD, x, y, cardW, cardH, Draw.alpha(base, fade));
        int sy = y + (compact && descriptions ? 6 : 9);
        Draw.text(g, font, card.name, x + 12, sy + 4, Draw.alpha(th.text, fade));
        if (descriptions) Draw.text(g, font, card.desc, x + 12, y + (compact ? 26 : 36), Draw.alpha(th.muted, fade));
        Draw.text(g, font, STAR, x + cardW - 64, sy + 5,
                Draw.alpha(GuiConfig.favorite(card.module.id) ? th.accent : th.muted, fade));
        drawSwitch(g, x + cardW - 48, sy, card.enabled, fade, th);
        if (lift != 0F) g.pose().popMatrix();
    }

    static void drawSwitch(GuiGraphics g, int x, int y, float v, float fade, GuiTheme.Palette th) {
        Draw.pill(g, x, y, 36, 18, Draw.alpha(AnimationUtil.lerpColor(th.paint().track(), th.accent, v), fade));
        Draw.pill(g, x + 2 + 18F * v, y + 2, 14, 14, Draw.alpha(th.paint().thumb(), fade));
    }

    static void chip(GuiGraphics g, int x, int y, int w, int h, boolean active, float fade, GuiTheme.Palette th) {
        int color = active ? th.paint().active() : th.paint().chip();
        Draw.guiSurface(g, CHIP, x, y, w, h, Draw.alpha(color, fade));
        if (active) Draw.pill(g, x + 8, y + h - 3, w - 16, 1, Draw.alpha(th.accent, fade * 0.8F));
    }

    private void drawFooter(GuiGraphics g, float mx, float my, float fade, GuiTheme.Palette th) {
        int y = wy + wh - (compact ? 27 : 32), bx = cx + cw - 87;
        boolean hover = mx >= bx && mx < bx + 87 && my >= y && my < y + 23;
        chip(g, bx, y, 87, 23, hover, fade, th);
        Draw.text(g, font, "HUD Layout", STRONG, bx + 11, y + 6, Draw.alpha(th.text, fade));
        Draw.text(g, font, HINT, cx, y + 6, Draw.alpha(th.muted, fade));
        regions.add(new ClickRegion(bx, y, 87, 23, "hud", ""));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (closing || event.button() != 0) return true;
        float centerX = wx + ww / 2F, centerY = wy + wh / 2F;
        double x = (event.x() - centerX) / windowScale + centerX;
        double y = (event.y() - offsetY - centerY) / windowScale + centerY;
        if (options != null) { options.click(event.x(), event.y()); return true; }
        for (ClickRegion r : regions) {
            if (!r.contains(x, y)) continue;
            searchFocused = r.action().equals("search");
            waypoints.focus(r.action().equals("place_name"));
            switch (r.action()) {
                case "tab" -> {
                    int next = indexOf(TABS, r.data());
                    if (next == 2 && tab != 2) waypoints.enter();
                    if (tab != next) tabReveal = 0;
                    tab = next;
                }
                case "filter" -> {
                    int next = indexOf(FILTERS, r.data());
                    if (next == filter) break;
                    categoryDirection = next > filter ? 1 : -1;
                    categoryReveal = 0;
                    filter = next;
                    scroll = scrollTarget = 0;
                    rebuildCards();
                }
                case "theme" -> GuiConfig.setTheme(r.data());
                case "settings_page" -> settings.select(Integer.parseInt(r.data()));
                case "look" -> {
                    GuiConfig.change(r.data());
                    if (r.data().equals("density") || r.data().equals("descriptions")) init();
                }
                case "adjust_down", "adjust_up" -> {
                    GuiConfig.adjust(r.data(), r.action().equals("adjust_up") ? 1 : -1);
                    if (r.data().startsWith("window_")) init();
                }
                case "reset_look" -> { GuiConfig.resetAppearance(); init(); }
                case "sort" -> { GuiConfig.cycleSort(); scroll = scrollTarget = 0F; rebuildCards(); }
                case "reset_sort" -> { GuiConfig.resetSort(); scroll = scrollTarget = 0F; rebuildCards(); }
                case "preset_save" -> GuiConfig.savePreset(Integer.parseInt(r.data()));
                case "preset_load" -> { if (GuiConfig.loadPreset(Integer.parseInt(r.data()))) init(); }
                case "place_save" -> waypoints.save();
                case "place_copy" -> waypoints.copy(r.data());
                case "place_delete" -> waypoints.remove(r.data());
                case "hud" -> LuxisClientMod.openHudEditor();
                default -> { }
            }
            return true;
        }
        searchFocused = false;
        waypoints.focus(false);
        if (tab != 0 || y < gridTop || y >= gridBottom || x < cx || x > cx + cw) return true;
        float fraction = Math.round(scroll) - scroll;
        for (Card card : visible) {
            float top = card.y + fraction;
            if (x < card.x || x >= card.x + cardW || y < top || y >= top + cardH) continue;
            if (x >= card.x + cardW - 70 && x < card.x + cardW - 50 && y < top + 28) {
                GuiConfig.toggleFavorite(card.module.id);
                if (filter == 1) rebuildCards();
                return true;
            }
            if (x >= card.x + cardW - 50 && y < top + (compact ? 28 : 35)) {
                card.module.enabled = !card.module.enabled;
                if (card.module.enabled) card.module.onEnable(); else card.module.onDisable();
                LuxisClientMod.save();
                if (filter == 7 || GuiConfig.sort() == GuiConfig.Sort.Enabled) rebuildCards();
            } else options = new ModuleOptionsOverlay(card.module, font);
            break;
        }
        return true;
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return 0;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return options != null && event.button() == 0 && options.drag(event.x());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return options != null && event.button() == 0 && options.release();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double ax, double ay) {
        if (closing) return true;
        if (options != null) options.scroll(ay);
        else if (tab == 0) scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) ay * 38F));
        else if (tab == 1) settings.scroll(ay);
        else if (tab == 2) waypoints.scroll(ay);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing) return true;
        if (options != null && options.captureKey(event)) return true;
        if (event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_RSHIFT) {
            if (options != null) options.close();
            else onClose();
        } else if (options == null && event.hasControlDown() && event.key() == InputConstants.KEY_F) {
            if (tab != 0) tabReveal = 0;
            tab = 0;
            searchFocused = true;
            waypoints.focus(false);
        } else if (options == null && tab == 0 && searchFocused) {
            if (event.key() == InputConstants.KEY_DELETE) setQuery("");
            else if (!search.isEmpty() && event.key() == InputConstants.KEY_BACKSPACE) {
                setQuery(search.substring(0, search.offsetByCodePoints(search.length(), -1)));
            }
        } else if (options == null && tab == 2 && waypoints.focused()) {
            if (event.key() == InputConstants.KEY_BACKSPACE) waypoints.backspace(font);
            else if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) waypoints.save();
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!closing && options != null && event.isAllowedChatCharacter() && options.type(event.codepointAsString())) return true;
        if (!closing && options == null && tab == 0 && searchFocused && search.length() < 48 && event.isAllowedChatCharacter()) {
            setQuery(search + event.codepointAsString());
        } else if (!closing && options == null && tab == 2 && waypoints.focused() && event.isAllowedChatCharacter()) {
            waypoints.type(event.codepointAsString(), font);
        }
        return true;
    }

    private void setQuery(String query) {
        search = query;
        scroll = scrollTarget = 0F;
        updateQueryLabel();
        rebuildCards();
    }

    @Override
    public void onClose() { closing = true; }

    @Override
    public void removed() {
        if (options != null) options.commit();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
