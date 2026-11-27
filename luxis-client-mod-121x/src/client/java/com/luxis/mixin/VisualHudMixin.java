package com.luxis.mixin;

import com.luxis.module.impl.VisualModules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class VisualHudMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void luxis$guides(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        var camera = mc.getCameraEntity();
        if (mc.level != null && camera != null && mc.screen == null) {
            VisualModules.drawGuides(g.guiWidth(), g.guiHeight(), camera.getXRot(), g::fill);
        }
    }

    @Inject(method = "renderBossOverlay", at = @At("HEAD"), cancellable = true, require = 1)
    private void luxis$boss(CallbackInfo ci) { if (VisualModules.BOSS.enabled) ci.cancel(); }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 1)
    private void luxis$scoreboard(CallbackInfo ci) { if (VisualModules.SCOREBOARD.enabled) ci.cancel(); }

    @Inject(method = "renderTitle", at = @At("HEAD"), cancellable = true, require = 1)
    private void luxis$titles(CallbackInfo ci) { if (VisualModules.TITLES.enabled) ci.cancel(); }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true, require = 1)
    private void luxis$action(CallbackInfo ci) { if (VisualModules.ACTION.enabled) ci.cancel(); }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true, require = 1)
    private void luxis$item(CallbackInfo ci) { if (VisualModules.ITEM.enabled) ci.cancel(); }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true, require = 1)
    private void luxis$effects(CallbackInfo ci) { if (VisualModules.EFFECTS.enabled) ci.cancel(); }
}
