package com.tac5studios.elementseconomy.core;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.adapters.Reflect;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.currency.CurrencyDetector;
import com.tac5studios.elementseconomy.currency.CurrencyDisplay;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Counting, taking and giving coins a player carries.
 *
 * Where coins are found (holders only when currency.count_holders is on):
 *  - the main inventory,
 *  - Curios slots, when Curios is installed (coin pouches, wallets worn as accessories),
 *  - inside holder items that keep items in the container component (bags, boxes), two levels deep,
 *  - holder items that store a coin value number instead of coins (Haven: Currency's pouch).
 */
public final class Coins {

    private static final int MAX_DEPTH = 2;

    /** Holders that store a number of coins as a data component: component id -> currency namespace. */
    private static final Map<String, String> VALUE_COMPONENTS = Map.of(
            "havencurrency:coin_value", "havencurrency"
    );

    private static final String CURIOS_API = "top.theillusivec4.curios.api.CuriosApi";
    private static boolean curiosBroken;

    private Coins() {}

    private static boolean holders() {
        return Features.on(Features.CURRENCY_COUNT_HOLDERS);
    }

    // ---------- where things are ----------

    /** A place one item stack lives, which can be read and replaced. */
    private interface Place {
        ItemStack get();

        void set(ItemStack stack);
    }

    private record InvPlace(Inventory inv, int slot) implements Place {
        public ItemStack get() {
            return inv.getItem(slot);
        }

        public void set(ItemStack s) {
            inv.setItem(slot, s);
        }
    }

    private record HandlerPlace(IItemHandlerModifiable handler, int slot) implements Place {
        public ItemStack get() {
            return handler.getStackInSlot(slot);
        }

        public void set(ItemStack s) {
            handler.setStackInSlot(slot, s);
        }
    }

    /** Slot {@code index} inside the container component of the item at {@code parent}. */
    private record InsidePlace(Place parent, int index) implements Place {
        public ItemStack get() {
            ItemContainerContents c = parent.get().get(DataComponents.CONTAINER);
            if (c == null) return ItemStack.EMPTY;
            List<ItemStack> items = c.stream().toList();
            return index < items.size() ? items.get(index) : ItemStack.EMPTY;
        }

        public void set(ItemStack s) {
            ItemStack holder = parent.get();
            ItemContainerContents c = holder.get(DataComponents.CONTAINER);
            if (c == null) return;
            List<ItemStack> items = new ArrayList<>(c.stream().map(ItemStack::copy).toList());
            if (index >= items.size()) return;
            items.set(index, s);
            ItemStack up = holder.copy();
            up.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
            parent.set(up);
        }
    }

    /** Every top-level place: inventory slots, then Curios slots. */
    private static List<Place> roots(ServerPlayer p) {
        List<Place> out = new ArrayList<>();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) out.add(new InvPlace(inv, i));
        if (holders()) {
            for (IItemHandlerModifiable h : curios(p)) {
                for (int i = 0; i < h.getSlots(); i++) out.add(new HandlerPlace(h, i));
            }
        }
        return out;
    }

    /** Curios slot handlers, or none when Curios isn't installed. */
    private static List<IItemHandlerModifiable> curios(ServerPlayer p) {
        List<IItemHandlerModifiable> out = new ArrayList<>();
        if (curiosBroken || !Reflect.has(CURIOS_API)) return out;
        try {
            Object opt = Reflect.callStatic(CURIOS_API, "getCuriosInventory", p);
            if (!(opt instanceof Optional<?> o) || o.isEmpty()) return out;
            Object curios = Reflect.call(o.get(), "getCurios");
            if (curios instanceof Map<?, ?> m) {
                for (Object stacksHandler : m.values()) {
                    Object stacks = Reflect.call(stacksHandler, "getStacks");
                    if (stacks instanceof IItemHandlerModifiable h) out.add(h);
                }
            }
        } catch (RuntimeException | LinkageError e) {
            curiosBroken = true;
            ElementsEconomy.LOGGER.warn("[Economy] Could not read Curios slots; coins there won't count: {}", e.toString());
        }
        return out;
    }

    // ---------- coin sources ----------

    /** Coins (or stored coin value) at one place. */
    private record Source(Place place, long each, long units, String valueComponent) {
        /** Remove n units and write the change back. */
        void take(long n) {
            ItemStack s = place.get().copy();
            if (valueComponent == null) {
                s.shrink((int) n);
                place.set(s.isEmpty() ? ItemStack.EMPTY : s);
            } else {
                DataComponentType<Object> type = componentType(valueComponent);
                Object now = s.get(type);
                long left = (now instanceof Number num ? num.longValue() : 0) - n;
                s.set(type, now instanceof Long ? (Object) left : (Object) (int) left);
                place.set(s);
            }
        }
    }

    private static void collect(Place place, String ns, int depth, List<Source> out) {
        ItemStack s = place.get();
        if (s.isEmpty()) return;
        long each = ns.equals(CurrencyDisplay.currencyOf(s.getItem())) ? CurrencyDetector.valueOf(s.getItem()) : 0;
        if (each > 0) out.add(new Source(place, each, s.getCount(), null));
        if (!holders()) return;
        for (Map.Entry<String, String> vc : VALUE_COMPONENTS.entrySet()) {
            if (!vc.getValue().equals(ns)) continue;
            DataComponentType<Object> type = componentType(vc.getKey());
            if (type == null) continue;
            Object v = s.get(type);
            if (v instanceof Number n && n.longValue() > 0) out.add(new Source(place, 1, n.longValue(), vc.getKey()));
        }
        if (depth < MAX_DEPTH) {
            ItemContainerContents inside = s.get(DataComponents.CONTAINER);
            if (inside != null) {
                int size = (int) inside.stream().count();
                for (int j = 0; j < size; j++) collect(new InsidePlace(place, j), ns, depth + 1, out);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<Object> componentType(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(rl)) return null;
        return (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl);
    }

    private static List<Source> sources(ServerPlayer p, String ns) {
        List<Source> out = new ArrayList<>();
        for (Place root : roots(p)) collect(root, ns, 0, out);
        return out;
    }

    // ---------- public ----------

    /** Total value of this currency the player carries. */
    public static long count(ServerPlayer player, String namespace) {
        long total = 0;
        for (Source s : sources(player, namespace)) total += s.each() * s.units();
        return total;
    }

    /**
     * Takes coins worth at least {@code amount}, smallest first (stored values, then small coins),
     * then pays back change. Returns false and changes nothing when the player doesn't carry enough.
     */
    public static boolean take(ServerPlayer player, String ns, long amount) {
        if (amount <= 0) return true;
        List<Source> list = sources(player, ns);
        long have = 0;
        for (Source s : list) have += s.each() * s.units();
        if (have < amount) return false;

        list.sort(Comparator.comparingLong(Source::each));
        long removed = 0;
        for (Source s : list) {
            if (removed >= amount) break;
            long need = (amount - removed + s.each() - 1) / s.each(); // round up
            long use = Math.min(s.units(), need);
            s.take(use);
            removed += use * s.each();
        }
        long change = removed - amount;
        if (change > 0) give(player, ns, change);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }

    /** Gives coins worth {@code amount} (fewest coins). Coins that don't fit drop at the player's feet. Returns the value too small for any coin. */
    public static long give(ServerPlayer player, String ns, long amount) {
        for (ItemStack stack : stacks(ns, amount)) {
            if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
        }
        player.containerMenu.broadcastChanges();
        return CurrencyDisplay.remainder(ns, amount);
    }

    /** Coin stacks for an amount, fewest coins, split into full stacks. */
    public static List<ItemStack> stacks(String ns, long amount) {
        List<ItemStack> out = new ArrayList<>();
        for (CurrencyDisplay.Coin c : CurrencyDisplay.breakdown(ns, amount)) {
            long left = c.count();
            int max = new ItemStack(c.item()).getMaxStackSize();
            while (left > 0) {
                int n = (int) Math.min(left, max);
                out.add(new ItemStack(c.item(), n));
                left -= n;
            }
        }
        return out;
    }

    /** Value of these stacks in one currency (coins inside holders included). */
    public static long valueOf(Iterable<ItemStack> stacks, String ns) {
        long v = 0;
        for (ItemStack s : stacks) {
            List<Source> out = new ArrayList<>();
            ItemStack copy = s.copy();
            Place p = new Place() {
                private ItemStack held = copy;

                public ItemStack get() {
                    return held;
                }

                public void set(ItemStack st) {
                    held = st;
                }
            };
            collect(p, ns, 0, out);
            for (Source src : out) v += src.each() * src.units();
        }
        return v;
    }

    /** The namespace an item belongs to as currency, or null when it isn't a counted coin. */
    public static String currencyOf(Item item) {
        return CurrencyDetector.valueOf(item) > 0 ? CurrencyDisplay.currencyOf(item) : null;
    }

    /** All denominations of a currency, largest first. */
    public static List<Map.Entry<Item, Long>> denominations(String ns) {
        return CurrencyDetector.denominations(ns);
    }
}
