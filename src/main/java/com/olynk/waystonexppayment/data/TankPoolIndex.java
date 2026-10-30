package com.olynk.waystonexppayment.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What the server last saw in the payment tank below each waystone slot, so a destination far away can still be
 * quoted and paid without its chunk being resident.
 *
 * <p>The map is written whenever a tank's contents change and whenever a chunk happens to be loaded and looked
 * at. A slot that was seen and had no tank stores {@code poolXp = NO_TANK}: "nothing here" is a fact worth
 * remembering, because it is what lets the client grey a button out honestly instead of guessing.
 *
 * <p>Treat every read as a hint, never as authority. The real payment resolves the block entity (loading its
 * chunk when it must) and drains that; this map exists for the preview and for the moment before a chunk load.
 */
public final class TankPoolIndex extends SavedData {

    /** {@code poolXp} for "a chunk was loaded and there was no tank below this waystone". */
    public static final int NO_TANK = -1;

    private static final String DATA_NAME = "waystone_xp_payment_pools";

    /**
     * Cap on remembered slots.
     *
     * <p>Nothing in the mod enumerates waystones, so entries only arrive from tanks that were actually seen; a
     * server with more than this many distinct tank slots has long since stopped being the case this feature is
     * for. Dropping new entries past the cap keeps memory bounded without an eviction policy that would need its
     * own correctness argument.
     */
    private static final int MAX_ENTRIES = 4096;

    private final Map<String, Pool> pools = new HashMap<>();

    // Not part of the saved data on purpose: a server that restarts may warn once more, which is the point.
    private boolean warnedAboutCap;

    /** @param poolXp XP the tank could pay, or {@link #NO_TANK} when the slot was seen and had no tank */
    public record Pool(int poolXp, int xpPerBucketMilli, long updatedAt) {

        public boolean hadTank() {
            return poolXp != NO_TANK;
        }
    }

    public static TankPoolIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TankPoolIndex::new, TankPoolIndex::load), DATA_NAME);
    }

    /** Records the current state of one slot. {@code poolXp = NO_TANK} means "looked, nothing there". */
    public void record(ResourceLocation dimension, BlockPos pos, int poolXp, int xpPerBucketMilli, long now) {
        String key = key(dimension, pos);
        if (!pools.containsKey(key) && pools.size() >= MAX_ENTRIES) {
            // Silent drops would make a preview that quietly stops updating for new tanks; one line per server
            // session is enough to point at the index without turning a crowded world into log spam.
            if (!warnedAboutCap) {
                warnedAboutCap = true;
                WaystoneXpPaymentMod.LOGGER.warn(
                        "Tank pool index is full ({} slots) - new tank slots will not be remembered for the "
                                + "remote destination preview until entries are removed",
                        MAX_ENTRIES);
            }
            return;
        }
        Pool updated = new Pool(poolXp, xpPerBucketMilli, now);
        Pool previous = pools.put(key, updated);
        if (!updated.equals(previous)) {
            setDirty();
        }
    }

    /** @return the last recorded state of that slot, or {@code null} when this server never saw it */
    @Nullable
    public Pool get(ResourceLocation dimension, BlockPos pos) {
        return pools.get(key(dimension, pos));
    }

    /** Drops a slot, used when the block there is removed and the entry would otherwise stay positive. */
    public void forget(ResourceLocation dimension, BlockPos pos) {
        if (pools.remove(key(dimension, pos)) != null) {
            setDirty();
        }
    }

    public int size() {
        return pools.size();
    }

    /** Stable key for a slot; the dimension is part of it because the same position exists in every dimension. */
    public static String key(ResourceLocation dimension, BlockPos pos) {
        return dimension + "@" + pos.asLong();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rows = new ListTag();
        for (Map.Entry<String, Pool> entry : pools.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("slot", entry.getKey());
            row.putInt("poolXp", entry.getValue().poolXp());
            row.putInt("xpPerBucketMilli", entry.getValue().xpPerBucketMilli());
            row.putLong("updatedAt", entry.getValue().updatedAt());
            rows.add(row);
        }
        tag.put("pools", rows);
        return tag;
    }

    public static TankPoolIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        TankPoolIndex index = new TankPoolIndex();
        ListTag rows = tag.getList("pools", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            String slot = row.getString("slot");
            if (slot.isEmpty()) {
                // A row without a slot cannot be addressed; skipping it keeps a partially written file readable.
                continue;
            }
            index.pools.put(slot, new Pool(row.getInt("poolXp"), milliOf(row), row.getLong("updatedAt")));
        }
        return index;
    }

    /**
     * Reads the rate of one saved row, converting a pre-datapack save on the way.
     *
     * <p>Old saves stored {@code mbPerXp}; a stored {@code 0} meant "no tank here", which is what makes the two
     * units distinguishable. Converting rather than discarding keeps a remembered distant tank quotable after the
     * upgrade instead of turning it into "unknown" until someone walks there again.
     */
    private static int milliOf(CompoundTag row) {
        if (row.contains("xpPerBucketMilli")) {
            return row.getInt("xpPerBucketMilli");
        }
        int legacyMbPerXp = row.getInt("mbPerXp");
        return legacyMbPerXp <= 0 ? 0 : (int) Math.max(1L, 1_000_000L / legacyMbPerXp);
    }

    /** Every remembered slot, for diagnostics. */
    public List<String> slots() {
        return List.copyOf(pools.keySet());
    }
}
