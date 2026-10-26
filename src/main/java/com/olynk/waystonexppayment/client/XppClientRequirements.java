package com.olynk.waystonexppayment.client;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.net.XppTankHintPayload;
import com.olynk.waystonexppayment.payment.TankPlacement;
import com.olynk.waystonexppayment.payment.XpRequirementScanner;

import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Turns the client's config-resolved requirement tree into one that also accepts the tank below the teleport
 * source as a payer.
 *
 * <p>Called from a client mixin around {@code WaystonesAPI.resolveRequirements}, which is the single place
 * every client preview builds its requirement tree from (selection list, inventory button, hotkey handler),
 * so the button state and the hover/icon rendering all agree.
 */
public final class XppClientRequirements {

    private XppClientRequirements() {
    }

    /**
     * @return the original requirement, or a wrapping one when a tank is there to pay for this trip - empty or not,
     *         because only the wrapped entries get the per-frame button refresh
     */
    public static WarpRequirement decorate(WaystoneTeleportContext context, WarpRequirement original) {
        if (context == null || original == null) {
            return original;
        }
        if (!(context.getEntity() instanceof Player player)) {
            return original;
        }
        Level level = player.level();
        if (level == null || !level.isClientSide()) {
            // Single-player runs both sides in one JVM: never touch the authoritative server-side resolution.
            return original;
        }

        Optional<Waystone> fromWaystone = context.getFromWaystone();
        UUID fromUid = fromWaystone.map(Waystone::getWaystoneUid).orElse(null);
        UUID targetUid = Optional.ofNullable(context.getTargetWaystone()).map(Waystone::getWaystoneUid).orElse(null);
        if (isSameWaystone(fromUid, targetUid)) {
            // The entry for the waystone you are standing on is not a trip: Waystones greys it itself, and a
            // payment preview here would both price a teleport to where you already are and hand the button back
            // to us, so a refilled tank would make it clickable again.
            return original;
        }
        // Mirror the server's choice of payer: a usable tank under the source wins, otherwise the destination's.
        // "Usable" means not empty, because an empty tank is no payer on the server either.
        TankRef sourceTank = tankBelow(level, fromWaystone.orElse(null));
        TankRef payer = payerAt(sourceTank);
        TankRef targetTank = null;
        if (payer == null) {
            targetTank = tankBelow(level, context.getTargetWaystone());
            payer = payerAt(targetTank);
        }
        // A tank that is here but holds nothing still decides this entry's fate. Wrapping it is never stricter than
        // leaving the vanilla requirement in place - with a pool of 0 the split asks the player for the whole cost,
        // which is exactly what the vanilla XP requirement asks - while not wrapping greys the entry for the rest of
        // the session: the per-frame refresh only runs on entries this addon wrapped, so a tank filled while the
        // list is open could never light the button back up.
        if (payer == null) {
            payer = readableTank(sourceTank, targetTank);
        }
        if (payer == null) {
            // Reaching this log pins the entry to Waystones' own verdict for the rest of the session: either no
            // tank is there at all, or the only one seen is a slot whose contents this client cannot read, so the
            // entry cannot follow a pool that changes while the list stays open.
            WaystoneXpPaymentMod.LOGGER.info("[client] no payment tank for this entry (from={}, target={}): "
                            + "under source={}, under target={}", describe(fromWaystone.orElse(null)),
                    describe(context.getTargetWaystone()), describeTank(sourceTank), describeTank(targetTank));
            return original;
        }

        int costXp = XpRequirementScanner.xpCost(original);
        if (costXp <= 0) {
            // Happens with XP *level* requirements, which this addon does not cover.
            WaystoneXpPaymentMod.LOGGER.info("[client] payment tank below the waystone, but this trip has no XP "
                    + "point cost; leaving the vanilla requirement in place");
            return original;
        }
        // Logged because "the button is grey" can mean either "our hook never ran" or "no tank is known here".
        WaystoneXpPaymentMod.LOGGER.info("[client] payment tank for this entry (from={}, target={}): {} "
                        + "(cost {} XP)", describe(fromWaystone.orElse(null)),
                describe(context.getTargetWaystone()),
                payer.loaded() == null ? "chunk not loaded, leaving the decision to the server"
                        : "pool " + payer.loaded().poolXpForClient() + " XP",
                costXp);
        return new ClientFluidPaymentRequirement(original, XpRequirementScanner.withoutXpCost(original), costXp,
                payer.loaded(), payer.dimension(), payer.pos());
    }

    /**
     * Whether the destination is the waystone the player is teleporting from.
     *
     * <p>A source the client never learned about (warp item, hotkey opening the list) is not a self-target, so the
     * trip keeps its preview.
     */
    static boolean isSameWaystone(@Nullable UUID fromUid, @Nullable UUID targetUid) {
        return fromUid != null && fromUid.equals(targetUid);
    }

    /**
     * A tank below a waystone: where its slot is, plus the loaded block entity when this client has that chunk.
     *
     * <p>The slot is kept even without a block entity because it is what a server hint is looked up by: the pool
     * of a far-away tank arrives as a position-keyed answer, not as an object this client can inspect.
     */
    private record TankRef(BlockPos pos, ResourceLocation dimension, @Nullable XpPaymentTankBlockEntity loaded) {
    }

