package com.tac5studios.elementseconomy.mixin;

import com.tac5studios.elementseconomy.shop.ShopGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets shop customers reach a shop screen before any mod handles the click (claim mods included),
 * when the shop's owner switched on claim access. Nothing else about the click changes:
 * the shop container is never opened or touched, the screen is Economy's own menu.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void economy$shopClick(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                   BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (ShopGuard.earlyClick(player, hit.getBlockPos(), hand)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
