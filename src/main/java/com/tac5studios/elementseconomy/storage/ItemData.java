package com.tac5studios.elementseconomy.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.tac5studios.elementseconomy.ElementsEconomy;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

/**
 * Turns items into JSON for the data store and back, keeping all components
 * (enchantments, names, contents of boxes and bags).
 *
 * If an item can't be read back (its mod was removed), the original JSON is kept,
 * so the item returns when the mod does. Nothing is ever thrown away.
 */
public final class ItemData {

    private ItemData() {}

    /** Item -> JSON. Empty stacks give an empty object. */
    public static JsonElement write(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return new JsonObject();
        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        return ItemStack.CODEC.encodeStart(ops, stack)
                .resultOrPartial(err -> ElementsEconomy.LOGGER.warn("[Economy] Could not save item {}: {}", stack, err))
                .orElse(new JsonObject());
    }

    /** JSON -> item. Returns null when the item can't be read (keep the JSON as it is). */
    public static ItemStack read(JsonElement json, HolderLookup.Provider registries) {
        if (json == null || (json.isJsonObject() && json.getAsJsonObject().isEmpty())) return ItemStack.EMPTY;
        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        return ItemStack.CODEC.parse(ops, json).result().orElse(null);
    }
}
