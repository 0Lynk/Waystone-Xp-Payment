package com.olynk.waystonexppayment.client;

import java.util.List;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.config.XppSettings;
import com.olynk.waystonexppayment.fluid.XpRate;
import com.olynk.waystonexppayment.net.XppTankHintPayload;
import com.olynk.waystonexppayment.payment.PoolQuote;

import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side preview that answers "can this teleport be paid for", mirroring the server's tank-first,
 * player-second split so the button greys out exactly when the trip is unaffordable.
 *
 * <p>The payer is a *loaded* tank, a tank the server reported through a hint (its chunk is not loaded here), or
 * none:
 * <ul>
 *   <li>loaded with data - mirror the arithmetic against the tank's live contents, a tank that is empty
 *       included: a pool of 0 asks the player for the whole cost, which is the vanilla rule;</li>
 *   <li>pool known from a hint - same arithmetic, on a value the server answered possibly a while ago; the button
 *       asks for a fresher one every second while the list is open, which is early enough never to move items: the
 *       server does the real split;</li>
 *   <li>a question about that slot still in flight - stay closed until it lands, because the answer decides the
 *       button and an entry that lights up for a tick only to go dark is a promise this client cannot keep;</li>
 *   <li>no data and no question outstanding - stay permissive and let the server decide, because reading "no
 *       information" as "empty" is what once left a full tank's button grey forever.</li>
 * </ul>
 *
 * <p>Never consumes anything: the server performs the real payment through its own requirement.
 */
public final class ClientFluidPaymentRequirement implements WarpRequirement {

    private final WarpRequirement delegate;
    private final WarpRequirement residual;
    private final int costXp;
    @Nullable
    private final XpPaymentTankBlockEntity payer;
    @Nullable
    private final ResourceLocation hintDimension;
    @Nullable
    private final BlockPos hintPos;

    /**
     * @param payer the loaded tank that will pay, or {@code null} when the tank itself is not loaded here
     * @param hintDimension dimension of the tank's slot, used to look up a server hint when the tank is elsewhere
     * @param hintPos slot below the waystone, used the same way
     */
    public ClientFluidPaymentRequirement(WarpRequirement delegate, WarpRequirement residual, int costXp,
                                         @Nullable XpPaymentTankBlockEntity payer,
                                         @Nullable ResourceLocation hintDimension, @Nullable BlockPos hintPos) {
        this.delegate = delegate;
        this.residual = residual;
        this.costXp = costXp;
        this.payer = payer;
        this.hintDimension = hintDimension;
        this.hintPos = hintPos;
    }

    public WarpRequirement delegate() {
        return delegate;
    }

    @Override
    public boolean canAfford(Player player) {
        Integer poolXp = knownPoolXp();
        boolean unresolved = poolXp == null && awaitingReading();
        boolean tankShort = poolXp != null
                && !PoolQuote.of(costXp, poolXp, knownRate(), player.totalExperience).affordable();
        if ((unresolved || tankShort) && !skipsPayment(player)) {
            return false;
        }
        // Everything except the XP cost still has to hold (cooldowns, item costs, ...).
        return residual == null || residual.canAfford(player);
    }

    /**
     * Whether this client asked about the tank and is still waiting for the first reading.
     *
     * <p>Asked is not the same as known: the entry is built before the answer arrives, and treating that gap as
     * "can pay" is what made a drained tank's entry light up for a tick and then go dark.
     */
    private boolean awaitingReading() {
        return hintDimension != null && hintPos != null && XppTankHints.awaitingAnswer(hintDimension, hintPos);
    }

    /**
     * Whether the server will charge this player nothing, which makes the tank's contents irrelevant here.
     *
     * <p>Waystones itself never greys a creative player's entries and the server skips the payment under
     * {@code skipPaymentInCreative}, so blacking the entry out would refuse a teleport that is actually free.
     * Following the config rather than {@code instabuild} alone keeps the two sides in step: a server that turned
     * the skip off still charges, and the worst a stale client preview can do is let one click through to be refused.
     */
    private boolean skipsPayment(Player player) {
        return XppSettings.current().skipPaymentInCreative() && player.getAbilities().instabuild;
    }

