package dev.fluery.gtoverhead.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Collision/outline shapes derived directly from the custom connector models.
 * Every model box is described by (halfWidth, yMin, yMax) in pixels - all model
 * boxes are centred on the pole axis and have a square cross-section - which is
 * then rotated so the pole grows away from the support face.
 */
final class PoleShapes {

    static final double[][][] TIER_BOX_SETS = new double[][][] {
            // lv
            { { 1, 0, 2 }, { 2, 2, 3 }, { 1, 3, 4 }, { 2, 0, 1 } },
            // mv: stand, bottom insulator, rod, upper insulator
            { { 1, 0, 2 }, { 2, 0, 1 }, { 1, 3, 4 }, { 2, 2, 3 } },
            // hv: stand, rod, three insulators
            { { 1, 0, 4 }, { 1, 5, 6 }, { 2, 2, 3 }, { 2, 0, 1 }, { 2, 4, 5 } },
            // ev: taller stand, rod, three insulators
            { { 1, 0, 5 }, { 1, 5, 6 }, { 2, 2, 3 }, { 2, 0, 1 }, { 2, 4, 5 } },
            // iv: same frame as ev
            { { 1, 0, 5 }, { 1, 5, 6 }, { 2, 2, 3 }, { 2, 0, 1 }, { 2, 4, 5 } },
    };

    /** Structural relay pole: stand y2..9, insulators at 0..1, 1..2, 3..4, 5..6, 7..8. */
    static final double[][] STRUCTURAL_BOXES = {
            { 1, 2, 9 }, { 2, 5, 6 }, { 2, 7, 8 }, { 2, 3, 4 }, { 3, 1, 2 }, { 2, 0, 1 },
    };

    private PoleShapes() {}

    static VoxelShape of(double[][] boxes, Direction support) {
        Direction pole = support.getOpposite();
        VoxelShape shape = Shapes.empty();
        for (double[] box : boxes) {
            double h = box[0];
            double y0 = box[1];
            double y1 = box[2];
            double minX;
            double maxX;
            double minY;
            double maxY;
            double minZ;
            double maxZ;
            switch (pole) {
                case DOWN -> {
                    minY = 16 - y1;
                    maxY = 16 - y0;
                    minX = 8 - h;
                    maxX = 8 + h;
                    minZ = 8 - h;
                    maxZ = 8 + h;
                }
                case NORTH -> {
                    minZ = 16 - y1;
                    maxZ = 16 - y0;
                    minY = 8 - h;
                    maxY = 8 + h;
                    minX = 8 - h;
                    maxX = 8 + h;
                }
                case SOUTH -> {
                    minZ = y0;
                    maxZ = y1;
                    minY = 8 - h;
                    maxY = 8 + h;
                    minX = 8 - h;
                    maxX = 8 + h;
                }
                case WEST -> {
                    minX = 16 - y1;
                    maxX = 16 - y0;
                    minY = 8 - h;
                    maxY = 8 + h;
                    minZ = 8 - h;
                    maxZ = 8 + h;
                }
                case EAST -> {
                    minX = y0;
                    maxX = y1;
                    minY = 8 - h;
                    maxY = 8 + h;
                    minZ = 8 - h;
                    maxZ = 8 + h;
                }
                default -> { // UP: authored orientation
                    minY = y0;
                    maxY = y1;
                    minX = 8 - h;
                    maxX = 8 + h;
                    minZ = 8 - h;
                    maxZ = 8 + h;
                }
            }
            shape = Shapes.join(shape, Shapes.box(minX / 16.0, minY / 16.0, minZ / 16.0,
                    maxX / 16.0, maxY / 16.0, maxZ / 16.0), BooleanOp.OR);
        }
        return shape;
    }
}
