package com.tac5studios.elementseconomy.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;

import java.util.List;

/**
 * Master control file: config/elements_economy/features.toml
 * Feature switch off = everything under it off. Feature on = each child switch decides.
 * Switches marked (restart) need a server restart.
 */
public final class Features {

    public static final ModConfigSpec SPEC;

    // Economy core
    public static final BooleanValue ECONOMY, ECO_BALANCE, ECO_BALANCE_OTHERS, ECO_PAY, ECO_PAY_OFFLINE, ECO_PAY_TOGGLE,
            ECO_BALTOP, ECO_STARTING_BALANCE, ECO_GIVE, ECO_TAKE, ECO_SET, ECO_RESET, ECO_HISTORY, ECO_TRANSACTION_LOG;

    // Currency
    public static final BooleanValue CURRENCY_DIGITAL, CURRENCY_AUTO_DETECT, CURRENCY_USE_DETECTED, CURRENCY_MULTIPLE,
            CURRENCY_COUNT_HOLDERS, CURRENCY_EXCHANGE, CURRENCY_DEPOSIT, CURRENCY_WITHDRAW, CURRENCY_PAY_WITH_COINS,
            CURRENCY_DETECT_LOG;

    // Currency switch-over
    public static final BooleanValue SWITCH_OVER, SWITCH_PREVIEW, SWITCH_BACKUP, SWITCH_BALANCES, SWITCH_SHOPS,
            SWITCH_LISTINGS;

    // Shop bridge (other mods' shops)
    public static final BooleanValue SHOP_BRIDGE, BRIDGE_ITEM_PRICED, BRIDGE_MONEY_PRICED, BRIDGE_CONVERT_ON_LOAD;

    // Player shops
    public static final BooleanValue PLAYER_SHOPS, PS_CREATE_BUY, PS_CREATE_SELL, PS_RENAME, PS_REMOVE, PS_MANAGE,
            PS_SALE_ALERTS, PS_PROTECTION, PS_BLOCK_EXTRACTION, PS_EXPLOSION_BLOCK, PS_CLAIM_CHECK, PS_RANK_LIMITS,
            PS_CREATE_FEE, PS_DAILY_LIMITS, PS_CLAIM_ACCESS, PS_DISPLAY, PS_SIGN;

    // Shop containers
    public static final BooleanValue CONTAINERS_ANY, CONTAINERS_CHESTS, CONTAINERS_BARRELS, CONTAINERS_SHULKERS,
            CONTAINERS_COMMON_TAGS, CONTAINERS_ALLOW_TAG, CONTAINERS_ADD_HANDLERS;

    // Stock vaults
    public static final BooleanValue STOCK_VAULTS, SV_LINK, SV_AUTO_RESTOCK, SV_OVERFLOW_SALES, SV_RESTOCK_AFTER_SALE;

    // Auction house
    public static final BooleanValue AUCTION, AH_OPEN, AH_SELL, AH_BUY_NOW, AH_BIDS, AH_CANCEL, AH_COLLECT, AH_SEARCH,
            AH_CATEGORIES, AH_MY_LISTINGS, AH_SALE_ALERTS, AH_LISTING_FEE, AH_SALES_TAX, AH_RANK_LIMITS,
            AH_ADMIN_REMOVE, AH_BLACKLIST;

    // Server shops (staff only)
    public static final BooleanValue SERVER_SHOPS, SS_CREATE, SS_EDIT, SS_DELETE, SS_OPEN_COMMAND, SS_NPC_HOOK,
            SS_VILLAGER, SS_BLOCK, SS_STOCK_LIMITS, SS_RANK_PRICES;

    // Menus
    public static final BooleanValue UI_PAGES, UI_SEARCH, UI_SORT, UI_CATEGORIES, UI_CONFIRM, UI_SOUNDS,
            UI_BALANCE_BUTTON, UI_TOOLTIPS, UI_THEME_PACK;

    // Elements: Vault API
    public static final BooleanValue API, API_READ, API_WRITE, API_ASYNC, API_EVENTS, API_EXTERNAL_BACKENDS,
            API_EXTERNAL_BRIDGES;

    // Bridges to other money systems
    public static final BooleanValue BRIDGES, BRIDGE_IMPACTOR, BRIDGE_OCTO, BRIDGE_SDM, BRIDGE_LIGHTMANS_TYPE;

    // Nexus
    public static final BooleanValue NEXUS, NEXUS_RANK_LIMITS;

