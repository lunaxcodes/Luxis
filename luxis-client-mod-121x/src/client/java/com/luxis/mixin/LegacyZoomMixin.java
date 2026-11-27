package com.luxis.mixin;

import com.luxis.client.LuxisClientMod;
import com.luxis.module.impl.ViewControls;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
abstract class LegacyZoomMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 1)
    private void luxis$zoom(Camera camera, float delta, boolean worldFov, CallbackInfoReturnable<Float> ci) {
        if (worldFov && LuxisClientMod.inGame(Minecraft.getInstance())) ci.setReturnValue(ViewControls.adjustFov(ci.getReturnValue()));
    }
}
