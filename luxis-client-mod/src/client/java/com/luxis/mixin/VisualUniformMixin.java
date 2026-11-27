package com.luxis.mixin;

import com.luxis.module.impl.VisualModules;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(PostPass.class)
abstract class VisualUniformMixin {
    @Shadow @Final private Map<String, GpuBuffer> customUniforms;
    @Unique private MappableRingBuffer luxis$lensBuffers;

    @Inject(method = "addToFrame", at = @At("HEAD"), require = 1)
    private void luxis$uploadLens(CallbackInfo ci) {
        if (!customUniforms.containsKey(VisualModules.UNIFORM)) return;
        boolean first = luxis$lensBuffers == null;
        if (first) luxis$lensBuffers = new MappableRingBuffer(() -> "Luxis lens settings",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, VisualModules.UNIFORM_BYTES);
        GpuBuffer buffer = luxis$lensBuffers.currentBuffer();
        try (var mapped = buffer.map(false, true)) {
            VisualModules.writeUniform(mapped.data());
        }
        GpuBuffer previous = customUniforms.put(VisualModules.UNIFORM, buffer);
        if (first) previous.close();
        luxis$lensBuffers.rotate();
    }

    @Inject(method = "close", at = @At("HEAD"), require = 1)
    private void luxis$releaseLens(CallbackInfo ci) {
        if (luxis$lensBuffers == null) return;
        customUniforms.remove(VisualModules.UNIFORM);
        luxis$lensBuffers.close();
        luxis$lensBuffers = null;
    }
}
