package com.luxis.mixin;

import com.luxis.module.impl.ViewControls;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
abstract class FullbrightMixin {
    @Inject(method = "extract", at = @At("TAIL"), require = 1)
    private void luxis$ambient(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        float floor = ViewControls.fullbright();
        if (floor == 0 || !state.needsUpdate || state.ambientColor == null) return;
        var ambient = new Vector3f(state.ambientColor);
        state.ambientColor = ambient.max(new Vector3f(floor));
        state.darknessEffectScale *= 1F - floor;
        state.bossOverlayWorldDarkening *= 1F - floor;
    }
}
