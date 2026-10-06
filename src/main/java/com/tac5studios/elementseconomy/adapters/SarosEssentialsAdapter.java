package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementsvault.Capability;
import com.tac5studios.elementsvault.Holding;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Saro's Essentials: one plain-text file per player, world/sarosessentialsmod/bank/<uuid>.bank,
 * holding a double. The mod re-reads the file every time, so writing it is safe. Works offline.
 */
final class SarosEssentialsAdapter extends ReflectBackend {

    SarosEssentialsAdapter() {
        super(new ExternalCurrency("sarosessentialsmod", "bank", "Money", 2,
                EnumSet.of(Capability.OFFLINE_READ, Capability.OFFLINE_WRITE, Capability.LIST_ALL), null, null));
    }

    private static Path folder() {
        return server().getWorldPath(LevelResource.ROOT).resolve("sarosessentialsmod").resolve("bank");
    }

    private static Path file(UUID id) {
        return folder().resolve(id + ".bank");
    }

    @Override
    protected boolean ready() {
        return server() != null && Reflect.has("de.sarocesch.sarosessentialsmod.command.CommandEco");
    }

    @Override
    protected BigInteger read(UUID id) {
        Path f = file(id);
        if (!Files.exists(f)) return BigInteger.ZERO;
        try {
            return fromDouble(Double.parseDouble(Files.readString(f, StandardCharsets.UTF_8).trim()));
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("can't read " + f + ": " + e);
        }
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        Path f = file(id);
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, String.valueOf(toDouble(amount)), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("can't write " + f + ": " + e);
        }
    }

    @Override
    protected List<Holding> list() {
        List<Holding> out = new ArrayList<>();
        if (!Files.isDirectory(folder())) return out;
        try (Stream<Path> s = Files.list(folder())) {
            for (Path p : s.toList()) {
                String n = p.getFileName().toString();
                if (!n.endsWith(".bank")) continue;
                try {
                    UUID id = UUID.fromString(n.substring(0, n.length() - 5));
                    out.add(holding(id, read(id)));
                } catch (IllegalArgumentException ignored) {
                    // not a player file
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("can't list " + folder() + ": " + e);
        }
        return out;
    }
}
