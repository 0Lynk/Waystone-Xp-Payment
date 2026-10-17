package com.olynk.waystonexppayment.block.entity;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.auth.AccessList;
import com.olynk.waystonexppayment.auth.TeamResolver;
import com.olynk.waystonexppayment.auth.TeamResolvers;
import com.olynk.waystonexppayment.client.XppClientTankRegistry;
import com.olynk.waystonexppayment.config.XppSettings;
import com.olynk.waystonexppayment.data.TankPoolIndex;
import com.olynk.waystonexppayment.fluid.FluidRates;
import com.olynk.waystonexppayment.registry.XppRegistry;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The payment tank: one shared fluid pool plus the access list that decides who may draw from it.
 *
 * <p>Capacity is read once at construction from {@link XppSettings}; a config change applies to newly placed
 * tanks (documented limitation: existing tanks keep the capacity they were created with).
 */
public class XpPaymentTankBlockEntity extends BlockEntity {

    /** Network-only key holding the server-computed XP value of the pool (never written to world data). */
    private static final String SYNC_POOL_XP = "XppPoolXp";

    /** Owner of the tank: the player who placed it. Null until the tank has been claimed. */
    private static final String OWNER_ID = "XppOwner";
    /** Cached owner name, display only - {@link #ownerId} is what authorisation matches on. */
    private static final String OWNER_NAME = "XppOwnerName";

    private final FluidTank tank;
    private AccessList access = new AccessList();
    @Nullable
    private UUID ownerId;
    private String ownerName = "";
    private int syncedPoolXp = -1;
    private int cachedPoolXp;
    private boolean hasSyncedData;
    /** Last refusal written to the log, plus how many identical ones were dropped since (see logRefusalOnce). */
    private String lastRefusal = "";
    private int suppressedRefusals;

    public XpPaymentTankBlockEntity(BlockPos pos, BlockState state) {
        super(XppRegistry.XP_PAYMENT_TANK_BE.get(), pos, state);
        this.tank = new FluidTank(XppSettings.current().capacityMb()) {
            @Override
            public boolean isFluidValid(FluidStack stack) {
                // Only declared experience fluids may enter, and only one kind at a time (FluidTank already
                // enforces "same fluid" once non-empty).
                boolean accepted = FluidRates.accepts(stack);
                if (!accepted) {
                    // Logged because "the tank refused my fluid" and "the tank accepted it but cannot pay with
                    // it" look identical from the outside, and only the log can tell them apart afterwards.
                    logRefusalOnce("refuses fluid: " + FluidRates.describe(stack));
                }
                return accepted;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                int filled = super.fill(resource, action);
                if (filled == 0 && action.execute() && !resource.isEmpty() && !getFluid().isEmpty()
                        && !FluidStack.isSameFluidSameComponents(getFluid(), resource)) {
                    logRefusalOnce("holds " + FluidRates.describe(getFluid()) + " and refuses the different fluid "
                            + FluidRates.describe(resource) + " - drain it first (one fluid per tank)");
                }
                return filled;
            }

            @Override
            protected void onContentsChanged() {
                // Cached here so the client preview (icon render + live button refresh) only ever reads a field.
                cachedPoolXp = FluidRates.xpValue(getFluid());
                setChanged();
                recordPoolInIndex();
            }
        };
    }

    /**
     * Publishes this slot's current pool to {@link TankPoolIndex}.
     *
     * <p>That index is what lets a destination whose chunk is not resident be quoted and paid; a tank that
     * changes without a write here would keep being quoted at its previous value until its chunk is loaded
     * again. Cheap on purpose - one map write per contents change, not per tick.
     */
    private void recordPoolInIndex() {
        if (level instanceof ServerLevel serverLevel) {
            TankPoolIndex.get(serverLevel.getServer()).record(serverLevel.dimension().location(), worldPosition,
                    cachedPoolXp, mbPerXp(), System.currentTimeMillis());
        }
    }

