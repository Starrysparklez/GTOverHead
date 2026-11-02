package dev.fluery.gtoverhead.client;

import dev.fluery.gtoverhead.tier.WireTier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side cache of all overhead wire links, fed from block entity sync
 * packets. Rendered by {@link WireLevelRenderer} every frame.
 */
public final class ClientWireCache {

    public record LinkData(BlockPos to, WireTier tier) {}

    public record ClientLink(Level level, BlockPos from, BlockPos to, WireTier tier) {}

    private static final Map<Long, List<ClientLink>> LINKS = new HashMap<>();

    public static List<ClientLink> all() {
        List<ClientLink> out = new ArrayList<>();
        for (List<ClientLink> links : LINKS.values()) {
            out.addAll(links);
        }
        return out;
    }

    public static void addLink(Level level, BlockPos from, BlockPos to, WireTier tier) {
        setLinks(level, from, List.of(new LinkData(to, tier)));
    }

    public static void setLinks(Level level, BlockPos from, List<LinkData> links) {
        List<ClientLink> converted = new ArrayList<>(links.size());
        for (LinkData link : links) {
            converted.add(new ClientLink(level, from.immutable(), link.to(), link.tier()));
        }
        if (converted.isEmpty()) {
            LINKS.remove(from.asLong());
        } else {
            LINKS.put(from.asLong(), converted);
        }
    }

    public static void forget(Level level, BlockPos from) {
        LINKS.remove(from.asLong());
    }

    /** Removes every entry of a different level/session, keeping freshly synced ones. */
    public static void purgeOtherLevels(Level level) {
        LINKS.values().removeIf(list -> !list.isEmpty() && list.get(0).level() != level);
    }

    public static void clear() {
        LINKS.clear();
    }

    private ClientWireCache() {}
}
