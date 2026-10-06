package com.tac5studios.elementseconomy.bridge.octo;

import com.epherical.octoecon.api.OctoEconomy;
import com.epherical.octoecon.api.user.FakeUser;
import com.epherical.octoecon.api.user.UniqueUser;
import com.epherical.octoecon.api.user.User;
import com.google.gson.JsonObject;
import com.tac5studios.elementseconomy.bridge.Bridges;
import com.tac5studios.elementseconomy.core.Accounts;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.storage.Collections;
import com.tac5studios.elementseconomy.storage.Storage;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Money;
import com.tac5studios.elementsvault.Result;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * OctoEconomy answered by Elements: Economy, always in the primary currency.
 * Player accounts are Elements: Economy balances. Fake accounts (named by a resource location, used by
 * mods for banks or server accounts) are kept in collection "bridge_accounts" as { currency, amount }.
 */
final class OctoProvider implements OctoEconomy<OctoProvider.Player, OctoProvider.Fake> {

    private static final String FAKE_PREFIX = "octo:";

    private static Economy economy() {
        Economy e = Economy.get();
        if (e == null || !Economy.running()) throw new IllegalStateException("Elements: Economy is not running");
        return e;
    }

    private static Currency primary() {
        return economy().primaryCurrency();
    }

    private static Cause cause(String reason) {
        return new Cause(Cause.Type.PLUGIN, null, Bridges.EIGHTS, reason == null ? "" : reason);
    }

    // ---------- OctoEconomy ----------

    @Override
    public boolean enabled() {
        return Economy.running();
    }

    @Override
    public void setServer(@Nullable MinecraftServer server) {}

    @Override
    public void onPlayerJoin(UUID uuid) {}

    @Override
    public void onPlayerLeave(UUID uuid) {}

    @Override
    public void savePlayers() {
        Storage.moneyChanged(); // balances are saved by Elements: Economy's own timer
    }

    @Override
    public void close() {}

    @Override
    public Fake getOrCreateAccount(ResourceLocation identifier) {
        return new Fake(identifier);
    }

    @Override
    public Player getOrCreatePlayerAccount(UUID identifier) {
        return new Player(identifier);
    }

