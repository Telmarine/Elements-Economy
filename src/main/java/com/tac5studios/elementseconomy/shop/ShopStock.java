package com.tac5studios.elementseconomy.shop;

import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.storage.ItemData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Reading and moving a shop's stock, live from its container (and linked vault).
 * Items match when item and all components are the same (enchantments, names, contents).
 */
public final class ShopStock {

    private ShopStock() {}

    /** Stable key for an item type: hash of its saved form (count 1). */
    public static String key(ItemStack stack) {
        ItemStack one = stack.copyWithCount(1);
        String json = String.valueOf(ItemData.write(one, Shops.registries()));
        try {
            byte[] h = MessageDigest.getInstance("MD5").digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(json.hashCode());
        }
    }

    /** Every item type in a handler, one count-1 copy each, in slot order. */
    public static List<ItemStack> types(@Nullable IItemHandler h) {
        List<ItemStack> out = new ArrayList<>();
        if (h == null) return out;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty()) continue;
            boolean seen = false;
            for (ItemStack t : out) {
                if (ItemStack.isSameItemSameComponents(t, s)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) out.add(s.copyWithCount(1));
        }
        return out;
    }

    public static int count(@Nullable IItemHandler h, ItemStack type) {
        if (h == null || type == null) return 0;
        long n = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, type)) n += s.getCount();
        }
        return (int) Math.min(Integer.MAX_VALUE, n);
    }

    /** Takes up to n of a type. Returns what was taken, as stacks. */
    public static List<ItemStack> extract(@Nullable IItemHandler h, ItemStack type, int n) {
        List<ItemStack> out = new ArrayList<>();
        if (h == null || n <= 0) return out;
        int left = n;
        for (int i = 0; i < h.getSlots() && left > 0; i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, type)) continue;
            ItemStack got = h.extractItem(i, Math.min(left, s.getCount()), false);
            if (!got.isEmpty()) {
                out.add(got);
                left -= got.getCount();
            }
        }
        return out;
    }

    /** Puts a stack in. Returns what didn't fit. */
    public static ItemStack insert(@Nullable IItemHandler h, ItemStack stack, boolean simulate) {
        if (h == null) return stack;
        ItemStack left = stack.copy();
        for (int i = 0; i < h.getSlots() && !left.isEmpty(); i++) {
            left = h.insertItem(i, left, simulate);
        }
        return left;
    }

    /** How many of a type fit (checked by simulating). */
    public static int room(@Nullable IItemHandler h, ItemStack type, int want) {
        if (h == null) return 0;
        int fit = 0;
        int max = type.getMaxStackSize();
        int left = (int) Math.min(want, (long) h.getSlots() * max);
        List<ItemStack> chunks = new ArrayList<>();
        while (left > 0) {
            int n = Math.min(max, left);
            chunks.add(type.copyWithCount(n));
            left -= n;
        }
        // Work on a copy of the slots so pieces add up (real simulation doesn't carry over between calls).
        ItemStack[] slots = new ItemStack[h.getSlots()];
        for (int i = 0; i < slots.length; i++) slots[i] = h.getStackInSlot(i).copy();
        for (ItemStack c : chunks) {
            int need = c.getCount();
            for (int i = 0; i < slots.length && need > 0; i++) {
                if (!h.isItemValid(i, c)) continue;
                int limit = Math.min(h.getSlotLimit(i), max);
                if (slots[i].isEmpty()) {
                    int put = Math.min(limit, need);
                    slots[i] = c.copyWithCount(put);
                    need -= put;
                } else if (ItemStack.isSameItemSameComponents(slots[i], c)) {
                    int put = Math.min(limit - slots[i].getCount(), need);
                    if (put > 0) {
                        slots[i].grow(put);
                        need -= put;
                    }
                }
            }
            fit += c.getCount() - need;
            if (need > 0) break;
        }
        return fit;
    }

    // ---------- shop helpers (always past the shop lock) ----------

    @Nullable
    public static IItemHandler shop(Level level, Shop s) {
        return level.isLoaded(s.pos()) ? ShopContainers.raw(level, s.pos()) : null;
    }

    @Nullable
    public static IItemHandler vault(Level level, Shop s) {
        if (s.vault == null || !Features.on(Features.STOCK_VAULTS)) return null;
        BlockPos v = s.vault;
        return level.isLoaded(v) ? ShopContainers.raw(level, v) : null;
    }

    public static int countShop(Level level, Shop s, ShopRow r) {
        return count(shop(level, s), r.item);
    }

    public static int countVault(Level level, Shop s, ShopRow r) {
        return count(vault(level, s), r.item);
    }

    /** What buyers can buy: the shop plus the vault when overflow sales are on. */
    public static int available(Level level, Shop s, ShopRow r) {
        int n = countShop(level, s, r);
        if (Features.on(Features.STOCK_VAULTS, Features.SV_OVERFLOW_SALES)) n += countVault(level, s, r);
        return n;
    }

    /** Takes n for a sale: shop first, then the vault. */
    public static List<ItemStack> takeForSale(Level level, Shop s, ShopRow r, int n) {
        List<ItemStack> out = extract(shop(level, s), r.item, n);
        int got = 0;
        for (ItemStack st : out) got += st.getCount();
        if (got < n && Features.on(Features.STOCK_VAULTS, Features.SV_OVERFLOW_SALES)) {
            out.addAll(extract(vault(level, s), r.item, n - got));
        }
        return out;
    }

    /** SELL: how many sold items the shop can store (its container, plus the vault when overflow is on). */
    public static int roomForSold(Level level, Shop s, ShopRow r, int want) {
        int n = room(shop(level, s), r.item, want);
        if (n < want && Features.on(Features.STOCK_VAULTS, Features.SV_OVERFLOW_SALES)) {
            n += room(vault(level, s), r.item, want - n);
        }
        return n;
    }

    /** SELL: store items bought from a player, shop first, then the vault. Returns what didn't fit. */
    public static ItemStack storeSold(Level level, Shop s, ItemStack stack) {
        ItemStack left = insert(shop(level, s), stack, false);
        if (!left.isEmpty() && Features.on(Features.STOCK_VAULTS, Features.SV_OVERFLOW_SALES)) {
            left = insert(vault(level, s), left, false);
        }
        return left;
    }

    /** SELL: take back items just stored (when a payment fails), shop first, then the vault. */
    public static List<ItemStack> takeBackSold(Level level, Shop s, ShopRow r, int n) {
        List<ItemStack> out = extract(shop(level, s), r.item, n);
        int got = 0;
        for (ItemStack st : out) got += st.getCount();
        if (got < n) out.addAll(extract(vault(level, s), r.item, n - got));
        return out;
    }

    /** SELL: move items the shop bought into the vault, keeping the shop free for more. */
    public static int moveShopToVault(Level level, Shop s, ShopRow r) {
        IItemHandler shop = shop(level, s);
        IItemHandler vault = vault(level, s);
        if (shop == null || vault == null || r.item == null) return 0;
        int want = Math.min(count(shop, r.item), room(vault, r.item, count(shop, r.item)));
        if (want <= 0) return 0;
        int moved = 0;
        for (ItemStack st : extract(shop, r.item, want)) {
            ItemStack left = insert(vault, st, false);
            moved += st.getCount() - left.getCount();
            if (!left.isEmpty()) insert(shop, left, false); // put back what didn't fit
        }
        return moved;
    }

    /** Moves up to max of a row's item from the vault into the shop, as far as it fits. */
    public static int moveVaultToShop(Level level, Shop s, ShopRow r, int max) {
        IItemHandler shop = shop(level, s);
        IItemHandler vault = vault(level, s);
        if (shop == null || vault == null || r.item == null) return 0;
        int want = Math.min(max, Math.min(count(vault, r.item), room(shop, r.item, Integer.MAX_VALUE / 2)));
        if (want <= 0) return 0;
        int moved = 0;
        for (ItemStack st : extract(vault, r.item, want)) {
            ItemStack left = insert(shop, st, false);
            moved += st.getCount() - left.getCount();
            if (!left.isEmpty()) insert(vault, left, false); // put back what didn't fit
        }
        return moved;
    }
}