    /**
     * Why this entry is grey, in numbers. Called only when the button flips, so a report of "still grey" can tell
     * "the tank is short" from "the player is short".
     */
    public String describeAffordability(Player player) {
        Integer poolXp = knownPoolXp();
        int held = player.totalExperience;
        if (poolXp == null) {
            String state = awaitingReading() ? "waiting for the first reading of that tank"
                    : "no pool reading here (server decides)";
            return state + ", player " + held + " XP, cost " + costXp + " XP";
        }
        PoolQuote quote = PoolQuote.of(costXp, poolXp, knownRate(), held);
        String shortfall = quote.affordable() ? ""
                : ", short " + quote.missingXp() + " XP (" + quote.missingMb() + " mB)";
        return "tank " + quote.xpFromTank() + " of " + poolXp + " XP, player " + quote.xpFromPlayer() + " of "
                + held + " XP, cost " + costXp + " XP" + shortfall;
    }

    /**
     * @return the pool this client can see, or {@code null} when it has no reading and the server must decide
     */
    @Nullable
    private Integer knownPoolXp() {
        if (payer != null && payer.hasSyncedData()) {
            return payer.poolXpForClient();
        }
        XppTankHintPayload.Hint hint = hint();
        return hint != null && hint.hasTank() ? hint.poolXp() : null;
    }

    /**
     * The rate only decides how many mB a payment moves, never whether it is affordable, so an unknown rate is
     * harmless here - but a hint carries the server's real one and using it keeps the preview exact.
     */
    private XpRate knownRate() {
        XppTankHintPayload.Hint hint = hint();
        return hint != null && hint.hasTank() && hint.xpPerBucketMilli() > 0
                ? new XpRate(hint.xpPerBucketMilli())
                : XpRate.ofBucket(1000);
    }

    @Nullable
    private XppTankHintPayload.Hint hint() {
        if (hintDimension == null || hintPos == null) {
            return null;
        }
        // The reading is what greys this button, so an old one must keep doing that until a newer one arrives: a tank
        // that paid for the trip here is empty in the server's index the moment it is drained, and dropping the old
        // answer would read as "no information", which this class treats as "can pay".
        XppTankHints.refreshIfStale(hintDimension, hintPos);
        return XppTankHints.hint(hintDimension, hintPos);
    }

    @Override
    public void consume(Player player) {
        // Client-side preview only; the server consumes through its own requirement.
    }

    @Override
    public void rollback(Player player) {
        // See consume().
    }

    @Override
    public void appendHoverText(Player player, List<Component> tooltip) {
        if (delegate != null) {
            delegate.appendHoverText(player, tooltip);
        }
        if (costXp > 0) {
            tooltip.add(Component.translatable("gui.waystone_xp_payment.fluid_cost", costXp)
                    .withStyle(ChatFormatting.AQUA));
            Integer poolXp = knownPoolXp();
            if (poolXp != null) {
                PoolQuote quote = PoolQuote.of(costXp, poolXp, knownRate(), player.totalExperience);
                // A tank of a cheap fluid holds plenty of mB and little XP; saying only "costs 27 XP" makes a grey
                // entry look like a bug. The numbers are what an operator can act on.
                if (!quote.affordable()) {
                    Integer capacity = payer == null ? null : payer.capacityMb();
                    boolean fillable = capacity != null && quote.topUpWouldCloseTheGap(capacity);
                    tooltip.add(fillable
                            ? Component.translatable("gui.waystone_xp_payment.tank_short", quote.xpFromTank(),
                                    costXp, quote.missingMb()).withStyle(ChatFormatting.RED)
                            : Component.translatable("gui.waystone_xp_payment.tank_short_of_reach",
                                    quote.xpFromTank(), costXp, quote.missingXp()).withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    @Override
    public boolean isEmpty() {
        // Never empty while it is offering to pay: the whole point is that the preview stays enabled.
        return false;
    }
}
