package dev.fluery.gtoverhead.block;

import dev.fluery.gtoverhead.blockentity.ConnectorBlockEntity;
import dev.fluery.gtoverhead.blockentity.RelayConnectorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

/**
 * Base for all overhead connector poles: a small stanchion with an insulator that
 * can be attached to the floor, the ceiling or any of the four walls.
 * The pole visually points away from the support block.
 */
public abstract class BaseConnectorBlock extends BaseEntityBlock {

    /**
     * Direction of the SUPPORT the pole is attached to (facing=down => placed on the
     * floor, pole points up). Matches the Immersive Engineering connector blockstate
     * mapping.
     */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    protected BaseConnectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PoleShapes.of(shapeHeight(), state.getValue(FACING));
    }

    /** Model height of this pole (insulator tip above the support face), in blocks. */
    protected abstract double shapeHeight();

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        // fires only on a real block replacement, never on chunk unload
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ConnectorBlockEntity connector) {
                connector.onBlockRemoved(pos);
            } else if (level.getBlockEntity(pos) instanceof RelayConnectorBlockEntity relay) {
                relay.onBlockRemoved(pos);
            }
        }
    }
}
