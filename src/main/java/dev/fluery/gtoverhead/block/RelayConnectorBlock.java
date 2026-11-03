package dev.fluery.gtoverhead.block;

import dev.fluery.gtoverhead.blockentity.RelayConnectorBlockEntity;
import dev.fluery.gtoverhead.registry.GTOverheadRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * Decorative structural pole: carries no energy into the local network and
 * exposes no energy capability, but can hold several wires at once (e.g. one
 * incoming wire fanned out to three consumers). It only re-transmits energy
 * between its own wires.
 */
public class RelayConnectorBlock extends BaseConnectorBlock {

    public RelayConnectorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected double shapeHeight() {
        return WireAnchors.RELAY_TIP_HEIGHT;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RelayConnectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || type != GTOverheadRegistries.RELAY_BE_TYPE.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<RelayConnectorBlockEntity>) RelayConnectorBlockEntity::serverTick;
    }
}
