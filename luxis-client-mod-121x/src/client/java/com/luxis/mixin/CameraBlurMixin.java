package com.luxis.mixin;

import com.luxis.client.CameraBlur;
import com.luxis.module.impl.VisualModules;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Set;

@Mixin(GameRenderer.class)
abstract class CameraBlurMixin {
    @Shadow private Identifier postEffectId;
    @Shadow private boolean effectActive;
    @Shadow @Final private CrossFrameResourcePool resourcePool;
    @Unique private Identifier luxis$effect;
    @Unique private boolean luxis$originalActive;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
    private void luxis$prepare(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && VisualModules.hasWorldEffect()) {
            PostChain lens = mc.getShaderManager().getPostChain(VisualModules.EFFECT, Set.of(PostChain.MAIN_TARGET_ID));
            if (lens != null) lens.process(mc.getMainRenderTarget(), resourcePool);
        }
        Identifier effect = CameraBlur.select(mc);
        if (effect == null || postEffectId != null) return;
        luxis$effect = effect;
        luxis$originalActive = effectActive;
        postEffectId = effect;
        effectActive = true;
    }

    @Inject(method = "render", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PostChain;process(Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;)V", shift = At.Shift.AFTER),
            @At("RETURN")
    })
    private void luxis$release(CallbackInfo ci) {
        if (luxis$effect != null && postEffectId == luxis$effect) {
            postEffectId = null;
            effectActive = luxis$originalActive;
        }
        luxis$effect = null;
    }
}
