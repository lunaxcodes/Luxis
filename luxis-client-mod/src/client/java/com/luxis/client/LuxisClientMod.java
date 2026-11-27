package com.luxis.client;

import com.luxis.config.ProfileManager;
import com.luxis.gui.HudEditorScreen;
import com.luxis.gui.LuxisModScreen;
import com.luxis.config.GuiConfig;
import com.luxis.config.WaypointStore;
import com.luxis.module.HudModule;
import com.luxis.module.ModuleRegistry;
import com.luxis.module.ModuleKeys;
import com.luxis.module.impl.ViewControls;
import com.luxis.gui.Draw;
import com.luxis.module.impl.CpsHudModule;
import com.luxis.module.impl.ClientTuning;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Client entrypoint for Luxis Client Mod. */
public final class LuxisClientMod implements ClientModInitializer {
    public static ProfileManager profiles;
    public static KeyMapping openMenuKey;
    private static boolean menuKeyWasDown = false;
    private static int readyTicks = 0;

    @Override
    public void onInitializeClient() {
        var config = FabricLoader.getInstance().getGameDir().resolve("config").resolve("luxis");
        GuiConfig.init(config);
        WaypointStore.init(config);
        profiles = new ProfileManager();
        profiles.apply(profiles.activeName(), ModuleRegistry.all());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ClientTuning.restore();
            GuiConfig.finishWrites();
            profiles.finishWrites();
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath("luxis", "utilities"),
                (g, delta) -> renderHud(g));

        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("luxis", "category"));
        openMenuKey = new KeyMapping("key.luxis.open_menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, category);
        KeyMappingHelper.registerKeyMapping(openMenuKey);

        final boolean[] applied = { false };
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!applied[0] && client.options != null) {
                applied[0] = true;
                ModuleRegistry.applyEnabledStates();
            }
            CpsHudModule.resetTick();
            if (!inGame(client)) ModuleKeys.reset();
            ClientTuning.tick(client);
            readyTicks++;
            boolean menuDown = InputConstants.isKeyDown(InputConstants.KEY_RSHIFT) || openMenuKey.isDown();
            if (readyTicks > 80 && menuDown && !menuKeyWasDown && client.level != null && client.gui.screen() == null) {
                client.gui.setScreen(new LuxisModScreen());
            }
            menuKeyWasDown = menuDown;
        });
    }

    public static boolean inGame(Minecraft mc) {
        return mc.player != null && mc.gui.screen() == null && mc.isWindowActive();
    }

    private static void renderHud(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() instanceof HudEditorScreen) return;
        var utilities = ModuleRegistry.liveUtilities();
        for (int i = 0; i < utilities.size(); i++) {
            HudModule m = utilities.get(i);
            if (m.enabled) m.renderHud(graphics, 0F);
        }
        if (ViewControls.isZoomActive() && Boolean.TRUE.equals(ModuleRegistry.byId("zoom").get("overlay")))
            Draw.text(graphics, mc.font, "ZOOM", Draw.Face.STRONG, graphics.guiWidth() / 2 - 13,
                    graphics.guiHeight() / 2 + 19, 0xFFE4E4E4);
    }

    public static ProfileManager profiles() { return profiles; }

    public static void save() {
        ClientTuning.changed();
        profiles.saveAsync(profiles.activeName(), ModuleRegistry.all());
    }

    public static void openHudEditor() {
        Minecraft.getInstance().gui.setScreen(new HudEditorScreen());
    }

    /** Called from MouseHandlerMixin for CPS tracking. */
    public static void onMouseClick(int button) {
        CpsHudModule.click(button);
    }
}
