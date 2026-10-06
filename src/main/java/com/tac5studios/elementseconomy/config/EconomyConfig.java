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

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        PRIMARY = b.comment(
                "The currency shops and /bal use.",
                "auto = the found currency mod when currency.use_detected is on, otherwise digital money.",
                "Or a currency id, e.g. \"elements_economy:digital\" or \"aiycoin:coins\"."
        ).define("primary_currency", "auto");

        b.comment("Built-in digital money.").push("digital");
        DIGITAL_NAME = b.comment("Name for one.").define("name", "Coin");
        DIGITAL_PLURAL = b.comment("Name for more than one.").define("plural", "Coins");
        DIGITAL_SYMBOL = b.comment("Symbol shown before amounts. Leave empty for none.").define("symbol", "");
        DIGITAL_DECIMALS = b.comment("Decimal places, e.g. 2 for 12.50. Change this only before anyone has money. (restart)")
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
                "Exchange rates, used by the currency switch-over and coin exchange.",
                "Each line: currency id = what its smallest unit is worth in digital money.",
                "Example: \"aiycoin:coins=1\" means one Stone Coin is worth 1 Coin.",
                "A currency with no line has no rate and can't be converted."
        ).defineListAllowEmpty("rates", List.of(), () -> "modid:coins=1", o -> o instanceof String s && s.contains("="));

        b.comment("Settings for currency mods that keep money in their own data.").push("currency_mods");
        SDM_CURRENCY = b.comment("SDM Economy currency to use.").define("sdm_currency", "basic_money");
        b.pop();

        SPEC = b.build();
    }

    private EconomyConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "elements_economy/economy.toml");
    }
}