    /**
     * Logs a refusal once per distinct message.
     *
     * <p>A pipe that keeps pushing the wrong fluid retries every tick, and on a live instance that turned into 419
     * byte-identical lines in three minutes (2026-10-16), burying everything else the log had to say. Repeats are
     * counted and reported together with the next different refusal instead of being written one by one.
     */
    private void logRefusalOnce(String message) {
        if (message.equals(lastRefusal)) {
            suppressedRefusals++;
            return;
        }
        if (suppressedRefusals > 0) {
            WaystoneXpPaymentMod.LOGGER.info("Tank at {}: {} more identical refusal(s) not shown",
                    worldPosition.toShortString(), suppressedRefusals);
            suppressedRefusals = 0;
        }
        lastRefusal = message;
        WaystoneXpPaymentMod.LOGGER.info("Tank at {} {}", worldPosition.toShortString(), message);
    }

    public IFluidHandler getFluidHandler(@Nullable net.minecraft.core.Direction side) {
        return tank;
    }

    public FluidTank getTank() {
        return tank;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        // Every tank the client ever loads is remembered by position: Waystones asks the client about
        // affordability even for waystones whose chunk is not currently loaded (see XppClientTankRegistry).
        XppClientTankRegistry.remember(level, worldPosition);
    }

    public AccessList access() {
        return access;
    }

    /** Placing player, or null while the tank has never been claimed. */
    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    /**
     * Display name of the owner, or an empty string while the tank is unclaimed.
     *
     * <p>Never used for authorisation: a name can be changed, a UUID cannot.
     */
    public String ownerName() {
        return ownerName;
    }

    /** Records the owner. First claim wins; later calls overwrite on purpose (an admin taking over a tank). */
    public void setOwner(@Nullable UUID ownerId, String ownerName) {
        this.ownerId = ownerId;
        this.ownerName = ownerName == null ? "" : ownerName;
        setChanged();
    }

    /** True while no player owns this tank yet - the legacy and freshly-filled-with-/setblock case. */
    public boolean isUnowned() {
        return ownerId == null;
    }

