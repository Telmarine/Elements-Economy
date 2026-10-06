package com.tac5studios.elementseconomy.storage;

import com.google.gson.JsonElement;
import com.tac5studios.elementseconomy.ElementsEconomy;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Copies everything in the running store into another backend. The running store is not changed.
 * After a successful copy the target is copied again when the server stops, so balances, tills and
 * listings that changed between the convert and the restart are not lost.
 */
public final class StorageConvert {

    private static volatile boolean running;
    /** Backend that was copied to this session. Refreshed with the final data on shutdown. */
    private static volatile String pending;

    private StorageConvert() {}

    public static boolean running() {
        return running;
    }

    /**
     * Runs on its own thread. done gets null on success, or the error message.
     * The target ends up an exact copy: entries it had that the source doesn't are removed.
     */
    public static void start(String target, Consumer<String> done) {
        Map<String, Map<String, JsonElement>> snap = Storage.get().snapshot();
        running = true;
        Thread t = new Thread(() -> {
            try {
                copy(target, snap);
                pending = target;
                done.accept(null);
            } catch (Exception | LinkageError ex) {
                ElementsEconomy.LOGGER.error("[Economy] Storage convert to {} failed.", target, ex);
                done.accept(ex.toString());
            } finally {
                running = false;
            }
        }, "Economy-Convert");
        t.setDaemon(true);
        t.start();
    }

    /** Called by Storage on shutdown, before the store closes: copy the final data to the convert target. */
    static void finish(DataStore store) {
        String target = pending;
        pending = null;
        if (target == null) return;
        long until = System.currentTimeMillis() + 30_000;
        while (running && System.currentTimeMillis() < until) {
            try {
                Thread.sleep(50); // a copy started just before shutdown; give it a moment to end
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (running) {
            ElementsEconomy.LOGGER.error("[Economy] A storage copy to {} is still running. Run /economy storage convert {} again before switching.",
                    target, target);
            return;
        }
        try {
            copy(target, store.snapshot());
            ElementsEconomy.LOGGER.info("[Economy] Final data copied to {} for the storage change.", target);
        } catch (Exception | LinkageError ex) {
            ElementsEconomy.LOGGER.error("[Economy] Final copy to {} failed. Run /economy storage convert {} again before switching.",
                    target, target, ex);
        }
    }

    private static void copy(String target, Map<String, Map<String, JsonElement>> snap) throws Exception {
        StorageBackend to = null;
        try {
            to = Storage.create(target, Storage.folder());
            to.open();
            Set<String> all = new HashSet<>(snap.keySet());
            all.addAll(to.collections());
            for (String col : all) {
                Map<String, JsonElement> data = snap.getOrDefault(col, Map.of());
                Set<String> removed = new HashSet<>(to.load(col).keySet());
                removed.removeAll(data.keySet());
                to.save(col, data, new HashSet<>(data.keySet()), removed);
            }
        } finally {
            if (to != null) to.close();
        }
    }
}