    // Claims
    public static final BooleanValue CLAIMS, CLAIMS_OPAC, CLAIMS_FTB_CHUNKS;

    // Overlap guard
    public static final BooleanValue OVERLAP_GUARD, OVERLAP_BRIDGE_FOUND;
    public static final ConfigValue<List<? extends String>> OVERLAP_FORCE_ON;

    // Migration
    public static final BooleanValue MIGRATION, MIGRATION_PREVIEW;

    // Admin
    public static final BooleanValue ADMIN, ADMIN_RELOAD, ADMIN_VERSION, ADMIN_STORAGE_CONVERT, ADMIN_CURRENCY_CONFIRM,
            ADMIN_CURRENCY_SWITCH;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment(
                "Elements: Economy master switches.",
                "Feature off turns off everything under it.",
                "Feature on lets each switch under it decide.",
                "Switches marked (restart) need a server restart."
        );

        b.comment("Balances and money commands.").push("economy");
        ECONOMY = sw(b, "enabled", true, "Turn the whole economy on or off. (restart)");
        ECO_BALANCE = sw(b, "balance", true, "Players can check their balance with /bal. (restart)");
        ECO_BALANCE_OTHERS = sw(b, "balance_others", true, "Staff can check other players' balances. (restart)");
        ECO_PAY = sw(b, "pay", true, "Players can send money with /pay. (restart)");
        ECO_PAY_OFFLINE = sw(b, "pay_offline", true, "Players can pay someone who is offline, when the currency allows it.");
        ECO_PAY_TOGGLE = sw(b, "pay_toggle", true, "Players can turn off receiving payments. (restart)");
        ECO_BALTOP = sw(b, "baltop", true, "Show the richest players with /baltop. (restart)");
        ECO_STARTING_BALANCE = sw(b, "starting_balance", true, "Give new players a starting balance. Digital money only.");
        ECO_GIVE = sw(b, "eco_give", true, "Staff can add money to a player. (restart)");
        ECO_TAKE = sw(b, "eco_take", true, "Staff can take money from a player. (restart)");
        ECO_SET = sw(b, "eco_set", true, "Staff can set a player's balance. (restart)");
        ECO_RESET = sw(b, "eco_reset", true, "Staff can reset a player's balance. (restart)");
        ECO_HISTORY = sw(b, "history", true, "Players can see their recent payments with /payments. (restart)");
        ECO_TRANSACTION_LOG = sw(b, "transaction_log", true, "Save every money change to a log file.");
        b.pop();

        b.comment("Which money the server uses.").push("currency");
        CURRENCY_DIGITAL = sw(b, "digital", true, "Use the built-in digital money when no currency mod is found.");
        CURRENCY_AUTO_DETECT = sw(b, "auto_detect", true, "Find every supported currency mod at startup. One switch covers them all. (restart)");
        CURRENCY_USE_DETECTED = sw(b, "use_detected", true, "Use the found currency mod instead of digital money. Falls back to digital if its balances can't be read. (restart)");
        CURRENCY_MULTIPLE = sw(b, "multiple_currencies", false, "Shops may accept more than one currency at the same time.");
        CURRENCY_COUNT_HOLDERS = sw(b, "count_holders", true, "Count coins kept inside wallets, bags and coin boxes.");
        CURRENCY_EXCHANGE = sw(b, "exchange", true, "Players can swap coins and digital money. (restart)");
        CURRENCY_DEPOSIT = sw(b, "deposit", true, "Players can turn coins into digital money. (restart)");
        CURRENCY_WITHDRAW = sw(b, "withdraw", true, "Players can turn digital money into coins. (restart)");
        CURRENCY_PAY_WITH_COINS = sw(b, "pay_with_coins", false, "Shops take and give coins straight from the inventory.");
        CURRENCY_DETECT_LOG = sw(b, "detect_log", true, "Log which currency mods were found.");
        b.pop();

        b.comment("Change the whole server to a new currency with one command.").push("switch_over");
        SWITCH_OVER = sw(b, "enabled", true, "Turn the currency switch-over on or off. (restart)");
        SWITCH_PREVIEW = sw(b, "preview", true, "Show what will change before anything is converted.");
        SWITCH_BACKUP = sw(b, "backup", true, "Save a backup before converting.");
        SWITCH_BALANCES = sw(b, "convert_balances", true, "Convert player balances.");
        SWITCH_SHOPS = sw(b, "convert_shops", true, "Convert shop prices and NPC trades.");
        SWITCH_LISTINGS = sw(b, "convert_listings", true, "Convert auction listings and bids.");
        b.pop();

