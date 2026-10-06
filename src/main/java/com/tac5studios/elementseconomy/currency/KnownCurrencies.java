package com.tac5studios.elementseconomy.currency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every currency mod Elements: Economy can find on NeoForge 1.21.1.
 * Class names and item values were checked against each mod's source or jar (Oct 2026).
 * Item values are in that mod's smallest unit. Owners change them in currency_values.toml.
 */
public final class KnownCurrencies {

    public static final List<KnownCurrency> ALL;

    static {
        List<KnownCurrency> l = new ArrayList<>();

        // ---------------- Digital balances with an API ----------------
        l.add(api("cobbledollars", "CobbleDollars",
                "Player NBT 'CobbleDollars'; offline mirror world/cobbledollarsplayerdata/<uuid>.json",
                "fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt"));
        l.add(api("lightmanscurrency", "Lightman's Currency",
                "MoneyAPI (wallet + inventory) and BankAPI",
                "io.github.lightman314.lightmanscurrency.api.money.MoneyAPI"));
        l.add(api("sdm_economy", "SDM Economy",
                "CurrencyHelper, named currencies",
                "net.sixik.sdm_economy.api.CurrencyHelper"));
        l.add(api("sdmeconomy", "SDM Economy (old API)",
                "CurrencyHelper, named currencies",
                "net.sixik.sdmeconomy.utils.CurrencyHelper"));
        l.add(new KnownCurrency("numismatics", "Create: Numismatics", CurrencyKind.API,
                List.of("dev.ithundxr.createnumismatics.Numismatics"),
                items("spur", 1, "bevel", 8, "sprocket", 16, "cog", 64, "crown", 512, "sun", 4096),
                "Numismatics.BANK accounts (spurs); SavedData numismatics_bank"));
        l.add(api("sg_economy", "SG-Economy API",
                "SGEconomyApi balance per entity",
                "net.sirgrantd.sg_economy.api.SGEconomyApi"));
        l.add(api("impactor", "Impactor",
                "EconomyService accounts (async)",
                "net.impactdev.impactor.api.economy.EconomyService"));
        l.add(api("economycraft", "EconomyCraft",
                "EconomyCraftApi balances; balances.json",
                "com.reazip.economycraft.api.v1.EconomyCraftApi"));
        l.add(api("eights_economy_p", "Eights Economy P",
                "OctoEconomy user accounts",
                "com.epherical.octoecon.api.Economy"));
        l.add(new KnownCurrency("banksystem", "KROIA BankSystem", CurrencyKind.API,
                List.of("net.kroia.banksystem.api.bankmanager.IServerBankManager"),
                items("money_cent5", 5, "money_cent10", 10, "money_cent20", 20, "money_cent50", 50,
                        "money", 100, "money5", 500, "money10", 1000, "money20", 2000, "money50", 5000,
                        "money100", 10000, "money200", 20000, "money500", 50000, "money1000", 100000,
                        "money5000", 500000, "money10000", 1000000, "money20000", 2000000,
                        "money50000", 5000000, "money100000", 10000000, "money500000", 50000000,
                        "money1000000", 100000000),
                "IServerBankManager accounts; banksystem.db"));
        l.add(api("dicemcmm", "DiceMC Money",
                "IMoneyManager (MoneyWSD SavedData)",
                "dicemc.money.api.IMoneyManager"));
        l.add(new KnownCurrency("saros__money_mod", "Saro's Money", CurrencyKind.API,
                List.of("de.sarocesch.sarosmoneymod.data.BalanceManager"),
                items("cent_1", 1, "cent_2", 2, "cent_5", 5, "cent_10", 10, "cent_20", 20, "cent_50", 50,
                        "euro_1", 100, "euro_2", 200, "euro_5", 500, "euro_10", 1000, "euro_20", 2000,
                        "euro_50", 5000, "euro_100", 10000, "euro_200", 20000, "euro_500", 50000),
                "SavedData sarosmoneymod_balance; works offline"));
        l.add(api("migueleconomy", "MiguelEconomy",
                "Player data 'migueleconomy_balance' (default 1000); works offline",
                "com.miguel.economy.utils.MoneyUtils"));
        l.add(api("bubusteinmoneymod", "Bubustein's Money",
                "SavedData bubustein_bank_accounts; many accounts and currencies per player",
                "com.bubustein.money.bank.BankAccountSavedData"));
        l.add(api("arcadia_lib", "Arcadia Lib (Arcadia AH)",
                "EconomyService: Numismatics, emerald wallet, or one item",
                "com.arcadia.lib.economy.EconomyService"));
        l.add(new KnownCurrency("avecoins", "Avecoins", CurrencyKind.API,
                List.of("net.sundggs.avecoins.shop.WalletStore"),
                items("coppercoin", 1, "ironcoin", 4, "goldcoin", 16, "diamondcoin", 64, "netheritecoin", 256),
                "world/data/avecoins/wallets.json; several currencies"));
        l.add(new KnownCurrency("sweconm", "Sweconm", CurrencyKind.ITEMS,
                List.of("com.alaharranhonor.sweconm.utils.PlayerUtils"),
                items("thym_copper", 1, "thym_iron", 5, "thym_emerald", 10, "thym_gold", 20,
                        "thym_diamond", 50, "thym_netherite", 100, "thym_amethyst", 500),
                "Thym coins in inventory and wallets"));
        l.add(api("currency", "Never Enough Currency 2",
                "SavedData currency_world_data accounts (cents)",
                "com.zundrel.currency.CurrencyData"));
        l.add(api("simpleeconomy", "Simple Economy",
                "EconomyManager SavedData",
                "com.simpleeconomy.economy.EconomyManager"));
        l.add(api("omnieconomy", "OmniEconomy",
                "SavedData omnieconomy",
                "com.saunhardy.omnieconomy.core.Economy"));

        // ---------------- Balance stored on the player ----------------
        l.add(new KnownCurrency("currency", "M Economy", CurrencyKind.PLAYER_DATA,
                List.of("currency.network.CurrencyModVariables"),
                items("dollar_bill", 1, "five_dollar_bill", 5, "ten_dollar_bill", 10, "twenty_dollar_bill", 20,
                        "fifty_dollar_bill", 50, "hundred_dollar_bill", 100),
                "Attachment currency:player_variables, field Balance"));
        l.add(new KnownCurrency("kuronomy", "Kuro's Economy", CurrencyKind.PLAYER_DATA,
                List.of("kuronomy.network.KuronomyModVariables"),
                items("dollar_coin", 1, "dollar_5", 5, "dollar_10", 10, "dollar_50", 50, "dollar_100", 100,
                        "dollar_500", 500),
                "Attachment kuronomy:player_variables, field bal"));
        l.add(new KnownCurrency("mezzos_money_mod", "Mezzo's Money", CurrencyKind.PLAYER_DATA,
                List.of("net.mcreator.mezzosmoneymod.network.MezzosMoneyModModVariables"),
                items("euro_1", 1, "euro_5", 5, "euro_10", 10, "euro_20", 20, "euro_50", 50, "euro_100", 100,
                        "euro_200", 200, "euro_500", 500),
                "Attachment mezzos_money_mod:player_variables, field bankaccount"));
        l.add(new KnownCurrency("betterecon", "Better Economy", CurrencyKind.PLAYER_DATA,
                List.of("net.ultimporks.betterecon.init.ModAttachmentTypes"),
                items("one_dollar_bill", 1, "five_dollar_bill", 5, "ten_dollar_bill", 10, "twenty_dollar_bill", 20,
                        "fifty_dollar_bill", 50, "one_hundred_dollar_bill", 100),
                "Attachment betterecon:balance"));

        // ---------------- Balance in a world file ----------------
        l.add(new KnownCurrency("sarosessentialsmod", "Saro's Essentials", CurrencyKind.FILE,
                List.of("de.sarocesch.sarosessentialsmod.command.CommandEco"), Map.of(),
                "world/sarosessentialsmod/bank/<uuid>.bank"));

        // ---------------- Physical coins only ----------------
        l.add(coins("aiycoin", "Aiycoin", "net.mcreator.aiycoin.init.AiycoinModItems",
                "50:1 chain in the Coin Box. coin_diamond=Rich, coin_emerald=Royal, coin_netherite=Cosmic",
                items("coin_stone", 1, "coin_bronze", 50, "coin_silver", 2500, "coin_gold", 125000,
                        "coin_diamond", 6250000, "coin_emerald", 312500000, "coin_netherite", 15625000000L,
                        "coin_aiycoin", 1000000000000L, "coin_streum", 0)));
        l.add(coins("coinsje", "Coins JE", null, "9:1 chain; piles hold 9",
                items("copper_coin", 1, "iron_coin", 9, "gold_coin", 81, "diamond_coin", 729, "netherite_coin", 6561,
                        "copper_coin_pile", 9, "iron_coin_pile", 81, "gold_coin_pile", 729,
                        "diamond_coin_pile", 6561, "netherite_coin_pile", 59049,
                        "amethyst_coin", 0, "emerald_coin", 0, "lapis_coin", 0, "echo_coin", 0,
                        "ender_coin", 0, "blazing_coin", 0, "brass_coin", 0)));
        l.add(coins("havencurrency", "Haven: Currency", null, "Coin pouch holds coins",
                items("nova_coin", 1, "nova_coin_2", 2, "nova_coin_5", 5, "nova_coin_10", 10, "nova_coin_20", 20,
                        "nova_coin_50", 50, "nova_coin_100", 100, "nova_coin_200", 200, "nova_coin_500", 500,
                        "nova_coin_1k", 1000, "nova_coin_5k", 5000, "nova_coin_10k", 10000, "nova_coin_50k", 50000,
                        "nova_coin_100k", 100000, "nova_coin_500k", 500000, "nova_coin_1m", 1000000,
                        "nova_coin_5m", 5000000, "nova_coin_10m", 10000000, "nova_coin_50m", 50000000,
                        "nova_coin_100m", 100000000, "nova_coin_500m", 500000000, "nova_coin_1b", 1000000000)));
        l.add(coins("ycurrenci", "Ycurrency", null, "Brazilian real, in centavos; carteira = wallet",
                items("cincocentavos", 5, "dezcentavos", 10, "vinteecincocentavos", 25, "cinquentacentavos", 50,
                        "umreal", 100, "cincoreais", 500, "dezreais", 1000, "vintereais", 2000,
                        "cinquentareais", 5000, "cemreais", 10000)));
        l.add(coins("moneyforeveryone", "Money for Everyone", null, "5:1 chain; briefcase holds coins",
                items("coin_copper", 1, "coin_iron", 5, "coin_gold", 25, "coin_diamond", 125,
                        "coin_netherite", 625, "coin_nether_star", 3125)));
        l.add(coins("pixieco", "PixieCo", null, "4:1 chain; pixie_wallet stores a balance",
                items("copper_pixie_coin", 1, "iron_pixie_coin", 4, "gold_pixie_coin", 16,
                        "diamond_pixie_coin", 64, "netherite_pixie_coin", 256)));
        l.add(coins("coins_and_money", "Coins and Money", null, "4:1 chain",
                items("copper_coin", 1, "silver_coin", 4, "gold_coin", 16, "bill", 0)));
        l.add(coins("coinverse_gearcoins", "Coinverse Gearcoins", null, "9 bronze = 1 silver, 4 silver = 1 gold",
                items("bronze_coin", 1, "silver_coin", 9, "gold_coin", 36)));
        l.add(coins("simple_coins", "Simple Coins", null, "Stack = 5 coins, handful = 25 coins",
                items("copper_coin", 1, "iron_coin", 10, "gold_coin", 100, "diamond_coin", 1000, "netherite_coin", 10000,
                        "stack_of_copper_coins", 5, "stack_of_iron_coins", 50, "stack_of_gold_coins", 500,
                        "stack_of_diamond_coins", 5000, "stack_of_netherite_coins", 50000,
                        "handful_of_copper_coins", 25, "handful_of_iron_coins", 250, "handful_of_gold_coins", 2500,
                        "handful_of_diamond_coins", 25000, "handful_of_netherite_coins", 250000)));
        l.add(coins("newcurrencies", "New Currencies", null, "Coins and notes",
                items("coin_1", 1, "coin_2", 2, "note_5", 5, "note_10", 10, "note_20", 20, "note_50", 50,
                        "note_100", 100, "note_200", 200, "note_500", 500)));
        l.add(coins("dragncurrency", "DragN's Currency", null, "In cents",
                items("five_cents", 5, "ten_cents", 10, "twenty_five_cents", 25, "fifty_cents", 50, "one_dollar", 100,
                        "five_dollars", 500, "ten_dollars", 1000, "twenty_dollars", 2000, "fifty_dollars", 5000,
                        "one_hundred_dollars", 10000)));
        l.add(coins("wallet", "Wallet", null, "coin_sack holds coins",
                items("coin_1", 1, "coin_5", 5, "coin_10", 10, "coin_20", 20, "coin_50", 50, "coin_100", 100,
                        "coin_500", 500)));
        l.add(coins("jackseconomy", "Jack's Economy", null, "Bill stacks hold 9 bills; wallets store a balance",
                items("dollar_bill", 1, "five_dollar_bill", 5, "ten_dollar_bill", 10, "twenty_dollar_bill", 20,
                        "fifty_dollar_bill", 50, "hundred_dollar_bill", 100, "thousand_dollar_bill", 1000,
                        "dollar_bill_stack", 9, "five_dollar_bill_stack", 45, "ten_dollar_bill_stack", 90,
                        "twenty_dollar_bill_stack", 180, "fifty_dollar_bill_stack", 450,
                        "hundred_dollar_bill_stack", 900, "thousand_dollar_bill_stack", 9000)));
        l.add(coins("nec3", "Never Enough Currency 3", null, "No exchange rate in the mod; set values",
                items("coin_1", 0, "coin_5", 0, "coin_10", 0, "coin_25", 0, "note_1", 0, "note_5", 0,
                        "note_10", 0, "note_20", 0, "note_50", 0, "note_100", 0, "note_500", 0)));
        l.add(coins("sparechange", "Spare Change", null, "No exchange rate in the mod; set values",
                items("copper_coin", 0, "iron_coin", 0, "gold_coin", 0, "diamond_coin", 0, "netherite_coin", 0)));
        l.add(coins("sc", "Silverwolf's Currency", null, "No exchange rate in the mod; set values",
                items("copper_coin", 0, "silver_coin", 0, "gold_coin", 0, "coal_bill", 0, "iron_bill", 0,
                        "gold_bill", 0, "diamond_bill", 0, "emerald_bill", 0)));
        l.add(coins("magic_coins", "Magic Coins", null, "No fixed rate found; set values",
                items("silver_coin", 0, "gold_coin", 0, "crystal_coin", 0)));
        l.add(coins("coins", "RPG Coins", null, "No fixed rate found; set values",
                items("copper_coin", 0, "iron_coin", 0, "gold_coin", 0)));
        l.add(coins("economy", "Economy: Coins, Bills and Gems", null, "9 money = 1 money_block; others unset",
                items("money", 1, "money_block", 9, "coin", 0, "golden_coin", 0, "token", 0)));

        ALL = List.copyOf(l);
    }

    private KnownCurrencies() {}

    private static KnownCurrency api(String modId, String name, String note, String marker) {
        return new KnownCurrency(modId, name, CurrencyKind.API, List.of(marker), Map.of(), note);
    }

    private static KnownCurrency coins(String modId, String name, String marker, String note, Map<String, Long> items) {
        return new KnownCurrency(modId, name, CurrencyKind.ITEMS, marker == null ? List.of() : List.of(marker), items, note);
    }

    /** Pairs of item path and value: items("a", 1, "b", 5). */
    private static Map<String, Long> items(Object... pairs) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m.put((String) pairs[i], ((Number) pairs[i + 1]).longValue());
        }
        return m;
    }
}
