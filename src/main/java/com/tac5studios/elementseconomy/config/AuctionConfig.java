package com.tac5studios.elementseconomy.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

/** config/elements_economy/auction_house.toml */
public final class AuctionConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> FEE_MODE;
    public static final ModConfigSpec.ConfigValue<String> FEE_AMOUNT;
    public static final ModConfigSpec.DoubleValue FEE_PERCENT;
    public static final ModConfigSpec.DoubleValue TAX_PERCENT;

    public static final ModConfigSpec.DoubleValue BID_STEP_PERCENT;
    public static final ModConfigSpec.ConfigValue<String> BID_STEP_MIN;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> DURATIONS;

    public static final ModConfigSpec.IntValue DEFAULT_LIMIT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RANK_LIMITS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    public static final ModConfigSpec.IntValue HISTORY_DAYS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Listing fee. Needs auction_house.listing_fee on in features.toml.").push("fee");
        FEE_MODE = b.comment("flat = a set amount per listing. percent = a share of the listing's price.")
                .defineInList("mode", "flat", Arrays.asList("flat", "percent"));
        FEE_AMOUNT = b.comment("Flat fee, in the primary currency.").define("amount", "10");
        FEE_PERCENT = b.comment("Percent fee, e.g. 2.5 for 2.5%.").defineInRange("percent", 2.5, 0.0, 100.0);
        b.pop();

        b.comment("Sales tax. Needs auction_house.sales_tax on in features.toml.").push("tax");
        TAX_PERCENT = b.comment("Percent taken from each sale, e.g. 5 for 5%.").defineInRange("percent", 5.0, 0.0, 100.0);
        b.pop();

        b.comment("Auctions (bids).").push("bids");
        BID_STEP_PERCENT = b.comment("Each new bid must beat the current one by at least this percent.")
                .defineInRange("step_percent", 5.0, 0.0, 100.0);
        BID_STEP_MIN = b.comment("…and by at least this amount.").define("step_min", "1");
        DURATIONS = b.comment("Listing lengths sellers can pick, in hours. The first is the default.")
                .defineListAllowEmpty("durations_hours", List.of(24, 12, 6, 1), () -> 24, o -> o instanceof Integer i && i > 0 && i <= 720);
        b.pop();

        b.comment("Limits.").push("limits");
        DEFAULT_LIMIT = b.comment("Active listings each player can have. -1 = no limit.").defineInRange("default_limit", 10, -1, 10000);
        RANK_LIMITS = b.comment(
                "Listing limits per Nexus rank. Needs auction_house.rank_limits and nexus.rank_limits on.",
                "Each line: rank = limit, e.g. \"vip=20\"."
        ).defineListAllowEmpty("rank_limits", List.of(), () -> "rank=10", o -> o instanceof String s && s.contains("="));
        BLACKLIST = b.comment(
                "Items that can't be listed. Item ids (\"minecraft:bedrock\") or item tags (\"#c:ores\").",
                "Needs auction_house.blacklist on. Staff can also use /ah blacklist add while holding the item."
        ).defineListAllowEmpty("blacklist", List.of("minecraft:bedrock", "minecraft:barrier", "minecraft:command_block"),
                () -> "minecraft:bedrock", o -> o instanceof String);
        HISTORY_DAYS = b.comment("Days sold and expired listings stay in My listings.").defineInRange("history_days", 7, 0, 365);
        b.pop();

        SPEC = b.build();
    }

    private AuctionConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "elements_economy/auction_house.toml");
    }
}
