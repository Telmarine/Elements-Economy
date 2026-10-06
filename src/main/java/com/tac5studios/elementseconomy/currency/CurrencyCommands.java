package com.tac5studios.elementseconomy.currency;

import net.neoforged.api.distmarker.Dist;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tac5studios.elementseconomy.config.Features;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /economy currency pending | confirm <item> | ignore <item>
 *   Lets an admin approve or refuse vanilla items added to currency_values.toml.
 * /economy currency switch <currency> | switch confirm
 *   Changes the whole server to a new currency ({@link SwitchOver}).
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class CurrencyCommands {

    private CurrencyCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        boolean confirm = Perm.enabled(Perm.CURRENCY_CONFIRM);
        boolean change = Perm.enabled(Perm.CURRENCY_SWITCH);
        if (!confirm && !change) return;
        register(event.getDispatcher(), confirm, change);
    }

    private static void register(CommandDispatcher<CommandSourceStack> d, boolean confirm, boolean change) {
        LiteralArgumentBuilder<CommandSourceStack> currency = Commands.literal("currency")
                .requires(src -> Perm.has(src, Perm.CURRENCY_CONFIRM) || Perm.has(src, Perm.CURRENCY_SWITCH));
        if (confirm) {
            currency.then(Commands.literal("pending").requires(src -> Perm.has(src, Perm.CURRENCY_CONFIRM))
                            .executes(CurrencyCommands::pending))
                    .then(Commands.literal("confirm").requires(src -> Perm.has(src, Perm.CURRENCY_CONFIRM))
                            .then(Commands.argument("item", ResourceLocationArgument.id())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                            CurrencyDetector.pendingVanilla(), b))
                                    .executes(c -> decide(c, true))))
                    .then(Commands.literal("ignore").requires(src -> Perm.has(src, Perm.CURRENCY_CONFIRM))
                            .then(Commands.argument("item", ResourceLocationArgument.id())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                            CurrencyDetector.pendingVanilla(), b))
                                    .executes(c -> decide(c, false))));
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
    private static java.util.List<ResourceLocation> switchTargets() {
        com.tac5studios.elementseconomy.core.Economy e = com.tac5studios.elementseconomy.core.Economy.get();
        if (e == null || e.primaryCurrency() == null) return java.util.List.of();
        return e.currencies().stream().filter(c -> !c.equals(e.primaryCurrency()))
                .map(com.tac5studios.elementsvault.Currency::id).toList();
    }

    private static int pending(CommandContext<CommandSourceStack> c) {
        var list = CurrencyDetector.pendingVanilla();
        if (list.isEmpty()) {
            Msg.send(c.getSource(), "currency.pending_none");
            return 0;
        }
        Msg.send(c.getSource(), "currency.pending_header", "count", list.size());
        if (c.getSource().getPlayer() != null) {
            CurrencyDetector.askToConfirm(c.getSource().getPlayer());
        } else {
            for (ResourceLocation id : list) {
                Msg.send(c.getSource(), "currency.pending_line", "item", id);
            }
        }
        return list.size();
    }

    private static int decide(CommandContext<CommandSourceStack> c, boolean confirm) {
        ResourceLocation id = ResourceLocationArgument.getId(c, "item");
        boolean ok = confirm ? CurrencyDetector.confirm(id) : CurrencyDetector.ignore(id);
        if (!ok) {
            Msg.fail(c.getSource(), "currency.not_pending", "item", id);
            return 0;
        }
        Msg.sendLogged(c.getSource(), confirm ? "currency.confirmed" : "currency.ignored", "item", id);
        if (confirm && com.tac5studios.elementseconomy.core.Economy.get() != null) {
            com.tac5studios.elementseconomy.core.Economy.get().reload(); // the confirmed item now counts
        }
        return 1;
    }
}
