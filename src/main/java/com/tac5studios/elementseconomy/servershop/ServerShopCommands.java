package com.tac5studios.elementseconomy.servershop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.tac5studios.elementseconomy.shop.ShopType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Staff:   /economy shop create buy|sell <name> · edit <name> · delete <name> · list
 *          /economy shop link <name> npc|villager|block · unlink <name>
 * Players: /shop open <name>
 * Players can never create, own or edit a server shop.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class ServerShopCommands {

    /** Tag on villagers made for server shops. */
    public static final String VILLAGER_TAG = "elements_economy_shop_villager";

    private static final SuggestionProvider<CommandSourceStack> SHOPS =
            (c, b) -> SharedSuggestionProvider.suggest(ServerShops.ids(), b);

    private ServerShopCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        if (!Features.on(Features.SERVER_SHOPS)) return;
        register(e.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> shop = Commands.literal("shop");
        boolean any = false;
        if (Perm.enabled(Perm.SERVER_SHOP_CREATE)) {
            shop.then(Commands.literal("create").requires(s -> Perm.has(s, Perm.SERVER_SHOP_CREATE))
                    .then(Commands.literal("buy").then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(c -> create(c, ShopType.BUY))))
                    .then(Commands.literal("sell").then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(c -> create(c, ShopType.SELL)))));
            any = true;
        }
        if (Perm.enabled(Perm.SERVER_SHOP_EDIT)) {
            shop.then(Commands.literal("edit").requires(s -> Perm.has(s, Perm.SERVER_SHOP_EDIT))
                    .then(Commands.argument("name", StringArgumentType.greedyString()).suggests(SHOPS).executes(ServerShopCommands::edit)));
            shop.then(Commands.literal("list").requires(s -> Perm.has(s, Perm.SERVER_SHOP_EDIT)).executes(ServerShopCommands::list));
            LiteralArgumentBuilder<CommandSourceStack> link = Commands.literal("link").requires(s -> Perm.has(s, Perm.SERVER_SHOP_EDIT));
            link.then(Commands.argument("name", StringArgumentType.word()).suggests(SHOPS)
                    .then(Commands.literal("npc").executes(c -> link(c, ServerShop.Link.NPC)))
                    .then(Commands.literal("villager").executes(c -> link(c, ServerShop.Link.VILLAGER)))
                    .then(Commands.literal("block").executes(c -> link(c, ServerShop.Link.BLOCK))));
            shop.then(link);
            shop.then(Commands.literal("unlink").requires(s -> Perm.has(s, Perm.SERVER_SHOP_EDIT))
                    .then(Commands.argument("name", StringArgumentType.word()).suggests(SHOPS).executes(ServerShopCommands::unlink)));
            any = true;
        }
        if (Perm.enabled(Perm.SERVER_SHOP_DELETE)) {
            shop.then(Commands.literal("delete").requires(s -> Perm.has(s, Perm.SERVER_SHOP_DELETE))
                    .then(Commands.argument("name", StringArgumentType.greedyString()).suggests(SHOPS).executes(ServerShopCommands::delete)));
            any = true;
        }
        if (any) d.register(Commands.literal("economy").then(shop));

        if (Features.on(Features.SS_OPEN_COMMAND) && Perm.enabled(Perm.SERVER_SHOP_USE)) {
            d.register(Commands.literal("shop").then(Commands.literal("open").requires(s -> Perm.has(s, Perm.SERVER_SHOP_USE))
                    .then(Commands.argument("name", StringArgumentType.greedyString()).suggests(SHOPS).executes(ServerShopCommands::open))));
        }
    }

    // ---------- helpers ----------

    @Nullable
    private static ServerShop named(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name");
        ServerShop s = ServerShops.get(name);
        if (s == null) Msg.fail(c.getSource(), "servershop.not_found", "name", name);
        return s;
    }

    @Nullable
    private static ServerPlayer player(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) Msg.fail(c.getSource(), "general.players_only");
        return p;
    }

    /** The entity the player is looking at (not a player), or null. */
    @Nullable
    static Entity lookEntity(ServerPlayer p) {
        double reach = p.entityInteractionRange();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1f);
        Vec3 end = eye.add(look.scale(reach));
        AABB box = p.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, box,
                e -> !e.isSpectator() && !(e instanceof Player), reach * reach);
        return hit == null ? null : hit.getEntity();
    }

    @Nullable
    static BlockPos lookBlock(ServerPlayer p) {
        HitResult hit = p.pick(p.blockInteractionRange(), 0f, false);
        return hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK ? b.getBlockPos() : null;
    }

    // ---------- commands ----------

    private static int create(CommandContext<CommandSourceStack> c, ShopType type) {
        String name = StringArgumentType.getString(c, "name").trim();
        String id = ServerShops.idOf(name);
        if (id.isEmpty() || name.length() > 32) {
            Msg.fail(c.getSource(), "general.bad_amount", "input", name);
            return 0;
        }
        if (ServerShops.get(id) != null) {
            Msg.fail(c.getSource(), "servershop.exists", "name", name);
            return 0;
        }
        ServerShop s = new ServerShop(id, name, type);
        ServerShops.add(s);
        Msg.sendLogged(c.getSource(), "servershop.created", "name", name);
        ServerPlayer p = c.getSource().getPlayer();
        if (p != null && Perm.has(p, Perm.SERVER_SHOP_EDIT)) new ServerEditMenu(s).open(p);
        return 1;
    }

    private static int edit(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        ServerShop s = p == null ? null : named(c);
        if (s == null) return 0;
        new ServerEditMenu(s).open(p);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        if (ServerShops.all().isEmpty()) {
            Msg.send(c.getSource(), "servershop.list_empty");
            return 0;
        }
        Msg.send(c.getSource(), "servershop.list_header", "count", ServerShops.all().size());
        for (ServerShop s : ServerShops.all()) {
            Msg.send(c.getSource(), "servershop.list_line", "name", s.name, "id", s.id,
                    "type", s.type == ShopType.BUY ? "buy" : "sell", "items", s.rows.size(), "links", s.links.size());
        }
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> c) {
        ServerShop s = named(c);
        if (s == null) return 0;
        // Remove the villagers this shop spawned, where they're loaded.
        for (ServerShop.Link l : s.links) {
            if (!ServerShop.Link.VILLAGER.equals(l.kind())) continue;
            UUID id = UUID.fromString(l.target().substring("entity|".length()));
            for (ServerLevel level : c.getSource().getServer().getAllLevels()) {
                Entity en = level.getEntity(id);
                if (en != null && en.getTags().contains(VILLAGER_TAG)) en.discard();
            }
        }
        ServerShops.delete(s);
        Msg.sendLogged(c.getSource(), "servershop.deleted", "name", s.name);
        return 1;
    }

    private static int link(CommandContext<CommandSourceStack> c, String kind) {
        ServerPlayer p = player(c);
        ServerShop s = p == null ? null : named(c);
        if (s == null) return 0;
        switch (kind) {
            case ServerShop.Link.NPC -> {
                if (!Features.on(Features.SS_NPC_HOOK)) {
                    Msg.fail(c.getSource(), "general.disabled");
                    return 0;
                }
                Entity target = lookEntity(p);
                if (target == null) {
                    Msg.fail(c.getSource(), "servershop.look_at_npc");
                    return 0;
                }
                ServerShops.link(s, new ServerShop.Link(kind, ServerShops.entityKey(target.getUUID())));
                Msg.sendLogged(c.getSource(), "servershop.linked", "name", s.name, "target", target.getName().getString());
            }
            case ServerShop.Link.VILLAGER -> {
                if (!Features.on(Features.SS_VILLAGER)) {
                    Msg.fail(c.getSource(), "general.disabled");
                    return 0;
                }
                Villager v = EntityType.VILLAGER.create(p.serverLevel());
                if (v == null) return 0;
                v.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0f);
                v.setYHeadRot(p.getYRot());
                v.setNoAi(true);
                v.setInvulnerable(true);
                v.setSilent(true);
                v.setPersistenceRequired();
                v.setCustomName(Component.literal(s.name));
                v.setCustomNameVisible(true);
                v.addTag(VILLAGER_TAG);
                p.serverLevel().addFreshEntity(v);
                ServerShops.link(s, new ServerShop.Link(kind, ServerShops.entityKey(v.getUUID())));
                Msg.sendLogged(c.getSource(), "servershop.linked", "name", s.name, "target", "villager");
            }
            default -> {
                if (!Features.on(Features.SS_BLOCK)) {
                    Msg.fail(c.getSource(), "general.disabled");
                    return 0;
                }
                BlockPos pos = lookBlock(p);
                if (pos == null) {
                    Msg.fail(c.getSource(), "servershop.look_at_block");
                    return 0;
                }
                ServerShops.link(s, new ServerShop.Link(kind, ServerShops.blockKey(p.level().dimension(), pos)));
                Msg.sendLogged(c.getSource(), "servershop.linked", "name", s.name,
                        "target", p.level().getBlockState(pos).getBlock().getName().getString());
            }
        }
        return 1;
    }

    private static int unlink(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        ServerShop s = p == null ? null : named(c);
        if (s == null) return 0;
        Entity target = lookEntity(p);
        String key = target != null ? ServerShops.entityKey(target.getUUID()) : null;
        if (key == null || !ServerShops.unlink(s, key)) {
            BlockPos pos = lookBlock(p);
            key = pos == null ? null : ServerShops.blockKey(p.level().dimension(), pos);
            if (key == null || !ServerShops.unlink(s, key)) {
                Msg.fail(c.getSource(), "servershop.not_linked", "name", s.name);
                return 0;
            }
        }
        if (target != null && target.getTags().contains(VILLAGER_TAG)) target.discard();
        Msg.sendLogged(c.getSource(), "servershop.unlinked", "name", s.name);
        return 1;
    }

    private static int open(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        ServerShop s = p == null ? null : named(c);
        if (s == null) return 0;
        ServerShopLinks.openFor(p, s);
        return 1;
    }
}