    /**
     * Whether this player owns the tank or shares a team with its owner.
     *
     * <p>Deliberately not the whole edit rule: {@code XppServerHandler} asks this plus an operator escape hatch,
     * because an operator has to be able to rescue a tank whose owner is gone. Keeping the exemption there and
     * not here means there is one place that answers "who may edit", and this method answers the narrower
     * question its callers actually ask. An unowned tank counts as editable by anyone, because there is no owner
     * to deny and the first successful edit claims it.
     */
    public boolean canEdit(Player player) {
        UUID owner = ownerId;
        if (owner == null) {
            return true;
        }
        UUID editor = player.getUUID();
        if (editor.equals(owner)) {
            return true;
        }
        TeamResolver resolver = TeamResolvers.get();
        List<TeamResolver.TeamInfo> ownerTeams = resolver.teamsOf(owner);
        if (ownerTeams.isEmpty()) {
            return false;
        }
        for (TeamResolver.TeamInfo team : resolver.teamsOf(editor)) {
            for (TeamResolver.TeamInfo ownerTeam : ownerTeams) {
                if (team.id().equals(ownerTeam.id())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** XP value of the whole tank, at the rates currently configured for its fluid. */
    public int poolXp() {
        return FluidRates.xpValue(tank.getFluid());
    }

    /**
     * XP the tank can pay with, as this side sees it.
     *
     * <p>The server's number wins whenever it has arrived: its rate table is the only one that knows fluids
     * declared by a datapack recipe, and it computed that number from the same contents this tag carries, so it
     * is not older than the local view. The local computation is what a single-player client has before the
     * first sync tag lands.
     */
    public int poolXpForClient() {
        return pickPool(syncedPoolXp, cachedPoolXp);
    }

    /**
     * Chooses between the server's number and this side's own computation.
     *
     * <p>{@code syncedPoolXp < 0} means "no sync tag has arrived"; it is not a pool of zero, which is why the
     * choice cannot be {@code cached > 0 ? cached : synced} - that direction shows a stale server number for a
     * tank this side has just watched run dry.
     */
    static int pickPool(int syncedPoolXp, int cachedPoolXp) {
        return syncedPoolXp >= 0 ? syncedPoolXp : Math.max(0, cachedPoolXp);
    }

    /**
     * Whether any tank data has reached this side.
     *
     * <p>False means "no information yet", which the preview must not read as "empty": treating missing data as
     * zero is exactly what once left a full tank's button grey forever.
     */
    public boolean hasSyncedData() {
        return hasSyncedData;
    }

    /**
     * Whether a load tag carries the server's pool number.
     *
     * <p>Kept as a named predicate because "the key is absent" is a state, not a zero: the whole reason the
     * preview has three answers instead of two. Testing it through {@link #loadAdditional} would need a live
     * level, and this one condition is what everything else keys off.
     */
    static boolean carriesSyncedPool(CompoundTag tag) {
        return tag.contains(SYNC_POOL_XP);
    }

    /** mB per XP for the fluid currently in the tank (falls back to the configured default). */
    public int mbPerXp() {
        return FluidRates.mbPerXp(tank.getFluid());
    }

    public FluidStack drainInternal(int amountMb) {
        return tank.drain(amountMb, IFluidHandler.FluidAction.EXECUTE);
    }

    public int fillInternal(FluidStack stack) {
        return tank.fill(stack, IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // FluidTank's helpers put the fluid under the conventional "Fluid" key of the tag they are handed, so we
        // hand them our own tag instead of nesting another wrapper. That keeps the block entity readable and
        // writable with vanilla /data commands (verified on a live server).
        tank.writeToNBT(registries, tag);
        tag.put("access", access.save());
        if (ownerId != null) {
            tag.putIntArray(OWNER_ID, UUIDUtil.uuidToIntArray(ownerId));
        }
        if (!ownerName.isEmpty()) {
            tag.putString(OWNER_NAME, ownerName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag);
        clampLoadedPoolToCapacity();
        if (tag.contains("access")) {
            access = AccessList.load(tag.getCompound("access"));
        }
        // A tag without int-array payload decodes as null rather than throwing, which is what an old world
        // (no owner keys at all) and a hand-edited tag both look like - both mean "unclaimed".
        int[] owner = tag.getIntArray(OWNER_ID);
        ownerId = owner.length == 4 ? UUIDUtil.uuidFromIntArray(owner) : null;
        ownerName = tag.getString(OWNER_NAME);
        // Present only in the network sync tag (getUpdateTag), never in saved world data. Its absence is the
        // signal the preview reads as "no information yet", so this has to follow the tag, not the load path.
        boolean carriesPool = carriesSyncedPool(tag);
        syncedPoolXp = carriesPool ? Math.max(0, tag.getInt(SYNC_POOL_XP)) : -1;
        cachedPoolXp = FluidRates.xpValue(tank.getFluid());
        hasSyncedData = carriesPool;
        // A loaded chunk is the only chance to refresh the index for a slot nobody touched since the world was
        // saved; without this the index would only learn about tanks that keep changing.
        recordPoolInIndex();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        broadcastToClients();
    }

    /**
     * Trims a loaded pool back to the tank's capacity.
     *
     * <p>{@code FluidTank.readFromNBT} assigns the stored stack without consulting the capacity, so a tank saved
     * before an admin lowered {@code tank.capacityMb}, or a tag written with {@code /data merge}, comes back
     * holding more than the tank can hold - and the screen then reports a pool no code path can ever refill
     * (measured on 2026-10-16: 999,999 mB inside a 4,000 mB tank, surviving a restart).
     */
    private void clampLoadedPoolToCapacity() {
        FluidStack fluid = tank.getFluid();
        int capacity = tank.getCapacity();
        if (fluid.getAmount() > capacity) {
            WaystoneXpPaymentMod.LOGGER.warn("Tank at {} loaded {} mB but its capacity is {} mB - trimming the pool",
                    worldPosition.toShortString(), fluid.getAmount(), capacity);
            FluidStack trimmed = fluid.copy();
            trimmed.setAmount(capacity);
            tank.setFluid(trimmed);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.putInt(SYNC_POOL_XP, poolXp());
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this, BlockEntity::getUpdateTag);
    }

    /** The client-side preview reads the pool value from the block entity, so changes have to reach clients. */
    private void broadcastToClients() {
        if (level instanceof ServerLevel serverLevel && !serverLevel.isClientSide()) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }
}