        b.comment("Connect other mods' shops and auction houses to the server currency.").push("shop_bridge");
        SHOP_BRIDGE = sw(b, "enabled", true, "Turn the shop bridge on or off. (restart)");
        BRIDGE_ITEM_PRICED = sw(b, "item_priced", true, "Bridge shops priced in mod coins. Vanilla-item prices are never touched.");
        BRIDGE_MONEY_PRICED = sw(b, "money_priced", true, "Bridge shops priced in their own money.");
        BRIDGE_CONVERT_ON_LOAD = sw(b, "convert_on_load", true, "Convert block shops when their chunk loads.");
        b.pop();

        b.comment("Player shops made from containers.").push("player_shops");
        PLAYER_SHOPS = sw(b, "enabled", true, "Turn player shops on or off. (restart)");
        PS_CREATE_BUY = sw(b, "create_buy", true, "Players can make shops that sell to players with /shop create buy. (restart)");
        PS_CREATE_SELL = sw(b, "create_sell", true, "Players can make shops that buy from players with /shop create sell. (restart)");
        PS_RENAME = sw(b, "rename", true, "Owners can rename their shop. (restart)");
        PS_REMOVE = sw(b, "remove", true, "Owners can remove their shop. (restart)");
        PS_MANAGE = sw(b, "manage", true, "Owners can open the owner screen. (restart)");
        PS_SALE_ALERTS = sw(b, "sale_alerts", true, "Tell owners when their shop makes a sale.");
        PS_PROTECTION = sw(b, "protection", true, "Only the owner and staff can open or break a shop.");
        PS_BLOCK_EXTRACTION = sw(b, "block_extraction", true, "Hoppers, pipes and other mods cannot take items out of shops.");
        PS_EXPLOSION_BLOCK = sw(b, "explosion_block", true, "Explosions cannot break shops.");
        PS_CLAIM_CHECK = sw(b, "claim_check", false, "Players can only make shops inside their own claim.");
        PS_RANK_LIMITS = sw(b, "rank_limits", true, "Limit how many shops each rank can have.");
        PS_CREATE_FEE = sw(b, "create_fee", false, "Charge money to make a shop.");
        PS_DAILY_LIMITS = sw(b, "daily_limits", true, "Owners can set a max per player per day.");
        PS_CLAIM_ACCESS = sw(b, "claim_access", true, "Owners can let customers use a shop inside their claim without a claim warning.");
        PS_DISPLAY = sw(b, "item_display", true, "Show the shop's items floating above it.");
        PS_SIGN = sw(b, "sign", true, "Show a small sign above the shop: buying or selling, and open or closed.");
        b.pop();

        b.comment("Which blocks can be player shops.").push("containers");
        CONTAINERS_ANY = sw(b, "any_inventory", false, "Any block that holds items can be a shop. Off keeps it to chests and barrels.");
        CONTAINERS_CHESTS = sw(b, "chests", true, "Chests can be shops, including mod chests.");
        CONTAINERS_BARRELS = sw(b, "barrels", true, "Barrels can be shops, including mod barrels.");
        CONTAINERS_SHULKERS = sw(b, "shulker_boxes", true, "Shulker boxes can be shops.");
        CONTAINERS_COMMON_TAGS = sw(b, "common_tags", true, "Blocks tagged c:chests or c:barrels can be shops.");
        CONTAINERS_ALLOW_TAG = sw(b, "allow_tag", true, "Blocks in the elements_economy:shop_containers tag can be shops.");
        CONTAINERS_ADD_HANDLERS = sw(b, "add_missing_handlers", true, "Give chests and barrels that lack one an item handler, so shop locks work. (restart)");
        b.pop();

        b.comment("Bulk storage that refills linked shops.").push("stock_vaults");
        STOCK_VAULTS = sw(b, "enabled", true, "Turn stock vaults on or off. (restart)");
        SV_LINK = sw(b, "link", true, "Owners can link a vault to their shops. (restart)");
        SV_AUTO_RESTOCK = sw(b, "auto_restock", true, "Refill shops from the vault on a timer.");
        SV_OVERFLOW_SALES = sw(b, "overflow_sales", true, "Big orders can take the rest straight from the vault.");
        SV_RESTOCK_AFTER_SALE = sw(b, "restock_after_sale", true, "Refill a shop right after a sale.");
        b.pop();

