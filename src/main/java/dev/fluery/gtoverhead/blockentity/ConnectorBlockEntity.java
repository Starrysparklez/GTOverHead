package dev.fluery.gtoverhead.blockentity;

import dev.fluery.gtoverhead.GTOverhead;
import dev.fluery.gtoverhead.GTOverheadConfig;
import dev.fluery.gtoverhead.block.ConnectorBlock;
import dev.fluery.gtoverhead.block.RelayConnectorBlock;
import dev.fluery.gtoverhead.block.WireAnchors;
import dev.fluery.gtoverhead.client.ClientWireCache;
import dev.fluery.gtoverhead.network.WireNetworkSavedData;
import dev.fluery.gtoverhead.registry.GTOverheadRegistries;
import dev.fluery.gtoverhead.tier.WireTier;
import dev.fluery.gtoverhead.util.ElectrocutionHandler;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ConnectorBlockEntity extends BlockEntity {

    private static final String TAG_TARGET = "TargetPos";
    private static final String TAG_DISTANCE = "Distance";
    private static final String TAG_ENERGY = "EnergyStored";

    private final ConnectorEnergyContainer energyContainer = new ConnectorEnergyContainer(this);
    private LazyOptional<IEnergyContainer> energyCapability = LazyOptional.empty();
    /** Immersive Engineering style guard: the vanilla server calls setRemoved on chunk unload too. */
    private boolean isUnloaded = false;

    @Nullable
    private BlockPos targetPos;
    private double distance;

    public ConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(GTOverheadRegistries.CONNECTOR_BE_TYPE.get(), pos, state);
    }

    public WireTier getTier() {
        if (getBlockState().getBlock() instanceof ConnectorBlock connector) {
            return connector.getTier();
        }
        return WireTier.LOW;
    }

    public ConnectorEnergyContainer getEnergyContainer() {
        return energyContainer;
    }

    @Nullable
    public BlockPos getTargetPos() {
        return targetPos;
    }

    public double getDistance() {
        return distance;
    }

    public boolean hasWire() {
        return targetPos != null;
    }

    // region linking

    public void setLink(BlockPos target, double distance) {
        this.targetPos = target.immutable();
        this.distance = distance;
        if (level instanceof ServerLevel serverLevel) {
            WireNetworkSavedData.get(serverLevel).addEdge(worldPosition, target, getTier(), distance);
        }
        sync();
    }

    public void clearLink(boolean clearRemote) {
        BlockPos remote = targetPos;
        this.targetPos = null;
        this.distance = 0;
        this.energyContainer.clearWireOriginEnergy();
        if (level instanceof ServerLevel serverLevel) {
            // graph first: it is authoritative, and self-heal relies on it
            WireNetworkSavedData.get(serverLevel).removeEdge(worldPosition, remote);
        }
        if (clearRemote && remote != null && level != null && !level.isClientSide && level.isLoaded(remote)) {
            try {
                if (level.getBlockEntity(remote) instanceof ConnectorBlockEntity other) {
                    other.clearLink(false);
                } else if (level.getBlockEntity(remote) instanceof RelayConnectorBlockEntity other) {
                    other.removeLink(getBlockPos(), null);
                }
            } catch (Exception e) {
                GTOverhead.LOGGER.error("Failed to notify remote wire endpoint {} about removal at {}",
                        remote, worldPosition, e);
            }
        }
        // synced last, so no failure on the remote side can skip it
        sync();
    }

    private void dropSpools(BlockPos dropAt) {
        int spools = wireSpoolCount();
        if (spools > 0) {
            Containers.dropItemStack(level, dropAt.getX() + 0.5, dropAt.getY() + 0.5, dropAt.getZ() + 0.5,
                    new ItemStack(getTier().spool().get(), spools));
        }
    }

    /**
     * Cut / burn the wire: drops the consumed spool items and clears both ends.
     */
    public void dropWire(BlockPos dropAt) {
        if (level == null || targetPos == null) {
            return;
        }
        dropSpools(dropAt);
        clearLink(true);
    }

    public int wireSpoolCount() {
        if (distance <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.floor(distance / GTOverheadConfig.wireSpoolSegmentLength()));
    }

    private void sync() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockState state = getBlockState();
            serverLevel.getChunkSource().blockChanged(worldPosition);
            // sendBlockUpdated alone is not guaranteed to resync BE data: push the
            // data packet explicitly to every player tracking this chunk
            ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
            for (ServerPlayer player : serverLevel.getChunkSource().chunkMap
                    .getPlayers(new net.minecraft.world.level.ChunkPos(worldPosition), false)) {
                player.connection.send(packet);
            }
        }
    }

    /**
     * Drops stale links: if the block at {@link #targetPos} is gone (exploded,
     * broken in an unloaded chunk, replaced), the wire would hang in the air forever.
     */
    public void validateTarget() {
        if (targetPos == null || level == null || level.isClientSide) {
            return;
        }
        if (!level.isLoaded(targetPos)) {
            return;
        }
        boolean valid = level.getBlockState(targetPos).getBlock() instanceof ConnectorBlock ||
                level.getBlockState(targetPos).getBlock() instanceof RelayConnectorBlock;
        if (!valid) {
            // the wire was already dropped when the endpoint block was broken
            clearLink(false);
        }
    }

    // endregion

    // region ticking

    @SuppressWarnings("unused")
    public static void serverTick(Level level, BlockPos pos, BlockState state, ConnectorBlockEntity connector) {
        if (level.isClientSide) {
            return;
        }
        if ((level.getGameTime() % 20L) == 0L) {
            connector.validateTarget();
            WireNetworkSavedData.get((ServerLevel) level).integrityCheck((ServerLevel) level, pos);
        }
        connector.pushToLocalNetwork();
        connector.pushAcrossNetwork();
    }

    private void pushToLocalNetwork() {
        WireTier tier = getTier();
        long voltage = tier.voltage();
        int maxAmps = tier.maxAmperage();
        if (voltage <= 0 || maxAmps <= 0) {
            return;
        }
        long ampsLeft = Math.min(energyContainer.getEnergyStored() / voltage, maxAmps);
        if (ampsLeft <= 0) {
            return;
        }
        long storedBefore = energyContainer.getEnergyStored();
        for (Direction side : GTUtil.DIRECTIONS) {
            if (ampsLeft <= 0) {
                break;
            }
            Direction opposite = side.getOpposite();
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(side));
            // adjacent connectors exchange energy through their wires, not locally
            if (neighbor instanceof ConnectorBlockEntity || neighbor instanceof RelayConnectorBlockEntity ||
                    neighbor == null) {
                continue;
            }
            IEnergyContainer container = GTCapabilityHelper.getEnergyContainer(level, worldPosition.relative(side),
                    opposite);
            if (container == null || !container.inputsEnergy(opposite)) {
                continue;
            }
            long used = container.acceptEnergyFromNetwork(opposite, voltage, ampsLeft);
            if (used > 0) {
                energyContainer.changeEnergy(-used * voltage);
                ampsLeft -= used;
            }
        }
        energyContainer.onEnergyDrained(storedBefore - energyContainer.getEnergyStored());
    }

    /**
     * Pushes buffered, locally-sourced energy through the whole wire graph.
     * Routing is data-driven (SavedData), so structural poles in unloaded
     * chunks are crossed as well; only the receiving connector must be loaded.
     */
    private void pushAcrossNetwork() {
        if (targetPos == null || level == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // graph is authoritative: a stale BE-side link (cut remotely, edge gone)
        // is cleaned up instead of silently carrying energy into nowhere
        if (WireNetworkSavedData.get(serverLevel).findEdge(worldPosition, targetPos) == null) {
            clearLink(false);
            return;
        }
        WireTier tier = getTier();
        long voltage = tier.voltage();
        int maxAmps = tier.maxAmperage();
        if (voltage <= 0 || maxAmps <= 0) {
            return;
        }
        long budget = Math.min(sendableEnergy() / voltage, maxAmps);
        if (budget <= 0) {
            return;
        }

        List<WireNetworkSavedData.Delivery> deliveries = WireNetworkSavedData.get(serverLevel).route(worldPosition,
                voltage, maxAmps);
        for (WireNetworkSavedData.Delivery delivery : deliveries) {
            if (budget <= 0) {
                break;
            }
            if (!level.isLoaded(delivery.target())) {
                continue;
            }
            if (!(level.getBlockEntity(delivery.target()) instanceof ConnectorBlockEntity target)) {
                // leaf structural poles or stale entries are not connectors
                continue;
            }

            // target overvoltage: the wire burns and the target explodes
            if (delivery.voltage() > target.getEnergyContainer().getInputVoltage()) {
                BlockPos burntTarget = delivery.target();
                WireNetworkSavedData.get(serverLevel).removeEdge(worldPosition, burntTarget);
                this.targetPos = null;
                this.distance = 0;
                this.energyContainer.clearWireOriginEnergy();
                dropSpools(worldPosition);
                sync();
                explodeAt(burntTarget, voltage);
                return;
            }

            long amps = Math.min(budget, delivery.maxAmperage());
            if (amps <= 0) {
                continue;
            }
            long accepted = target.receiveFromWire(delivery.voltage(), amps, worldPosition);
            if (accepted > 0) {
                // source pays its own full voltage per ampere, the target receives
                // the voltage remaining after all path losses
                energyContainer.changeEnergy(-voltage * accepted);
                energyContainer.onEnergyDrained(voltage * accepted);
                budget -= accepted;
                // live wire electrocution along the source's own wire
                ElectrocutionHandler.zapAlong(serverLevel, anchor(), targetAnchor(delivery.target()),
                        tier, energyContainer.getEnergyStored() >= voltage);
            }
        }
    }

    private Vec3 anchor() {
        Vec3 anchor = WireAnchors.anchor(level, worldPosition);
        return anchor != null ? anchor : Vec3.atCenterOf(worldPosition);
    }

    private Vec3 targetAnchor(BlockPos pos) {
        Vec3 anchor = WireAnchors.anchor(level, pos);
        return anchor != null ? anchor : Vec3.atCenterOf(pos);
    }

    /**
     * Called by the sending side through the wire graph. Returns the accepted amperes.
     */
    public long receiveFromWire(long netVoltage, long amperes, BlockPos from) {
        long accepted = energyContainer.acceptWireEnergy(netVoltage, amperes);
        if (accepted > 0) {
            energyContainer.addWireOriginEnergy(accepted * netVoltage);
        }
        return accepted;
    }

    public long sendableEnergy() {
        return Math.max(0, energyContainer.getEnergyStored() - energyContainer.getWireOriginEnergy());
    }

    public boolean hasWireTo(BlockPos pos) {
        return targetPos != null && targetPos.equals(pos);
    }

    /**
     * {@code hasWire()} backed by the authoritative wire graph: a BE whose edge
     * is gone from SavedData is a stale leftover (e.g. the wire was cut on the
     * other side) and must not block relinking.
     */
    public boolean hasActiveWire() {
        if (!hasWire()) {
            return false;
        }
        if (level instanceof ServerLevel serverLevel) {
            return WireNetworkSavedData.get(serverLevel).findEdge(worldPosition, targetPos) != null;
        }
        return true;
    }

    private void spawnSmokeParticles() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                    worldPosition.getZ() + 0.5, 5, 0.1, 0.1, 0.1, 0.01);
        }
    }

    public void explode(long voltage) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (targetPos != null) {
            // the wire is destroyed together with the exploding connector
            dropWire(worldPosition);
        }
        explodeAt(worldPosition, voltage);
    }

    private void explodeAt(BlockPos pos, long voltage) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.destroyBlock(pos, false);
        serverLevel.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                GTUtil.getExplosionPower(voltage), Level.ExplosionInteraction.BLOCK);
    }

    // endregion

    // region capabilities

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction facing) {
        if (capability == GTCapability.CAPABILITY_ENERGY_CONTAINER) {
            if (!energyCapability.isPresent()) {
                energyCapability = LazyOptional.of(() -> energyContainer);
            }
            return energyCapability.cast();
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCapability.invalidate();
        energyCapability = LazyOptional.empty();
    }

    // endregion

    // region data

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            ClientWireCache.forget(level, worldPosition);
        } else if (!isUnloaded) {
            // real block removal (player, explosion, setblock, world edit...):
            // the whole wire drops as spools at this position. Chunk unload also
            // runs this method - guarded by the isUnloaded flag, like IE does.
            if (targetPos != null) {
                dropWire(worldPosition);
            }
        }
        invalidateCaps();
    }

    /**
     * Belt-and-braces hook fired from ConnectorBlock#onRemove (only on real
     * removal); dropWire is idempotent, so double invocation is harmless.
     */
    public void onBlockRemoved(BlockPos dropAt) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (targetPos != null) {
            dropWire(dropAt);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.isUnloaded = true;
        if (level != null && level.isClientSide) {
            ClientWireCache.forget(level, worldPosition);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.isUnloaded = false;
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            updateClientCache();
        } else {
            // heal links whose remote end was destroyed while this chunk was unloaded
            validateTarget();
            if (level instanceof ServerLevel serverLevel) {
                WireNetworkSavedData.get(serverLevel).integrityCheck(serverLevel, worldPosition);
                if (targetPos != null) {
                    WireNetworkSavedData.get(serverLevel).addEdge(worldPosition, targetPos, getTier(), distance);
                }
            }
        }
    }

    private void updateClientCache() {
        if (targetPos != null) {
            ClientWireCache.addLink(level, worldPosition, targetPos, getTier());
        } else {
            ClientWireCache.forget(level, worldPosition);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.energyContainer.setEnergyStored(tag.getLong(TAG_ENERGY));
        this.energyContainer.clearWireOriginEnergy();
        if (tag.contains(TAG_TARGET)) {
            BlockPos loaded = NbtUtils.readBlockPos(tag.getCompound(TAG_TARGET));
            this.targetPos = loaded != null && !loaded.equals(BlockPos.ZERO) ? loaded : null;
        } else {
            this.targetPos = null;
        }
        this.distance = tag.contains(TAG_DISTANCE) ? tag.getDouble(TAG_DISTANCE) : 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong(TAG_ENERGY, energyContainer.getEnergyStored());
        if (targetPos != null) {
            tag.put(TAG_TARGET, NbtUtils.writeBlockPos(targetPos));
            tag.putDouble(TAG_DISTANCE, distance);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (targetPos != null) {
            tag.put(TAG_TARGET, NbtUtils.writeBlockPos(targetPos));
            tag.putDouble(TAG_DISTANCE, distance);
        }
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            load(tag);
            updateClientCache();
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        updateClientCache();
    }

    // endregion

    // region rendering support

    @Override
    public AABB getRenderBoundingBox() {
        if (targetPos != null) {
            return new AABB(
                    Math.min(worldPosition.getX(), targetPos.getX()) - 1,
                    Math.min(worldPosition.getY(), targetPos.getY()) - 4,
                    Math.min(worldPosition.getZ(), targetPos.getZ()) - 1,
                    Math.max(worldPosition.getX(), targetPos.getX()) + 2,
                    Math.max(worldPosition.getY(), targetPos.getY()) + 4,
                    Math.max(worldPosition.getZ(), targetPos.getZ()) + 2);
        }
        return super.getRenderBoundingBox();
    }

    // endregion
}
