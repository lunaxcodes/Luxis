package com.luxis.mixin;

import com.luxis.client.LuxisClientMod;
import com.luxis.module.impl.ViewControls;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
abstract class ViewCameraMixin {
    @ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1), require = 1)
    private void luxis$freelook(Args rotation) {
        Minecraft mc = Minecraft.getInstance();
        if (LuxisClientMod.inGame(mc) && mc.getCameraEntity() == mc.player && ViewControls.isFreelookActive()) {
            rotation.set(0, ViewControls.yaw());
            rotation.set(1, ViewControls.pitch());
        }
    }
}
