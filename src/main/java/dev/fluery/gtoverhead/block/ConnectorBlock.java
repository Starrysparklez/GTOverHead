package dev.fluery.gtoverhead.block;

import dev.fluery.gtoverhead.blockentity.ConnectorBlockEntity;
import dev.fluery.gtoverhead.registry.GTOverheadRegistries;
import dev.fluery.gtoverhead.tier.WireTier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

public class ConnectorBlock extends BaseConnectorBlock {

    private final WireTier tier;

    public ConnectorBlock(Properties properties, WireTier tier) {
        super(properties);
        this.tier = tier;
    }

    public WireTier getTier() {
        return tier;
    }

    @Override
    protected double[][] shapeBoxes() {
        return PoleShapes.TIER_BOX_SETS[tier.index];
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConnectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || type != GTOverheadRegistries.CONNECTOR_BE_TYPE.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<ConnectorBlockEntity>) ConnectorBlockEntity::serverTick;
    }
}
