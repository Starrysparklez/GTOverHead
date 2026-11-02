package dev.fluery.gtoverhead.blockentity;

import dev.fluery.gtoverhead.GTOverheadConfig;
import dev.fluery.gtoverhead.block.ConnectorBlock;
import dev.fluery.gtoverhead.block.WireAnchors;
import dev.fluery.gtoverhead.client.ClientWireCache;
import dev.fluery.gtoverhead.network.WireNetworkSavedData;
import dev.fluery.gtoverhead.registry.GTOverheadRegistries;
import dev.fluery.gtoverhead.tier.WireTier;
import dev.fluery.gtoverhead.util.ElectrocutionHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decorative structural pole. Pure junction of the wire graph: it holds several
 * wires, never releases energy to adjacent machines and exposes no energy
 * capability. Energy routing across it is handled by the persistent wire graph
 * (SavedData), so it works even while this chunk is unloaded.
 */
public class RelayConnectorBlockEntity extends BlockEntity {

    public record Link(BlockPos target, double distance, WireTier tier) {

        public int spoolCount() {
            if (distance <= 0) {
                return 0;
            }
            return (int) Math.max(1, Math.floor(distance / GTOverheadConfig.wireSpoolSegmentLength()));
        }
    }

    private static final String TAG_LINKS = "Links";

    private final Map<BlockPos, Link> links = new HashMap<>();
    /** Immersive Engineering style guard: the vanilla server calls setRemoved on chunk unload too. */
    private boolean isUnloaded = false;

