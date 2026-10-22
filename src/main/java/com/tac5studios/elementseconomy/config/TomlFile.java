package com.tac5studios.elementseconomy.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.tac5studios.elementseconomy.ElementsEconomy;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A hand-written TOML file in config/elements_economy/.
 * The first time, the default file (with all its comments) is copied out of the mod jar.
 * Keys missing from the owner's copy (added in a later version) fall back to the jar's default.
 *
 * <p>Files made with {@code autoUpdate} are also kept up to date: when the jar's default text changes, the file is
 * rebuilt from the jar's copy. A line the owner never edited (it still holds an earlier built-in text) gets the new
 * text; a line the owner edited keeps the owner's text. The old file is backed up first. Earlier built-in texts come
 * from {@code /defaults/<name>.history.toml} in the jar and from {@code .<name>.defaults} next to the file, which
 * records the built-in text the file was last brought up to date with.
 */
public class TomlFile {

    /** A key line in the jar's file: indent, key, " = ", value. */
    private static final Pattern KEY_LINE = Pattern.compile("^(\\s*)([A-Za-z0-9_-]+)(\\s*=\\s*)(.*)$");

    private final String name;
    private final BooleanSupplier autoUpdate;
    private Config data = Config.inMemory();
    private Config defaults = Config.inMemory();

    public TomlFile(String name) {
        this(name, () -> false);
    }

    /** @param autoUpdate checked on every load; true = bring the file up to date with the jar's text */
    public TomlFile(String name, BooleanSupplier autoUpdate) {
        this.name = name;
        this.autoUpdate = autoUpdate;
    }

