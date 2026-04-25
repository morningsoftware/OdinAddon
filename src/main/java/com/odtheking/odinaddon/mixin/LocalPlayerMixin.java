package com.odtheking.odinaddon.mixin;

import com.odtheking.odinaddon.features.impl.skyblock.QuickWarp;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(
            method = "swing",
            at = @At("HEAD"),
            cancellable = true
    ) private void cancelSwing(InteractionHand interactionHand, CallbackInfo ci) {
        if (QuickWarp.INSTANCE.shouldSuppressLeftClick())
            ci.cancel();
    }
}
