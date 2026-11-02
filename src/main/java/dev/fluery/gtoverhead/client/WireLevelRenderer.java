package dev.fluery.gtoverhead.client;

import dev.fluery.gtoverhead.GTOverhead;
import dev.fluery.gtoverhead.block.BaseConnectorBlock;
import dev.fluery.gtoverhead.block.WireAnchors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.Iterator;

/**
 * Renders all cached wires every frame from the level render stage, so wires
 * stay visible independently of chunk section culling. Each catenary segment
 * is drawn as two crossed double-sided color quads, looking like a single
 * solid cable from every angle (same geometry idea as Immersive Engineering).
 */
@Mod.EventBusSubscriber(modid = GTOverhead.MOD_ID, value = Dist.CLIENT)
public final class WireLevelRenderer {

    private static final double WIRE_RADIUS = 0.03125;
    private static final int MIN_POINTS = 16;
    private static final int MAX_POINTS = 96;
    private static final double MAX_RENDER_DISTANCE = 512.0;

    /** Re-exposes the protected RenderStateShard constants (subclass trick). */
    private static final class Shards extends RenderStateShard {

        static final RenderStateShard.ShaderStateShard LIGHTNING_SHADER = RENDERTYPE_LIGHTNING_SHADER;
        static final RenderStateShard.TransparencyStateShard TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
        static final RenderStateShard.DepthTestStateShard LEQUAL_DEPTH = LEQUAL_DEPTH_TEST;
        static final RenderStateShard.CullStateShard NO_CULL_SHARD = NO_CULL;
        static final RenderStateShard.WriteMaskStateShard COLOR_AND_DEPTH_WRITE = COLOR_DEPTH_WRITE;

        private Shards() {
            super("gtoverhead_dummy", () -> {}, () -> {});
        }
    }

    /** Position-color quad layer without any texture, depth-tested against terrain. */
    private static final RenderType WIRE_TYPE = RenderType.create("gtoverhead_wire",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(Shards.LIGHTNING_SHADER)
                    .setTransparencyState(Shards.TRANSLUCENT)
                    .setDepthTestState(Shards.LEQUAL_DEPTH)
                    .setCullState(Shards.NO_CULL_SHARD)
                    .setWriteMaskState(Shards.COLOR_AND_DEPTH_WRITE)
                    .createCompositeState(false));

    @SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        // the static wire cache must not leak into the next session
        ClientWireCache.clear();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            return;
        }
        // drop stale entries from another session/level without touching
        // links that were just synced for the current one
        ClientWireCache.purgeOtherLevels(level);
        Iterator<ClientWireCache.ClientLink> links = ClientWireCache.all().iterator();
        if (!links.hasNext()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack transform = event.getPoseStack();
        transform.pushPose();
        transform.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        VertexConsumer builder = buffers.getBuffer(WIRE_TYPE);

        while (links.hasNext()) {
            ClientWireCache.ClientLink link = links.next();
            if (link.level() != level) {
                continue;
            }
            // if an endpoint block is gone the wire must vanish immediately,
            // even if the server update packet was missed
            boolean valid = level.getBlockState(link.from()).getBlock() instanceof BaseConnectorBlock &&
                    (!level.isLoaded(link.to()) ||
                            level.getBlockState(link.to()).getBlock() instanceof BaseConnectorBlock);
            if (level.isLoaded(link.from()) && !valid) {
                ClientWireCache.forget(level, link.from());
                continue;
            }
            Vec3 from = anchorOrCenter(level, link.from());
            Vec3 to = anchorOrCenter(level, link.to());
            double sag = WireCurve.sag(from.distanceTo(to));
            AABB renderBox = new AABB(from, to).inflate(WIRE_RADIUS + 1.0, sag + WIRE_RADIUS + 1.0,
                    WIRE_RADIUS + 1.0);
            if (renderBox.distanceToSqr(camera) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
                continue;
            }
            if (!event.getFrustum().isVisible(renderBox)) {
                continue;
            }
            drawWire(builder, transform, from, to, link.tier().wireColor());
        }
        buffers.endBatch();
        transform.popPose();
    }

    /** Block present AND its block entity still declares the wire. */
    private static boolean endpointRenderable(Level level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof BaseConnectorBlock)) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof dev.fluery.gtoverhead.blockentity.ConnectorBlockEntity connector) {
            return connector.hasWire();
        }
        if (level.getBlockEntity(pos) instanceof dev.fluery.gtoverhead.blockentity.RelayConnectorBlockEntity relay) {
            return relay.hasWires();
        }
        return false;
    }

    private static Vec3 anchorOrCenter(Level level, BlockPos pos) {
        Vec3 anchor = WireAnchors.anchor(level, pos);
        return anchor != null ? anchor : Vec3.atCenterOf(pos);
    }

    private static void drawWire(VertexConsumer builder, PoseStack transform, Vec3 from, Vec3 to, int color) {
        float r = ((color >> 16) & 0xFF) / 255F;
        float g = ((color >> 8) & 0xFF) / 255F;
        float b = (color & 0xFF) / 255F;

        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0E-4) {
            return;
        }

        double sag = WireCurve.sag(distance);
        int points = Mth.clamp((int) distance, MIN_POINTS, MAX_POINTS);

        boolean vertical = Math.abs(dx) < 1.0E-4 && Math.abs(dz) < 1.0E-4;
        Vec3 horNormal = vertical ? new Vec3(1, 0, 0) : new Vec3(-dz, 0, dx).normalize();

        Vec3 prev = from;
        for (int i = 1; i <= points; i++) {
            double t = (double) i / points;
            Vec3 next = new Vec3(from.x + dx * t, from.y + dy * t - sag * 4.0 * t * (1.0 - t), from.z + dz * t);
            Vec3 verticalNormal = prev.subtract(next).cross(horNormal).normalize();
            Vec3 horRadius = horNormal.scale(WIRE_RADIUS);
            Vec3 verticalRadius = verticalNormal.scale(-WIRE_RADIUS);

            crossedQuad(builder, transform.last(), prev, next, horRadius, r, g, b);
            crossedQuad(builder, transform.last(), prev, next, verticalRadius, r, g, b);

            prev = next;
        }
    }

    private static void crossedQuad(VertexConsumer builder, PoseStack.Pose pose, Vec3 start, Vec3 end,
                                    Vec3 radius, float r, float g, float b) {
        Vec3[] vertices = {
                start.add(radius), end.add(radius), end.subtract(radius), start.subtract(radius),
        };
        for (Vec3 vertex : vertices) {
            vertex(builder, pose, vertex, r, g, b);
        }
        for (int i = vertices.length - 1; i >= 0; i--) {
            vertex(builder, pose, vertices[i], r, g, b);
        }
    }

    private static void vertex(VertexConsumer builder, PoseStack.Pose pose, Vec3 pos, float r, float g, float b) {
        builder.vertex(pose.pose(), (float) pos.x, (float) pos.y, (float) pos.z)
                .color(r, g, b, 1.0F)
                .endVertex();
    }

    private WireLevelRenderer() {}
}
