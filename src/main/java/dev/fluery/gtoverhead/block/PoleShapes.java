package dev.fluery.gtoverhead.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Simple "barrel" collision shapes: a 4x4 pixel column extending from the
 * support face by the height of the model. Deliberately not pixel-perfect -
 * chunky boxes are easier to target.
 */
final class PoleShapes {

    private static final int WIDTH = 4;

    private PoleShapes() {}

    static VoxelShape of(double tipHeight, Direction support) {
        Direction pole = support.getOpposite();
        double tip = Math.min(16, Math.max(2, tipHeight * 16.0));
        double lo = 8 - WIDTH / 2.0;
        double hi = 8 + WIDTH / 2.0;
        return switch (pole) {
            case UP -> Shapes.box(lo / 16.0, 0.0, lo / 16.0, hi / 16.0, tip / 16.0, hi / 16.0);
            case DOWN -> Shapes.box(lo / 16.0, (16 - tip) / 16.0, lo / 16.0, hi / 16.0, 1.0, hi / 16.0);
            case NORTH -> Shapes.box(lo / 16.0, lo / 16.0, (16 - tip) / 16.0, hi / 16.0, hi / 16.0, 1.0);
            case SOUTH -> Shapes.box(lo / 16.0, lo / 16.0, 0.0, hi / 16.0, hi / 16.0, tip / 16.0);
            case WEST -> Shapes.box((16 - tip) / 16.0, lo / 16.0, lo / 16.0, 1.0, hi / 16.0, hi / 16.0);
            case EAST -> Shapes.box(0.0, lo / 16.0, lo / 16.0, tip / 16.0, hi / 16.0, hi / 16.0);
        };
    }
}
