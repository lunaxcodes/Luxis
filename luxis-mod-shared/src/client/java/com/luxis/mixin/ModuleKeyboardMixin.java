package com.luxis.mixin;

import com.luxis.client.LuxisClientMod;
import com.luxis.module.ModuleKeys;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class ModuleKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), require = 1)
    private void luxis$moduleKeys(long window, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (window == mc.getWindow().handle()) {
            ModuleKeys.handle(InputConstants.getKey(event), action, mc.player, LuxisClientMod.inGame(mc));
        }
    }
}
