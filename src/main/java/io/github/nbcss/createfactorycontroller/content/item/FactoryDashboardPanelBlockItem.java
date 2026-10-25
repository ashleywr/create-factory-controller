package io.github.nbcss.createfactorycontroller.content.item;

import io.github.nbcss.createfactorycontroller.content.block.DashboardAssemblyManager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class FactoryDashboardPanelBlockItem extends BlockItem {
    public FactoryDashboardPanelBlockItem(Block block, Properties properties) { super(block, properties); }
    @Override protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        return super.canPlace(context, state) && DashboardAssemblyManager.canPlace(context.getLevel(),
                context.getClickedPos(), state, false, context.getPlayer());
    }
}
