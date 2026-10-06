package com.tac5studios.elementseconomy.bridge.impactor;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.bridge.Bridges;
import com.tac5studios.elementseconomy.config.Features;
import net.impactdev.impactor.api.economy.events.SuggestEconomyServiceEvent;
import net.impactdev.impactor.api.events.ImpactorEventBus;
import net.impactdev.impactor.api.platform.plugins.PluginMetadata;
import net.neoforged.fml.ModList;

/**
 * Suggests Elements: Economy as Impactor's economy service. Impactor asks for suggestions once, during
 * dedicated server setup, and keeps the highest priority (its own service is 0, its Vault add-on 1).
 * Only loaded when Impactor is installed.
 */
public final class ImpactorBridge {

    private static final int PRIORITY = 10;

    private ImpactorBridge() {}

    public static void register() {
        ImpactorEventBus.bus().subscribe(SuggestEconomyServiceEvent.class, event -> {
            if (!Features.on(Features.BRIDGES, Features.BRIDGE_IMPACTOR)) return;
            String version = ModList.get().getModContainerById(ElementsEconomy.MOD_ID)
                    .map(c -> c.getModInfo().getVersion().toString()).orElse("1.0.0");
            PluginMetadata meta = PluginMetadata.builder()
                    .id(ElementsEconomy.MOD_ID)
                    .name("Elements: Economy")
                    .version(version)
                    .build();
            event.suggest(meta, () -> {
                Bridges.impactorActive();
                ElementsEconomy.LOGGER.info("[Economy] Impactor now uses Elements: Economy money.");
                return new ImpactorEconomy();
            }, PRIORITY);
        });
    }
}
