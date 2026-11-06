package dev.fluery.gtoverhead.item;

import dev.fluery.gtoverhead.GTOverheadConfig;
import dev.fluery.gtoverhead.block.WireAnchors;
import dev.fluery.gtoverhead.blockentity.ConnectorBlockEntity;
import dev.fluery.gtoverhead.blockentity.RelayConnectorBlockEntity;
import dev.fluery.gtoverhead.tier.WireTier;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WireSpoolItem extends Item {

    private static final String TAG_LINK_POS = "LinkPos";

    private final WireTier tier;

    public WireSpoolItem(Properties properties, WireTier tier) {
        super(properties);
        this.tier = tier;
    }

    public WireTier getTier() {
        return tier;
    }

    @Nullable
    private static BlockPos getLinkPos(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_LINK_POS)) {
            return null;
        }
        BlockPos pos = NbtUtils.readBlockPos(tag.getCompound(TAG_LINK_POS));
        return pos != null && !pos.equals(BlockPos.ZERO) ? pos : null;
    }

    private static void setLinkPos(ItemStack stack, @Nullable BlockPos pos) {
        if (pos == null) {
            stack.removeTagKey(TAG_LINK_POS);
        } else {
            stack.getOrCreateTag().put(TAG_LINK_POS, NbtUtils.writeBlockPos(pos));
        }
    }

    private static boolean isRelay(BlockEntity blockEntity) {
        return blockEntity instanceof RelayConnectorBlockEntity;
    }

    private static boolean tierAcceptable(WireSpoolItem spool, BlockEntity endpoint) {
        if (endpoint instanceof RelayConnectorBlockEntity) {
            return true;
        }
        return endpoint instanceof ConnectorBlockEntity connector && connector.getTier() == spool.getTier();
    }

    private static boolean endpointAvailable(WireSpoolItem spool, BlockEntity endpoint, BlockPos other) {
        if (endpoint instanceof RelayConnectorBlockEntity relay) {
            return !relay.hasWireTo(other);
        }
        if (endpoint instanceof ConnectorBlockEntity connector) {
            // a stale link (wire cut elsewhere) does not block relinking
            return connector.getTier() == spool.getTier() && !connector.hasActiveWire();
        }
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ConnectorBlockEntity) && !isRelay(blockEntity)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown() && getLinkPos(stack) != null) {
            // shift + right click anywhere: cancel the anchor
            setLinkPos(stack, null);
            message(player, "message.gtoverhead.spool_cancelled");
            return InteractionResult.SUCCESS;
        }
        if (!tierAcceptable(this, blockEntity)) {
            message(player, "message.gtoverhead.tier_mismatch");
            return InteractionResult.FAIL;
        }

        BlockPos storedPos = getLinkPos(stack);
        if (storedPos == null) {
            if (blockEntity instanceof ConnectorBlockEntity connector && connector.hasActiveWire()) {
                message(player, "message.gtoverhead.already_linked");
                return InteractionResult.FAIL;
            }
            setLinkPos(stack, pos);
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.8F, 1.2F);
            message(player, "message.gtoverhead.spool_first", formatPos(pos));
            return InteractionResult.SUCCESS;
        }

        if (storedPos.equals(pos)) {
            // second click on the same connector: cancel selection
            setLinkPos(stack, null);
            message(player, "message.gtoverhead.spool_cancelled");
            return InteractionResult.SUCCESS;
        }

        BlockEntity remote = level.getBlockEntity(storedPos);
        if (!(remote instanceof ConnectorBlockEntity) && !isRelay(remote)) {
            setLinkPos(stack, null);
            message(player, "message.gtoverhead.stored_endpoint_missing");
            return InteractionResult.FAIL;
        }
        if (!tierAcceptable(this, remote) || !endpointAvailable(this, remote, pos) ||
                !endpointAvailable(this, blockEntity, storedPos)) {
            message(player, "message.gtoverhead.already_linked");
            return InteractionResult.FAIL;
        }

        double distance = Math.sqrt(pos.distSqr(storedPos));
        int maxLength = GTOverheadConfig.maxWireLength();
        if (distance > maxLength) {
            message(player, "message.gtoverhead.too_far", String.format("%.0f", distance), maxLength);
            return InteractionResult.FAIL;
        }

        // the wire may not be strung through blocks
        Vec3 anchorA = WireAnchors.anchor(level, storedPos);
        Vec3 anchorB = WireAnchors.anchor(level, pos);
        if (anchorA == null || anchorB == null || !WireAnchors.pathIsClear(level, storedPos, pos, anchorA, anchorB)) {
            message(player, "message.gtoverhead.blocked_path");
            return InteractionResult.FAIL;
        }

        int spoolsNeeded = (int) Math.ceil(distance / GTOverheadConfig.wireSpoolSegmentLength());
        if (stack.getCount() < spoolsNeeded) {
            message(player, "message.gtoverhead.not_enough_wire", spoolsNeeded, stack.getCount());
            return InteractionResult.FAIL;
        }

        if (remote instanceof ConnectorBlockEntity from) {
            from.setLink(pos, distance);
        } else if (remote instanceof RelayConnectorBlockEntity fromRelay) {
            fromRelay.addLink(pos, distance, tier);
        }
        if (blockEntity instanceof ConnectorBlockEntity connector) {
            connector.setLink(storedPos, distance);
        } else if (blockEntity instanceof RelayConnectorBlockEntity relay) {
            relay.addLink(storedPos, distance, tier);
        }

        stack.shrink(spoolsNeeded);
        setLinkPos(stack, null);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, storedPos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
        message(player, "message.gtoverhead.linked", String.format("%.0f", distance), spoolsNeeded);
        return InteractionResult.SUCCESS;
    }

    private static void message(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                                      net.minecraft.world.InteractionHand hand) {
        // shift + right click into the air: cancel the anchor
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player.isShiftKeyDown() && getLinkPos(stack) != null) {
            setLinkPos(stack, null);
            message(player, "message.gtoverhead.spool_cancelled");
            return net.minecraft.world.InteractionResultHolder.consume(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        // GT-style wire stats
        long voltage = tier.voltage();
        int amperage = tier.maxAmperage();
        double lossDistance = GTOverheadConfig.lossDistance();

        Style tierColor = Style.EMPTY.withColor(TextColor.fromRgb(GTValues.VCM[tier.gtTier]));

        tooltip.add(Component.empty()
                .append(Component.translatable("tooltip.gtoverhead.wire.voltage").withStyle(ChatFormatting.GREEN))
                .append(" ")
                .append(Component.literal(String.valueOf(voltage)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (").withStyle(ChatFormatting.GREEN))
                .append(Component.translatable("tooltip.gtoverhead.tier." + tier.shortName).withStyle(tierColor))
                .append(Component.literal(")").withStyle(ChatFormatting.GREEN)));

        tooltip.add(Component.empty()
                .append(Component.translatable("tooltip.gtoverhead.wire.current").withStyle(ChatFormatting.YELLOW))
                .append(" ")
                .append(Component.literal(String.valueOf(amperage)).withStyle(ChatFormatting.WHITE)));

        tooltip.add(Component.empty()
                .append(Component.translatable("tooltip.gtoverhead.wire.loss", lossDistance)
                        .withStyle(ChatFormatting.RED))
                .append(" ")
                .append(Component.literal(String.valueOf(tier.lossPerStep())).withStyle(ChatFormatting.WHITE))
                .append(" ")
                .append(Component.literal("EU-Volt").withStyle(ChatFormatting.LIGHT_PURPLE)));

        BlockPos stored = getLinkPos(stack);
        if (stored != null) {
            tooltip.add(Component.translatable("tooltip.gtoverhead.spool.linked", formatPos(stored))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.gtoverhead.spool.not_linked")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
