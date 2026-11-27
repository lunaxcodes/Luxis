package com.luxis.mixin;

import com.luxis.module.impl.ClientTuning;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystalRenderer.class)
abstract class CrystalRenderMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V", at = @At("RETURN"))
    private void luxis$details(EndCrystal crystal, EndCrystalRenderState state, float partialTick, CallbackInfo ci) {
        if (ClientTuning.trimCrystalBase()) state.showsBottom = false;
        if (ClientTuning.trimCrystalBeam()) state.beamOffset = null;
    }
}
