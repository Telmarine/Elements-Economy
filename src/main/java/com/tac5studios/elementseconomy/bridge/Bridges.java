package com.tac5studios.elementseconomy.bridge;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Bridges to other money systems: Elements: Economy becomes their money provider, so mods built on them
 * use Elements: Economy money without knowing about it.
 *  - Impactor: suggested as Impactor's economy service (AH Plus, Cobblemon mods, and SDM Economy 2.x,
 *    which lists every Impactor currency as an SDM currency).
 *  - OctoEconomy (Eights Economy P, Shoppy): set as the economy in Eights' provider event.
 *
 * Each bridge class touches the other mod's classes, so it is only loaded when that mod is installed.
 */
public final class Bridges {

    public static final String IMPACTOR = "impactor";
    public static final String EIGHTS = "eights_economy_p";

    private static boolean impactorActive;

    private Bridges() {}

    /** Called from the mod constructor: subscribe to the other mods' setup events. */
    public static void init() {
        ModList mods = ModList.get();
        if (mods.isLoaded(IMPACTOR)) {
            try {
                com.tac5studios.elementseconomy.bridge.impactor.ImpactorBridge.register();
            } catch (LinkageError | RuntimeException ex) {
                ElementsEconomy.LOGGER.warn("[Economy] Impactor bridge could not start: {}", ex.toString());
            }
        }
        if (mods.isLoaded(EIGHTS)) {
            try {
                com.tac5studios.elementseconomy.bridge.octo.OctoBridge.register();
            } catch (LinkageError | RuntimeException ex) {
                ElementsEconomy.LOGGER.warn("[Economy] OctoEconomy bridge could not start: {}", ex.toString());
            }
        }
    }

    /** Impactor picked Elements: Economy as its economy service. */
    public static void impactorActive() {
        impactorActive = true;
    }

    /**
     * True when Elements: Economy is (or will be) that mod's money provider. Its own balances are then
     * Elements: Economy balances, so currency detection skips it.
     */
    public static boolean providing(String modId) {
        if (!Features.on(Features.BRIDGES)) return false;
        return switch (modId) {
            case IMPACTOR -> impactorActive && Features.on(Features.BRIDGES, Features.BRIDGE_IMPACTOR);
            case EIGHTS -> ModList.get().isLoaded(EIGHTS) && Features.on(Features.BRIDGES, Features.BRIDGE_OCTO);
            default -> false;
        };
    }

    // ---------- shared helpers ----------

    /** Run on the server thread (other mods may call from their own threads). */
    public static <T> T onServer(Supplier<T> work) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.isSameThread()) return work.get();
        // 0 = waiting, 1 = running, 2 = given up. A call that timed out before it started never runs,
        // so the caller is never told "failed" while money still moves later.
        AtomicInteger state = new AtomicInteger();
        java.util.concurrent.CompletableFuture<T> future = server.submit(() -> state.compareAndSet(0, 1) ? work.get() : null);
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            if (state.compareAndSet(0, 2)) {
                throw new IllegalStateException("Economy call timed out on the server thread; nothing was changed", timeout);
            }
            try {
                return future.get(60, TimeUnit.SECONDS); // already running: wait for the real result
            } catch (TimeoutException stuck) {
                ElementsEconomy.LOGGER.error("[Economy] A money call from another mod has been running on the server thread for over a minute.");
                throw new IllegalStateException("Economy call still running on the server thread", stuck);
            } catch (Exception ex) {
                throw new IllegalStateException("Economy call failed on the server thread", ex);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Economy call failed on the server thread", ex);
        }
    }

    /** 12.50 -> 1250 with 2 decimals. Empty-safe: null or negative gives -1. */
    public static BigInteger toUnits(BigDecimal amount, int decimals) {
        if (amount == null || amount.signum() < 0) return BigInteger.ONE.negate();
        return amount.movePointRight(decimals).setScale(0, RoundingMode.DOWN).toBigInteger();
    }

    public static BigInteger toUnits(double amount, int decimals) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0) return BigInteger.ONE.negate();
        return toUnits(BigDecimal.valueOf(amount), decimals);
    }

    public static BigDecimal fromUnits(BigInteger units, int decimals) {
        return new BigDecimal(units).movePointLeft(decimals);
    }
}
