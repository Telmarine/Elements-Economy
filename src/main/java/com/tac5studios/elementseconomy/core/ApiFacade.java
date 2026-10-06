package com.tac5studios.elementseconomy.core;

import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.EconomyService;
import com.tac5studios.elementsvault.Holding;
import com.tac5studios.elementsvault.Money;
import com.tac5studios.elementsvault.Result;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * What other mods get from Elements: Vault. Same as {@link Economy}, but the api.read,
 * api.write and api.async switches in features.toml apply.
 */
final class ApiFacade implements EconomyService {

    private final Economy eco;

    ApiFacade(Economy eco) {
        this.eco = eco;
    }

    private static boolean read() {
        return Features.on(Features.API, Features.API_READ);
    }

    private static boolean write() {
        return Features.on(Features.API, Features.API_WRITE);
    }

    private static boolean async() {
        return Features.on(Features.API, Features.API_ASYNC);
    }

    private static Result off(Money m) {
        return Result.fail(Result.Reason.NOT_SUPPORTED, m, Component.literal("Turned off by the server"));
    }

    @Override
    public String providerName() {
        return eco.providerName();
    }

    @Override
    public Currency primaryCurrency() {
        return eco.primaryCurrency();
    }

    @Override
    public List<Currency> activeCurrencies() {
        return eco.activeCurrencies();
    }

    @Override
    public Collection<Currency> currencies() {
        return eco.currencies();
    }

    @Override
    public Optional<Currency> currency(ResourceLocation id) {
        return eco.currency(id);
    }

    @Override
    public Money balance(UUID player, Currency currency) {
        return read() ? eco.balance(player, currency) : currency.zero();
    }

    @Override
    public List<Holding> top(Currency currency, int limit) {
        return read() ? eco.top(currency, limit) : List.of();
    }

    @Override
    public Result canWithdraw(UUID player, Money money) {
        return write() ? eco.canWithdraw(player, money) : off(money);
    }

    @Override
    public Result canDeposit(UUID player, Money money) {
        return write() ? eco.canDeposit(player, money) : off(money);
    }

    @Override
    public Result withdraw(UUID player, Money money, Cause cause) {
        return write() ? eco.withdraw(player, money, cause) : off(money);
    }

    @Override
    public Result deposit(UUID player, Money money, Cause cause) {
        return write() ? eco.deposit(player, money, cause) : off(money);
    }

    @Override
    public Result set(UUID player, Money money, Cause cause) {
        return write() ? eco.set(player, money, cause) : off(money);
    }

    @Override
    public Result transfer(UUID from, UUID to, Money money, Cause cause) {
        return write() ? eco.transfer(from, to, money, cause) : off(money);
    }

    @Override
    public Optional<Money> convert(Money money, Currency target) {
        return eco.convert(money, target);
    }

    @Override
    public List<ItemStack> toItems(Money money) {
        return eco.toItems(money);
    }

    @Override
    public Money fromItems(Collection<ItemStack> stacks, Currency currency) {
        return eco.fromItems(stacks, currency);
    }

    @Override
    public boolean isCurrencyItem(ItemStack stack) {
        return eco.isCurrencyItem(stack);
    }

    // Async calls run on the server thread so backends stay safe.

    private static <T> CompletableFuture<T> onServer(java.util.function.Supplier<T> job) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return CompletableFuture.failedFuture(new IllegalStateException("Server not running"));
        return CompletableFuture.supplyAsync(job, server);
    }

    @Override
    public CompletableFuture<Money> balanceAsync(UUID player, Currency currency) {
        return async() ? onServer(() -> balance(player, currency)) : CompletableFuture.completedFuture(currency.zero());
    }

    @Override
    public CompletableFuture<Result> withdrawAsync(UUID player, Money money, Cause cause) {
        return async() ? onServer(() -> withdraw(player, money, cause)) : CompletableFuture.completedFuture(off(money));
    }

    @Override
    public CompletableFuture<Result> depositAsync(UUID player, Money money, Cause cause) {
        return async() ? onServer(() -> deposit(player, money, cause)) : CompletableFuture.completedFuture(off(money));
    }

    @Override
    public CompletableFuture<Result> setAsync(UUID player, Money money, Cause cause) {
        return async() ? onServer(() -> set(player, money, cause)) : CompletableFuture.completedFuture(off(money));
    }

    @Override
    public CompletableFuture<Result> transferAsync(UUID from, UUID to, Money money, Cause cause) {
        return async() ? onServer(() -> transfer(from, to, money, cause)) : CompletableFuture.completedFuture(off(money));
    }
}
