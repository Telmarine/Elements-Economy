package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Holding;
import com.tac5studios.elementsvault.Result;
import com.tac5studios.elementsvault.backend.CurrencyBackend;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Base for adapters that reach another mod by reflection.
 * Subclasses read and write raw balances; this class adds the checks, results and
 * the safety switch: the first failure turns the adapter off for the session and logs why,
 * so a changed mod can never crash the server or lose money silently.
 */
public abstract class ReflectBackend implements CurrencyBackend {

    protected final ExternalCurrency currency;
    private volatile boolean broken;

    protected ReflectBackend(ExternalCurrency currency) {
        this.currency = currency;
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath("elements_economy", "adapter_" + currency.sourceMod());
    }

    @Override
    public Currency currency() {
        return currency;
    }

    @Override
    public final boolean isAvailable() {
        if (broken) return false;
        try {
            return ready();
        } catch (RuntimeException | LinkageError e) {
            fail("start check", e);
            return false;
        }
    }

    /** True when the mod's classes are there and it can be used right now. */
    protected abstract boolean ready();

    /** Raw balance in the smallest unit. Null when it can't be read (e.g. player offline). */
    protected abstract BigInteger read(UUID player);

    /** Write a new balance. False when it can't be written now (e.g. player offline). */
    protected abstract boolean write(UUID player, BigInteger amount);

    /** Optional: list balances (only when the currency has LIST_ALL). */
    protected List<Holding> list() {
        return List.of();
    }

    // ---------- CurrencyBackend ----------

    @Override
    public BigInteger balance(UUID player) {
        if (broken) return BigInteger.ZERO;
        try {
            BigInteger v = read(player);
            return v == null ? BigInteger.ZERO : v.max(BigInteger.ZERO);
        } catch (RuntimeException | LinkageError e) {
            fail("read", e);
            return BigInteger.ZERO;
        }
    }

    @Override
    public Result withdraw(UUID player, BigInteger amount, Cause cause) {
        if (broken) return error(amount);
        try {
            BigInteger before = read(player);
            if (before == null) return offline(amount);
            if (before.compareTo(amount) < 0) {
                return Result.fail(Result.Reason.INSUFFICIENT_FUNDS, currency.of(amount), Component.literal("Not enough money"));
            }
            BigInteger after = before.subtract(amount);
            if (!write(player, after)) return offline(amount);
            return Result.ok(currency.of(amount), currency.of(before), currency.of(after));
        } catch (RuntimeException | LinkageError e) {
            fail("withdraw", e);
            return error(amount);
        }
    }

    @Override
    public Result deposit(UUID player, BigInteger amount, Cause cause) {
        if (broken) return error(amount);
        try {
            BigInteger before = read(player);
            if (before == null) return offline(amount);
            BigInteger after = before.add(amount);
            if (!write(player, after)) return offline(amount);
            return Result.ok(currency.of(amount), currency.of(before), currency.of(after));
        } catch (RuntimeException | LinkageError e) {
            fail("deposit", e);
            return error(amount);
        }
    }

    @Override
    public Result set(UUID player, BigInteger amount, Cause cause) {
        if (broken) return error(amount);
        try {
            BigInteger before = read(player);
            if (before == null) return offline(amount);
            if (!write(player, amount)) return offline(amount);
            return Result.ok(currency.of(amount.subtract(before).abs()), currency.of(before), currency.of(amount));
        } catch (RuntimeException | LinkageError e) {
            fail("set", e);
            return error(amount);
        }
    }

    @Override
    public boolean canDeposit(UUID player, BigInteger amount) {
        if (broken) return false;
        try {
            return read(player) != null;
        } catch (RuntimeException | LinkageError e) {
            fail("read", e);
            return false;
        }
    }

    @Override
    public List<Holding> top(int limit) {
        if (broken) return List.of();
        try {
            List<Holding> all = new ArrayList<>(list());
            all.removeIf(h -> h.money().amount().signum() <= 0);
            all.sort(Comparator.comparing((Holding h) -> h.money().amount()).reversed());
            return all.size() > limit ? all.subList(0, limit) : all;
        } catch (RuntimeException | LinkageError e) {
            fail("list", e);
            return List.of();
        }
    }

    // ---------- helpers for subclasses ----------

    protected static MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    /** The online player, or null. */
    protected static ServerPlayer online(UUID id) {
        MinecraftServer s = server();
        return s == null ? null : s.getPlayerList().getPlayer(id);
    }

    /** A double from the mod -> smallest unit (rounded down to this currency's decimals). */
    protected BigInteger fromDouble(Object v) {
        if (!(v instanceof Number n)) return BigInteger.ZERO;
        return BigDecimal.valueOf(n.doubleValue()).movePointRight(currency.decimals()).setScale(0, RoundingMode.DOWN).toBigInteger();
    }

    /** Smallest unit -> double for the mod. */
    protected double toDouble(BigInteger v) {
        return new BigDecimal(v).movePointLeft(currency.decimals()).doubleValue();
    }

    protected static BigInteger fromLong(Object v) {
        return v instanceof Number n ? BigInteger.valueOf(n.longValue()) : BigInteger.ZERO;
    }

    /** Clamp for mods that store an int. */
    protected static int toInt(BigInteger v) {
        return v.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0 ? Integer.MAX_VALUE : v.intValue();
    }

    protected static long toLong(BigInteger v) {
        return v.bitLength() > 63 ? Long.MAX_VALUE : v.longValue();
    }

    protected Holding holding(UUID id, BigInteger amount) {
        return new Holding(id, currency.of(amount));
    }

    private Result offline(BigInteger amount) {
        return Result.fail(Result.Reason.OFFLINE_NOT_SUPPORTED, currency.of(amount), Component.literal("Player must be online"));
    }

    private Result error(BigInteger amount) {
        return Result.fail(Result.Reason.BACKEND_ERROR, currency.of(amount), Component.literal("Currency unavailable"));
    }

    private void fail(String what, Throwable e) {
        if (broken) return;
        broken = true;
        ElementsEconomy.LOGGER.error("[Economy] {} adapter failed during {} and is off until restart: {}",
                currency.name().getString(), what, e.toString());
    }
}
