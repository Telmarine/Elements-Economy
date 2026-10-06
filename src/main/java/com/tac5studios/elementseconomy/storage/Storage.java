package com.tac5studios.elementseconomy.storage;

import net.neoforged.api.distmarker.Dist;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.StorageConfig;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Starts, runs and stops the data store for the server.
 * Starts before anything else on server start, and closes last on server stop.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Storage {

    /** Data layout version. Raise it when a collection's format changes, and add an upgrade step. */
    public static final int SCHEMA_VERSION = 1;

    /** Ticks to wait before a quick save, so several payments in a row save together. */
    private static final int QUICK_SAVE_TICKS = 40;

    private static DataStore store;
    private static int ticks;
    private static int quickSaveIn = -1;

    private Storage() {}

    /** The live data store. Only valid while the server is running. */
    public static DataStore get() {
        if (store == null) throw new IllegalStateException("Elements: Economy storage is not running.");
        return store;
    }

    public static boolean running() {
        return store != null;
    }

    // ---------- server events ----------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAboutToStart(ServerAboutToStartEvent e) {
        start();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        tick();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onStopped(ServerStoppedEvent e) {
        stop();
    }

    // ---------- lifecycle ----------

    public static void start() {
        Path folder = folder();
        String wanted = StorageConfig.BACKEND.get();

        StorageBackend backend;
        try {
            backend = create(wanted, folder);
            backend.open();
        } catch (Exception | LinkageError ex) { // LinkageError = a bundled driver failed to load
            ElementsEconomy.LOGGER.warn("[Economy] Storage '{}' could not start ({}). Using json instead.", wanted, ex.toString());
            backend = new JsonBackend(folder);
            try {
                backend.open();
            } catch (Exception fatal) {
                throw new RuntimeException("Elements: Economy could not create its data folder: " + folder, fatal);
            }
        }

        store = new DataStore(backend);
        int loaded;
        try {
            loaded = store.loadAll();
        } catch (Exception ex) {
            ElementsEconomy.LOGGER.error("[Economy] Could not list saved data.", ex);
            loaded = 0;
        }
        Integer saved = store.get(Collections.META, "schema_version", Integer.class);
        if (saved != null && saved > SCHEMA_VERSION) {
            ElementsEconomy.LOGGER.warn("[Economy] Saved data is from a newer version (schema {}). Unknown fields are kept but may be ignored.", saved);
        } else {
            store.put(Collections.META, "schema_version", SCHEMA_VERSION);
        }
        store.put(Collections.META, "last_start", Instant.now().toString());
        ticks = 0;
        quickSaveIn = -1;

        TransactionLog.start();
        ElementsEconomy.LOGGER.info("[Economy] Storage: {} at {} ({} data sets loaded).", backend.name(), folder, loaded);
    }

    /** Called every server tick. Saves on the timer from storage.toml, or sooner after money moved. */
    public static void tick() {
        if (store == null) return;
        if (quickSaveIn >= 0 && --quickSaveIn < 0) {
            ticks = 0;
            store.flush(false);
            return;
        }
        if (++ticks >= StorageConfig.SAVE_INTERVAL.get() * 20) {
            ticks = 0;
            quickSaveIn = -1;
            store.flush(false);
        }
    }

    /**
     * Call after money changes hands. Saves within two seconds when quick_save_money is on.
     * Several calls close together share one save.
     */
    public static void moneyChanged() {
        if (store != null && StorageConfig.QUICK_SAVE_MONEY.get() && quickSaveIn < 0) {
            quickSaveIn = QUICK_SAVE_TICKS;
        }
    }

    /** Save everything now and wait. Use before and after big changes (switch-over, migration). */
    public static void saveNow() {
        if (store == null) return;
        quickSaveIn = -1;
        ticks = 0;
        store.flush(true);
    }

    public static void stop() {
        if (store == null) return;
        store.close();
        store = null;
        TransactionLog.stop();
        ElementsEconomy.LOGGER.info("[Economy] Storage saved and closed.");
    }

    /** The storage folder from storage.toml. */
    public static Path folder() {
        return FMLPaths.GAMEDIR.get().resolve(StorageConfig.FOLDER.get()).normalize();
    }

    static StorageBackend create(String name, Path folder) {
        return switch (name) {
            case "json" -> new JsonBackend(folder);
            case "yaml" -> new YamlBackend(folder);
            case "sqlite" -> new SqliteBackend(folder);
            case "mysql" -> new MysqlBackend();
            default -> throw new IllegalArgumentException("unknown backend '" + name + "'");
        };
    }
}
