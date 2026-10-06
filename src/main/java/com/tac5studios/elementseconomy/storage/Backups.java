package com.tac5studios.elementseconomy.storage;

import com.google.gson.JsonElement;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.StorageConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Full copies of all Economy data, taken before a currency switch, a migration or a storage convert.
 * Always saved as plain json in <storage folder>/backups/<time>-<reason>/, whatever the backend is,
 * so an owner can read or restore them by hand.
 */
public final class Backups {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private Backups() {}

    public static Path folder() {
        return Storage.folder().resolve("backups");
    }

    /**
     * Save everything in memory right now (including unsaved changes) to a new backup folder.
     * Runs on the calling thread. Returns the folder, or throws if the backup failed —
     * callers must stop the change they were about to make when that happens.
     */
    public static Path take(String reason) throws IOException {
        Map<String, Map<String, JsonElement>> snap = Storage.get().snapshot();
        String safe = reason.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path dir = folder().resolve(LocalDateTime.now().format(TIME) + "-" + safe);
        JsonBackend out = new JsonBackend(dir);
        out.open();
        for (Map.Entry<String, Map<String, JsonElement>> e : snap.entrySet()) {
            out.save(e.getKey(), e.getValue(), new HashSet<>(e.getValue().keySet()), new HashSet<>());
        }
        out.close();
        ElementsEconomy.LOGGER.info("[Economy] Backup saved: {}", dir);
        prune();
        return dir;
    }

    /** Delete the oldest backups past the keep count in storage.toml. */
    private static void prune() {
        int keep = StorageConfig.KEEP_BACKUPS.get();
        if (keep <= 0) return;
        try (Stream<Path> s = Files.list(folder())) {
            List<Path> all = s.filter(Files::isDirectory).sorted().toList(); // names start with the time, so oldest first
            for (int i = 0; i < all.size() - keep; i++) deleteTree(all.get(i));
        } catch (IOException ex) {
            ElementsEconomy.LOGGER.warn("[Economy] Could not clean up old backups.", ex);
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
