package com.tac5studios.elementseconomy;

import com.tac5studios.elementseconomy.config.AuctionConfig;
import com.tac5studios.elementseconomy.config.EconomyConfig;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.config.ShopConfig;
import com.tac5studios.elementseconomy.config.StorageConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elements: Economy — the currency bridge.
 * Dedicated servers only, same as Elements: Nexus. Players install nothing.
 *
 * Everything else starts from server events (each class subscribes itself):
 *   storage.Storage            start first, save on a timer, close last
 *   messages.Msg               read messages.toml
 *   currency.CurrencyDetector  find the server's currency
 *   perms.Perm                 register permission nodes
 *   core.Economy              currencies, balances, every money change (Elements: Vault provider)
 *   commands.MoneyCommands     /bal /pay /baltop /history /eco
 *   currency.CurrencyCommands  /economy currency ...
 *   shop.*                     player shops, the shop lock, stock vaults, /shop
 *   servershop.*               server shops, /economy shop, /shop open
 *   auction.*                  auction house, /ah
 *   bridge.*                   Impactor / OctoEconomy provider, other mods' shop bridges
 */
@Mod(value = ElementsEconomy.MOD_ID, dist = Dist.DEDICATED_SERVER)
public class ElementsEconomy {

    public static final String MOD_ID = "elements_economy";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElementsEconomy");

    public ElementsEconomy(IEventBus modEventBus, ModContainer container) {
        // config/elements_economy/features.toml, storage.toml, economy.toml, shops.toml, auction_house.toml
        Features.register(container);
        StorageConfig.register(container);
        EconomyConfig.register(container);
        ShopConfig.register(container);
        AuctionConfig.register(container);
        // Impactor and OctoEconomy ask for a money provider before the server starts.
        com.tac5studios.elementseconomy.bridge.Bridges.init();
        LOGGER.info("[Economy] Elements: Economy loading.");
    }
}
