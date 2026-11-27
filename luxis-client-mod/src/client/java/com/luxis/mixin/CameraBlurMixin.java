package com.luxis.mixin;

import com.luxis.client.CameraBlur;
import com.luxis.module.impl.VisualModules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(GameRenderer.class)
abstract class CameraBlurMixin {
    @ModifyVariable(method = "preparePostEffects", at = @At("HEAD"), argsOnly = true, require = 1)
    private List<Identifier> luxis$effects(List<Identifier> requested) {
        Minecraft mc = Minecraft.getInstance();
        Identifier effect = CameraBlur.select(mc);
        boolean lens = mc.level != null && mc.player != null && VisualModules.hasWorldEffect();
        if (effect == null && !lens) return requested;
        List<Identifier> combined = new ArrayList<>(requested);
        if (effect != null) combined.add(effect);
        if (lens) combined.add(VisualModules.EFFECT);
        return combined;
    }
}
