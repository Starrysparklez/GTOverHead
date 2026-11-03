package dev.fluery.gtoverhead.compat.jade;

import dev.fluery.gtoverhead.block.RelayConnectorBlock;
import dev.fluery.gtoverhead.blockentity.ConnectorBlockEntity;
import dev.fluery.gtoverhead.blockentity.RelayConnectorBlockEntity;
import dev.fluery.gtoverhead.tier.WireTier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade tooltip for overhead connectors: live voltage, energy and wire info.
 */
public class ConnectorJadeProvider implements IBlockComponentProvider {

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("gtoverhead", "connector");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    private static MutableComponent tierName(WireTier tier) {
        return Component.translatable("tooltip.gtoverhead.tier." + tier.shortName)
                .withStyle(net.minecraft.network.chat.Style.EMPTY
                        .withColor(net.minecraft.network.chat.TextColor
                                .fromRgb(com.gregtechceu.gtceu.api.GTValues.VCM[tier.gtTier])));
    }

    private static MutableComponent line(String key, Object... args) {
        return Component.translatable(key, args);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlockEntity() instanceof ConnectorBlockEntity connector) {
            WireTier tier = connector.getTier();
            var container = connector.getEnergyContainer();

            tooltip.add(line("jade.gtoverhead.header", tierName(tier)).withStyle(ChatFormatting.DARK_GRAY));

            tooltip.add(Component.empty()
                    .append(line("jade.gtoverhead.voltage").withStyle(ChatFormatting.GREEN))
                    .append(" ")
                    .append(Component.literal(String.valueOf(container.getEffectiveOutputVoltage()))
                            .withStyle(ChatFormatting.WHITE)));

            tooltip.add(Component.empty()
                    .append(line("jade.gtoverhead.current").withStyle(ChatFormatting.YELLOW))
                    .append(" ")
                    .append(Component.literal(String.valueOf(tier.maxAmperage())).withStyle(ChatFormatting.WHITE)));

            tooltip.add(Component.empty()
                    .append(line("jade.gtoverhead.energy").withStyle(ChatFormatting.RED))
                    .append(" ")
                    .append(Component.literal(container.getEnergyStored() + " / " + container.getEnergyCapacity())
                            .withStyle(ChatFormatting.WHITE))
                    .append(" ")
                    .append(Component.literal("EU").withStyle(ChatFormatting.LIGHT_PURPLE)));

            if (connector.hasWire() && connector.getTargetPos() != null) {
                var target = connector.getTargetPos();
                tooltip.add(line("jade.gtoverhead.linked",
                        target.getX() + ", " + target.getY() + ", " + target.getZ(),
                        String.format("%.0f", connector.getDistance())).withStyle(ChatFormatting.AQUA));
            }
            return;
        }
        if (accessor.getBlockState().getBlock() instanceof RelayConnectorBlock &&
                accessor.getBlockEntity() instanceof RelayConnectorBlockEntity relay && relay.hasWires()) {
            tooltip.add(line("jade.gtoverhead.header",
                    Component.translatable("tooltip.gtoverhead.tier.structural")).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(line("jade.gtoverhead.wires", relay.getLinks().size()).withStyle(ChatFormatting.GREEN));
            for (var link : relay.getLinks()) {
                tooltip.add(Component.empty()
                        .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                        .append(tierName(link.tier()))
                        .append(Component.literal(String.format(" (%.0f)", link.distance()))
                                .withStyle(ChatFormatting.GRAY)));
            }
        }
    }
}
