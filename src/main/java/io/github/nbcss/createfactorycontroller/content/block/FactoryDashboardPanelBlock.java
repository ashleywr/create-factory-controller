package io.github.nbcss.createfactorycontroller.content.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import io.github.nbcss.createfactorycontroller.registry.CFCBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** Full-depth cabinet extension; its BE only stores assembly membership. */
public class FactoryDashboardPanelBlock extends HorizontalDirectionalBlock
        implements IBE<FactoryDashboardPanelBlockEntity>, IWrenchable {
    public static final MapCodec<FactoryDashboardPanelBlock> CODEC = simpleCodec(FactoryDashboardPanelBlock::new);
    public FactoryDashboardPanelBlock(Properties properties) {
        super(properties); registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, DashboardAssemblyManager.placementFacing(context));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            FactoryControllerBlockEntity core = DashboardAssemblyManager.coreAt(level, pos);
            if (core != null) {
                serverPlayer.openMenu(core, buf -> FactoryControllerMenu.writeExtraData(core, buf));
                core.syncEverything();
            }
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock())) DashboardAssemblyManager.schedule(level, pos);
        IBE.onRemove(state, level, pos, replacement);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean moved) {
        DashboardAssemblyManager.schedule(level, pos);
    }
    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel(); BlockPos pos = context.getClickedPos();
        if (!level.isClientSide) {
            BlockState rotated = state.setValue(FACING, state.getValue(FACING).getClockWise());
            if (DashboardAssemblyManager.canPlace(level, pos, rotated, false, context.getPlayer())) {
                level.setBlock(pos, rotated, Block.UPDATE_ALL);
                DashboardAssemblyManager.schedule(level, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }
    @Override public Class<FactoryDashboardPanelBlockEntity> getBlockEntityClass() { return FactoryDashboardPanelBlockEntity.class; }
    @Override public BlockEntityType<? extends FactoryDashboardPanelBlockEntity> getBlockEntityType() { return CFCBlockEntityTypes.FACTORY_DASHBOARD_PANEL.get(); }
}
