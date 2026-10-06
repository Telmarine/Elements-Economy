package com.tac5studios.elementseconomy.shop;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.config.ShopConfig;
import com.tac5studios.elementseconomy.messages.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The little shop front above each player shop, made of vanilla display entities (no client mod):
 *  - a floating item (cycles through what the shop trades), and
 *  - a small sign: the shop's name, FOR SALE / WE BUY, and OPEN / CLOSED / SOLD OUT / NOT BUYING.
 *
 * The entities are never kept between restarts or chunk reloads: any saved copy is dropped when it
 * loads, and fresh ones are made while the shop's chunk is loaded. So nothing is ever left behind.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class ShopDisplays {

    /** Scoreboard tag on every display entity Economy makes. */
    public static final String TAG = "elements_economy_shop_display";

    private static final class Front {
        Display.ItemDisplay item;
        Display.TextDisplay sign;
        String itemKey = "";
        String signJson = "";
        int cycle;
    }

    private static final Map<String, Front> FRONTS = new HashMap<>();
    private static int ticks;

    private ShopDisplays() {}

    // ---------- lifecycle ----------

    /** Saved copies of our display entities are dropped as they load; live ones are made fresh. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.loadedFromDisk() && e.getEntity().getTags().contains(TAG)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        for (Front f : FRONTS.values()) discard(f);
        FRONTS.clear();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (++ticks < 20) return; // once a second
        ticks = 0;
        MinecraftServer server = e.getServer();
        boolean showItem = Features.on(Features.PLAYER_SHOPS, Features.PS_DISPLAY);
        boolean showSign = Features.on(Features.PLAYER_SHOPS, Features.PS_SIGN);

        // Fronts of removed shops go away.
        FRONTS.entrySet().removeIf(en -> {
            if (Shops.get(en.getKey()) != null) return false;
            discard(en.getValue());
            return true;
        });

        for (Shop s : Shops.all()) {
            ServerLevel level = server.getLevel(s.dimension);
            if (level == null || !level.isLoaded(s.pos()) || !(showItem || showSign)) {
                Front f = FRONTS.remove(s.id);
                if (f != null) discard(f);
                continue;
            }
            Front f = FRONTS.computeIfAbsent(s.id, k -> new Front());
            try {
                update(level, s, f, showItem, showSign);
            } catch (RuntimeException ex) {
                ElementsEconomy.LOGGER.warn("[Economy] Shop display for {} failed: {}", s.id, ex.toString());
            }
        }
    }

    /** Remove a shop's front now (shop removed). */
    public static void remove(String shopId) {
        Front f = FRONTS.remove(shopId);
        if (f != null) discard(f);
    }

    /** Redraw a shop's front on the next update (rename, open/closed, prices). */
    public static void touch(String shopId) {
        Front f = FRONTS.get(shopId);
        if (f != null) {
            f.signJson = "";
            f.itemKey = "";
        }
    }

    private static void discard(Front f) {
        if (f.item != null) f.item.discard();
        if (f.sign != null) f.sign.discard();
        f.item = null;
        f.sign = null;
    }

    // ---------- drawing ----------

    /** Centre of the top of the shop (both halves of a double chest). */
    private static Vec3 top(Shop s) {
        double x = 0, z = 0, y = Integer.MIN_VALUE;
        for (BlockPos p : s.positions) {
            x += p.getX() + 0.5;
            z += p.getZ() + 0.5;
            y = Math.max(y, p.getY());
        }
        int n = s.positions.size();
        return new Vec3(x / n, y + 1.0, z / n);
    }

    private static void update(ServerLevel level, Shop s, Front f, boolean showItem, boolean showSign) {
        Vec3 top = top(s);
        double itemScale = ShopConfig.ITEM_SCALE.get();
        Status status = status(level, s);

        // Floating item
        if (showItem) {
            List<ShopRow> rows = featured(level, s);
            ItemStack shown = ItemStack.EMPTY;
            if (!rows.isEmpty() && status != Status.CLOSED) {
                f.cycle = (f.cycle + 1) % (rows.size() * ShopConfig.DISPLAY_CYCLE.get());
                shown = rows.get(f.cycle / ShopConfig.DISPLAY_CYCLE.get()).item.copyWithCount(1);
            }
            String key = shown.isEmpty() ? "" : ShopStock.key(shown) + "|" + itemScale;
            if (f.item == null || f.item.isRemoved()) {
                f.item = spawnItem(level, top.add(0, 0.25 * itemScale + 0.05, 0), itemScale);
                f.itemKey = "!";
            }
            if (f.item != null && !key.equals(f.itemKey)) {
                setItem(level, f.item, shown);
                f.itemKey = key;
            }
        } else if (f.item != null) {
            f.item.discard();
            f.item = null;
        }

        // Sign
        if (showSign) {
            double signY = top.y + (showItem ? 0.55 * itemScale + 0.15 : 0.1);
            Component text = signText(s, status);
            String json = Component.Serializer.toJson(text, level.registryAccess()) + "|" + ShopConfig.SIGN_SCALE.get()
                    + "|" + ShopConfig.SIGN_BACKGROUND.get();
            if (f.sign == null || f.sign.isRemoved()) {
                f.sign = spawnSign(level, new Vec3(top.x, signY, top.z));
                f.signJson = "!";
            }
            if (f.sign != null && !json.equals(f.signJson)) {
                setSign(level, f.sign, text);
                f.signJson = json;
            }
        } else if (f.sign != null) {
            f.sign.discard();
            f.sign = null;
        }
    }

    // ---------- what the front says ----------

    enum Status { OPEN, CLOSED, EMPTY }

    static Status status(ServerLevel level, Shop s) {
        if (!s.open) return Status.CLOSED;
        return featured(level, s).isEmpty() ? Status.EMPTY : Status.OPEN;
    }

    /** Rows a customer could trade right now (buy: in stock; sell: the shop still wants it). */
    private static List<ShopRow> featured(ServerLevel level, Shop s) {
        List<ShopRow> out = new ArrayList<>();
        for (ShopRow r : s.rows.values()) {
            if (!r.listed()) continue;
            if (s.type == ShopType.BUY && ShopStock.available(level, s, r) < r.per) continue;
            if (s.type == ShopType.SELL && (r.wanted < r.per || !canPay(s, r))) continue;
            out.add(r);
        }
        return out;
    }

    private static boolean canPay(Shop s, ShopRow r) {
        return s.till(r.currency == null ? "" : r.currency).compareTo(r.price) >= 0;
    }

    private static Component signText(Shop s, Status status) {
        MutableComponent text = Component.empty();
        if (ShopConfig.SIGN_NAME.get()) text.append(Msg.text("display.name", "shop", s.name)).append("\n");
        text.append(Msg.text(s.type == ShopType.BUY ? "display.for_sale" : "display.we_buy")).append("\n");
        String key = switch (status) {
            case OPEN -> "display.open";
            case CLOSED -> "display.closed";
            case EMPTY -> s.type == ShopType.BUY ? "display.sold_out" : "display.not_buying";
        };
        text.append(Msg.text(key));
        return text;
    }

    // ---------- vanilla display entities (set up through their own save format) ----------

    @Nullable
    private static Display.ItemDisplay spawnItem(ServerLevel level, Vec3 at, double scale) {
        Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(level);
        if (d == null) return null;
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        tag.putString("billboard", "vertical");
        tag.putString("item_display", "fixed");
        tag.put("transformation", transformation((float) scale));
        tag.putFloat("view_range", 0.5f);
        d.load(tag);
        place(level, d, at);
        return d;
    }

    @Nullable
    private static Display.TextDisplay spawnSign(ServerLevel level, Vec3 at) {
        Display.TextDisplay d = EntityType.TEXT_DISPLAY.create(level);
        if (d == null) return null;
        place(level, d, at);
        return d;
    }

    private static void place(ServerLevel level, Entity d, Vec3 at) {
        d.setPos(at.x, at.y, at.z);
        d.addTag(TAG);
        d.setInvulnerable(true);
        level.addFreshEntity(d);
    }

    private static void setItem(ServerLevel level, Display.ItemDisplay d, ItemStack stack) {
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        if (stack.isEmpty()) tag.remove("item");
        else tag.put("item", stack.save(level.registryAccess()));
        d.load(tag);
    }

    private static void setSign(ServerLevel level, Display.TextDisplay d, Component text) {
        float scale = ShopConfig.SIGN_SCALE.get().floatValue();
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        tag.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
        tag.putString("billboard", "center");
        tag.putString("alignment", "center");
        tag.putInt("line_width", 200);
        tag.putInt("background", ShopConfig.SIGN_BACKGROUND.get() ? 0x40000000 : 0);
        tag.putBoolean("shadow", !ShopConfig.SIGN_BACKGROUND.get());
        tag.put("transformation", transformation(scale));
        tag.putFloat("view_range", 0.5f);
        d.load(tag);
    }

    private static CompoundTag transformation(float scale) {
        CompoundTag t = new CompoundTag();
        t.put("left_rotation", floats(0, 0, 0, 1));
        t.put("right_rotation", floats(0, 0, 0, 1));
        t.put("translation", floats(0, 0, 0));
        t.put("scale", floats(scale, scale, scale));
        return t;
    }

    private static ListTag floats(float... v) {
        ListTag l = new ListTag();
        for (float f : v) l.add(FloatTag.valueOf(f));
        return l;
    }
}
