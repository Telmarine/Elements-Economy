package com.tac5studios.elementseconomy.mixin;

import com.tac5studios.elementseconomy.shop.ShopGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * Create drills and saws (placed blocks) can't break shops or linked stock vaults.
 * Create breaks blocks without a player, so no break event fires. Skipped when Create isn't installed;
 * if a Create update renames things, the hook simply does nothing instead of crashing.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity")
public abstract class CreateDrillMixin {

    private static Field economy$breakingPos;
    private static boolean economy$looked;

    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void economy$keepShops(BlockState state, float hardness, CallbackInfoReturnable<Boolean> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        BlockPos pos = economy$pos(self);
        if (pos != null && ShopGuard.isProtected(self.getLevel(), pos)) {
            cir.setReturnValue(false);
        }
    }

    private static BlockPos economy$pos(Object self) {
        if (!economy$looked) {
            economy$looked = true;
            for (Class<?> c = self.getClass(); c != null && economy$breakingPos == null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField("breakingPos");
                    f.setAccessible(true);
                    economy$breakingPos = f;
                } catch (NoSuchFieldException ignored) {
                    // keep looking up the hierarchy
                } catch (RuntimeException ignored) {
                    break;
                }
            }
        }
        try {
            return economy$breakingPos == null ? null : (BlockPos) economy$breakingPos.get(self);
        } catch (IllegalAccessException | RuntimeException e) {
            return null;
        }
    }
}
