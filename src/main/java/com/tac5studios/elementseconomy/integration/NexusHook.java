package com.tac5studios.elementseconomy.integration;

import com.mojang.logging.LogUtils;
import com.tac5studios.elementseconomy.config.Features;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Optional link to Elements: Nexus. Safe to call whether Nexus is installed or not.
 * Nothing here touches a Nexus class. Only {@link NexusBridge} does, and it is loaded
 * only after this class has checked that Nexus is present and the switch is on.
 * Any error from Nexus turns the link off for the rest of the session instead of crashing.
 */
public final class NexusHook {

    public static final String NEXUS_ID = "elements_nexus";

    private static final Logger LOG = LogUtils.getLogger();
    private static Boolean present;
    private static boolean broken;

    private NexusHook() {}

    /** True when Nexus is installed, nexus.enabled is on, and the link has not failed. */
    public static boolean available() {
        if (broken || !Features.on(Features.NEXUS)) {
            return false;
        }
        if (present == null) {
            present = ModList.get() != null && ModList.get().isLoaded(NEXUS_ID);
            if (present) {
                LOG.info("[Elements: Economy] Elements: Nexus found. Nexus links are on.");
            }
        }
        return present;
    }

    /** The player's Nexus rank name, when nexus.rank_limits is on. Empty otherwise. */
    public static Optional<String> rankOf(UUID player) {
        if (!available() || !Features.on(Features.NEXUS, Features.NEXUS_RANK_LIMITS)) {
            return Optional.empty();
        }
        try {
            String r = NexusBridge.rankOf(player);
            return r == null || r.isEmpty() ? Optional.empty() : Optional.of(r);
        } catch (Throwable t) {
            fail("rank lookup", t);
            return Optional.empty();
        }
    }

    /** The player's Nexus rank for rank prices (server shops). Empty when Nexus isn't there. */
    public static Optional<String> rank(UUID player) {
        if (!available()) {
            return Optional.empty();
        }
        try {
            String r = NexusBridge.rankOf(player);
            return r == null || r.isEmpty() ? Optional.empty() : Optional.of(r);
        } catch (Throwable t) {
            fail("rank lookup", t);
            return Optional.empty();
        }
    }

    private static void fail(String what, Throwable t) {
        broken = true;
        LOG.warn("[Elements: Economy] Nexus link failed during {}. Nexus links are off until restart.", what, t);
    }
}
