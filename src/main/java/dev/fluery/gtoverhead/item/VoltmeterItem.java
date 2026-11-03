package dev.fluery.gtoverhead.item;

import dev.fluery.gtoverhead.blockentity.ConnectorBlockEntity;
import dev.fluery.gtoverhead.blockentity.RelayConnectorBlockEntity;
import dev.fluery.gtoverhead.tier.WireTier;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.common.pipelike.cable.EnergyNetHandler;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Multimeter: right-click an overhead connector or a GregTech wire/machine to
 * see the voltage and amperage actually present right now.
 */
public class VoltmeterItem extends Item {

    private static Style gtColor(int color) {
        return Style.EMPTY.withColor(TextColor.fromRgb(color));
    }

    public VoltmeterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (level.isClientSide || player == null) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ConnectorBlockEntity connector) {
            readOverheadConnector(player, connector);
            return InteractionResult.CONSUME;
        }
        if (blockEntity instanceof RelayConnectorBlockEntity relay) {
            readRelay(player, relay);
            return InteractionResult.CONSUME;
        }

        // GregTech wires and machines through the shared capability
        IEnergyContainer container = GTCapabilityHelper.getEnergyContainer(level, pos,
                context.getClickedFace());
        if (container != null) {
            readGTContainer(player, pos, container);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private static void header(Player player, Component name) {
        player.displayClientMessage(Component.empty()
                .append(Component.literal("--- ").withStyle(ChatFormatting.DARK_GRAY))
                .append(name)
                .append(Component.literal(" ---").withStyle(ChatFormatting.DARK_GRAY)), false);
    }

    private static void field(Player player, String key, ChatFormatting color, String value) {
        player.displayClientMessage(Component.empty()
                .append(Component.translatable(key).withStyle(color))
                .append(" ")
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
    }

    private static void readOverheadConnector(Player player, ConnectorBlockEntity connector) {
        WireTier tier = connector.getTier();
        var container = connector.getEnergyContainer();
        header(player, Component.translatable("block.gtoverhead." + tier.shortName + "_connector")
                .withStyle(gtColor(com.gregtechceu.gtceu.api.GTValues.VCM[tier.gtTier])));

        field(player, "gtoverhead.voltmeter.voltage", ChatFormatting.GREEN,
                container.getBufferVoltage() > 0 ? String.valueOf(container.getBufferVoltage()) :
                        String.valueOf(tier.voltage()) + " (idle)");
        field(player, "gtoverhead.voltmeter.current", ChatFormatting.YELLOW,
                String.valueOf(tier.maxAmperage()));
        field(player, "gtoverhead.voltmeter.energy", ChatFormatting.RED,
                container.getEnergyStored() + " / " + container.getEnergyCapacity());

        if (connector.hasWire() && connector.getTargetPos() != null) {
            var target = connector.getTargetPos();
            field(player, "gtoverhead.voltmeter.wire", ChatFormatting.AQUA,
                    target.getX() + ", " + target.getY() + ", " + target.getZ() +
                            String.format(" (%.0f)", connector.getDistance()));
        }
    }

    private static void readRelay(Player player, RelayConnectorBlockEntity relay) {
        header(player, Component.translatable("block.gtoverhead.structural_connector")
                .withStyle(ChatFormatting.GRAY));
        field(player, "gtoverhead.voltmeter.wires", ChatFormatting.GREEN, String.valueOf(relay.getLinks().size()));
        for (var link : relay.getLinks()) {
            player.displayClientMessage(Component.empty()
                    .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("tooltip.gtoverhead.tier." + link.tier().shortName)
                            .withStyle(gtColor(com.gregtechceu.gtceu.api.GTValues.VCM[link.tier().gtTier])))
                    .append(Component.literal(String.format(" (%.0f)", link.distance()))
                            .withStyle(ChatFormatting.GRAY)),
                    false);
        }
    }

    private static void readGTContainer(Player player, BlockPos pos, IEnergyContainer container) {
        if (container instanceof EnergyNetHandler handler) {
            // GregTech cable: the wire itself does not store energy - show its
            // rating and the current flux of the whole wire network
            header(player, Component.translatable("gtoverhead.voltmeter.gt_wire")
                    .withStyle(ChatFormatting.GRAY));
            field(player, "gtoverhead.voltmeter.voltage", ChatFormatting.GREEN,
                    String.valueOf(container.getInputVoltage()));
            field(player, "gtoverhead.voltmeter.current", ChatFormatting.YELLOW,
                    String.valueOf(container.getInputAmperage()));
            field(player, "gtoverhead.voltmeter.flux", ChatFormatting.RED,
                    String.valueOf(handler.getNet().getEnergyFluxPerSec()));
            return;
        }
        // GregTech machine energy container
        header(player, Component.translatable("gtoverhead.voltmeter.gt_machine")
                .withStyle(ChatFormatting.GRAY));
        field(player, "gtoverhead.voltmeter.energy", ChatFormatting.RED,
                container.getEnergyStored() + " / " + container.getEnergyCapacity());
        field(player, "gtoverhead.voltmeter.input", ChatFormatting.GREEN,
                container.getInputVoltage() + "V × " + container.getInputAmperage() + "A");
        field(player, "gtoverhead.voltmeter.output", ChatFormatting.GREEN,
                container.getOutputVoltage() + "V × " + container.getOutputAmperage() + "A");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("gtoverhead.voltmeter.tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
