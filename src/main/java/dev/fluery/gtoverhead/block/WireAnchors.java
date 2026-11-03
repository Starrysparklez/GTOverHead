package dev.fluery.gtoverhead.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/**
 * Wire anchor points. The wire attaches at the insulator tip of the pole, whose
 * height above the support face matches the custom connector model.
 */
public final class WireAnchors {

    public static final double RELAY_TIP_HEIGHT = 0.8125;

    private WireAnchors() {}

    /**
     * Anchor of the pole at {@code pos}, or null if the block there is not one of
     * our connector poles.
     */
    @Nullable
    public static Vec3 anchor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BaseConnectorBlock block)) {
            return null;
        }
        double tip = tipHeight(state);
        // facing = support direction, pole points away from it
        Direction poleDir = state.getValue(BaseConnectorBlock.FACING).getOpposite();
        return Vec3.atCenterOf(pos).add(poleDir.getStepX() * (tip - 0.5), poleDir.getStepY() * (tip - 0.5),
                poleDir.getStepZ() * (tip - 0.5));
    }

    private static double tipHeight(BlockState state) {
        if (state.getBlock() instanceof ConnectorBlock connector) {
            return connector.getTier().tipHeight();
        }
        if (state.getBlock() instanceof RelayConnectorBlock) {
            return RELAY_TIP_HEIGHT;
        }
        return 0.5;
    }

    /**
     * Whether the straight wire between the two anchors is free of blocks.
     * Sampled along the line; the two endpoint blocks are ignored.
     */
    public static boolean pathIsClear(Level level, BlockPos fromPos, BlockPos toPos, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1.0E-4) {
            return true;
        }
        int steps = (int) Math.ceil(length * 2.0);
        Vec3 step = delta.scale(1.0 / steps);
        for (int i = 1; i < steps; i++) {
            Vec3 point = from.add(step.scale(i));
            BlockPos pos = BlockPos.containing(point);
            if (pos.equals(fromPos) || pos.equals(toPos)) {
                continue;
            }
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.getBlock() instanceof BaseConnectorBlock) {
                // other poles are crossed only if the wire misses their shape
                if (pointInsideShape(state, level, pos, point)) {
                    return false;
                }
                continue;
            }
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean pointInsideShape(BlockState state, Level level, BlockPos pos, Vec3 point) {
        return !state.getShape(level, pos).isEmpty() &&
                state.getShape(level, pos).toAabbs().stream().anyMatch(voxel -> {
                    Vec3 rel = point.subtract(Vec3.atLowerCornerOf(pos));
                    return rel.x >= voxel.minX && rel.x <= voxel.maxX && rel.y >= voxel.minY && rel.y <= voxel.maxY &&
                            rel.z >= voxel.minZ && rel.z <= voxel.maxZ;
                });
    }
}
