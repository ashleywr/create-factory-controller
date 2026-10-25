package io.github.nbcss.createfactorycontroller.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface DashboardTile {
    DashboardMembership dashboard();
    BlockPos getBlockPos();
    BlockState getBlockState();
    Level getLevel();
    void setChanged();
    void sendData();
}
