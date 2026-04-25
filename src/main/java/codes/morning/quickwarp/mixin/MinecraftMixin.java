package codes.morning.quickwarp.mixin;

import codes.morning.quickwarp.features.impl.skyblock.QuickWarp;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void cancelQuickWarpAttack(CallbackInfoReturnable<Boolean> cir) {
        if (QuickWarp.INSTANCE.shouldSuppressLeftClick()) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void cancelQuickWarpAttackHold(boolean isAttacking, CallbackInfo ci) {
        if (isAttacking && QuickWarp.INSTANCE.shouldSuppressLeftClick()) ci.cancel();
    }
}