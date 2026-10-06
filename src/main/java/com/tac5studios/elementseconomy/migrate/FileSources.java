package com.tac5studios.elementseconomy.migrate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Sources read straight from another mod's save files (formats checked against each mod's source). */
final class FileSources {

    private FileSources() {}

    static List<MigrationSource> all() {
        return List.of(new Impactor(), new Eights(), new Wano());
    }

    private static BigDecimal number(JsonElement e) {
        try {
            return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsBigDecimal() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Impactor's own economy (file storage, the default): config/impactor/economy/accounts/users/<xx>/<uuid>.conf,
     * one top-level number per currency key (e.g. "impactor:dollars"). Written as JSON, YAML or HOCON depending on
     * Impactor's storage.method. SQL storage can't be read here. Virtual (non-player) accounts are skipped.
     */
    static final class Impactor implements MigrationSource {
        private static final Pattern LINE = Pattern.compile(
                "^\\s*\"?([a-z0-9_.\\-]+:[a-z0-9_./\\-]+)\"?\\s*[:=]\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)\\s*,?\\s*$");

        @Override
        public String id() {
            return "impactor";
        }

        @Override
        public String name() {
            return "Impactor";
        }

        private static Path users(MinecraftServer server) {
            return server.getFile("config/impactor/economy/accounts/users");
        }

        @Override
        public boolean present(MinecraftServer server) {
            return Files.isDirectory(users(server));
        }

        @Override
        public Result read(MinecraftServer server) throws IOException {
            List<Entry> out = new ArrayList<>();
            Map<String, Rate> rates = MigrationSource.rates();
            int skipped = 0;
            List<Path> files;
            try (Stream<Path> s = Files.walk(users(server))) {
                files = s.filter(p -> p.getFileName().toString().endsWith(".conf") && !p.toString().contains("transactions")).toList();
            }
            for (Path f : files) {
                String name = f.getFileName().toString();
                UUID owner;
                try {
                    owner = UUID.fromString(name.substring(0, name.indexOf('.')));
                } catch (IllegalArgumentException ex) {
                    skipped++;
                    continue;
                }
                BigDecimal worth = BigDecimal.ZERO;
                String text = Files.readString(f);
                if (text.trim().startsWith("{")) {
                    try {
                        JsonObject o = JsonParser.parseString(text).getAsJsonObject();
                        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                            BigDecimal v = number(e.getValue());
                            if (v != null && e.getKey().contains(":") && v.signum() > 0) {
                                worth = worth.add(v.multiply(MigrationSource.rate(e.getKey(), rates).value()));
                            }
                        }
                    } catch (RuntimeException ex) {
                        skipped++;
                        continue;
                    }
                } else {
                    for (String line : text.split("\\R")) {
                        Matcher m = LINE.matcher(line);
                        if (!m.matches()) continue;
                        BigDecimal v = new BigDecimal(m.group(2));
                        if (v.signum() > 0) worth = worth.add(v.multiply(MigrationSource.rate(m.group(1), rates).value()));
                    }
                }
                if (worth.signum() > 0) out.add(new Entry(owner, null, worth));
            }
            return new Result(out, rates, skipped);
        }
    }

    /**
     * Eights Economy P: <world>/eights_economy_p/<uuid>.json = { uuid, name, balance }, and named accounts in
     * sub-folders (<namespace>/<path>.json = { name, balance }), which become OctoEconomy bridge accounts.
     */
    static final class Eights implements MigrationSource {
        private static final String KEY = "eights_economy_p:balance";

        @Override
        public String id() {
            return "eights";
        }

        @Override
        public String name() {
            return "Eights Economy P";
        }

        private static Path folder(MinecraftServer server) {
            return server.getWorldPath(LevelResource.ROOT).resolve("eights_economy_p");
        }

        @Override
        public boolean present(MinecraftServer server) {
            return Files.isDirectory(folder(server));
        }

        @Override
        public Result read(MinecraftServer server) throws IOException {
            List<Entry> out = new ArrayList<>();
            Map<String, Rate> rates = MigrationSource.rates();
            BigDecimal rate = MigrationSource.rate(KEY, rates).value();
            int skipped = 0;
            Path root = folder(server);
            List<Path> files;
            try (Stream<Path> s = Files.walk(root)) {
                files = s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json")).toList();
            }
            for (Path f : files) {
                try {
                    JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                    BigDecimal v = number(o.get("balance"));
                    if (v == null || v.signum() <= 0) continue;
                    BigDecimal worth = v.multiply(rate);
                    if (o.has("uuid")) {
                        out.add(new Entry(UUID.fromString(o.get("uuid").getAsString()), null, worth));
                    } else if (o.has("name")) {
                        ResourceLocation id = ResourceLocation.tryParse(o.get("name").getAsString());
                        if (id != null) out.add(new Entry(null, id, worth));
                        else skipped++;
                    }
                } catch (RuntimeException ex) {
                    skipped++;
                }
            }
            return new Result(out, rates, skipped);
        }
    }

    /** Wano Economy: config/wanoeconomy/data/balances.json = { "<uuid>": number }. */
    static final class Wano implements MigrationSource {
        private static final String KEY = "wanoeconomy:balance";

        @Override
        public String id() {
            return "wano";
        }

        @Override
        public String name() {
            return "Wano Economy";
        }

        private static Path file(MinecraftServer server) {
            return server.getFile("config/wanoeconomy/data/balances.json");
        }

        @Override
        public boolean present(MinecraftServer server) {
            return Files.isRegularFile(file(server));
        }

        @Override
        public Result read(MinecraftServer server) throws IOException {
            List<Entry> out = new ArrayList<>();
            Map<String, Rate> rates = MigrationSource.rates();
            BigDecimal rate = MigrationSource.rate(KEY, rates).value();
            int skipped = 0;
            JsonObject o;
            try {
                o = JsonParser.parseString(Files.readString(file(server))).getAsJsonObject();
            } catch (RuntimeException ex) {
                throw new IOException("balances.json could not be read", ex);
            }
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                try {
                    BigDecimal v = number(e.getValue());
                    if (v != null && v.signum() > 0) out.add(new Entry(UUID.fromString(e.getKey()), null, v.multiply(rate)));
                } catch (IllegalArgumentException ex) {
                    skipped++;
                }
            }
            return new Result(out, rates, skipped);
        }
    }
}
