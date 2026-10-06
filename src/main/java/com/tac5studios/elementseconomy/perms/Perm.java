package com.tac5studios.elementseconomy.perms;

import net.neoforged.api.distmarker.Dist;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Every Elements: Economy permission node, and the one place that checks them.
 *
 * Nodes go through the NeoForge PermissionAPI, so they work with:
 *  - Elements: Nexus ranks (permissionHandler = "elements_nexus:permissions"),
 *  - any other permission mod (LuckPerms, FTB Ranks ...),
 *  - or plain OP levels when nothing else is installed.
 * Economy never needs Nexus for this.
 *
 * A node whose feature switch is off is not registered at all.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Perm {

    /** Who gets a node when no permission mod says otherwise. */
    public enum Who {
        PLAYER(0),  // everyone
        STAFF(2),   // OP level 2 and up
        ADMIN(3);   // OP level 3 and up

        final int opLevel;

        Who(int opLevel) {
            this.opLevel = opLevel;
        }
    }

    private record Entry(PermissionNode<Boolean> node, Who who, BooleanSupplier gate) {}

    private static final Map<String, Entry> NODES = new LinkedHashMap<>();
    private static final Map<String, PermissionNode<Integer>> LIMITS = new LinkedHashMap<>();
    private static final Map<String, BooleanSupplier> LIMIT_GATES = new LinkedHashMap<>();

    // ---------- economy core ----------
    public static final String BALANCE = node("balance", Who.PLAYER, Features.ECONOMY, Features.ECO_BALANCE);
    public static final String BALANCE_OTHERS = node("balance.others", Who.STAFF, Features.ECONOMY, Features.ECO_BALANCE_OTHERS);
    public static final String PAY = node("pay", Who.PLAYER, Features.ECONOMY, Features.ECO_PAY);
    public static final String PAY_OFFLINE = node("pay.offline", Who.PLAYER, Features.ECONOMY, Features.ECO_PAY_OFFLINE);
    public static final String PAY_TOGGLE = node("pay.toggle", Who.PLAYER, Features.ECONOMY, Features.ECO_PAY_TOGGLE);
    public static final String BALTOP = node("baltop", Who.PLAYER, Features.ECONOMY, Features.ECO_BALTOP);
    public static final String HISTORY = node("history", Who.PLAYER, Features.ECONOMY, Features.ECO_HISTORY);
    public static final String HISTORY_OTHERS = node("history.others", Who.STAFF, Features.ECONOMY, Features.ECO_HISTORY);
    public static final String ECO_GIVE = node("eco.give", Who.ADMIN, Features.ECONOMY, Features.ECO_GIVE);
    public static final String ECO_TAKE = node("eco.take", Who.ADMIN, Features.ECONOMY, Features.ECO_TAKE);
    public static final String ECO_SET = node("eco.set", Who.ADMIN, Features.ECONOMY, Features.ECO_SET);
    public static final String ECO_RESET = node("eco.reset", Who.ADMIN, Features.ECONOMY, Features.ECO_RESET);

    // ---------- currency ----------
    public static final String EXCHANGE_DEPOSIT = node("exchange.deposit", Who.PLAYER, Features.CURRENCY_EXCHANGE, Features.CURRENCY_DEPOSIT);
    public static final String EXCHANGE_WITHDRAW = node("exchange.withdraw", Who.PLAYER, Features.CURRENCY_EXCHANGE, Features.CURRENCY_WITHDRAW);
    /** /economy currency pending | confirm | ignore (vanilla item "Are you sure?"). */
    public static final String CURRENCY_CONFIRM = node("currency.confirm", Who.ADMIN, Features.ADMIN, Features.ADMIN_CURRENCY_CONFIRM);
    /** /economy currency switch <new> and switch confirm. */
    public static final String CURRENCY_SWITCH = node("currency.switch", Who.ADMIN,
            () -> Features.on(Features.ADMIN, Features.ADMIN_CURRENCY_SWITCH) && Features.on(Features.SWITCH_OVER));

    // ---------- player shops ----------
    public static final String SHOP_CREATE_BUY = node("shop.create.buy", Who.PLAYER, Features.PLAYER_SHOPS, Features.PS_CREATE_BUY);
    public static final String SHOP_CREATE_SELL = node("shop.create.sell", Who.PLAYER, Features.PLAYER_SHOPS, Features.PS_CREATE_SELL);
    public static final String SHOP_RENAME = node("shop.rename", Who.PLAYER, Features.PLAYER_SHOPS, Features.PS_RENAME);
    public static final String SHOP_REMOVE = node("shop.remove", Who.PLAYER, Features.PLAYER_SHOPS, Features.PS_REMOVE);
    public static final String SHOP_MANAGE = node("shop.manage", Who.PLAYER, Features.PLAYER_SHOPS, Features.PS_MANAGE);
    public static final String SHOP_ALERTS = node("shop.alerts", Who.PLAYER, Features.PLAYER_SHOPS, Features.PS_SALE_ALERTS);
    /** Staff can rename, manage or remove anyone's shop. */
    public static final String SHOP_OTHERS = node("shop.others", Who.STAFF, () -> Features.on(Features.PLAYER_SHOPS));
    /** Staff can open and break shop containers they don't own. */
    public static final String SHOP_BYPASS = node("shop.bypass", Who.STAFF, Features.PLAYER_SHOPS, Features.PS_PROTECTION);
    /** Create shops outside your own claims. */
    public static final String SHOP_CLAIM_BYPASS = node("shop.claim.bypass", Who.STAFF, Features.PLAYER_SHOPS, Features.PS_CLAIM_CHECK);
    /** No fee when creating a shop. */
    public static final String SHOP_FREE = node("shop.free", Who.STAFF, Features.PLAYER_SHOPS, Features.PS_CREATE_FEE);
    /** No limit on how many shops you own. */
    public static final String SHOP_NO_LIMIT = node("shop.nolimit", Who.STAFF, Features.PLAYER_SHOPS, Features.PS_RANK_LIMITS);
    /** Link a Create item vault to your shops as a stock vault. */
    public static final String STOCK_VAULT_LINK = node("stockvault.link", Who.PLAYER, Features.STOCK_VAULTS, Features.SV_LINK);

    // ---------- server shops (players can only buy and sell, never create or edit) ----------
    /** Buy and sell at server shops (any way they open). */
    public static final String SERVER_SHOP_USE = node("servershop.use", Who.PLAYER, () -> Features.on(Features.SERVER_SHOPS));
    public static final String SERVER_SHOP_CREATE = node("servershop.create", Who.ADMIN, Features.SERVER_SHOPS, Features.SS_CREATE);
    public static final String SERVER_SHOP_EDIT = node("servershop.edit", Who.ADMIN, Features.SERVER_SHOPS, Features.SS_EDIT);
    public static final String SERVER_SHOP_DELETE = node("servershop.delete", Who.ADMIN, Features.SERVER_SHOPS, Features.SS_DELETE);

    // ---------- auction house ----------
    public static final String AH_OPEN = node("ah.open", Who.PLAYER, Features.AUCTION, Features.AH_OPEN);
    public static final String AH_SELL = node("ah.sell", Who.PLAYER, Features.AUCTION, Features.AH_SELL);
    public static final String AH_BUY = node("ah.buy", Who.PLAYER, Features.AUCTION, Features.AH_BUY_NOW);
    public static final String AH_BID = node("ah.bid", Who.PLAYER, Features.AUCTION, Features.AH_BIDS);
    public static final String AH_CANCEL = node("ah.cancel", Who.PLAYER, Features.AUCTION, Features.AH_CANCEL);
    public static final String AH_COLLECT = node("ah.collect", Who.PLAYER, Features.AUCTION, Features.AH_COLLECT);
    public static final String AH_ALERTS = node("ah.alerts", Who.PLAYER, Features.AUCTION, Features.AH_SALE_ALERTS);
    /** No listing fee. */
    public static final String AH_NO_FEE = node("ah.nofee", Who.STAFF, Features.AUCTION, Features.AH_LISTING_FEE);
    /** No sales tax. */
    public static final String AH_NO_TAX = node("ah.notax", Who.STAFF, Features.AUCTION, Features.AH_SALES_TAX);
    /** No limit on how many listings you have. */
    public static final String AH_NO_LIMIT = node("ah.nolimit", Who.STAFF, Features.AUCTION, Features.AH_RANK_LIMITS);
    /** Remove anyone's listing. */
    public static final String AH_REMOVE = node("ah.remove", Who.STAFF, Features.AUCTION, Features.AH_ADMIN_REMOVE);
    /** Edit the list of items that can't be sold. */
    public static final String AH_BLACKLIST = node("ah.blacklist", Who.ADMIN, Features.AUCTION, Features.AH_BLACKLIST);

    // ---------- admin ----------
    public static final String ADMIN_RELOAD = node("admin.reload", Who.ADMIN, Features.ADMIN, Features.ADMIN_RELOAD);
    public static final String ADMIN_VERSION = node("admin.version", Who.STAFF, Features.ADMIN, Features.ADMIN_VERSION);
    public static final String ADMIN_STORAGE = node("admin.storage", Who.ADMIN, Features.ADMIN, Features.ADMIN_STORAGE_CONVERT);
    public static final String ADMIN_MIGRATE = node("admin.migrate", Who.ADMIN, () -> Features.on(Features.MIGRATION));

    // ---------- number nodes (limits) ----------
    /** How many shops a player may own. -1 = use the default from the config. */
    public static final String SHOP_LIMIT = limit("shop.limit", Features.PLAYER_SHOPS, Features.PS_RANK_LIMITS);
    /** How many auction listings a player may have. -1 = use the default from the config. */
    public static final String AH_LIMIT = limit("ah.limit", Features.AUCTION, Features.AH_RANK_LIMITS);

    private Perm() {}

    // ---------- building nodes ----------

    private static String node(String path, Who who, BooleanValue feature, BooleanValue child) {
        return node(path, who, () -> Features.on(feature, child));
    }

    /** Create a yes/no node named "economy.<path>". */
    private static String node(String path, Who who, BooleanSupplier gate) {
        PermissionNode<Boolean> n = new PermissionNode<>("economy", path, PermissionTypes.BOOLEAN,
                (player, uuid, ctx) -> player != null && player.hasPermissions(who.opLevel));
        n.setInformation(net.minecraft.network.chat.Component.literal("economy." + path),
                net.minecraft.network.chat.Component.literal("Default: " + defaultText(who)));
        NODES.put(n.getNodeName(), new Entry(n, who, gate));
        return n.getNodeName();
    }

    /** Create a number node named "economy.<path>". Default -1 means "use the config". */
    private static String limit(String path, BooleanValue feature, BooleanValue child) {
        PermissionNode<Integer> n = new PermissionNode<>("economy", path, PermissionTypes.INTEGER,
                (player, uuid, ctx) -> -1);
        LIMITS.put(n.getNodeName(), n);
        LIMIT_GATES.put(n.getNodeName(), () -> Features.on(feature, child));
        return n.getNodeName();
    }

    /** Tell NeoForge about nodes whose features are on. Runs at server start, after configs load. */
    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        List<PermissionNode<?>> on = new ArrayList<>();
        for (Entry e : NODES.values()) if (e.gate().getAsBoolean()) on.add(e.node());
        LIMITS.forEach((name, n) -> {
            if (LIMIT_GATES.get(name).getAsBoolean()) on.add(n);
        });
        event.addNodes(on.toArray(new PermissionNode<?>[0]));
        ElementsEconomy.LOGGER.info("[Economy] {} permission nodes registered.", on.size());
    }

    // ---------- checking ----------

    /** True when the node's feature is on. Commands use this to decide whether to register. */
    public static boolean enabled(String node) {
        Entry e = NODES.get(node);
        if (e != null) return e.gate().getAsBoolean();
        BooleanSupplier g = LIMIT_GATES.get(node);
        return g != null && g.getAsBoolean();
    }

    /** Can this command source use this node? Console and command blocks use their OP level. */
    public static boolean has(CommandSourceStack source, String node) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            Entry e = NODES.get(node);
            Who who = e == null ? Who.ADMIN : e.who();
            return source.hasPermission(Math.max(who.opLevel, 2));
        }
        return has(player, node);
    }

    public static boolean has(ServerPlayer player, String node) {
        Entry e = NODES.get(node);
        if (e == null || !e.gate().getAsBoolean()) return false; // unknown or switched off
        try {
            return PermissionAPI.getPermission(player, e.node());
        } catch (RuntimeException ex) {
            ElementsEconomy.LOGGER.warn("[Economy] Permission check failed for {}: {}", node, ex.toString());
            return player.hasPermissions(e.who().opLevel);
        }
    }

    /**
     * A player's limit from a number node, or -1 when no permission mod sets one.
     * Callers fall back to the rank table (Nexus) and then the config default.
     */
    public static int limit(ServerPlayer player, String node) {
        PermissionNode<Integer> n = LIMITS.get(node);
        if (n == null || !LIMIT_GATES.get(node).getAsBoolean()) return -1;
        try {
            Integer v = PermissionAPI.getPermission(player, n);
            return v == null ? -1 : v;
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    private static String defaultText(Who who) {
        return switch (who) {
            case PLAYER -> "everyone";
            case STAFF -> "OP level 2+";
            case ADMIN -> "OP level 3+";
        };
    }
}
