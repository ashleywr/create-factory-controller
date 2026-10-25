package io.github.nbcss.createfactorycontroller.registry;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlock;
import io.github.nbcss.createfactorycontroller.content.block.FactoryDashboardPanelBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CFCBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CreateFactoryController.MODID);

    public static final DeferredBlock<FactoryControllerBlock> FACTORY_CONTROLLER =
            BLOCKS.register("factory_controller", () ->
                    new FactoryControllerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.TERRACOTTA_YELLOW)
                            .strength(300.0f, 1200.0f)
                            .sound(SoundType.WOOD)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    public static final DeferredBlock<FactoryDashboardPanelBlock> FACTORY_DASHBOARD_PANEL =
            BLOCKS.register("factory_dashboard_panel", () -> new FactoryDashboardPanelBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY).strength(3.0f, 6.0f).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion()));

    private CFCBlocks() {}

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
