package com.luxis.mixin;

import com.luxis.client.ReachRing;
import com.luxis.module.impl.ClientTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class ReachRingMixin {
    @Shadow public abstract Gizmos.TemporaryCollection collectPerFrameGizmos();

    @Inject(method = "finalizeGizmoCollection", at = @At("HEAD"))
    private void luxis$ring(CallbackInfo ci) {
        if (!ClientTuning.radiusEnabled()) return;
        try (var ignored = collectPerFrameGizmos()) {
            ReachRing.draw(Minecraft.getInstance());
        }
    }
}
