package com.luxis.mixin;

import com.luxis.client.LuxisClientMod;
import com.luxis.module.ModuleKeys;
import com.luxis.module.impl.ViewControls;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class ModuleMouseMixin {
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), require = 1)
    private void luxis$cameraOnly(LocalPlayer player, double dx, double dy) {
        if (!LuxisClientMod.inGame(Minecraft.getInstance()) || !ViewControls.move(dx, dy)) player.turn(dx, dy);
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 1)
    private void luxis$zoomScroll(long window, double x, double y, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (window == mc.getWindow().handle() && LuxisClientMod.inGame(mc) && ViewControls.scroll(y)) ci.cancel();
    }

    @Inject(method = "onButton", at = @At("HEAD"), require = 1)
    private void luxis$mouseKeys(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().handle()) return;
        boolean playing = LuxisClientMod.inGame(mc);
        if (playing && action == 1) LuxisClientMod.onMouseClick(button.button());
        ModuleKeys.handle(InputConstants.Type.MOUSE.getOrCreate(button.button()), action, mc.player, playing);
    }
}