    @Override
    public @Nullable Player getPlayerAccountByName(String name) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return new Player(online.getUUID());
        if (server.getProfileCache() != null) {
            Optional<GameProfile> gp = server.getProfileCache().get(name);
            if (gp.isPresent()) return new Player(gp.get().getId());
        }
        return null;
    }

    @Override
    public Collection<Player> getUniqueUsers() {
        List<Player> out = new ArrayList<>();
        for (String k : Accounts.keys()) {
            try {
                out.add(new Player(UUID.fromString(k)));
            } catch (IllegalArgumentException ignored) {
                // not a player key
            }
        }
        return out;
    }

    @Override
    public Collection<User> getAllUsers() {
        List<User> out = new ArrayList<>(getUniqueUsers());
        out.addAll(getFakeUsers());
        return out;
    }

    @Override
    public Collection<Fake> getFakeUsers() {
        List<Fake> out = new ArrayList<>();
        for (String k : Storage.get().keys(Collections.BRIDGE_ACCOUNTS)) {
            if (!k.startsWith(FAKE_PREFIX)) continue;
            ResourceLocation id = ResourceLocation.tryParse(k.substring(FAKE_PREFIX.length()));
            if (id != null) out.add(new Fake(id));
        }
        return out;
    }

    @Override
    public boolean hasAccount(UUID identifier) {
        return true;
    }

    @Override
    public boolean hasAccount(ResourceLocation identifier) {
        return Storage.get().has(Collections.BRIDGE_ACCOUNTS, FAKE_PREFIX + identifier);
    }

    @Override
    public boolean deleteAccount(UUID identifier) {
        new Player(identifier).setBalance(0, "delete account");
        return true;
    }

    @Override
    public boolean deleteAccount(ResourceLocation identifier) {
        Storage.get().remove(Collections.BRIDGE_ACCOUNTS, FAKE_PREFIX + identifier);
        return true;
    }

    // ---------- users ----------

    /** Shared money logic; subclasses say where the balance lives. */
    abstract static class Base implements User {

        abstract BigInteger units();

        abstract boolean change(String kind, BigInteger units, String reason);

        @Override
        public double getBalance(String reasonCode) {
            return Bridges.onServer(() -> Bridges.fromUnits(units(), primary().decimals()).doubleValue());
        }

        @Override
        public boolean hasAmount(double amount, String reasonCode) {
            BigInteger want = Bridges.toUnits(amount, primary().decimals());
            return want.signum() >= 0 && Bridges.onServer(() -> units().compareTo(want) >= 0);
        }

        @Override
        public void resetBalance(String reasonCode) {
            setBalance(0, reasonCode);
        }

        @Override
        public void setBalance(double amount, String reasonCode) {
            BigInteger units = Bridges.toUnits(amount, primary().decimals());
            if (units.signum() >= 0) Bridges.onServer(() -> change("set", units, reasonCode));
        }

        @Override
        public void depositMoney(double amount, String reasonCode) {
            BigInteger units = Bridges.toUnits(amount, primary().decimals());
            if (units.signum() > 0) Bridges.onServer(() -> change("deposit", units, reasonCode));
        }

        @Override
        public void withdrawMoney(double amount, String reasonCode) {
            BigInteger units = Bridges.toUnits(amount, primary().decimals());
            if (units.signum() > 0) Bridges.onServer(() -> change("withdraw", units, reasonCode));
        }

        @Override
        public void sendTo(User user, double amount, String reasonCode) {
            BigInteger units = Bridges.toUnits(amount, primary().decimals());
            if (units.signum() <= 0 || !(user instanceof Base to)) return;
            Bridges.onServer(() -> {
                if (this instanceof Player a && to instanceof Player b) {
                    return economy().transfer(a.id, b.id, primary().of(units), cause(reasonCode)).success();
                }
                if (!change("withdraw", units, reasonCode)) return false;
                if (!to.change("deposit", units, reasonCode)) {
                    change("deposit", units, "refund"); // put it back
                    return false;
                }
                return true;
            });
        }

        @Override
        public boolean isDirty() {
            return false;
        }
    }

    /** A player: the Elements: Economy balance in the primary currency. */
    static final class Player extends Base implements UniqueUser {
        final UUID id;

        Player(UUID id) {
            this.id = id;
        }

        @Override
        BigInteger units() {
            return economy().balance(id, primary()).amount();
        }

        @Override
        boolean change(String kind, BigInteger units, String reason) {
            Money m = primary().of(units);
            Result r = switch (kind) {
                case "deposit" -> economy().deposit(id, m, cause(reason));
                case "withdraw" -> economy().withdraw(id, m, cause(reason));
                default -> economy().set(id, m, cause(reason));
            };
            return r.success();
        }

        @Override
        public UUID getUserID() {
            return id;
        }

        @Override
        public Component getDisplayName() {
            return Component.literal(Accounts.name(id));
        }

        @Override
        public String getIdentity() {
            return id.toString();
        }
    }

    /** A named account kept by Elements: Economy for another mod. */
    static final class Fake extends Base implements FakeUser {
        final ResourceLocation id;

        Fake(ResourceLocation id) {
            this.id = id;
        }

        private String key() {
            return FAKE_PREFIX + id;
        }

        @Override
        BigInteger units() {
            JsonObject o = Storage.get().get(Collections.BRIDGE_ACCOUNTS, key(), JsonObject.class);
            if (o == null || !o.has("amount")) return BigInteger.ZERO;
            BigInteger amount = new BigInteger(o.get("amount").getAsString());
            String cur = o.has("currency") ? o.get("currency").getAsString() : primary().id().toString();
            if (cur.equals(primary().id().toString())) return amount;
            // Saved before a currency switch: convert at today's rate.
            ResourceLocation rl = ResourceLocation.tryParse(cur);
            Optional<Currency> old = rl == null ? Optional.empty() : economy().currency(rl);
            return old.flatMap(c -> economy().convert(c.of(amount), primary())).map(Money::amount).orElse(BigInteger.ZERO);
        }

        @Override
        boolean change(String kind, BigInteger units, String reason) {
            BigInteger now = units();
            BigInteger next = switch (kind) {
                case "deposit" -> now.add(units);
                case "withdraw" -> now.subtract(units);
                default -> units;
            };
            if (next.signum() < 0) return false;
            JsonObject o = new JsonObject();
            o.addProperty("currency", primary().id().toString());
            o.addProperty("amount", next.toString());
            Storage.get().put(Collections.BRIDGE_ACCOUNTS, key(), o);
            Storage.moneyChanged();
            return true;
        }

        @Override
        public ResourceLocation getResourceLocation() {
            return id;
        }

        @Override
        public Component getDisplayName() {
            return Component.literal(id.toString());
        }

        @Override
        public String getIdentity() {
            return id.toString();
        }
    }
}
