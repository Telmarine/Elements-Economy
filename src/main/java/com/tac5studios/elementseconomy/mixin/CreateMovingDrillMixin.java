package com.tac5studios.elementseconomy.mixin;

import com.tac5studios.elementseconomy.shop.ShopGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drills and saws on moving Create contraptions can't break shops or linked stock vaults.
 * Skipped when Create isn't installed.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour")
public abstract class CreateMovingDrillMixin {

    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void economy$keepShops(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (ShopGuard.isProtected(level, pos)) cir.setReturnValue(false);
    }
}
