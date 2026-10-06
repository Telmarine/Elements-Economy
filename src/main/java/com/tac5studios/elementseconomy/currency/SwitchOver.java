package com.tac5studios.elementseconomy.currency;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.auction.Auctions;
import com.tac5studios.elementseconomy.auction.CollectionBox;
import com.tac5studios.elementseconomy.auction.Listing;
import com.tac5studios.elementseconomy.bridge.ShopBridges;
import com.tac5studios.elementseconomy.config.EconomyConfig;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Accounts;
import com.tac5studios.elementseconomy.core.Amounts;
import com.tac5studios.elementseconomy.core.Coins;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.core.ItemCurrency;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.servershop.ServerRow;
import com.tac5studios.elementseconomy.servershop.ServerShop;
import com.tac5studios.elementseconomy.servershop.ServerShops;
import com.tac5studios.elementseconomy.shop.Shop;
import com.tac5studios.elementseconomy.shop.ShopDisplays;
import com.tac5studios.elementseconomy.shop.ShopRow;
import com.tac5studios.elementseconomy.shop.Shops;
import com.tac5studios.elementseconomy.storage.Backups;
import com.tac5studios.elementseconomy.storage.Collections;
import com.tac5studios.elementseconomy.storage.Storage;
import com.tac5studios.elementseconomy.storage.TransactionLog;
import com.tac5studios.elementsvault.Capability;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Money;
import com.tac5studios.elementsvault.Result;
import com.tac5studios.elementsvault.event.CurrencyChangeEvent;
import com.tac5studios.elementsvault.shop.ConversionPlan;
import com.tac5studios.elementsvault.shop.CurrencyChange;
import com.tac5studios.elementsvault.shop.ShopBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The currency switch-over: /economy currency switch <new> shows a preview, /economy currency switch confirm
 * converts everything priced or held in the old primary currency, then makes the new one primary.
 *
 * Converted: player balances, player shop prices and tills, server shop prices (and rank prices),
 * active auction listings and bids, collection boxes, and other mods' shops through their bridges.
 * Item-priced shops only swap one coin currency for another; they never become digital.
 *
 * Balances that can't change right now (coins, or currencies that need the player online) convert at
 * that player's next login, at the rate saved with the switch. Every switch is kept in meta "switches",
 * and each account remembers how many it has been through, so players who miss several switches
 * catch up in order.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class SwitchOver {

    private static final String META_KEY = "switches";
    private static final long CONFIRM_MS = 120_000L;
    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();

    private record Pending(ResourceLocation target, long expires) {}

    /** One saved switch: from, to, and both rates (worth of one smallest unit in digital) at that time. */
    public record Step(String from, String to, BigDecimal fromRate, BigDecimal toRate) {
        JsonObject toJson(Instant when) {
            JsonObject o = new JsonObject();
            o.addProperty("from", from);
            o.addProperty("to", to);
            o.addProperty("from_rate", fromRate.toPlainString());
            o.addProperty("to_rate", toRate.toPlainString());
            o.addProperty("when", when.toString());
            return o;
        }

        static Step fromJson(JsonObject o) {
            return new Step(o.get("from").getAsString(), o.get("to").getAsString(),
                    new BigDecimal(o.get("from_rate").getAsString()), new BigDecimal(o.get("to_rate").getAsString()));
        }

        public BigInteger convert(BigInteger amount) {
            return new BigDecimal(amount).multiply(fromRate).divide(toRate, 0, RoundingMode.DOWN).toBigInteger();
        }
    }

    /** What a switch changes (or would change, in the preview). */
    private static final class Counts {
        int balancesNow, balancesLater, shopPrices, serverPrices, tills, listings, boxes, skipped;
        final Map<ResourceLocation, int[]> bridges = new LinkedHashMap<>();

        int total() {
            int n = balancesNow + shopPrices + serverPrices + tills + listings + boxes;
            for (int[] b : bridges.values()) n += b[0];
            return n;
        }
    }

    private SwitchOver() {}

    // ---------- commands ----------

    private static String who(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        return p == null ? "console" : p.getUUID().toString();
    }

    /** /economy currency switch <new>: preview (or switch at once when the preview is off). */
    public static int start(CommandSourceStack src, ResourceLocation targetId) {
        Economy e = Economy.get();
        if (e == null || !Economy.running()) {
            Msg.fail(src, "general.currency_unavailable");
            return 0;
        }
        Currency from = e.primaryCurrency();
        Optional<Currency> to = e.currency(targetId);
        if (to.isEmpty()) {
            Msg.fail(src, "switch.unknown", "currency", targetId);
            return 0;
        }
        if (to.get().equals(from)) {
            Msg.fail(src, "switch.same", "currency", name(from));
            return 0;
        }
        if (e.rate(from).isEmpty() || e.rate(to.get()).isEmpty()) {
            Msg.fail(src, "switch.no_rate", "from", name(from), "to", name(to.get()));
            return 0;
        }
        if (!Features.on(Features.SWITCH_OVER, Features.SWITCH_PREVIEW)) {
            return run(src, from, to.get());
        }
        Step step = new Step(from.id().toString(), to.get().id().toString(), e.rate(from).get(), e.rate(to.get()).get());
        Counts c = convert(from, to.get(), step, true);
        Msg.send(src, "switch.preview_header", "from", name(from), "to", name(to.get()));
        Msg.send(src, "switch.preview_rate", "example", example(e, from, to.get(), step));
        if (Features.on(Features.SWITCH_OVER, Features.SWITCH_BALANCES)) {
            Msg.send(src, "switch.preview_balances", "now", c.balancesNow, "later", c.balancesLater);
        }
        if (Features.on(Features.SWITCH_OVER, Features.SWITCH_SHOPS)) {
            Msg.send(src, "switch.preview_shops", "prices", c.shopPrices, "server", c.serverPrices, "tills", c.tills);
        }
        if (Features.on(Features.SWITCH_OVER, Features.SWITCH_LISTINGS)) {
            Msg.send(src, "switch.preview_listings", "listings", c.listings, "boxes", c.boxes);
        }
        if (c.skipped > 0) Msg.send(src, "switch.preview_skipped", "skipped", c.skipped);
        c.bridges.forEach((id, n) -> Msg.send(src, "switch.preview_bridge", "bridge", id, "changed", n[0], "skipped", n[1]));
        PENDING.put(who(src), new Pending(to.get().id(), System.currentTimeMillis() + CONFIRM_MS));
        Msg.send(src, "switch.preview_confirm");
        return 1;
    }

    /** /economy currency switch confirm */
    public static int confirm(CommandSourceStack src) {
        Pending p = PENDING.remove(who(src));
        Economy e = Economy.get();
        if (p == null || p.expires() < System.currentTimeMillis() || e == null || !Economy.running()) {
            Msg.fail(src, "switch.nothing_pending");
            return 0;
        }
        Optional<Currency> to = e.currency(p.target());
        if (to.isEmpty() || to.get().equals(e.primaryCurrency())) {
            Msg.fail(src, "switch.nothing_pending");
            return 0;
        }
        return run(src, e.primaryCurrency(), to.get());
    }

    // ---------- the switch ----------

    private static int run(CommandSourceStack src, Currency from, Currency to) {
        Economy e = Economy.get();
        Optional<BigDecimal> fr = e.rate(from), tr = e.rate(to);
        if (fr.isEmpty() || tr.isEmpty()) {
            Msg.fail(src, "switch.no_rate", "from", name(from), "to", name(to));
            return 0;
        }
        Step step = new Step(from.id().toString(), to.id().toString(), fr.get(), tr.get());
        CurrencyChange change = new CurrencyChange(from, to, Instant.now());
        boolean events = Features.on(Features.API, Features.API_EVENTS);

        if (events) {
            Plan check = new Plan(from, to, step, true, new Counts());
            if (NeoForge.EVENT_BUS.post(new CurrencyChangeEvent.Pre(change, check)).isCanceled()) {
                Msg.fail(src, "switch.cancelled");
                return 0;
            }
        }

        if (Features.on(Features.SWITCH_OVER, Features.SWITCH_BACKUP)) {
            try {
                Path dir = Backups.take("currency-switch");
                Msg.send(src, "switch.backup", "folder", dir.getFileName());
            } catch (IOException | RuntimeException ex) {
                ElementsEconomy.LOGGER.error("[Economy] Backup before the currency switch failed.", ex);
                Msg.fail(src, "switch.backup_failed");
                return 0;
            }
        }

        // Save the switch first, so balances that wait for a login use these rates.
        JsonArray steps = steps();
        int index = steps.size();
        steps.add(step.toJson(change.when()));
        Storage.get().put(Collections.META, META_KEY, steps);
        markPlayersDue(index);

        Counts c = convert(from, to, step, false);

        EconomyConfig.PRIMARY.set(to.id().toString());
        EconomyConfig.PRIMARY.save();
        e.reload();
        Storage.moneyChanged();

        String line = name(from) + " -> " + name(to) + ": " + c.total() + " changed, " + c.balancesLater
                + " balances at next login, " + c.skipped + " item-priced left alone";
        TransactionLog.add("SWITCH", line);
        ElementsEconomy.LOGGER.info("[Economy] Currency switched. {}", line);
        if (events) NeoForge.EVENT_BUS.post(new CurrencyChangeEvent.Post(change, new Plan(from, to, step, false, c)));
        PENDING.clear();
        Msg.sendLogged(src, "switch.done", "to", name(to), "changed", c.total(), "later", c.balancesLater);
        return 1;
    }

    /** Count (preview) or convert everything in the old currency. */
    private static Counts convert(Currency from, Currency to, Step step, boolean preview) {
        Counts c = new Counts();
        String oldId = from.id().toString();
        String newId = to.id().toString();
        // Item prices never become digital: coin-priced shops only follow a switch to another coin currency.
        boolean shopsFollow = !(from instanceof ItemCurrency) || to instanceof ItemCurrency;

        if (Features.on(Features.SWITCH_OVER, Features.SWITCH_BALANCES)) balances(from, to, step, preview, c);

        if (Features.on(Features.SWITCH_OVER, Features.SWITCH_SHOPS)) {
            for (Shop s : new ArrayList<>(Shops.all())) {
                boolean changed = false;
                for (ShopRow r : s.rows.values()) {
                    if (!oldId.equals(r.currency)) continue;
                    if (!shopsFollow) {
                        c.skipped++;
                        continue;
                    }
                    c.shopPrices++;
                    if (!preview) {
                        r.price = price(step, r.price);
                        r.currency = newId;
                        changed = true;
                    }
                }
                BigInteger till = s.till(oldId);
                if (till.signum() > 0) {
                    c.tills++;
                    if (!preview) {
                        s.till.remove(oldId);
                        s.addTill(newId, step.convert(till));
                        changed = true;
                    }
                }
                if (changed) {
                    Shops.save(s);
                    ShopDisplays.touch(s.id);
                }
            }
            for (ServerShop s : new ArrayList<>(ServerShops.all())) {
                boolean changed = false;
                for (ServerRow r : s.rows.values()) {
                    if (!oldId.equals(r.currency)) continue;
                    if (!shopsFollow) {
                        c.skipped++;
                        continue;
                    }
                    c.serverPrices++;
                    if (!preview) {
                        r.price = price(step, r.price);
                        r.rankPrices.replaceAll((rank, p) -> price(step, p));
                        r.currency = newId;
                        changed = true;
                    }
                }
                if (changed) ServerShops.save(s);
            }
        }

        if (Features.on(Features.SWITCH_OVER, Features.SWITCH_LISTINGS)) {
            for (Listing l : new ArrayList<>(Auctions.all())) {
                if (!l.active() || !oldId.equals(l.currency)) continue;
                c.listings++;
                if (!preview) {
                    l.price = price(step, l.price);
                    if (l.topBid.signum() > 0) l.topBid = price(step, l.topBid); // the held bid is refunded in the new currency
                    l.currency = newId;
                    Auctions.save(l);
                }
            }
            for (String key : Storage.get().keys(Collections.COLLECT)) {
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                BigInteger held = CollectionBox.money(id).getOrDefault(oldId, BigInteger.ZERO);
                if (held.signum() <= 0) continue;
                c.boxes++;
                if (!preview) {
                    CollectionBox.removeMoney(id, oldId);
                    BigInteger out = step.convert(held);
                    if (out.signum() > 0) CollectionBox.addMoney(id, newId, out);
                }
            }
        }

        if (Features.on(Features.SHOP_BRIDGE)) {
            Plan plan = new Plan(from, to, step, preview, c);
            CurrencyChange change = new CurrencyChange(from, to, Instant.now());
            for (ShopBridge b : ShopBridges.all()) {
                try {
                    b.onCurrencyChanged(change, plan);
                } catch (RuntimeException ex) {
                    ElementsEconomy.LOGGER.error("[Economy] Shop bridge {} failed during the currency switch.", b.id(), ex);
                }
            }
        }
        return c;
    }

    /**
     * A converted price never drops to zero (that would take the item off sale), and for a coin
     * currency it is rounded up to whole coins so it can always be paid.
     */
    private static BigInteger price(Step step, BigInteger old) {
        if (old.signum() <= 0) return old;
        BigInteger p = step.convert(old).max(BigInteger.ONE);
        Economy e = Economy.get();
        ResourceLocation to = ResourceLocation.tryParse(step.to());
        return e == null || to == null ? p : e.currency(to).map(c -> Economy.roundUp(c, p)).orElse(p);
    }

    // ---------- balances ----------

    private static void balances(Currency from, Currency to, Step step, boolean preview, Counts c) {
        Economy e = Economy.get();
        int index = steps().size() - (preview ? 0 : 1); // the real switch is already saved
        boolean offline = from.can(Capability.OFFLINE_READ) && from.can(Capability.OFFLINE_WRITE) && to.can(Capability.OFFLINE_WRITE);
        for (UUID id : knownPlayers()) {
            ServerPlayer online = player(id);
            int done = Accounts.switchStep(id);
            boolean due = preview ? done < 0 || done >= index : done == index;
            if (preview) {
                if ((online != null || offline) && due) {
                    if (!e.balance(id, from).isZero()) c.balancesNow++;
                } else if (!from.can(Capability.OFFLINE_READ) || !e.balance(id, from).isZero()) {
                    c.balancesLater++; // coins and online-only money can't be read until they log in
                }
                continue;
            }
            if (online != null && !due) {
                if (catchUp(online)) c.balancesNow++; else c.balancesLater++;
            } else if ((online != null || offline) && due) {
                int r = convertBalance(id, from, to, step);
                if (r >= 0) Accounts.setSwitchStep(id, index + 1);
                if (r > 0) c.balancesNow++;
                if (r < 0) c.balancesLater++;
            } else if (!from.can(Capability.OFFLINE_READ) || !e.balance(id, from).isZero()) {
                c.balancesLater++;
            }
        }
    }

    /** Players with an account or a digital balance. */
    private static Set<UUID> knownPlayers() {
        Set<UUID> out = new LinkedHashSet<>();
        List<String> keys = new ArrayList<>(Accounts.keys());
        keys.addAll(Storage.get().keys(Collections.BALANCES));
        for (String k : keys) {
            try {
                out.add(UUID.fromString(k));
            } catch (IllegalArgumentException ignored) {
                // not a player key
            }
        }
        return out;
    }

    /** Everyone known now still needs this switch (accounts made later start up to date). */
    private static void markPlayersDue(int index) {
        for (UUID id : knownPlayers()) {
            int s = Accounts.switchStep(id);
            if (s < 0 || s > index) Accounts.setSwitchStep(id, index);
        }
    }

    /** Move one player's whole balance. 1 = moved, 0 = nothing to move, -1 = can't right now (retries at login). */
    private static int convertBalance(UUID id, Currency from, Currency to, Step step) {
        Economy e = Economy.get();
        BigInteger have = e.balance(id, from).amount();
        if (have.signum() <= 0) return 0;
        BigInteger out = Economy.payable(to, step.convert(have)); // coin currencies: whole coins only
        if (out.signum() <= 0) return 0; // worth less than the smallest new unit: left as it is
        Cause cause = Cause.system("currency switch");
        if (!e.canDeposit(id, to.of(out)).success()) return -1;
        Result taken = e.withdraw(id, from.of(have), cause);
        if (!taken.success()) return -1;
        Result given = e.deposit(id, to.of(out), cause);
        if (!given.success()) {
            e.deposit(id, from.of(have), Cause.system("currency switch refund"));
            return -1;
        }
        ServerPlayer p = player(id);
        if (p != null) Msg.send(p, "switch.converted", "from", e.format(from.of(have)), "to", e.format(to.of(out)));
        return 1;
    }

    /** Catch a player up on switches they missed while offline. */
    @SubscribeEvent(priority = EventPriority.LOW) // after Economy remembers the account
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !Economy.running() || !Storage.running()) return;
        if (!Features.on(Features.SWITCH_OVER, Features.SWITCH_BALANCES)) return;
        if (Accounts.switchStep(p.getUUID()) < 0) {
            Accounts.setSwitchStep(p.getUUID(), steps().size()); // new player: nothing to catch up on
            return;
        }
        catchUp(p);
    }

    /** Apply every saved switch this player hasn't had yet, in order. True when fully caught up. */
    private static boolean catchUp(ServerPlayer p) {
        JsonArray steps = steps();
        Economy e = Economy.get();
        for (int i = Math.max(0, Accounts.switchStep(p.getUUID())); i < steps.size(); i++) {
            Step s;
            try {
                s = Step.fromJson(steps.get(i).getAsJsonObject());
            } catch (RuntimeException ex) {
                Accounts.setSwitchStep(p.getUUID(), i + 1);
                continue;
            }
            Optional<Currency> from = e.currency(ResourceLocation.parse(s.from()));
            Optional<Currency> to = e.currency(ResourceLocation.parse(s.to()));
            if (from.isPresent() && to.isPresent() && convertBalance(p.getUUID(), from.get(), to.get(), s) < 0) {
                return false; // try again next login
            }
            Accounts.setSwitchStep(p.getUUID(), i + 1);
        }
        return true;
    }

    /** Every switch so far, oldest first (shop bridges use this to convert shops when they load). */
    public static List<Step> history() {
        List<Step> out = new ArrayList<>();
        if (!Storage.running()) return out;
        for (JsonElement el : steps()) {
            try {
                out.add(Step.fromJson(el.getAsJsonObject()));
            } catch (RuntimeException ignored) {
                // unreadable entry, skipped
            }
        }
        return out;
    }

    private static JsonArray steps() {
        JsonElement el = Storage.get().raw(Collections.META, META_KEY);
        return el != null && el.isJsonArray() ? el.getAsJsonArray().deepCopy() : new JsonArray();
    }

    // ---------- helpers ----------

    private static ServerPlayer player(UUID id) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? null : s.getPlayerList().getPlayer(id);
    }

    private static String name(Currency c) {
        return c.name().getString();
    }

    /** "1 Stone Coin = 1 Coin": the smallest round amount of the old currency worth at least one new unit. */
    private static String example(Economy e, Currency from, Currency to, Step step) {
        BigInteger amount = BigInteger.TEN.pow(from.decimals());
        for (int i = 0; i < 7 && step.convert(amount).signum() == 0; i++) amount = amount.multiply(BigInteger.TEN);
        return e.format(from.of(amount)) + " = " + e.format(to.of(step.convert(amount)));
    }

    /** What shop bridges get: converts prices and coins, and collects their counts. */
    private static final class Plan implements ConversionPlan {
        private final Currency from, to;
        private final Step step;
        private final boolean preview;
        private final Counts counts;

        Plan(Currency from, Currency to, Step step, boolean preview, Counts counts) {
            this.from = from;
            this.to = to;
            this.step = step;
            this.preview = preview;
            this.counts = counts;
        }

        @Override
        public boolean preview() {
            return preview;
        }

        @Override
        public Optional<Money> convert(Money price) {
            if (!price.currency().equals(from)) return Optional.empty();
            return Optional.of(to.of(SwitchOver.price(step, price.amount())));
        }

        @Override
        public boolean isOldCurrencyItem(ItemStack stack) {
            return from instanceof ItemCurrency ic && !stack.isEmpty() && ic.namespace().equals(Coins.currencyOf(stack.getItem()));
        }

        @Override
        public List<ItemStack> swap(ItemStack oldCoins) {
            if (!(from instanceof ItemCurrency oldC) || !(to instanceof ItemCurrency newC) || !isOldCurrencyItem(oldCoins)) {
                return List.of(); // item prices never become digital; other items are left alone
            }
            long value = Coins.valueOf(List.of(oldCoins), oldC.namespace());
            BigInteger out = step.convert(BigInteger.valueOf(value));
            if (out.signum() <= 0) return List.of();
            return Coins.stacks(newC.namespace(), Amounts.toLong(out));
        }

        @Override
        public void report(ResourceLocation bridge, int changed, int skipped) {
            int[] n = counts.bridges.computeIfAbsent(bridge, k -> new int[2]);
            n[0] += changed;
            n[1] += skipped;
        }
    }
}
