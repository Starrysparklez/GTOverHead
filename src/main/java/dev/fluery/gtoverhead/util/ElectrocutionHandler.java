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
 * Shocks entities that touch (or, for EV/IV wires, get too close to) a live
 * wire. Damage uses the GregTech electric damage type.
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
        for (int i = 0; i <= steps; i++) {
            Vec3 point = from.add(step.scale(i));
            AABB box = new AABB(point, point).inflate(radius);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive() && e.isAttackable())) {
                // distance falloff: full damage at the wire, half at the edge
                Vec3 closest = closestPointOnSegment(from, to, entity.position());
                double dist = entity.position().distanceTo(closest);
                if (dist > radius) {
                    continue;
                }
                float scaled = (float) Math.max(1.0F, damage * (1.0 - 0.5 * dist / radius));
                if (entity.hurt(GTDamageTypes.ELECTRIC.source(level), scaled)) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(),
                            entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
                }
            }
        }
    }

    private static Vec3 closestPointOnSegment(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double t = p.subtract(a).dot(ab) / Math.max(1.0E-9, ab.lengthSqr());
        return a.add(ab.scale(Math.max(0.0, Math.min(1.0, t))));
    }
}