        b.comment("Server-wide auction house.").push("auction_house");
        AUCTION = sw(b, "enabled", true, "Turn the auction house on or off. (restart)");
        AH_OPEN = sw(b, "open", true, "Players can open the auction house with /ah. (restart)");
        AH_SELL = sw(b, "sell", true, "Players can list items with /ah sell. (restart)");
        AH_BUY_NOW = sw(b, "buy_now", true, "Players can buy items at a set price.");
        AH_BIDS = sw(b, "bids", true, "Players can bid on items.");
        AH_CANCEL = sw(b, "cancel", true, "Players can cancel their own listings.");
        AH_COLLECT = sw(b, "collect", true, "Players can collect money and items from the collection box.");
        AH_SEARCH = sw(b, "search", true, "Players can search listings.");
        AH_CATEGORIES = sw(b, "categories", true, "Sort listings into categories.");
        AH_MY_LISTINGS = sw(b, "my_listings", true, "Players can see their own listings.");
        AH_SALE_ALERTS = sw(b, "sale_alerts", true, "Tell sellers when an item sells or a bid is placed.");
        AH_LISTING_FEE = sw(b, "listing_fee", false, "Charge money to list an item. Amount is set in auction_house.toml.");
        AH_SALES_TAX = sw(b, "sales_tax", false, "Take a cut of each sale. Amount is set in auction_house.toml.");
        AH_RANK_LIMITS = sw(b, "rank_limits", true, "Limit how many listings each rank can have.");
        AH_ADMIN_REMOVE = sw(b, "admin_remove", true, "Staff can remove any listing. (restart)");
        AH_BLACKLIST = sw(b, "blacklist", true, "Block certain items from being listed.");
        b.pop();

        b.comment("Server shops. Staff only. Players can never make or edit these.").push("server_shops");
        SERVER_SHOPS = sw(b, "enabled", true, "Turn server shops on or off. (restart)");
        SS_CREATE = sw(b, "create", true, "Staff can make server shops with /economy shop create. (restart)");
        SS_EDIT = sw(b, "edit", true, "Staff can edit server shops. (restart)");
        SS_DELETE = sw(b, "delete", true, "Staff can delete server shops. (restart)");
        SS_OPEN_COMMAND = sw(b, "open_command", true, "Players can open server shops with /shop open. (restart)");
        SS_NPC_HOOK = sw(b, "npc_hook", true, "Server shops can open from an NPC mod's NPC. Only used when that mod is installed.");
        SS_VILLAGER = sw(b, "villager", true, "Server shops can open from a vanilla villager.");
        SS_BLOCK = sw(b, "block", true, "Server shops can open from a block staff choose.");
        SS_STOCK_LIMITS = sw(b, "stock_limits", false, "Server shops can run out and refill on a timer.");
        SS_RANK_PRICES = sw(b, "rank_prices", false, "Server shop prices can change by rank.");
        b.pop();

        b.comment("Shop and auction house menus.").push("ui");
        UI_PAGES = sw(b, "pages", true, "Menus can have more than one page.");
        UI_SEARCH = sw(b, "search", true, "Menus have a search button.");
        UI_SORT = sw(b, "sort", true, "Menus have a sort button.");
        UI_CATEGORIES = sw(b, "categories", true, "Menus have a category button.");
        UI_CONFIRM = sw(b, "confirm_purchases", true, "Ask before paying.");
        UI_SOUNDS = sw(b, "sounds", true, "Play sounds on clicks, sales and errors.");
        UI_BALANCE_BUTTON = sw(b, "balance_button", true, "Show the player's balance in menus.");
        UI_TOOLTIPS = sw(b, "tooltips", true, "Show full details when hovering over a slot.");
        UI_THEME_PACK = sw(b, "theme_pack", false, "Use an optional resource pack for themed menus. Menus work without it.");
        b.pop();

        b.comment("Elements: Vault. Lets other mods use Elements: Economy money.").push("api");
        API = sw(b, "enabled", true, "Turn the API on or off. (restart)");
        API_READ = sw(b, "read", true, "Other mods can read balances.");
        API_WRITE = sw(b, "write", true, "Other mods can change balances.");
        API_ASYNC = sw(b, "async", true, "Other mods can make calls that finish later, for offline players.");
        API_EVENTS = sw(b, "events", true, "Send events for payments, sales and the currency switch-over.");
        API_EXTERNAL_BACKENDS = sw(b, "external_backends", true, "Other mods can add their own currency. (restart)");
        API_EXTERNAL_BRIDGES = sw(b, "external_bridges", true, "Other mods can add their own shop bridge. (restart)");
        b.pop();

