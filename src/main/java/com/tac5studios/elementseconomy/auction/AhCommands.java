package com.tac5studios.elementseconomy.auction;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.AuctionConfig;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /ah · /ah sell · /ah collect · /ah listings · /ah remove <id> (staff)
 * /ah blacklist add|remove|list (admin, uses the item in hand)
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class AhCommands {

    private AhCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        if (!Features.on(Features.AUCTION)) return;
        register(e.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Perm.enabled(Perm.AH_OPEN)) return;
        LiteralArgumentBuilder<CommandSourceStack> ah = Commands.literal("ah")
                .requires(s -> Perm.has(s, Perm.AH_OPEN))
                .executes(AhCommands::open);
        if (Perm.enabled(Perm.AH_SELL)) ah.then(Commands.literal("sell").requires(s -> Perm.has(s, Perm.AH_SELL)).executes(AhCommands::sell));
        if (Perm.enabled(Perm.AH_COLLECT)) ah.then(Commands.literal("collect").requires(s -> Perm.has(s, Perm.AH_COLLECT)).executes(AhCommands::collect));
        if (Features.on(Features.AH_MY_LISTINGS)) ah.then(Commands.literal("listings").executes(AhCommands::listings));
        if (Perm.enabled(Perm.AH_REMOVE)) {
            ah.then(Commands.literal("remove").requires(s -> Perm.has(s, Perm.AH_REMOVE))
                    .then(Commands.argument("id", StringArgumentType.word()).executes(AhCommands::remove)));
        }
        if (Perm.enabled(Perm.AH_BLACKLIST)) {
            ah.then(Commands.literal("blacklist").requires(s -> Perm.has(s, Perm.AH_BLACKLIST))
                    .then(Commands.literal("add").executes(c -> blacklist(c, true)))
                    .then(Commands.literal("remove").executes(c -> blacklist(c, false)))
                    .then(Commands.literal("list").executes(AhCommands::blacklistList)));
        }
        d.register(ah);
    }

    @Nullable
    private static ServerPlayer player(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) Msg.fail(c.getSource(), "general.players_only");
        return p;
    }

    private static int open(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        new AhBrowseMenu().open(p);
        return 1;
    }

    private static int sell(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) {
            Msg.send(p, "ah.hold_item");
            return 0;
        }
        if (AhTrade.blacklisted(hand)) {
            Msg.send(p, "ah.blacklisted");
            return 0;
        }
        new AhSellMenu(hand).open(p);
        return 1;
    }

    private static int collect(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        int n = AhTrade.collectAll(p);
        Msg.send(p, n > 0 ? "ah.collected" : "ah.nothing", "count", n);
        return n;
    }

    private static int listings(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        new AhMyListingsMenu(new AhBrowseMenu()).open(p);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        Listing l = Auctions.get(StringArgumentType.getString(c, "id"));
        if (l == null) {
            Msg.send(p, "ah.gone");
            return 0;
        }
        return AhTrade.adminRemove(p, l) ? 1 : 0;
    }

    private static int blacklist(CommandContext<CommandSourceStack> c, boolean add) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) {
            Msg.send(p, "ah.hold_item");
            return 0;
        }
        String id = BuiltInRegistries.ITEM.getKey(hand.getItem()).toString();
        List<String> list = new ArrayList<>(AuctionConfig.BLACKLIST.get());
        boolean changed = add ? !list.contains(id) && list.add(id) : list.remove(id);
        if (changed) {
            AuctionConfig.BLACKLIST.set(list);
            AuctionConfig.BLACKLIST.save();
        }
        Msg.send(p, add ? "ah.blacklist_added" : "ah.blacklist_removed", "item", id);
        return 1;
    }

    private static int blacklistList(CommandContext<CommandSourceStack> c) {
        List<? extends String> list = AuctionConfig.BLACKLIST.get();
        Msg.send(c.getSource(), "ah.blacklist_header", "count", list.size());
        for (String s : list) Msg.send(c.getSource(), "ah.blacklist_line", "item", s);
        return list.size();
    }
}
