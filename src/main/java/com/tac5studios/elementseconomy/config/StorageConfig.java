package com.tac5studios.elementseconomy.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;

/** config/elements_economy/storage.toml */
public final class StorageConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> BACKEND;
    public static final ModConfigSpec.ConfigValue<String> FOLDER;
    public static final ModConfigSpec.IntValue SAVE_INTERVAL;
    public static final ModConfigSpec.BooleanValue QUICK_SAVE_MONEY;

    public static final ModConfigSpec.IntValue KEEP_BACKUPS;

    public static final ModConfigSpec.ConfigValue<String> LOG_FOLDER;
    public static final ModConfigSpec.IntValue LOG_KEEP_DAYS;
    public static final ModConfigSpec.IntValue HISTORY_SIZE;

    public static final ModConfigSpec.ConfigValue<String> MYSQL_HOST;
    public static final ModConfigSpec.IntValue MYSQL_PORT;
    public static final ModConfigSpec.ConfigValue<String> MYSQL_DATABASE;
    public static final ModConfigSpec.ConfigValue<String> MYSQL_USERNAME;
    public static final ModConfigSpec.ConfigValue<String> MYSQL_PASSWORD;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        BACKEND = b.comment(
                "Where Elements: Economy saves its data.",
                "json   = plain files, lightest, best for most servers (default)",
                "yaml   = plain files, same as json but YAML format",
                "sqlite = one database file, good for big servers",
                "mysql  = external database, for networks sharing data between servers",
                "If the one you pick fails to start, Elements: Economy uses json and logs a warning.",
                "This change needs a server restart."
        ).defineInList("backend", "json", Arrays.asList("json", "yaml", "sqlite", "mysql"));

        FOLDER = b.comment("Folder for json/yaml files and the sqlite file. Starts from the server folder.")
                .define("folder", "elements_economy/store");

        SAVE_INTERVAL = b.comment("Seconds between saving changed data to disk.")
                .defineInRange("save_interval_seconds", 60, 5, 3600);

        QUICK_SAVE_MONEY = b.comment("Save money changes within a few seconds instead of waiting for the timer.")
                .define("quick_save_money", true);

        b.comment("Backups taken before a currency switch, a migration or a storage convert.").push("backups");
        KEEP_BACKUPS = b.comment("How many backups to keep. Oldest are deleted first. 0 = keep all.")
                .defineInRange("keep", 10, 0, 1000);
        b.pop();

        b.comment("Transaction log and payment history.").push("logs");
        LOG_FOLDER = b.comment("Folder for daily transaction log files. Starts from the server folder.")
                .define("folder", "logs/elements_economy");
        LOG_KEEP_DAYS = b.comment("Days to keep log files. 0 = keep all.")
                .defineInRange("keep_days", 30, 0, 3650);
        HISTORY_SIZE = b.comment("Payments kept per player for /payments.")
                .defineInRange("history_size", 50, 0, 1000);
        b.pop();

        b.comment("Only used when backend = \"mysql\".").push("mysql");
        MYSQL_HOST = b.comment("Database server address.").define("host", "localhost");
        MYSQL_PORT = b.comment("Database server port.").defineInRange("port", 3306, 1, 65535);
        MYSQL_DATABASE = b.comment("Database name.").define("database", "elements_economy");
        MYSQL_USERNAME = b.comment("Database user.").define("username", "user");
        MYSQL_PASSWORD = b.comment("Database password.").define("password", "change_me");
        b.pop();

        SPEC = b.build();
    }

    private StorageConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "elements_economy/storage.toml");
    }
}