        b.comment("Lets mods built on other money systems use Elements: Economy money.").push("bridges");
        BRIDGES = sw(b, "enabled", true, "Turn all bridges on or off. (restart)");
        BRIDGE_IMPACTOR = sw(b, "impactor_provider", true, "Be the money provider for Impactor. Only used when Impactor is installed. (restart)");
        BRIDGE_OCTO = sw(b, "octo_provider", true, "Be the money provider for OctoEconomy (Eights, Shoppy). Only used when installed. (restart)");
        BRIDGE_SDM = sw(b, "sdm_currency", true, "Show Elements: Economy money in SDM Economy and SDM Shop. Works through the Impactor provider, so Impactor must be installed. (restart)");
        BRIDGE_LIGHTMANS_TYPE = sw(b, "lightmans_currency_type", false, "Add a money type to Lightman's Currency. Needs a client add-on. Leave off. (restart)");
        b.pop();

        b.comment("Links to Elements: Nexus. Only used when Nexus is installed.").push("nexus");
        NEXUS = sw(b, "enabled", true, "Turn all Nexus links on or off. (restart)");
        NEXUS_RANK_LIMITS = sw(b, "rank_limits", true, "Use Nexus ranks for shop and listing limits.");
        b.pop();

        b.comment("Links to claim mods. Only used when the mod is installed.").push("claims");
        CLAIMS = sw(b, "enabled", true, "Turn all claim links on or off. (restart)");
        CLAIMS_OPAC = sw(b, "opac", true, "Use Open Parties and Claims for claim checks.");
        CLAIMS_FTB_CHUNKS = sw(b, "ftb_chunks", true, "Use FTB Chunks for claim checks.");
        b.pop();

        b.comment("Finds other shop mods, bridges them, and turns off the matching built-in feature.").push("overlap_guard");
        OVERLAP_GUARD = sw(b, "enabled", true, "Turn the overlap guard on or off. (restart)");
        OVERLAP_BRIDGE_FOUND = sw(b, "bridge_found_shops", true, "Connect found shop mods to the server currency.");
        OVERLAP_FORCE_ON = b.comment("Built-in features to keep on even when another mod does the same job.",
                        "Names: player_shops, server_shops, auction_house, economy_commands.")
                .defineListAllowEmpty("force_on", List.of(), () -> "", o -> o instanceof String);
        b.pop();

        b.comment("Import data from other economy and shop mods.").push("migration");
        MIGRATION = sw(b, "enabled", true, "Turn migration on or off. (restart)");
        MIGRATION_PREVIEW = sw(b, "preview", true, "Show what will be imported before it runs.");
        b.pop();

        b.comment("Admin commands under /economy.").push("admin");
        ADMIN = sw(b, "enabled", true, "Turn admin commands on or off. (restart)");
        ADMIN_RELOAD = sw(b, "reload", true, "Reload config files with /economy reload. (restart)");
        ADMIN_VERSION = sw(b, "version", true, "Show the mod version with /economy version. (restart)");
        ADMIN_STORAGE_CONVERT = sw(b, "storage_convert", true, "Copy data to another storage type. (restart)");
        ADMIN_CURRENCY_CONFIRM = sw(b, "currency_confirm", true, "Admins confirm or ignore vanilla items added as currency. (restart)");
        ADMIN_CURRENCY_SWITCH = sw(b, "currency_switch", true, "Admins change the server currency with /economy currency switch. (restart)");
        b.pop();

        SPEC = b.build();
    }

    private Features() {}

    private static BooleanValue sw(ModConfigSpec.Builder b, String key, boolean def, String comment) {
        return b.comment(comment).define(key, def);
    }

    /** Registers config/elements_economy/features.toml. Call from the mod constructor. */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "elements_economy/features.toml");
    }

    /** True only when the parent feature and the child switch are both on. */
    public static boolean on(BooleanValue feature, BooleanValue child) {
        return on(feature) && on(child);
    }

    /** True when the feature is on (and the overlap guard didn't turn it off for another mod). */
    public static boolean on(BooleanValue feature) {
        return feature.get() && !com.tac5studios.elementseconomy.overlap.OverlapGuard.blocks(feature);
    }

    /** True when a feature name is listed in overlap_guard.force_on. */
    public static boolean forcedOn(String feature) {
        return OVERLAP_FORCE_ON.get().contains(feature);
    }
}