    public RelayConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(GTOverheadRegistries.RELAY_BE_TYPE.get(), pos, state);
    }

    public boolean hasWires() {
        return !links.isEmpty();
    }

    public List<Link> getLinks() {
        return new ArrayList<>(links.values());
    }

    public void addLink(BlockPos target, double distance, WireTier tier) {
        links.put(target.immutable(), new Link(target.immutable(), distance, tier));
        if (level instanceof ServerLevel serverLevel) {
            WireNetworkSavedData.get(serverLevel).addEdge(worldPosition, target, tier, distance);
        }
        sync();
    }

    /**
     * Removes the wire to {@code target}. {@code dropAt} non-null drops the wire
     * items there; null just forgets the link (items were dropped elsewhere).
     */
    public void removeLink(BlockPos target, @Nullable BlockPos dropAt) {
        Link removed = links.remove(target);
        if (removed != null) {
            if (level instanceof ServerLevel serverLevel) {
                WireNetworkSavedData.get(serverLevel).removeEdge(worldPosition, target);
            }
            if (dropAt != null && level != null && !level.isClientSide && removed.spoolCount() > 0) {
                Containers.dropItemStack(level, dropAt.getX() + 0.5, dropAt.getY() + 0.5, dropAt.getZ() + 0.5,
                        new ItemStack(removed.tier().spool().get(), removed.spoolCount()));
            }
        }
        sync();
    }

    /**
     * Drops every attached wire as spools (used when the pole is really removed).
     */
    public void cutWires() {
        for (BlockPos target : new ArrayList<>(links.keySet())) {
            removeLink(target, getBlockPos());
        }
    }

    public boolean hasWireTo(BlockPos pos) {
        return links.containsKey(pos);
    }

    private void sync() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockState state = getBlockState();
            serverLevel.getChunkSource().blockChanged(worldPosition);
            ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
            for (var player : serverLevel.getChunkSource().chunkMap
                    .getPlayers(new net.minecraft.world.level.ChunkPos(worldPosition), false)) {
                player.connection.send(packet);
            }
        }
    }

    private void validateTargets() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (BlockPos target : new ArrayList<>(links.keySet())) {
            if (!level.isLoaded(target)) {
                continue;
            }
            boolean valid = level.getBlockState(target).getBlock() instanceof ConnectorBlock ||
                    level.getBlockState(target).getBlock() instanceof dev.fluery.gtoverhead.block.RelayConnectorBlock;
            if (!valid) {
                // the wire items were dropped when the endpoint block was broken
                removeLink(target, null);
            }
        }
    }

    // region ticking

    @SuppressWarnings("unused")
    public static void serverTick(Level level, BlockPos pos, BlockState state, RelayConnectorBlockEntity relay) {
        if (level.isClientSide) {
            return;
        }
        if ((level.getGameTime() % 20L) == 0L) {
            relay.validateTargets();
            WireNetworkSavedData.get((ServerLevel) level).integrityCheck((ServerLevel) level, pos);
        }
        relay.zapEdges();
    }

    /**
     * Live wires hanging on this pole shock entities around them, judging by the
     * energy currently carried by the connector on the other side.
     */
    private void zapEdges() {
        if (links.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 anchor = WireAnchors.anchor(level, worldPosition);
        if (anchor == null) {
            anchor = Vec3.atCenterOf(worldPosition);
        }
        for (Link link : links.values()) {
            if (!level.isLoaded(link.target())) {
                continue;
            }
            if (!(level.getBlockEntity(link.target()) instanceof ConnectorBlockEntity connector)) {
                continue;
            }
            boolean live = connector.getEnergyContainer().getEnergyStored() >= link.tier().voltage();
            if (!live) {
                continue;
            }
            Vec3 other = WireAnchors.anchor(level, link.target());
            if (other == null) {
                other = Vec3.atCenterOf(link.target());
            }
            ElectrocutionHandler.zapAlong(serverLevel, anchor, other, link.tier(), true);
        }
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
            // every attached wire drops as spools here. Chunk unload also runs
            // this method - guarded by the isUnloaded flag, like IE does.
            if (!links.isEmpty()) {
                cutWires();
            }
        }
    }

    /**
     * Belt-and-braces hook fired from RelayConnectorBlock#onRemove (only on
     * real removal); cutWires is idempotent, so double invocation is harmless.
     */
    public void onBlockRemoved(BlockPos dropAt) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!links.isEmpty()) {
            cutWires();
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
        if (level != null && level.isClientSide) {
            updateClientCache();
        } else if (level instanceof ServerLevel serverLevel) {
            // re-register links after world reload and check integrity
            for (Link link : getLinks()) {
                WireNetworkSavedData.get(serverLevel).addEdge(worldPosition, link.target(), link.tier(),
                        link.distance());
            }
            WireNetworkSavedData.get(serverLevel).integrityCheck(serverLevel, worldPosition);
        }
    }

    private void updateClientCache() {
        List<ClientWireCache.LinkData> payload = new ArrayList<>();
        for (Link link : links.values()) {
            payload.add(new ClientWireCache.LinkData(link.target(), link.tier()));
        }
        ClientWireCache.setLinks(level, worldPosition, payload);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.links.clear();
        ListTag list = tag.getList(TAG_LINKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos target = NbtUtils.readBlockPos(entry.getCompound("Target"));
            if (target == null || target.equals(BlockPos.ZERO)) {
                continue;
            }
            double distance = entry.getDouble("Distance");
            WireTier tier = WireTier.byIndex(entry.getInt("Tier"));
            links.put(target, new Link(target, distance, tier));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (Link link : links.values()) {
            CompoundTag entry = new CompoundTag();
            entry.put("Target", NbtUtils.writeBlockPos(link.target()));
            entry.putDouble("Distance", link.distance());
            entry.putInt("Tier", link.tier().index);
            list.add(entry);
        }
        tag.put(TAG_LINKS, list);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.put(TAG_LINKS, linksTag());
        return tag;
    }

    private ListTag linksTag() {
        ListTag list = new ListTag();
        for (Link link : links.values()) {
            CompoundTag entry = new CompoundTag();
            entry.put("Target", NbtUtils.writeBlockPos(link.target()));
            entry.putInt("Tier", link.tier().index);
            list.add(entry);
        }
        return list;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void readClientTag(CompoundTag tag) {
        this.links.clear();
        ListTag list = tag.getList(TAG_LINKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos target = NbtUtils.readBlockPos(entry.getCompound("Target"));
            if (target == null || target.equals(BlockPos.ZERO)) {
                continue;
            }
            links.put(target, new Link(target, 0, WireTier.byIndex(entry.getInt("Tier"))));
        }
        updateClientCache();
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            readClientTag(tag);
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        readClientTag(tag);
    }

    // endregion
}