    public Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(ElementsEconomy.MOD_ID).resolve(name);
    }

    /** Copy the default if missing, then read the file. Returns false if it could not be read. */
    public boolean load() {
        defaults = readDefaults();
        Path p = path();
        try {
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent());
                try (InputStream in = jarDefault()) {
                    if (in == null) throw new IllegalStateException("default " + name + " missing from the jar");
                    Files.copy(in, p);
                }
                if (autoUpdate.getAsBoolean()) saveSnapshot();
            } else if (autoUpdate.getAsBoolean()) {
                update(p);
            }
            FileBackups.prune(p);
            try (CommentedFileConfig cfg = CommentedFileConfig.builder(p).build()) {
                cfg.load();
                data = Config.copy(cfg);
            }
            return true;
        } catch (Exception e) {
            ElementsEconomy.LOGGER.error("[Economy] Could not read {}. Using built-in defaults.", p, e);
            data = Config.inMemory();
            return false;
        }
    }

    // ------------------------------------------------------------------ auto update

    /**
     * Rebuilds the owner's file from the jar's copy, keeping every line the owner edited.
     * Does nothing when the result is the same as the file. Never throws: a problem is logged and the file is left alone.
     */
    private void update(Path p) {
        try {
            String template = jarText();
            if (template == null) return;
            Config owner;
            try (CommentedFileConfig cfg = CommentedFileConfig.builder(p).build()) {
                cfg.load();
                owner = Config.copy(cfg);
            }
            Config current = parse(template);
            Config history = parse(resourceText("/defaults/" + historyName()));
            Config snapshot = Files.exists(snapshotPath()) ? parse(Files.readString(snapshotPath(), StandardCharsets.UTF_8)) : Config.inMemory();

            StringBuilder out = new StringBuilder(template.length() + 1024);
            String section = "";
            int moved = 0, added = 0, kept = 0;
            for (String line : template.split("\\R", -1)) {
                String t = line.trim();
                if (t.startsWith("[") && t.endsWith("]") && !t.startsWith("[[")) {
                    section = t.substring(1, t.length() - 1).trim();
                    out.append(line).append('\n');
                    continue;
                }
                Matcher m = t.startsWith("#") ? null : KEY_LINE.matcher(line);
                if (m == null || !m.matches()) {
                    out.append(line).append('\n');
                    continue;
                }
                String key = section.isEmpty() ? m.group(2) : section + "." + m.group(2);
                Object mine = owner.get(key);
                Object builtIn = current.get(key);
                if (mine == null) {
                    added++;
                    out.append(line).append('\n');
                } else if (mine.equals(builtIn) || isOldDefault(mine, key, history, snapshot)) {
                    if (!mine.equals(builtIn)) moved++;
                    out.append(line).append('\n');
                } else {
                    String value = tomlValue(mine);
                    if (value == null) {
                        out.append(line).append('\n');
                    } else {
                        kept++;
                        out.append(m.group(1)).append(m.group(2)).append(m.group(3)).append(value).append('\n');
                    }
                }
            }
            String result = out.substring(0, out.length() - 1); // split kept the last line break as an empty line
            String old = Files.readString(p, StandardCharsets.UTF_8);
            if (result.replace("\r\n", "\n").equals(old.replace("\r\n", "\n"))) {
                saveSnapshot();
                return;
            }
            String backup = FileBackups.take(p);
            Path tmp = p.resolveSibling(name + ".tmp");
            Files.writeString(tmp, result, StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            saveSnapshot();
            ElementsEconomy.LOGGER.info("[Economy] {} updated: {} line(s) moved to the new built-in text, {} new line(s) added, "
                    + "{} of your own line(s) kept. Old file saved as {}.", name, moved, added, kept, backup);
        } catch (Exception e) {
            ElementsEconomy.LOGGER.error("[Economy] Could not update {}. The file was left as it is.", name, e);
        }
    }

    /** True when the owner's value is a built-in text from an earlier version, so the owner never edited it. */
    private static boolean isOldDefault(Object mine, String key, Config history, Config snapshot) {
        if (mine.equals(snapshot.get(key))) return true;
        Object old = history.get(key);
        if (old instanceof List<?> l) return l.contains(mine);
        return mine.equals(old);
    }

    /** A value written back as TOML, or null for a type this file never uses. */
    private static String tomlValue(Object v) {
        if (v instanceof String s) return quote(s);
        if (v instanceof Boolean || v instanceof Integer || v instanceof Long) return v.toString();
        if (v instanceof Number n) return n.toString();
        return null;
    }

    /** A TOML basic string. */
    private static String quote(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '\b' -> b.append("\\b");
                case '\f' -> b.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x7F) b.append(String.format("\\u%04X", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }

    /** Records the jar's current text so the next update knows which lines were never edited. */
    private void saveSnapshot() {
        try {
            String template = jarText();
            if (template != null) Files.writeString(snapshotPath(), template, StandardCharsets.UTF_8);
        } catch (Exception e) {
            ElementsEconomy.LOGGER.warn("[Economy] Could not save {}: {}", snapshotPath().getFileName(), e.toString());
        }
    }

    private Path snapshotPath() {
        return path().resolveSibling("." + name + ".defaults");
    }

    private String historyName() {
        int dot = name.lastIndexOf('.');
        return (dot < 0 ? name : name.substring(0, dot)) + ".history.toml";
    }

    private String jarText() throws java.io.IOException {
        return resourceText("/defaults/" + name);
    }

    private static String resourceText(String resource) throws java.io.IOException {
        try (InputStream in = ElementsEconomy.class.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Config parse(String text) {
        if (text == null || text.isBlank()) return Config.inMemory();
        return new TomlParser().parse(text);
    }

    private InputStream jarDefault() {
        return ElementsEconomy.class.getResourceAsStream("/defaults/" + name);
    }

    private Config readDefaults() {
        try (InputStream in = jarDefault()) {
            if (in == null) return Config.inMemory();
            return new TomlParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            ElementsEconomy.LOGGER.error("[Economy] Built-in {} could not be read.", name, e);
            return Config.inMemory();
        }
    }

    private Object value(String key) {
        Object v = data.get(key);
        return v != null ? v : defaults.get(key);
    }

    public String str(String key, String def) {
        return value(key) instanceof String s ? s : def;
    }

    public boolean bool(String key, boolean def) {
        return value(key) instanceof Boolean b ? b : def;
    }

    public int num(String key, int def) {
        return value(key) instanceof Number n ? n.intValue() : def;
    }

    public long lng(String key, long def) {
        return value(key) instanceof Number n ? n.longValue() : def;
    }

    /** A list of plain strings. */
    public List<String> strings(String key) {
        List<String> out = new ArrayList<>();
        if (value(key) instanceof List<?> l) for (Object o : l) if (o instanceof String s) out.add(s);
        return out;
    }

    /** A list of inline tables, e.g. [{ item = "x", value = 5 }]. */
    public List<Config> tables(String key) {
        List<Config> out = new ArrayList<>();
        if (value(key) instanceof List<?> l) for (Object o : l) if (o instanceof Config c) out.add(c);
        return out;
    }
}
