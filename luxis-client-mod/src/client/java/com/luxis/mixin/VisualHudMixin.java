package com.luxis.mixin;

import com.luxis.module.impl.VisualModules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
abstract class VisualHudMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 1)
    private void luxis$guides(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        var camera = mc.getCameraEntity();
        if (mc.level != null && camera != null && mc.gui.screen() == null) {
            VisualModules.drawGuides(g.guiWidth(), g.guiHeight(), camera.getXRot(), g::fill);
        }
    }

    @Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true, require = 1)
    private void luxis$boss(CallbackInfo ci) { if (VisualModules.BOSS.enabled) ci.cancel(); }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 1)
    private void luxis$scoreboard(CallbackInfo ci) { if (VisualModules.SCOREBOARD.enabled) ci.cancel(); }

    @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true, require = 1)
    private void luxis$titles(CallbackInfo ci) { if (VisualModules.TITLES.enabled) ci.cancel(); }

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true, require = 1)
    private void luxis$action(CallbackInfo ci) { if (VisualModules.ACTION.enabled) ci.cancel(); }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true, require = 1)
    private void luxis$item(CallbackInfo ci) { if (VisualModules.ITEM.enabled) ci.cancel(); }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true, require = 1)
    private void luxis$effects(CallbackInfo ci) { if (VisualModules.EFFECTS.enabled) ci.cancel(); }
}
