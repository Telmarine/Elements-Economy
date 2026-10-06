package com.tac5studios.elementseconomy.shop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.config.ShopConfig;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Money;
import com.tac5studios.elementsvault.Result;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /shop create buy|sell · /shop rename <name> · /shop remove · /shop manage
 * /shop vault link · /shop vault add · /shop vault remove
 * All work on the block the player is looking at. Disabled parts are not registered.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class ShopCommands {

    /** The vault each player last linked, for /shop vault add. */
    private static final Map<UUID, Selected> SELECTED = new ConcurrentHashMap<>();

    private record Selected(ResourceKey<Level> dim, BlockPos pos) {}

    private ShopCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        if (!Features.on(Features.PLAYER_SHOPS)) return;
        register(e.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> shop = Commands.literal("shop");
        boolean any = false;

        LiteralArgumentBuilder<CommandSourceStack> create = Commands.literal("create");
        boolean createAny = false;
        if (Perm.enabled(Perm.SHOP_CREATE_BUY)) {
            create.then(Commands.literal("buy").requires(s -> Perm.has(s, Perm.SHOP_CREATE_BUY)).executes(c -> create(c, ShopType.BUY)));
            createAny = true;
        }
        if (Perm.enabled(Perm.SHOP_CREATE_SELL)) {
            create.then(Commands.literal("sell").requires(s -> Perm.has(s, Perm.SHOP_CREATE_SELL)).executes(c -> create(c, ShopType.SELL)));
            createAny = true;
        }
        if (createAny) {
            shop.then(create);
            any = true;
        }
        if (Perm.enabled(Perm.SHOP_RENAME)) {
            shop.then(Commands.literal("rename").requires(s -> Perm.has(s, Perm.SHOP_RENAME))
                    .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ShopCommands::renameCmd)));
            any = true;
        }
        if (Perm.enabled(Perm.SHOP_REMOVE)) {
            shop.then(Commands.literal("remove").requires(s -> Perm.has(s, Perm.SHOP_REMOVE)).executes(ShopCommands::removeCmd));
            any = true;
        }
        if (Perm.enabled(Perm.SHOP_MANAGE)) {
            shop.then(Commands.literal("manage").requires(s -> Perm.has(s, Perm.SHOP_MANAGE)).executes(ShopCommands::manageCmd));
            any = true;
        }
        if (Perm.enabled(Perm.STOCK_VAULT_LINK)) {
            shop.then(Commands.literal("vault").requires(s -> Perm.has(s, Perm.STOCK_VAULT_LINK))
                    .then(Commands.literal("link").executes(ShopCommands::vaultLink))
                    .then(Commands.literal("add").executes(ShopCommands::vaultAdd))
                    .then(Commands.literal("remove").executes(ShopCommands::vaultRemove)));
            any = true;
        }
        if (any) d.register(shop);
    }

    // ---------- helpers ----------

    @Nullable
    private static ServerPlayer player(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) Msg.fail(c.getSource(), "general.players_only");
        return p;
    }

    /** The block the player is looking at, or null. */
    @Nullable
    static BlockPos target(ServerPlayer p) {
        HitResult hit = p.pick(p.blockInteractionRange(), 0f, false);
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) return b.getBlockPos();
        return null;
    }

    /** The shop being looked at that this player may change (their own, or anyone's with shop.others). */
    @Nullable
    private static Shop myShop(ServerPlayer p) {
        BlockPos pos = target(p);
        Shop s = pos == null ? null : Shops.at(p.level(), pos);
        if (s == null) {
            Msg.send(p, "shop.look_at_container");
            return null;
        }
        if (!s.owner.equals(p.getUUID()) && !Perm.has(p, Perm.SHOP_OTHERS)) {
            Msg.send(p, "shop.not_owner");
            return null;
        }
        return s;
    }

    // ---------- create ----------

    private static int create(CommandContext<CommandSourceStack> c, ShopType type) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        Economy e = Economy.get();
        if (e == null || !Economy.running()) {
            Msg.send(p, "general.currency_unavailable");
            return 0;
        }
        Level level = p.level();
        BlockPos pos = target(p);
        if (pos == null) {
            Msg.send(p, "shop.look_at_container");
            return 0;
        }
        if (Shops.at(level, pos) != null) {
            Msg.send(p, "shop.already_shop");
            return 0;
        }
        if (!ShopContainers.canBeShop(level, pos)) {
            Msg.send(p, "shop.not_allowed");
            return 0;
        }
        BlockPos other = ShopContainers.otherHalf(level, pos);
        if (other != null && Shops.at(level, other) != null) {
            Msg.send(p, "shop.already_shop");
            return 0;
        }
        if (!ShopRules.claimOk(p, pos)) {
            Msg.send(p, "shop.not_your_claim");
            return 0;
        }
        int limit = ShopRules.limit(p);
        int owned = Shops.ownedBy(p.getUUID()).size();
        if (limit >= 0 && owned >= limit) {
            Msg.send(p, "shop.limit", "count", owned, "limit", limit);
            return 0;
        }
        if (Features.on(Features.PS_CREATE_FEE) && !Perm.has(p, Perm.SHOP_FREE)) {
            Currency cur = e.primaryCurrency();
            BigInteger fee = Economy.configAmount(cur, ShopConfig.CREATE_FEE.get()).orElse(BigInteger.ZERO);
            if (fee.signum() > 0) {
                Money m = cur.of(fee);
                Result r = e.withdraw(p.getUUID(), m, Cause.command("/shop create", p.getUUID()).withReason("shop fee"));
                if (!r.success()) {
                    Msg.send(p, "shop.fee_not_enough", "amount", e.format(m));
                    return 0;
                }
                Msg.send(p, "shop.fee", "amount", e.format(m));
            }
        }

        String name = p.getGameProfile().getName() + "'s shop";
        Shop s = new Shop(Shops.newId(), p.getUUID(), p.getGameProfile().getName(), type, name, level.dimension());
        s.positions.add(pos);
        if (other != null) s.positions.add(other);
        s.created = System.currentTimeMillis();
        Shops.add(s);
        for (BlockPos bp : s.positions) level.invalidateCapabilities(bp);
        Msg.send(p, type == ShopType.BUY ? "shop.created_buy" : "shop.created_sell");
        return 1;
    }

    // ---------- rename / remove / manage ----------

    private static int renameCmd(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        Shop s = myShop(p);
        return s == null ? 0 : (rename(p, s, StringArgumentType.getString(c, "name")) ? 1 : 0);
    }

    static boolean rename(ServerPlayer p, Shop s, String text) {
        String name = text.trim();
        if (name.isEmpty() || name.length() > ShopConfig.NAME_MAX.get()) {
            Msg.send(p, "general.bad_amount", "input", name);
            return false;
        }
        s.name = name;
        Shops.save(s);
        Msg.send(p, "shop.renamed", "name", name);
        return true;
    }

    private static int removeCmd(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        Shop s = myShop(p);
        return s != null && remove(p, s) ? 1 : 0;
    }

    /**
     * Removes a shop. Money left in the till goes to the owner first; if it can't be paid
     * (owner offline with a coin currency), the shop stays so nothing is lost.
     */
    static boolean remove(ServerPlayer actor, Shop s) {
        Economy e = Economy.get();
        if (e != null && !s.till.isEmpty()) {
            for (Map.Entry<String, BigInteger> t : new ArrayList<>(s.till.entrySet())) {
                ResourceLocation id = ResourceLocation.tryParse(t.getKey());
                Optional<Currency> cur = id == null ? Optional.empty() : e.currency(id);
                if (cur.isEmpty()) continue;
                BigInteger pay = Economy.payable(cur.get(), t.getValue());
                if (pay.signum() > 0) {
                    Result r = e.deposit(s.owner, cur.get().of(pay), Cause.shop(s.id, actor.getUUID()).withReason("shop removed"));
                    if (!r.success()) {
                        Shops.save(s);
                        Msg.send(actor, "shop.till_not_empty");
                        return false;
                    }
                }
                BigInteger dust = t.getValue().subtract(pay);
                if (dust.signum() > 0) {
                    // Smaller than any coin (left by a currency switch): it can't be paid, so it goes with the shop.
                    com.tac5studios.elementseconomy.storage.TransactionLog.add("SHOP_DUST", s.id + " " + dust + " (" + t.getKey() + ")");
                }
                s.addTill(t.getKey(), t.getValue().negate());
            }
        }
        ServerLevel level = actor.server.getLevel(s.dimension);
        Shops.remove(s);
        if (level != null) for (BlockPos bp : s.positions) level.invalidateCapabilities(bp);
        Msg.send(actor, "shop.removed");
        return true;
    }

    private static int manageCmd(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        Shop s = myShop(p);
        if (s == null) return 0;
        new OwnerMenu(s).open(p);
        return 1;
    }

    // ---------- stock vaults ----------

    private static int vaultLink(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        BlockPos pos = target(p);
        Level level = p.level();
        if (pos == null || !ShopContainers.isStockVault(level, pos)) {
            Msg.send(p, "stockvault.not_vault");
            return 0;
        }
        if (!ShopRules.claimOk(p, pos)) {
            Msg.send(p, "shop.not_your_claim");
            return 0;
        }
        BlockPos controller = ShopCapabilities.controller(level.getBlockEntity(pos), pos);
        if (!StockVaults.link(level, controller, p.getUUID())) {
            Msg.send(p, "shop.not_owner");
            return 0;
        }
        SELECTED.put(p.getUUID(), new Selected(level.dimension(), controller));
        Msg.send(p, "stockvault.registered");
        return 1;
    }

    private static int vaultAdd(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        Selected v = SELECTED.get(p.getUUID());
        if (v == null) {
            Msg.send(p, "stockvault.none_selected");
            return 0;
        }
        Shop s = myShop(p);
        if (s == null) return 0;
        int range = ShopConfig.VAULT_RANGE.get();
        if (!s.dimension.equals(v.dim()) || s.pos().distSqr(v.pos()) > (double) range * range) {
            Msg.send(p, "stockvault.too_far");
            return 0;
        }
        UUID vaultOwner = StockVaults.owner(p.level(), v.pos());
        if (vaultOwner == null || !vaultOwner.equals(s.owner)) {
            Msg.send(p, "shop.not_owner");
            return 0;
        }
        s.vault = v.pos();
        Shops.save(s);
        Msg.send(p, "stockvault.linked", "shop", s.name);
        return 1;
    }

    private static int vaultRemove(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        BlockPos pos = target(p);
        Level level = p.level();
        if (pos != null && ShopContainers.isStockVault(level, pos)) {
            BlockPos controller = ShopCapabilities.controller(level.getBlockEntity(pos), pos);
            UUID owner = StockVaults.owner(level, controller);
            if (owner != null && (owner.equals(p.getUUID()) || Perm.has(p, Perm.SHOP_OTHERS))) {
                StockVaults.unlink(level, controller);
                Msg.send(p, "stockvault.unlinked_all");
                return 1;
            }
            Msg.send(p, "shop.not_owner");
            return 0;
        }
        Shop s = myShop(p);
        if (s == null || s.vault == null) return 0;
        s.vault = null;
        Shops.save(s);
        Msg.send(p, "stockvault.unlinked", "shop", s.name);
        return 1;
    }
}
