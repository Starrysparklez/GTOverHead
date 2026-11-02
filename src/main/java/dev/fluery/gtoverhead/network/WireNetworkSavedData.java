package dev.fluery.gtoverhead.network;

import dev.fluery.gtoverhead.GTOverheadConfig;
import dev.fluery.gtoverhead.block.BaseConnectorBlock;
import dev.fluery.gtoverhead.tier.WireTier;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Persistent graph of all overhead wires in one dimension, stored in SavedData.
 * Every wire is an edge between two pole positions; several disjoint graphs
 * (separate power lines of different players) coexist naturally as connected
 * components. Routing works purely on this data, so energy flows through
 * structural poles in unloaded chunks as well. Each network is a connected
 * component - two independently built lines are two separate components.
 */
public class WireNetworkSavedData extends SavedData {

    public static final String DATA_NAME = "gtoverhead_wire_networks";

    public record Edge(BlockPos a, BlockPos b, WireTier tier, double distance) {

        boolean touches(BlockPos pos) {
            return a.equals(pos) || b.equals(pos);
        }

        public BlockPos other(BlockPos pos) {
            return a.equals(pos) ? b : a;
        }

        /** EU lost per ampere across this wire segment. */
        long lossPerAmp() {
            return (long) Math.floor(distance / GTOverheadConfig.lossDistance()) * tier.lossPerStep();
        }

        public long key() {
            long la = a.asLong();
            long lb = b.asLong();
            return la < lb ? combine(la, lb) : combine(lb, la);
        }

        private static long combine(long lo, long hi) {
            // XOR-folding is enough for a dedupe key of an unordered pair
            return lo * 0x9E3779B97F4A7C15L ^ hi;
        }
    }

    /**
     * A delivery candidate produced by routing: a connector node reachable from
     * the source through the graph.
     */
    public record Delivery(BlockPos target, long voltage, int maxAmperage) {}

    private final List<Edge> edges = new ArrayList<>();
    private final com.google.common.collect.Multimap<Long, Edge> adjacency = com.google.common.collect.HashMultimap
            .create();

    public static WireNetworkSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WireNetworkSavedData::load, WireNetworkSavedData::new,
                DATA_NAME);
    }

    public void addEdge(BlockPos a, BlockPos b, WireTier tier, double distance) {
        Edge edge = new Edge(a.immutable(), b.immutable(), tier, distance);
        for (Edge existing : edges) {
            if (existing.key() == edge.key()) {
                return;
            }
        }
        edges.add(edge);
        adjacency.put(a.asLong(), edge);
        adjacency.put(b.asLong(), edge);
        setDirty();
    }

    public void removeEdge(BlockPos a, BlockPos b) {
        removeIf(edge -> (edge.a.equals(a) && edge.b.equals(b)) || (edge.a.equals(b) && edge.b.equals(a)));
    }

    private void removeEdge(Edge edge) {
        removeIf(e -> e == edge || e.key() == edge.key());
    }

    private void removeIf(java.util.function.Predicate<Edge> predicate) {
        Iterator<Edge> it = edges.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            Edge edge = it.next();
            if (predicate.test(edge)) {
                it.remove();
                adjacency.remove(edge.a().asLong(), edge);
                adjacency.remove(edge.b().asLong(), edge);
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public List<Edge> edgesAt(BlockPos pos) {
        return new ArrayList<>(adjacency.get(pos.asLong()));
    }

    public List<Edge> edges() {
        return new ArrayList<>(edges);
    }

    /**
     * Breaks every edge touching {@code pos} whose other endpoint block no
     * longer exists (only checked when that chunk is loaded). The wire drops
     * as spools at the broken position.
     */
    public void integrityCheck(ServerLevel level, BlockPos pos) {
        for (Edge edge : edgesAt(pos)) {
            BlockPos other = edge.other(pos);
            if (!level.isLoaded(other)) {
                continue;
            }
            if (level.getBlockState(other).getBlock() instanceof BaseConnectorBlock) {
                continue;
            }
            // the remote endpoint block is gone: break the circuit, drop the wire there
            removeEdge(edge);
            int spools = (int) Math.max(1, Math.floor(edge.distance() / GTOverheadConfig.wireSpoolSegmentLength()));
            if (spools > 0) {
                Containers.dropItemStack(level, other.getX() + 0.5, other.getY() + 0.5, other.getZ() + 0.5,
                        new ItemStack(edge.tier().spool().get(), spools));
            }
        }
    }

    /**
     * Routes from a source connector through the whole graph (loaded or not).
     * Every leaf node of the connected component is a delivery candidate;
     * voltage drops by each edge's loss, amperage is limited by the weakest
     * wire on the path.
     */
    public List<Delivery> route(BlockPos start, long startVoltage, int startAmperage) {
        List<Delivery> deliveries = new ArrayList<>();
        if (startVoltage <= 0 || startAmperage <= 0) {
            return deliveries;
        }
        Set<Long> visited = new HashSet<>();
        visited.add(start.asLong());
        record Node(BlockPos pos, long voltage, int amperage) {}
        Deque<Node> queue = new ArrayDeque<>();
        queue.add(new Node(start, startVoltage, startAmperage));
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            for (Edge edge : edgesAt(current.pos())) {
                BlockPos other = edge.other(current.pos());
                if (!visited.add(other.asLong())) {
                    continue;
                }
                long voltage = current.voltage() - edge.lossPerAmp();
                int amperage = Math.min(current.amperage(), edge.tier().maxAmperage());
                if (voltage <= 0 || amperage <= 0) {
                    // dead branch: the wire cannot carry the voltage any further
                    continue;
                }
                if (adjacency.get(other.asLong()).size() <= 1) {
                    // leaf node: an endpoint connector (single-link node)
                    deliveries.add(new Delivery(other, voltage, amperage));
                }
                queue.add(new Node(other, voltage, amperage));
            }
        }
        return deliveries;
    }

    @Nullable
    public Edge findEdge(BlockPos a, BlockPos b) {
        for (Edge edge : edges) {
            if ((edge.a.equals(a) && edge.b.equals(b)) || (edge.a.equals(b) && edge.b.equals(a))) {
                return edge;
            }
        }
        return null;
    }

    private static WireNetworkSavedData load(CompoundTag tag) {
        WireNetworkSavedData data = new WireNetworkSavedData();
        ListTag list = tag.getList("Edges", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos a = BlockPos.of(entry.getLong("A"));
            BlockPos b = BlockPos.of(entry.getLong("B"));
            WireTier tier = WireTier.byIndex(entry.getInt("Tier"));
            double distance = entry.getDouble("Distance");
            data.edges.add(new Edge(a, b, tier, distance));
            data.adjacency.put(a.asLong(), data.edges.get(data.edges.size() - 1));
            data.adjacency.put(b.asLong(), data.edges.get(data.edges.size() - 1));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Edge edge : edges) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("A", edge.a().asLong());
            entry.putLong("B", edge.b().asLong());
            entry.putInt("Tier", edge.tier().index);
            entry.putDouble("Distance", edge.distance());
            list.add(entry);
        }
        tag.put("Edges", list);
        return tag;
    }
}
