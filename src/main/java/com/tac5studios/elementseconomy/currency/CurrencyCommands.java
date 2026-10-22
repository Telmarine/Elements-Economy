package com.tac5studios.elementseconomy.currency;

import net.neoforged.api.distmarker.Dist;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Currency commands, all usable from chat with clickable buttons:
 * <ul>
 *   <li>/economy currency setup - asks: digital money or vanilla items</li>
 *   <li>/economy currency setup digital | vanilla | add &lt;item&gt; &lt;value&gt; | remove &lt;item&gt; | confirm | cancel</li>
 *   <li>/economy currency values - every currency item and its value, with Change and Remove buttons</li>
 *   <li>/economy currency value &lt;item&gt; &lt;value&gt; and /economy currency remove &lt;item&gt;</li>
 *   <li>/economy currency switch &lt;currency&gt; | switch confirm - changes the whole server to a new currency ({@link SwitchOver})</li>
 * </ul>
 * Picking vanilla items and confirming the list is the approval: vanilla items never count as money otherwise.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class CurrencyCommands {

    /** Vanilla items being picked with /economy currency setup vanilla, before confirm. One list for the whole server. */
    private static Map<ResourceLocation, Long> draft;

    private static final SuggestionProvider<CommandSourceStack> VANILLA_ITEMS = (c, b) -> SharedSuggestionProvider.suggestResource(
            BuiltInRegistries.ITEM.keySet().stream().filter(CurrencyDetector::isVanilla), b);

    private static final SuggestionProvider<CommandSourceStack> DRAFT_ITEMS = (c, b) -> SharedSuggestionProvider.suggestResource(
            draft == null ? List.of() : List.copyOf(draft.keySet()), b);

    private static final SuggestionProvider<CommandSourceStack> LISTED_ITEMS = (c, b) -> SharedSuggestionProvider.suggestResource(
            CurrencyDetector.lines().keySet().stream().map(ResourceLocation::tryParse).filter(Objects::nonNull), b);

    private CurrencyCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        boolean setup = Perm.enabled(Perm.CURRENCY_SETUP);
        boolean change = Perm.enabled(Perm.CURRENCY_SWITCH);
        if (!setup && !change) return;
        register(event.getDispatcher(), setup, change);
    }

    private static void register(CommandDispatcher<CommandSourceStack> d, boolean setup, boolean change) {
        LiteralArgumentBuilder<CommandSourceStack> currency = Commands.literal("currency")
                .requires(src -> Perm.has(src, Perm.CURRENCY_SETUP) || Perm.has(src, Perm.CURRENCY_SWITCH));
        if (setup) {
            currency.then(Commands.literal("setup").requires(src -> Perm.has(src, Perm.CURRENCY_SETUP))
                            .executes(CurrencyCommands::ask)
                            .then(Commands.literal("digital").executes(CurrencyCommands::digital))
                            .then(Commands.literal("vanilla").executes(CurrencyCommands::vanilla))
                            .then(Commands.literal("add")
                                    .then(Commands.argument("item", ResourceLocationArgument.id()).suggests(VANILLA_ITEMS)
                                            .then(Commands.argument("value", LongArgumentType.longArg(1))
                                                    .executes(CurrencyCommands::draftAdd))))
                            .then(Commands.literal("remove")
                                    .then(Commands.argument("item", ResourceLocationArgument.id()).suggests(DRAFT_ITEMS)
                                            .executes(CurrencyCommands::draftRemove)))
                            .then(Commands.literal("confirm").executes(CurrencyCommands::draftConfirm))
                            .then(Commands.literal("cancel").executes(CurrencyCommands::draftCancel)))
                    .then(Commands.literal("values").requires(src -> Perm.has(src, Perm.CURRENCY_SETUP))
                            .executes(CurrencyCommands::values))
                    .then(Commands.literal("value").requires(src -> Perm.has(src, Perm.CURRENCY_SETUP))
                            .then(Commands.argument("item", ResourceLocationArgument.id()).suggests(LISTED_ITEMS)
                                    .then(Commands.argument("value", LongArgumentType.longArg(0))
                                            .executes(CurrencyCommands::setValue))))
                    .then(Commands.literal("remove").requires(src -> Perm.has(src, Perm.CURRENCY_SETUP))
                            .then(Commands.argument("item", ResourceLocationArgument.id()).suggests(LISTED_ITEMS)
                                    .executes(CurrencyCommands::removeValue)));
        }
        if (change) {
            currency.then(Commands.literal("switch").requires(src -> Perm.has(src, Perm.CURRENCY_SWITCH))
                    .then(Commands.literal("confirm").executes(c -> SwitchOver.confirm(c.getSource())))
                    .then(Commands.argument("currency", ResourceLocationArgument.id())
                            .suggests((c, b) -> SharedSuggestionProvider.suggestResource(switchTargets(), b))
                            .executes(c -> SwitchOver.start(c.getSource(), ResourceLocationArgument.getId(c, "currency")))));
        }
        d.register(Commands.literal("economy").then(currency));
    }

    /** Installed currencies other than the current one. */
    private static List<ResourceLocation> switchTargets() {
        Economy e = Economy.get();
        if (e == null || e.primaryCurrency() == null) return List.of();
        return e.currencies().stream().filter(c -> !c.equals(e.primaryCurrency()))
                .map(com.tac5studios.elementsvault.Currency::id).toList();
    }

    // ---------- setup ----------

    private static int ask(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSystemMessage(CurrencyDetector.askMessage());
        return 1;
    }

    private static int digital(CommandContext<CommandSourceStack> c) {
        draft = null;
        if (!CurrencyDetector.chooseDigital()) {
            Msg.fail(c.getSource(), "general.error");
            return 0;
        }
        reloadEconomy();
        Msg.sendLogged(c.getSource(), "currency.setup_digital_done");
        return 1;
    }

    private static int vanilla(CommandContext<CommandSourceStack> c) {
        Map<ResourceLocation, Long> start = new LinkedHashMap<>();
        // Start from the vanilla items already in use, if any.
        CurrencyDetector.lines().forEach((k, v) -> {
            ResourceLocation id = ResourceLocation.tryParse(k);
            if (id != null && CurrencyDetector.isVanilla(id) && v > 0) start.put(id, v);
        });
        draft = start;
        Msg.send(c.getSource(), "currency.setup_vanilla_start");
        c.getSource().sendSystemMessage(Msg.join(
                example("minecraft:emerald", 1),
                example("minecraft:emerald_block", 9)));
        showDraft(c.getSource());
        return 1;
    }

    private static int draftAdd(CommandContext<CommandSourceStack> c) {
        if (draft == null) {
            Msg.fail(c.getSource(), "currency.setup_no_draft");
            return 0;
        }
        ResourceLocation id = ResourceLocationArgument.getId(c, "item");
        if (!checkVanillaItem(c.getSource(), id)) return 0;
        draft.put(id, LongArgumentType.getLong(c, "value"));
        showDraft(c.getSource());
        return 1;
    }

    private static int draftRemove(CommandContext<CommandSourceStack> c) {
        if (draft == null) {
            Msg.fail(c.getSource(), "currency.setup_no_draft");
            return 0;
        }
        draft.remove(ResourceLocationArgument.getId(c, "item"));
        showDraft(c.getSource());
        return 1;
    }

    private static int draftConfirm(CommandContext<CommandSourceStack> c) {
        if (draft == null) {
            Msg.fail(c.getSource(), "currency.setup_no_draft");
            return 0;
        }
        if (draft.isEmpty()) {
            Msg.fail(c.getSource(), "currency.setup_needs_items");
            return 0;
        }
        int count = draft.size();
        boolean ok = CurrencyDetector.chooseVanilla(draft);
        draft = null;
        if (!ok) {
            Msg.fail(c.getSource(), "general.error");
            return 0;
        }
        reloadEconomy();
        Msg.sendLogged(c.getSource(), "currency.setup_vanilla_done", "count", count);
        return 1;
    }

    private static int draftCancel(CommandContext<CommandSourceStack> c) {
        draft = null;
        Msg.send(c.getSource(), "currency.setup_cancelled");
        return 1;
    }

    private static void showDraft(CommandSourceStack src) {
        if (draft.isEmpty()) {
            Msg.send(src, "currency.setup_draft_empty");
        } else {
            Msg.send(src, "currency.setup_draft_header");
            draft.forEach((id, v) -> src.sendSystemMessage(Msg.join(
                    Msg.text("currency.setup_draft_line", "name", name(id), "item", id, "value", v),
                    Msg.button("currency.remove_button", "currency.remove_hover", "/economy currency setup remove " + id,
                            "item", name(id)))));
        }
        src.sendSystemMessage(Msg.join(
                Msg.suggest("currency.add_button", "currency.add_hover", "/economy currency setup add minecraft:"),
                Msg.button("currency.setup_confirm_button", "currency.setup_confirm_hover", "/economy currency setup confirm"),
                Msg.button("currency.setup_cancel_button", "currency.setup_cancel_hover", "/economy currency setup cancel")));
    }

    private static Component example(String id, long value) {
        return Msg.suggest("currency.setup_example", "currency.setup_example_hover",
                "/economy currency setup add " + id + " " + value, "item", name(ResourceLocation.parse(id)), "value", value);
    }

    // ---------- values ----------

    private static int values(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        Map<String, Long> lines = CurrencyDetector.lines();
        if (lines.isEmpty()) {
            Msg.send(src, "currency.values_empty");
        } else {
            Msg.send(src, "currency.values_header");
            lines.forEach((k, v) -> {
                ResourceLocation id = ResourceLocation.tryParse(k);
                if (id == null) return;
                MutableComponent row = Msg.join(
                        Msg.text("currency.values_line", "name", name(id), "item", id, "value", v),
                        Msg.suggest("currency.change_button", "currency.change_hover", "/economy currency value " + id + " ",
                                "item", name(id)));
                if (CurrencyDetector.isVanilla(id)) {
                    row = Msg.join(row, Msg.button("currency.remove_button", "currency.remove_hover",
                            "/economy currency remove " + id, "item", name(id)));
                }
                src.sendSystemMessage(row);
            });
        }
        if (CurrencyDetector.VANILLA_ITEMS.equals(CurrencyDetector.setupChoice())) {
            src.sendSystemMessage(Msg.suggest("currency.add_button", "currency.add_hover", "/economy currency value minecraft:"));
        }
        src.sendSystemMessage(Msg.button("currency.setup_change_button", "currency.setup_change_hover", "/economy currency setup"));
        return lines.size();
    }

    private static int setValue(CommandContext<CommandSourceStack> c) {
        ResourceLocation id = ResourceLocationArgument.getId(c, "item");
        long value = LongArgumentType.getLong(c, "value");
        if (!CurrencyDetector.setValue(id, value)) {
            Msg.fail(c.getSource(), "currency.value_not_listed", "item", id);
            return 0;
        }
        reloadEconomy();
        if (value <= 0 && CurrencyDetector.isVanilla(id)) {
            Msg.sendLogged(c.getSource(), "currency.value_removed", "item", name(id));
        } else {
            Msg.sendLogged(c.getSource(), "currency.value_set", "item", name(id), "value", value);
        }
        return 1;
    }

    private static int removeValue(CommandContext<CommandSourceStack> c) {
        ResourceLocation id = ResourceLocationArgument.getId(c, "item");
        if (!CurrencyDetector.setValue(id, 0)) {
            Msg.fail(c.getSource(), "currency.value_not_listed", "item", id);
            return 0;
        }
        reloadEconomy();
        Msg.sendLogged(c.getSource(), "currency.value_removed", "item", name(id));
        return 1;
    }

    // ---------- helpers ----------

    private static boolean checkVanillaItem(CommandSourceStack src, ResourceLocation id) {
        if (!CurrencyDetector.isVanilla(id)) {
            Msg.fail(src, "currency.setup_not_vanilla", "item", id);
            return false;
        }
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            Msg.fail(src, "currency.setup_unknown_item", "item", id);
            return false;
        }
        return true;
    }

    /** The item's in-game name, e.g. "Block of Emerald". */
    private static String name(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString() : id.toString();
    }

    private static void reloadEconomy() {
        Economy e = Economy.get();
        if (e != null) e.reload();
    }
}
