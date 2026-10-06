package com.tac5studios.elementseconomy.storage;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.config.StorageConfig;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Plain text log of every money movement, one file per day:
 * logs/elements_economy/transactions-2026-10-04.log
 *
 * Kept outside the data store on purpose: it only grows, it's for people to read,
 * and it stays readable even if the store is broken. Writing runs on its own thread.
 * Switched by economy.transaction_log.
 */
public final class TransactionLog {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static ExecutorService writer;

    private TransactionLog() {}

    static void start() {
        if (!Features.on(Features.ECONOMY, Features.ECO_TRANSACTION_LOG)) return;
        writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Economy-Log");
            t.setDaemon(true);
            return t;
        });
        writer.submit(TransactionLog::prune);
    }

    static void stop() {
        if (writer == null) return;
        writer.shutdown();
        try {
            writer.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        writer = null;
    }

    /**
     * Add one line. Example:
     * 14:02:11 PAY Steve -> Alex 250 Coins (elements_economy:digital) "for the iron"
     *
     * @param kind   short tag: PAY, GIVE, TAKE, SET, RESET, SHOP_BUY, SHOP_SELL, AH_SALE, AH_TAX, EXCHANGE, SWITCH ...
     * @param detail the rest of the line, already readable
     */
    public static void add(String kind, String detail) {
        ExecutorService w = writer;
        if (w == null) return;
        String line = LocalTime.now().format(CLOCK) + " " + kind + " " + detail.replace('\n', ' ') + System.lineSeparator();
        LocalDate day = LocalDate.now();
        w.submit(() -> {
            Path file = folder().resolve("transactions-" + day + ".log");
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                ElementsEconomy.LOGGER.warn("[Economy] Could not write the transaction log.", ex);
            }
        });
    }

    private static Path folder() {
        return FMLPaths.GAMEDIR.get().resolve(StorageConfig.LOG_FOLDER.get()).normalize();
    }

    /** Delete log files older than keep_days. */
    private static void prune() {
        int days = StorageConfig.LOG_KEEP_DAYS.get();
        if (days <= 0 || !Files.isDirectory(folder())) return;
        String oldest = "transactions-" + LocalDate.now().minusDays(days) + ".log";
        try (Stream<Path> s = Files.list(folder())) {
            for (Path p : s.toList()) {
                String n = p.getFileName().toString();
                if (n.startsWith("transactions-") && n.endsWith(".log") && n.compareTo(oldest) < 0) Files.deleteIfExists(p);
            }
        } catch (IOException ex) {
            ElementsEconomy.LOGGER.warn("[Economy] Could not clean up old transaction logs.", ex);
        }
    }
}
