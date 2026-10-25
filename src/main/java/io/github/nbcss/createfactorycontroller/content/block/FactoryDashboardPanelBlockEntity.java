package io.github.nbcss.createfactorycontroller.content.block;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class FactoryDashboardPanelBlockEntity extends SmartBlockEntity implements DashboardTile {
    private final DashboardMembership dashboard = new DashboardMembership();
    public FactoryDashboardPanelBlockEntity(BlockPos pos, BlockState state) {
        this(io.github.nbcss.createfactorycontroller.registry.CFCBlockEntityTypes.FACTORY_DASHBOARD_PANEL.get(), pos, state);
    }
    public FactoryDashboardPanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
    @Override public DashboardMembership dashboard() { return dashboard; }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
    @Override public void onLoad() { super.onLoad(); DashboardAssemblyManager.schedule(level, worldPosition); }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket); dashboard.write(tag);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket); dashboard.read(tag);
    }
}
