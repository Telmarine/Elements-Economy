package com.tac5studios.elementseconomy.core;

import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Result;
import com.tac5studios.elementsvault.backend.CurrencyBackend;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Coins carried by the player (inventory plus holders). Online players only:
 * an offline player's balance reads as zero and changes fail with OFFLINE_NOT_SUPPORTED.
 */
public final class ItemBackend implements CurrencyBackend {

    private final ItemCurrency currency;

    public ItemBackend(ItemCurrency currency) {
        this.currency = currency;
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath("elements_economy", "items_" + currency.namespace());
    }

    @Override
    public Currency currency() {
        return currency;
    }

    @Override
    public boolean isAvailable() {
        return !Coins.denominations(currency.namespace()).isEmpty();
    }

    private static ServerPlayer online(UUID id) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    @Override
    public BigInteger balance(UUID player) {
        ServerPlayer p = online(player);
        return p == null ? BigInteger.ZERO : BigInteger.valueOf(Coins.count(p, currency.namespace()));
    }

    @Override
    public Result withdraw(UUID player, BigInteger amount, Cause cause) {
        ServerPlayer p = online(player);
        if (p == null) return offline(amount);
        long before = Coins.count(p, currency.namespace());
        if (!Coins.take(p, currency.namespace(), Amounts.toLong(amount))) {
            return Result.fail(Result.Reason.INSUFFICIENT_FUNDS, currency.of(amount), Component.literal("Not enough coins"));
        }
        return Result.ok(currency.of(amount), currency.of(before), currency.of(Coins.count(p, currency.namespace())));
    }

    @Override
    public Result deposit(UUID player, BigInteger amount, Cause cause) {
        ServerPlayer p = online(player);
        if (p == null) return offline(amount);
        long before = Coins.count(p, currency.namespace());
        long left = Coins.give(p, currency.namespace(), Amounts.toLong(amount));
        BigInteger paid = amount.subtract(BigInteger.valueOf(left));
        return Result.ok(currency.of(paid), currency.of(before), currency.of(Coins.count(p, currency.namespace())));
    }

    @Override
    public boolean canDeposit(UUID player, BigInteger amount) {
        return online(player) != null;
    }

    private Result offline(BigInteger amount) {
        return Result.fail(Result.Reason.OFFLINE_NOT_SUPPORTED, currency.of(amount), Component.literal("Player must be online"));
    }
}
