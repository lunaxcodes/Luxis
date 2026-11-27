package com.luxis.mixin;

import com.luxis.module.impl.ClientTuning;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
abstract class ExplosionParticleMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void luxis$limit(ParticleOptions option, double x, double y, double z, double vx, double vy, double vz,
                             CallbackInfoReturnable<Particle> cir) {
        var type = option.getType();
        if (type == ParticleTypes.EXPLOSION_EMITTER && ClientTuning.dropExplosion(true)
                || type == ParticleTypes.EXPLOSION && ClientTuning.dropExplosion(false)) cir.setReturnValue(null);
    }
}
