package com.tac5studios.elementseconomy.bridge.octo;

import com.epherical.eights.event.NeoForgeEconomyProviderPreEvent;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Economy;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Sets Elements: Economy as the OctoEconomy provider. Eights Economy P asks for a provider when the server
 * starts (ServerStartingEvent, after the economy core is up); mods on OctoEconomy (Eights' own commands,
 * Shoppy) then use Elements: Economy money. Only loaded when Eights Economy P is installed.
 */
public final class OctoBridge {

    private OctoBridge() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, NeoForgeEconomyProviderPreEvent.class, OctoBridge::onProvider);
    }

    private static void onProvider(NeoForgeEconomyProviderPreEvent e) {
        if (!Features.on(Features.BRIDGES, Features.BRIDGE_OCTO) || !Economy.running()) return;
        e.setEconomy(new OctoProvider());
        ElementsEconomy.LOGGER.info("[Economy] OctoEconomy (Eights Economy, Shoppy) now uses Elements: Economy money.");
    }
}
