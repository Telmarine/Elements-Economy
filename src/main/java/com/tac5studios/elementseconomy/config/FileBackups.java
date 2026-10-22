package com.tac5studios.elementseconomy.config;

import com.tac5studios.elementseconomy.ElementsEconomy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Backups of config files Economy rewrites itself (messages.toml, currency_values.toml), saved next to the file as
 * {@code <name>.<yyyyMMdd-HHmmss>.bak}. Only the newest few are kept (economy.toml {@code [files] backups_kept}).
 */
public final class FileBackups {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private FileBackups() {}

    /** Copies the file to a new backup, removes the oldest ones over the limit, and returns the backup's file name. */
    public static String take(Path file) throws IOException {
        String name = file.getFileName().toString();
        Path backup = file.resolveSibling(name + "." + LocalDateTime.now().format(STAMP) + ".bak");
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        prune(file);
        return backup.getFileName().toString();
    }

    /** Removes the oldest backups of this file until only backups_kept are left. */
    public static void prune(Path file) {
        String prefix = file.getFileName().toString() + ".";
        int keep = EconomyConfig.SPEC.isLoaded() ? EconomyConfig.BACKUPS_KEPT.get() : 2;
        List<Path> backups = new ArrayList<>();
        try (Stream<Path> s = Files.list(file.getParent())) {
            s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(prefix) && n.endsWith(".bak");
            }).forEach(backups::add);
        } catch (IOException e) {
            return;
        }
        // The time stamp in the name sorts oldest first.
        backups.sort(Comparator.comparing(p -> p.getFileName().toString()));
        for (int i = 0; i < backups.size() - keep; i++) {
            try {
                Files.deleteIfExists(backups.get(i));
            } catch (IOException e) {
                ElementsEconomy.LOGGER.warn("[Economy] Could not delete old backup {}: {}", backups.get(i).getFileName(), e.toString());
            }
        }
    }
}
