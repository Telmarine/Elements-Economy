package com.tac5studios.elementseconomy.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** config/elements_economy/economy.toml */
public final class EconomyConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> PRIMARY;

    public static final ModConfigSpec.ConfigValue<String> DIGITAL_NAME;
    public static final ModConfigSpec.ConfigValue<String> DIGITAL_PLURAL;
    public static final ModConfigSpec.ConfigValue<String> DIGITAL_SYMBOL;
    public static final ModConfigSpec.IntValue DIGITAL_DECIMALS;
    public static final ModConfigSpec.ConfigValue<String> DIGITAL_ICON;
    public static final ModConfigSpec.ConfigValue<String> STARTING_BALANCE;

    public static final ModConfigSpec.ConfigValue<String> PAY_MIN;
    public static final ModConfigSpec.IntValue BALTOP_PAGE;
    public static final ModConfigSpec.IntValue HISTORY_PAGE;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RATES;

    public static final ModConfigSpec.ConfigValue<String> SDM_CURRENCY;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCHANGE_NAMES;
    public static final ModConfigSpec.ConfigValue<String> PAY_ORDER;

    public static final ModConfigSpec.IntValue BACKUPS_KEPT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        PRIMARY = b.comment(
                "The currency shops and /bal use.",
                "auto = the found currency mod when currency.use_detected is on, otherwise digital money.",
                "Or pick one: \"elements_economy:digital\" for digital money, or the full ID of one of the",
                "currency items, e.g. \"minecraft:emerald\" to use the confirmed vanilla items."
        ).define("primary_currency", "auto");

        b.comment("Built-in digital money.").push("digital");
        DIGITAL_NAME = b.comment("Name for one.").define("name", "Coin");
        DIGITAL_PLURAL = b.comment("Name for more than one.").define("plural", "Coins");
        DIGITAL_SYMBOL = b.comment("Symbol shown before amounts. Leave empty for none.").define("symbol", "");
        DIGITAL_DECIMALS = b.comment("Decimal places, e.g. 2 for 12.50. Change this only before anyone has money.")
                .defineInRange("decimals", 0, 0, 4);
        DIGITAL_ICON = b.comment("Item that shows digital money in menus.").define("icon", "minecraft:paper");
        STARTING_BALANCE = b.comment("Money new players start with. Needs economy.starting_balance on.")
                .define("starting_balance", "0");
        b.pop();

        b.comment("Commands.").push("commands");
        PAY_MIN = b.comment("Smallest amount /pay accepts.").define("pay_min", "1");
        BALTOP_PAGE = b.comment("Players per /baltop page.").defineInRange("baltop_page_size", 10, 1, 50);
        HISTORY_PAGE = b.comment("Payments per /payments page.").defineInRange("history_page_size", 10, 1, 50);
        b.pop();

        RATES = b.comment(
                "Money is 1:1 between currencies: 1 of any currency is worth 1 of any other.",
                "The only exception: when the server moves from digital money to items players carry (vanilla items",
                "or a money mod) with /economy currency switch or /economy migrate, you can set what one of the",
                "smallest items is worth in the old digital money. One line per currency: \"<currency id>=<amount>\".",
                "Press Tab on /economy currency switch to see the currency ids. No line = 1:1."
        ).defineListAllowEmpty("rates", List.of(), () -> "modid:coins=1", o -> o instanceof String s && s.contains("="));

        b.comment("Settings for currency mods that keep money in their own data.").push("currency_mods");
        SDM_CURRENCY = b.comment("SDM Economy currency to use.").define("sdm_currency", "basic_money");
        b.pop();

        b.comment("/exchange").push("exchange");
        EXCHANGE_NAMES = b.comment(
                "Names players see in the Tab list for the [item] part of /exchange, and can type instead of the item ID.",
                "One line per currency item: \"<item id>=<name>\". Items without a line use their in-game name",
                "without \"Coin\" (Gold Coin -> Gold). Names can't have spaces. Not case-sensitive when typed."
        ).defineListAllowEmpty("names", List.of(), () -> "modid:item=Name", o -> o instanceof String s && s.contains("="));
        b.pop();

        b.comment("Paying for items with the bank (features.toml currency.pay_from_bank).").push("bank");
        PAY_ORDER = b.comment(
                "Which money goes first when a player buys something and the server uses currency items.",
                "hands_first = the currency items they carry first, the bank covers the rest",
                "bank_first  = the bank first, the currency items they carry cover the rest"
        ).defineInList("pay_order", "hands_first", java.util.Arrays.asList("hands_first", "bank_first"));
        b.pop();

        b.comment("Config files Economy rewrites itself (messages.toml, currency_values.toml).").push("files");
        BACKUPS_KEPT = b.comment("How many backups of each file to keep. The oldest are deleted. 0 = keep none.")
                .defineInRange("backups_kept", 2, 0, 20);
        b.pop();

        SPEC = b.build();
    }

    private EconomyConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "elements_economy/economy.toml");
    }
}
