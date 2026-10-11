package com.olynk.waystonexppayment.block.entity;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.auth.AccessList;
import com.olynk.waystonexppayment.auth.TeamResolver;
import com.olynk.waystonexppayment.auth.TeamResolvers;
import com.olynk.waystonexppayment.client.XppClientTankRegistry;
import com.olynk.waystonexppayment.config.XppSettings;
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
                    WaystoneXpPaymentMod.LOGGER.info("Tank at {} refuses fluid: {}",
                            worldPosition.toShortString(), FluidRates.describe(stack));
                }
                return accepted;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                int filled = super.fill(resource, action);
                if (filled == 0 && action.execute() && !resource.isEmpty() && !getFluid().isEmpty()
                        && !FluidStack.isSameFluidSameComponents(getFluid(), resource)) {
                    WaystoneXpPaymentMod.LOGGER.info("Tank at {} holds {} and refuses the different fluid {} - "
                                    + "drain it first (one fluid per tank)",
                            worldPosition.toShortString(), FluidRates.describe(getFluid()),
                            FluidRates.describe(resource));
                }
                return filled;
            }

            @Override
            protected void onContentsChanged() {
                // Cached here so the client preview (icon render + live button refresh) only ever reads a field.
                cachedPoolXp = FluidRates.xpValue(getFluid());
                setChanged();
            }
        };
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
     * Whether this player may change the tank's access list.
     *
     * <p>Owner, or a player who shares any team with the owner. An unowned tank is open to everyone, because
     * there is no owner to deny and the first successful edit claims it (see XppServerHandler). Nobody else,
     * including operators, passes here - the op command path has its own permission check.
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
     * Pool value as the server last computed it, for the client-side teleport preview.
     *
     * <p>The client has its own rate table but not the server's recipe manager, so a fluid declared only by a
     * datapack recipe would look worthless there; the sync tag carries the authoritative number.
     */
    /**
     * XP the tank can pay with, as this side sees it.
     *
     * <p>Preferred source is the live fluid contents through this side's own rate table, so draining the tank is
     * reflected immediately. The server's number is only a fallback for fluids declared by a datapack recipe,
     * whose rate the client's recipe view may not know.
     */
    public int poolXpForClient() {
        return cachedPoolXp > 0 ? cachedPoolXp : Math.max(0, syncedPoolXp);
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
        if (tag.contains("access")) {
            access = AccessList.load(tag.getCompound("access"));
        }
        // A tag without int-array payload decodes as null rather than throwing, which is what an old world
        // (no owner keys at all) and a hand-edited tag both look like - both mean "unclaimed".
        int[] owner = tag.getIntArray(OWNER_ID);
        ownerId = owner.length == 4 ? UUIDUtil.uuidFromIntArray(owner) : null;
        ownerName = tag.getString(OWNER_NAME);
        // Present only in the network sync tag (getUpdateTag), never in saved world data.
        syncedPoolXp = tag.contains(SYNC_POOL_XP) ? Math.max(0, tag.getInt(SYNC_POOL_XP)) : -1;
        cachedPoolXp = FluidRates.xpValue(tank.getFluid());
        hasSyncedData = true;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        broadcastToClients();
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
