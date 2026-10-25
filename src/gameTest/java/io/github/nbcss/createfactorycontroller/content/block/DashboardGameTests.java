package io.github.nbcss.createfactorycontroller.content.block;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.gauge.LogicalTubeBehaviour;
import io.github.nbcss.createfactorycontroller.registry.CFCBlocks;
import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Integration tests included in the dev game-test run only, never in the release JAR. */
@GameTestHolder(CreateFactoryController.MODID)
@PrefixGameTestTemplate(false)
public final class DashboardGameTests {
    private static final BlockPos CORE = new BlockPos(6, 4, 3);
    private static BlockState core() { return CFCBlocks.FACTORY_CONTROLLER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH); }
    private static BlockState panel() { return CFCBlocks.FACTORY_DASHBOARD_PANEL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH); }

    @GameTest(template = "dashboard_empty", timeoutTicks = 100)
    public static void frozenSnapshotRoundTripsRegisteredInstrumentTypes(GameTestHelper test) {
        test.setBlock(CORE, core());
        FactoryControllerBlockEntity controller = test.getBlockEntity(CORE);
        int x = 0;
        for (var type : io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry.types()) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(type.items().getFirst());
            if (item == net.minecraft.world.item.Items.AIR) continue;
            var cell = new VirtualComponentPosition(x++, 0);
            controller.components.put(cell, type.create(controller, cell, item, java.util.UUID.randomUUID()));
        }
        controller.customName = "Frozen cabinet"; controller.dashboard().originX = -4;
        var frozen = io.github.nbcss.createfactorycontroller.content.packet.EncodedPanelUpdatePacket.freeze(
                controller.buildFullSnapshot(), test.getLevel().registryAccess());
        controller.customName = "Changed after encoding"; controller.components.clear();
        var buffer = io.github.nbcss.createfactorycontroller.content.component.SyncCodecs.wrap(frozen.body(), test.getLevel().registryAccess());
        try {
            var decoded = io.github.nbcss.createfactorycontroller.content.packet.SyncPanelStatePacket.STREAM_CODEC.decode(buffer);
            test.assertTrue(decoded.components().size() == x && x >= 4, "Every built-in instrument must survive the world snapshot codec");
            test.assertTrue(decoded.controllerName().equals("Frozen cabinet"), "Fan-out packet must not retain mutable board data");
            test.assertTrue(decoded.identity().equals(controller.dashboard().identity), "Assembly identity must survive");
            var membership = new DashboardMembership(); membership.read(decoded.dashboard());
            test.assertTrue(membership.originX == -4, "Shared origin must survive the snapshot");
            test.assertTrue(buffer.readableBytes() == 0, "Codec must consume the complete frame");
        } finally { buffer.release(); }
        test.succeed();
    }

    @GameTest(template = "dashboard_empty", timeoutTicks = 100)
    public static void replacementCoreReclaimsOldExtensionsWithNewIdentity(GameTestHelper test) {
        test.setBlock(CORE, core()); test.setBlock(CORE.west(), panel()); test.setBlock(CORE.west(2), panel());
        test.runAfterDelay(3, () -> {
            FactoryControllerBlockEntity original = test.getBlockEntity(CORE);
            var oldIdentity = original.dashboard().identity;
            test.setBlock(CORE, Blocks.AIR); test.setBlock(CORE, core());
            test.runAfterDelay(3, () -> {
                FactoryControllerBlockEntity replacement = test.getBlockEntity(CORE);
                FactoryDashboardPanelBlockEntity extension = test.getBlockEntity(CORE.west(2));
                test.assertTrue(!replacement.dashboard().identity.equals(oldIdentity), "Replacement needs a fresh identity");
                test.assertTrue(replacement.dashboard().tiles.size() == 3, "Existing extensions must join the replacement");
                test.assertTrue(extension.dashboard().identity.equals(replacement.dashboard().identity), "No stale extension identity");
                test.succeed();
            });
        });
    }

    @GameTest(template = "dashboard_empty", timeoutTicks = 100)
    public static void editingRequiresMatchingReachableIdentitySession(GameTestHelper test) {
        BlockPos localCore = new BlockPos(12, 7, 3);
        test.setBlock(localCore, core());
        for (int i = 1; i <= 7; i++) test.setBlock(localCore.west(i), panel());
        for (int i = 1; i <= 7; i++) test.setBlock(localCore.west(7).below(i), panel());
        test.runAfterDelay(3, () -> {
            FactoryControllerBlockEntity controller = test.getBlockEntity(localCore);
            // Keep the session fixture out of PlayerList: an unnegotiated mock connection
            // cannot receive Create's login/broadcast payloads on a real dedicated server.
            var player = new net.minecraft.server.level.ServerPlayer(test.getLevel().getServer(), test.getLevel(),
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "dashboard-test"),
                    net.minecraft.server.level.ClientInformation.createDefault());
            BlockPos member = test.absolutePos(localCore.west(7).below(7));
            player.setPos(member.getX() + .5, member.getY() + .5, member.getZ() - 1);
            test.assertTrue(ControllerAccess.get(player, controller.getBlockPos()) == null, "No menu must reject mutations");
            player.containerMenu = new FactoryControllerMenu(1, player.getInventory(), controller);
            test.assertTrue(player.distanceToSqr(controller.getBlockPos().getX(), controller.getBlockPos().getY(),
                    controller.getBlockPos().getZ()) > 64, "Fixture must be farther than legacy core-only reach");
            test.assertTrue(ControllerAccess.get(player, controller.getBlockPos()) == controller, "Nearby actual member must allow edits");
            player.setPos(member.getX() + 100, member.getY(), member.getZ());
            test.assertTrue(ControllerAccess.get(player, controller.getBlockPos()) == null, "Remote active menu must reject mutations");
            player.setPos(member.getX() + .5, member.getY() + .5, member.getZ() - 1);
            test.setBlock(localCore, Blocks.AIR); test.setBlock(localCore, core());
            test.assertTrue(ControllerAccess.get(player, controller.getBlockPos()) == null, "Replaced core must invalidate old session");
            player.containerMenu = player.inventoryMenu;
            player.discard();
            test.succeed();
        });
    }

    @GameTest(template = "dashboard_empty", timeoutTicks = 100)
    public static void preservedSetupKeepsOriginButCreatesFreshAssemblyIdentity(GameTestHelper test) {
        test.setBlock(CORE, core());
        FactoryControllerBlockEntity controller = test.getBlockEntity(CORE);
        controller.dashboard().originX = -8; controller.dashboard().originY = 4;
        var cell = new VirtualComponentPosition(1, 2);
        controller.components.put(cell, new LogicalTubeBehaviour(controller, cell, AllItems.ELECTRON_TUBE.get()));
        var setup = controller.writeSetup(test.getLevel().registryAccess());
        test.setBlock(CORE.east(3), core());
        FactoryControllerBlockEntity copy = test.getBlockEntity(CORE.east(3));
        copy.applySetup(setup, test.getLevel().registryAccess());
        test.assertTrue(copy.componentAt(cell) != null, "Preserved components must retain their coordinates");
        test.assertTrue(copy.dashboard().originX == -8 && copy.dashboard().originY == 4, "Shared display origin must survive");
        test.assertTrue(!copy.dashboard().identity.equals(controller.dashboard().identity), "Setup must not copy assembly identity");
        test.succeed();
    }

    @GameTest(template = "dashboard_empty", timeoutTicks = 100)
    public static void ninthColumnIsRejectedAtEightBlockEnvelope(GameTestHelper test) {
        BlockPos localCore = new BlockPos(12, 7, 3);
        test.setBlock(localCore, core());
        for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++)
            if (x != 0 || y != 0) test.setBlock(localCore.west(x).below(y), panel());
        test.runAfterDelay(3, () -> {
            FactoryControllerBlockEntity controller = test.getBlockEntity(localCore);
            test.assertTrue(controller.dashboard().tiles.size() == 64, "A complete 8 by 8 assembly should be valid");
            test.assertTrue(!DashboardAssemblyManager.canPlace(test.getLevel(), test.absolutePos(localCore.west(8)), panel(), false, null),
                    "The ninth column must fail before placement");
            test.succeed();
        });
    }

    @GameTest(template = "dashboard_empty", timeoutTicks = 100)
    public static void expansionDoesNotMoveComponents(GameTestHelper test) {
        test.setBlock(CORE, core());
        FactoryControllerBlockEntity controller = test.getBlockEntity(CORE);
        VirtualComponentPosition cell = new VirtualComponentPosition(1, 2);
        controller.components.put(cell, new LogicalTubeBehaviour(controller, cell, AllItems.ELECTRON_TUBE.get()));
        test.setBlock(CORE.west(), panel()); test.setBlock(CORE.east(), panel());
        test.setBlock(CORE.above(), panel()); test.setBlock(CORE.below(), panel());
        test.runAfterDelay(3, () -> {
            test.assertTrue(controller.dashboard().tiles.size() == 5, "Five cabinet members expected");
            test.assertTrue(controller.componentAt(cell) != null, "Expansion must retain the original cell");
            test.assertTrue(controller.dashboard().tiles.contains(new BlockPos(-1, 0, 0)), "Signed left offset expected");
            test.assertTrue(controller.dashboard().tiles.contains(new BlockPos(0, -1, 0)), "Signed upper offset expected");
            test.succeed();
        });
    }
    @GameTest(template = "dashboard_empty", timeoutTicks = 100)
    public static void removalHidesWithoutDeletingAndReplacementReveals(GameTestHelper test) {
        test.setBlock(CORE, core()); test.setBlock(CORE.west(), panel());
        FactoryControllerBlockEntity controller = test.getBlockEntity(CORE);
        VirtualComponentPosition cell = new VirtualComponentPosition(5, 0);
        controller.components.put(cell, new LogicalTubeBehaviour(controller, cell, AllItems.ELECTRON_TUBE.get()));
        test.runAfterDelay(3, () -> {
            test.assertTrue(controller.dashboard().tiles.size() == 2, "Panel should join");
            test.setBlock(CORE.west(), Blocks.AIR);
            test.runAfterDelay(3, () -> {
                test.assertTrue(controller.dashboard().tiles.size() == 1, "Removed tile should leave mask");
                test.assertTrue(controller.componentAt(cell) != null, "Hidden component must survive");
                test.setBlock(CORE.west(), panel());
                test.runAfterDelay(3, () -> {
                    test.assertTrue(controller.dashboard().tiles.size() == 2, "Replacement should reveal same cells");
                    test.assertTrue(controller.componentAt(cell) != null, "Replacement must not duplicate or delete");
                    test.succeed();
                });
            });
        });
    }
    @GameTest(template = "dashboard_empty", timeoutTicks = 100)
    public static void detachedBranchIsUnlinked(GameTestHelper test) {
        test.setBlock(CORE, core()); test.setBlock(CORE.west(), panel()); test.setBlock(CORE.west(2), panel());
        test.runAfterDelay(3, () -> {
            test.setBlock(CORE.west(), Blocks.AIR);
            test.runAfterDelay(3, () -> {
                FactoryDashboardPanelBlockEntity detached = test.getBlockEntity(CORE.west(2));
                test.assertTrue(detached.dashboard().core == null, "Disconnected branch must be unlinked");
                test.setBlock(CORE.west(), panel());
                test.runAfterDelay(3, () -> {
                    FactoryControllerBlockEntity controller = test.getBlockEntity(CORE);
                    test.assertTrue(controller.dashboard().tiles.size() == 3, "Restored bridge must reconnect branch");
                    test.succeed();
                });
            });
        });
    }
    @GameTest(template = "dashboard_empty", timeoutTicks = 100)
    public static void bridgeBetweenCoresIsRejectedBeforePlacement(GameTestHelper test) {
        test.setBlock(CORE, core()); test.setBlock(CORE.west(2), core());
        test.runAfterDelay(3, () -> {
            boolean allowed = DashboardAssemblyManager.canPlace(test.getLevel(), test.absolutePos(CORE.west()), panel(), false, null);
            test.assertTrue(!allowed, "A bridge between two cores must be rejected");
            test.assertTrue(test.getBlockState(CORE.west()).isAir(), "Validation must not place or consume anything");
            test.succeed();
        });
    }
    @GameTest(template = "dashboard_empty", timeoutTicks = 100)
    public static void cornerAndBackToBackPanelsStaySeparate(GameTestHelper test) {
        test.setBlock(CORE, core());
        test.setBlock(CORE.west(), panel().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        test.setBlock(CORE.north(), panel().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        test.runAfterDelay(3, () -> {
            FactoryControllerBlockEntity controller = test.getBlockEntity(CORE);
            test.assertTrue(controller.dashboard().tiles.size() == 1, "Only coplanar, equally facing members join");
            test.succeed();
        });
    }
}
