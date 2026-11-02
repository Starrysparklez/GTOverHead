package dev.fluery.gtoverhead.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Shared catenary curve drawing for both the block entity renderer and the
 * always-visible level stage renderer.
 *
 * y(t) = (1-t)*y1 + t*y2 - sag * 4t(1-t), t in [0, 1]
 */
public final class WireCurve {

    private static final int SEGMENTS_PER_BLOCK = 2;
    private static final int MIN_SEGMENTS = 12;
    private static final double SAG_PER_BLOCK = 0.04;
    private static final double SAG_MIN = 0.3;
    private static final double SAG_MAX = 2.5;
    private static final double LINE_OFFSET = 0.035;

    public static double sag(double distance) {
        return Mth.clamp(distance * SAG_PER_BLOCK, SAG_MIN, SAG_MAX);
    }

    public static void draw(VertexConsumer consumer, PoseStack.Pose pose, Vec3 from, Vec3 to, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0E-4) {
            return;
        }

        double sag = sag(distance);
        int segments = Math.max(MIN_SEGMENTS, (int) (distance * SEGMENTS_PER_BLOCK));

        // normalized horizontal perpendicular for a slight thickening offset
        double invHoriz = 1.0 / Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
        double perpX = -dz * invHoriz * LINE_OFFSET;
        double perpZ = dx * invHoriz * LINE_OFFSET;

        double prevX = from.x;
        double prevY = from.y;
        double prevZ = from.z;
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            double x = from.x + dx * t;
            double y = from.y + dy * t - sag * 4.0 * t * (1.0 - t);
            double z = from.z + dz * t;

            line(consumer, pose, prevX + perpX, prevY, prevZ + perpZ, x + perpX, y, z + perpZ, r, g, b);
            line(consumer, pose, prevX, prevY, prevZ, x, y, z, r, g, b);
            line(consumer, pose, prevX - perpX, prevY, prevZ - perpZ, x - perpX, y, z - perpZ, r, g, b);

            prevX = x;
            prevY = y;
            prevZ = z;
        }
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose, double x1, double y1, double z1,
                             double x2, double y2, double z2, int r, int g, int b) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        float norm = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (norm < 1.0E-6F) {
            norm = 1.0F;
        }
        nx /= norm;
        ny /= norm;
        nz /= norm;
        vertex(consumer, pose, x1, y1, z1, nx, ny, nz, r, g, b);
        vertex(consumer, pose, x2, y2, z2, nx, ny, nz, r, g, b);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, double x, double y, double z,
                               float nx, float ny, float nz, int r, int g, int b) {
        consumer.vertex(pose.pose(), (float) x, (float) y, (float) z)
                .color(r, g, b, 255)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
    }

    private WireCurve() {}
}