    /**
     * The tank below a waystone: the loaded block entity when its chunk is here, otherwise a position this client
     * has reason to believe holds a tank, or {@code null} when there is nothing to go on.
     *
     * <p>Reasons to believe, in the order they are trusted: what this client saw itself, then what the server last
     * reported for that slot. A slot the server has never answered for is unknown, not empty - but it is also not
     * evidence enough to light the button, so it is queued for one question and the entry stays gated this round.
     *
     * <p>Two candidates, because a waystone in an unloaded chunk has an unknown shape: the tank hangs either one
     * or two blocks down ({@link TankPlacement}), and pressing the button is only worth offering when at least one
     * of them can pay. Both are asked about, so the entry un-greys one tick after the server answers, whichever
     * slot the tank is in.
     */
    private static TankRef tankBelow(Level level, Waystone waystone) {
        if (waystone == null || !waystone.getDimension().equals(level.dimension())) {
            // Cross-dimension destinations are not loaded on this client; their entry stays gated.
            return null;
        }
        ResourceLocation dimension = waystone.getDimension().location();
        List<BlockPos> candidates = TankPlacement.candidateTankPositions(level, waystone.getPos());
        for (BlockPos candidate : candidates) {
            if (level.isLoaded(candidate)
                    && level.getBlockEntity(candidate) instanceof XpPaymentTankBlockEntity tank) {
                XppClientTankRegistry.remember(level, candidate);
                return new TankRef(candidate, dimension, tank);
            }
            // Only the slots this client cannot see are worth a question, and request() drops slots that already
            // have an answer, so asking costs nothing once the server has spoken.
            if (!level.isLoaded(candidate)) {
                XppTankHints.request(dimension, candidate);
            }
        }
        BlockPos believed = selectPayerSlot(candidates,
                candidate -> XppClientTankRegistry.knows(waystone.getDimension(), candidate),
                candidate -> XppTankHints.hint(dimension, candidate));
        return believed == null ? null : new TankRef(believed, dimension, null);
    }

    /**
     * The candidate slot this client should treat as the payer, or {@code null} when nothing is known yet.
     *
     * <p>Split from {@link #tankBelow} so the choice can be unit tested without a client world:
     * {@link BlockPos} and {@link XppTankHintPayload.Hint} are plain data.
     *
     * <p>Two rounds on purpose. A slot this client has seen itself is ground truth, a server hint is a cached
     * answer, and when the waystone's shape is unknown the tank is as likely to be the second candidate as the
     * first - so a hint on the first must not shadow the tank this client remembers on the second.
     *
     * @param candidates slots to consider, in preference order
     * @param seenHere whether this client has already seen a tank at that slot
     * @param hintOf the last server answer for that slot, or {@code null} when there is none yet
     */
    @Nullable
    static BlockPos selectPayerSlot(List<BlockPos> candidates, Predicate<BlockPos> seenHere,
                                    Function<BlockPos, XppTankHintPayload.Hint> hintOf) {
        for (BlockPos candidate : candidates) {
            if (seenHere.test(candidate)) {
                return candidate;
            }
        }
        for (BlockPos candidate : candidates) {
            if (paysFrom(hintOf.apply(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Whether a server answer is evidence of a payer.
     *
     * <p>Stricter than {@link XppTankHintPayload.Hint#mayPay()} in exactly one point: a tank the server measured as
     * empty is not a payer here. That matters with two candidates - a known-empty tank on one of them must not
     * shadow a tank the other one might hold - and it is the same rule {@link #payerAt} applies to a remembered
     * slot, so "empty" cannot mean one thing on the way in and another on the way out.
     *
     * <p>{@code mayPay()} itself is the right base: it only rules out "you may not use it" and "nothing is there",
     * and leaves an unanswered pool permissive - an unvisited far-away tank must not grey the button out.
     */
    private static boolean paysFrom(@Nullable XppTankHintPayload.Hint hint) {
        if (hint == null || !hint.mayPay()) {
            return false;
        }
        boolean measuredEmpty = hint.hasTank() && hint.poolXp() <= 0;
        return !measuredEmpty;
    }

    /**
     * A tank can only pay when it holds something and admits this player.
     *
     * <p>For a tank whose contents are not here, the last server answer decides: an explicit empty pool is a real
     * "no payer" rather than an unknown, which is the one case that must not stay permissive.
     */
    @Nullable
    private static TankRef payerAt(@Nullable TankRef ref) {
        if (ref == null) {
            return null;
        }
        if (ref.loaded() != null) {
            return ref.loaded().getTank().isEmpty() ? null : ref;
        }
        XppTankHintPayload.Hint hint = XppTankHints.hint(ref.dimension(), ref.pos());
        if (hint == null) {
            // No answer yet: permissive on purpose. The client only decides whether the button lights up; the
            // server reads the tank for real and is the one that can refuse.
            return ref;
        }
        return paysFrom(hint) ? ref : null;
    }

    /**
     * The first end holding a tank whose contents this client can read right now, empty or not, or {@code null}
     * when neither does.
     *
     * <p>Only tanks that are loaded here count: a far-away slot is quoted from a hint that is refreshed when the
     * entry is built, so wrapping it could not follow the pool while the list stays open - it would only swap one
     * never-changing grey row for another.
     */
    @Nullable
    private static TankRef readableTank(@Nullable TankRef source, @Nullable TankRef target) {
        if (isReadable(source)) {
            return source;
        }
        return isReadable(target) ? target : null;
    }

    private static boolean isReadable(@Nullable TankRef ref) {
        return ref != null && ref.loaded() != null;
    }

    /** Waystone dimension and position for diagnostics, or {@code none}. */
    private static String describe(Waystone waystone) {
        return waystone == null
                ? "none"
                : waystone.getDimension().location() + "@" + waystone.getPos().toShortString();
    }

    /** What this client knows about one candidate tank, for the log: nothing, position only, or a real tank. */
    private static String describeTank(@Nullable TankRef ref) {
        if (ref == null) {
            return "no tank";
        }
        return ref.loaded() == null
                ? "tank not loaded here"
                : (ref.loaded().getTank().isEmpty() ? "empty tank" : "usable tank")
                        + " at " + ref.loaded().getBlockPos().toShortString();
    }
}
