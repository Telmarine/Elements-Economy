package com.tac5studios.elementseconomy.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** config/elements_economy/shops.toml — player shops and stock vaults. */
public final class ShopConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue DEFAULT_LIMIT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RANK_LIMITS;
    public static final ModConfigSpec.ConfigValue<String> CREATE_FEE;
    public static final ModConfigSpec.IntValue NAME_MAX;
    public static final ModConfigSpec.IntValue USE_DISTANCE;

    public static final ModConfigSpec.IntValue DISPLAY_CYCLE;
    public static final ModConfigSpec.DoubleValue ITEM_SCALE;
    public static final ModConfigSpec.DoubleValue SIGN_SCALE;
    public static final ModConfigSpec.BooleanValue SIGN_NAME;
    public static final ModConfigSpec.BooleanValue SIGN_BACKGROUND;

    public static final ModConfigSpec.IntValue VAULT_RANGE;
    public static final ModConfigSpec.IntValue RESTOCK_BELOW;
    public static final ModConfigSpec.IntValue RESTOCK_SECONDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Player shops.").push("player_shops");
        DEFAULT_LIMIT = b.comment("Shops each player can own. -1 = no limit.").defineInRange("default_limit", 5, -1, 10000);
        RANK_LIMITS = b.comment(
                "Shop limits per Nexus rank. Needs player_shops.rank_limits and nexus.rank_limits on.",
                "Each line: rank = limit, e.g. \"vip=10\". Ranks not listed use default_limit."
        ).defineListAllowEmpty("rank_limits", List.of(), () -> "rank=5", o -> o instanceof String s && s.contains("="));
        CREATE_FEE = b.comment("Cost to make a shop, in the primary currency. Needs player_shops.create_fee on.")
                .define("create_fee", "100");
        NAME_MAX = b.comment("Longest shop name allowed.").defineInRange("name_max_length", 32, 4, 64);
        USE_DISTANCE = b.comment("How far (blocks) a player can be from a shop before its screen closes.")
                .defineInRange("use_distance", 8, 3, 64);
        b.pop();

        b.comment("The item and sign floating above each shop.").push("display");
        DISPLAY_CYCLE = b.comment("Seconds each item shows before the next one, when a shop has several.")
                .defineInRange("cycle_seconds", 3, 1, 60);
        ITEM_SCALE = b.comment("Size of the floating item. 1 = a dropped item's size.").defineInRange("item_scale", 0.5, 0.1, 2.0);
        SIGN_SCALE = b.comment("Size of the sign text. 1 = name tag size.").defineInRange("sign_scale", 0.5, 0.1, 2.0);
        SIGN_NAME = b.comment("Show the shop's name on the sign.").define("sign_shows_name", true);
        SIGN_BACKGROUND = b.comment("Dark background behind the sign text.").define("sign_background", true);
        b.pop();

        b.comment("Stock vaults (Create item vaults and other bulk storage).").push("stock_vaults");
        VAULT_RANGE = b.comment("Furthest a linked shop can be from its vault, in blocks.").defineInRange("vault_range", 64, 4, 512);
        RESTOCK_BELOW = b.comment("Refill a shop item when it has fewer than this many.").defineInRange("restock_below", 16, 1, 6400);
        RESTOCK_SECONDS = b.comment("Seconds between restock checks.").defineInRange("restock_seconds", 10, 1, 3600);
        b.pop();

        SPEC = b.build();
    }

    private ShopConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "elements_economy/shops.toml");
    }
}
