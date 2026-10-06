package com.tac5studios.elementseconomy.migrate;

import com.tac5studios.elementseconomy.core.Economy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Somewhere old balances can be read from. Sources only read; they never change the original data. */
interface MigrationSource {

    /** Short id typed in /economy migrate <id>. */
    String id();

    /** Name shown to admins. */
    String name();

    /** True when this source's data is on this server. */
    boolean present(MinecraftServer server);

    /** Read every balance. */
    Result read(MinecraftServer server) throws IOException;

    /**
     * One balance: a player, or a named account kept for another mod ({@code account}).
     * {@code worth} is the value in whole digital money (rates applied).
     */
    record Entry(@Nullable UUID player, @Nullable ResourceLocation account, BigDecimal worth) {}

    /** Balances plus the rate used for each source currency (true = from economy.toml, false = 1:1 default). */
    record Result(List<Entry> entries, Map<String, Rate> rates, int skipped) {}

    record Rate(BigDecimal value, boolean configured) {}

    /** Rate for a source currency key: the economy.toml line, or 1 (one stored unit = one digital coin). */
    static Rate rate(String key, Map<String, Rate> used) {
        return used.computeIfAbsent(key, k -> Economy.rateFor(k).map(r -> new Rate(r, true)).orElse(new Rate(BigDecimal.ONE, false)));
    }

    static Map<String, Rate> rates() {
        return new LinkedHashMap<>();
    }
}
