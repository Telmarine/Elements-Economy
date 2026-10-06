package com.tac5studios.elementseconomy.shop;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.adapters.Reflect;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.config.ShopConfig;
import com.tac5studios.elementseconomy.integration.NexusHook;
import com.tac5studios.elementseconomy.perms.Perm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.Optional;
import java.util.UUID;

/** Shop limits and claim rules (Open Parties and Claims, FTB Chunks). */
public final class ShopRules {

    /** Whose land a block is on, as far as the claim mods know. */
    public enum Claim { UNCLAIMED, OWN, OTHER, UNKNOWN }

    private static final String OPAC_API = "xaero.pac.common.server.api.OpenPACServerAPI";
    private static final String FTB_API = "dev.ftb.mods.ftbchunks.api.FTBChunksAPI";
    private static final String FTB_POS = "dev.ftb.mods.ftblibrary.math.ChunkDimPos";

    private static boolean opacWarned;
    private static boolean ftbWarned;

    private ShopRules() {}

    /** Shops this player may own, or -1 for no limit. */
    public static int limit(ServerPlayer p) {
        if (Perm.has(p, Perm.SHOP_NO_LIMIT)) return -1;
        int fromPerms = Perm.limit(p, Perm.SHOP_LIMIT);
        if (fromPerms >= 0) return fromPerms;
        if (Features.on(Features.PLAYER_SHOPS, Features.PS_RANK_LIMITS)) {
            Optional<String> rank = NexusHook.rankOf(p.getUUID());
            if (rank.isPresent()) {
                for (String line : ShopConfig.RANK_LIMITS.get()) {
                    int eq = line.indexOf('=');
                    if (eq > 0 && line.substring(0, eq).trim().equalsIgnoreCase(rank.get())) {
                        try {
                            return Integer.parseInt(line.substring(eq + 1).trim());
                        } catch (NumberFormatException ignored) {
                            // bad line
                        }
                    }
                }
            }
        }
        return ShopConfig.DEFAULT_LIMIT.get();
    }

    /**
     * May this player turn this block into a shop (or link it as a vault)?
     *  - Never on land claimed by someone else, so nobody can lock another player's chest.
     *  - With player_shops.claim_check on, only inside the player's own claim.
     * Staff with economy.shop.claim.bypass skip both.
     */
    public static boolean claimOk(ServerPlayer p, BlockPos pos) {
        if (!Features.on(Features.CLAIMS) || Perm.has(p, Perm.SHOP_CLAIM_BYPASS)) return true;
        Claim c = claimAt(p.level(), pos, p.getUUID());
        if (c == Claim.OTHER) return false;
        if (Features.on(Features.PLAYER_SHOPS, Features.PS_CLAIM_CHECK)) return c == Claim.OWN || c == Claim.UNKNOWN;
        return true;
    }

    /** Checks every installed claim mod that's switched on. OTHER wins over OWN. */
    public static Claim claimAt(Level level, BlockPos pos, UUID player) {
        Claim result = Claim.UNCLAIMED;
        if (Features.on(Features.CLAIMS_OPAC) && ModList.get().isLoaded("openpartiesandclaims")) {
            result = merge(result, opac(level, pos, player));
        }
        if (Features.on(Features.CLAIMS_FTB_CHUNKS) && ModList.get().isLoaded("ftbchunks")) {
            result = merge(result, ftb(level, pos, player));
        }
        return result;
    }

    private static Claim merge(Claim a, Claim b) {
        if (a == Claim.OTHER || b == Claim.OTHER) return Claim.OTHER;
        if (a == Claim.OWN || b == Claim.OWN) return Claim.OWN;
        if (a == Claim.UNKNOWN || b == Claim.UNKNOWN) return Claim.UNKNOWN;
        return Claim.UNCLAIMED;
    }

    /** Open Parties and Claims: the claim's owner must be the player. */
    private static Claim opac(Level level, BlockPos pos, UUID player) {
        try {
            Object api = Reflect.callStatic(OPAC_API, "get", level.getServer());
            Object claims = Reflect.call(api, "getServerClaimsManager");
            Object claim = Reflect.call(claims, "get", level.dimension().location(), pos.getX() >> 4, pos.getZ() >> 4);
            if (claim == null) return Claim.UNCLAIMED;
            Object owner = Reflect.call(claim, "getPlayerId");
            return player.equals(owner) ? Claim.OWN : Claim.OTHER;
        } catch (RuntimeException e) {
            if (!opacWarned) {
                opacWarned = true;
                ElementsEconomy.LOGGER.warn("[Economy] Open Parties and Claims check failed: {}", e.toString());
            }
            return Claim.UNKNOWN;
        }
    }

    /** FTB Chunks: the player must be a member of the team that owns the chunk. */
    private static Claim ftb(Level level, BlockPos pos, UUID player) {
        try {
            Object api = Reflect.callStatic(FTB_API, "api");
            if (!Boolean.TRUE.equals(Reflect.call(api, "isManagerLoaded"))) return Claim.UNKNOWN;
            Object manager = Reflect.call(api, "getManager");
            Object chunkPos = Reflect.create(FTB_POS, level, pos);
            Object chunk = Reflect.call(manager, "getChunk", chunkPos);
            if (chunk == null) return Claim.UNCLAIMED;
            Object team = Reflect.call(chunk, "getTeamData");
            return Boolean.TRUE.equals(Reflect.call(team, "isTeamMember", player)) ? Claim.OWN : Claim.OTHER;
        } catch (RuntimeException e) {
            if (!ftbWarned) {
                ftbWarned = true;
                ElementsEconomy.LOGGER.warn("[Economy] FTB Chunks check failed: {}", e.toString());
            }
            return Claim.UNKNOWN;
        }
    }
}
