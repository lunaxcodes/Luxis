package com.luxis.mixin;

import com.luxis.module.impl.ViewControls;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LightTexture.class)
abstract class FullbrightMixin {
    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 0), require = 1)
    private float luxis$ambientFactor(float original) {
        return Math.max(original, ViewControls.fullbright());
    }

    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 1), require = 1)
    private org.joml.Vector3fc luxis$ambientColor(org.joml.Vector3fc original) {
        return ViewControls.fullbright() > 0F ? new Vector3f(1F) : original;
    }
}
