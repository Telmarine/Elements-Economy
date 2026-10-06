package com.tac5studios.elementseconomy.integration;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.shop.ShopGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Stops Create contraptions from picking up shops and linked stock vaults (a contraption would carry
 * the stock away or leave the shop pointing at an empty spot). Registered through Create's own
 * BlockMovementChecks API by reflection, so Economy has no hard dependency on Create.
 */
public final class CreateGuard {

    private static boolean done;

    private CreateGuard() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static synchronized void register() {
        if (done || !ModList.get().isLoaded("create")) return;
        done = true;
        try {
            Class<?> checks = Class.forName("com.simibubi.create.api.contraption.BlockMovementChecks");
            Class<?> allowed = Class.forName("com.simibubi.create.api.contraption.BlockMovementChecks$MovementAllowedCheck");
            Class<? extends Enum> result = (Class<? extends Enum>) Class.forName("com.simibubi.create.api.contraption.BlockMovementChecks$CheckResult");
            Object fail = Enum.valueOf(result, "FAIL");
            Object pass = Enum.valueOf(result, "PASS");
            Object check = Proxy.newProxyInstance(allowed.getClassLoader(), new Class<?>[]{allowed}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> "ElementsEconomy shop guard";
                    };
                }
                // isMovementAllowed(BlockState state, Level world, BlockPos pos)
                if (args != null && args.length == 3 && args[1] instanceof Level level && args[2] instanceof BlockPos pos
                        && ShopGuard.isProtected(level, pos)) {
                    return fail;
                }
                return pass;
            });
            Method register = checks.getMethod("registerMovementAllowedCheck", allowed);
            register.invoke(null, check);
            ElementsEconomy.LOGGER.info("[Economy] Create found: contraptions can't move shops or linked stock vaults.");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            ElementsEconomy.LOGGER.warn("[Economy] Create found, but its movement checks could not be hooked: {}", ex.toString());
        }
    }
}
