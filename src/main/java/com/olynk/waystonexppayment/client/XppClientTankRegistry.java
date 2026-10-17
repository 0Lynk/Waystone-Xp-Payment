package com.olynk.waystonexppayment.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Remembers every payment tank this client has ever loaded.
 *
 * <p>Waystones asks the client whether a teleport is affordable while building the list, and for a destination
 * whose chunk is not loaded the client has no block entity to look at - which used to leave the "travel back to
 * the tank" entry disabled forever, even though the server would have paid from that tank. The position is a
 * stable structural fact (a tank block below a waystone), so remembering it is enough; the server still decides
 * whether the tank can actually pay.
 *
 * <p>Only positives are remembered. A tank that was removed keeps its stale entry until the client restarts or
 * revisits the chunk; the worst case is a clickable button whose teleport the server then rejects, never the
 * other way around.
 */
public final class XppClientTankRegistry {

    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private XppClientTankRegistry() {
    }

    public static void remember(Level level, BlockPos tankPos) {
        if (level == null || !level.isClientSide()) {
            return;
        }
        SEEN.add(key(level.dimension(), tankPos));
    }

    public static boolean knows(ResourceKey<Level> dimension, BlockPos tankPos) {
        return SEEN.contains(key(dimension, tankPos));
    }

    /**
     * Forgets every position, called when the client leaves a world.
     *
     * <p>Without this the entries outlive the world they were seen in: joining another save that happens to have a
     * waystone at the same coordinates would inherit this one's "there is a tank here" claim.
     */
    public static void clear() {
        SEEN.clear();
    }

    private static String key(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location() + "@" + pos.asLong();
    }
}
