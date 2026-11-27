package com.luxis.mixin;

import com.luxis.module.impl.ClientTuning;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class HandViewMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void luxis$offhand(AbstractClientPlayer player, float delta, float pitch,
                               InteractionHand hand, float swing, ItemStack item, float equip, PoseStack pose,
                               SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (ClientTuning.handEnabled() && hand == InteractionHand.OFF_HAND && ClientTuning.hideOffhand()) ci.cancel();
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
    private void luxis$pose(AbstractClientPlayer player, float delta, float pitch,
                            InteractionHand hand, float swing, ItemStack item, float equip, PoseStack pose,
                            SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (!ClientTuning.handEnabled()) return;
        pose.translate(ClientTuning.handX(), ClientTuning.handY(), 0F);
        float scale = ClientTuning.handScale();
        pose.scale(scale, scale, scale);
    }
}
