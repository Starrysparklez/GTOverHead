package dev.fluery.gtoverhead.util;

import dev.fluery.gtoverhead.GTOverheadConfig;
import dev.fluery.gtoverhead.tier.WireTier;

import com.gregtechceu.gtceu.common.data.GTDamageTypes;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shocks entities near a live wire, following the "bird on a wire" rule:
 * <ul>
 * <li>no path to the ground - the entity is perfectly safe, even when
 * touching the wire;</li>
 * <li>grounded entity (touching ground/blocks/water) is shocked by direct
 * contact with the wire;</li>
 * <li>Eg and IV wires additionally arc through the air: airborne entities
 * within the hazard radius get shocked even without contact.</li>
 * </ul>
 * Damage uses the GregTech electric damage type.
 */
public final class ElectrocutionHandler {

    private ElectrocutionHandler() {}

    /**
     * @param live whether the wire actually carries at least one ampere worth of
     *             energy right now (a dead wire never shocks)
     */
    public static void zapAlong(ServerLevel level, Vec3 from, Vec3 to, WireTier tier, boolean live) {
        if (!live) {
            return;
        }
        double radius = GTOverheadConfig.hazardRadius(tier.index);
        double damage = GTOverheadConfig.hazardDamage(tier.index);
        if (radius <= 0.0 || damage <= 0.0) {
            return;
        }
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1.0E-4) {
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(length));
        Vec3 step = delta.scale(1.0 / steps);
        // direct contact reach while standing on something conductive
        double contactRadius = Math.min(radius, 0.75);
        // EV/IV arcs through the air reach airborne entities
        boolean arcsThroughAir = tier.index >= WireTier.EXTREME.index;
        for (int i = 0; i <= steps; i++) {
            Vec3 point = from.add(step.scale(i));
            AABB box = new AABB(point, point).inflate(radius);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive() && e.isAttackable())) {
                Vec3 closest = closestPointOnSegment(from, to, entity.position());
                double dist = entity.position().distanceTo(closest);
                if (dist > radius) {
                    continue;
                }
                boolean grounded = touchesBlocks(entity);
                boolean shocked = grounded ? dist <= contactRadius : arcsThroughAir;
                if (!shocked) {
                    continue;
                }
                // distance falloff: full damage at the wire, half at the edge
                float scaled = (float) Math.max(1.0F, damage * (1.0 - 0.5 * dist / radius));
                if (entity.hurt(GTDamageTypes.ELECTRIC.source(level), scaled)) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(),
                            entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
                }
            }
        }
    }

    /**
     * True when the entity has a path to the earth: standing/climbing on
     * blocks, pressed against them, or swimming.
     */
    private static boolean touchesBlocks(LivingEntity entity) {
        return entity.onGround() || entity.horizontalCollision || entity.verticalCollision ||
                entity.isInWaterOrBubble();
    }

    private static Vec3 closestPointOnSegment(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double t = p.subtract(a).dot(ab) / Math.max(1.0E-9, ab.lengthSqr());
        return a.add(ab.scale(Math.max(0.0, Math.min(1.0, t))));
    }
}
