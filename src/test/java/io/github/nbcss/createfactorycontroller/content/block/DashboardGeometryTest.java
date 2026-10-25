package io.github.nbcss.createfactorycontroller.content.block;

import io.github.nbcss.createfactorycontroller.content.client.panel.DashboardCanvasLayout;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.joml.Vector2i;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DashboardGeometryTest {
    @Test void runtimeOnlyUpdatesKeepTheSameCachedLayoutObjects() {
        var state = new SyncedPanelState(BlockPos.ZERO, UUID.randomUUID());
        for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) state.dashboard.tiles.add(new BlockPos(x, y, 0));
        var layout = new DashboardCanvasLayout(); layout.update(state);
        var cells = layout.cells; var instruments = layout.instruments; var wires = layout.wires;
        assertEquals(1024, cells.size());
        for (int tick = 0; tick < 1000; tick++) {
            state.syncRevision++; state.controllerPowered = !state.controllerPowered;
            layout.update(state);
            assertSame(cells, layout.cells); assertSame(instruments, layout.instruments); assertSame(wires, layout.wires);
        }
        state.dashboard.generation++; state.dashboard.originX = -4;
        layout.update(state);
        assertNotSame(cells, layout.cells);
        assertTrue(layout.cells.contains(new VirtualComponentPosition(-4, 0)));
    }
    @Test void edgeLoopUsesAnOnCabinetQuadrantInsteadOfDisappearing() {
        Set<VirtualComponentPosition> mask = new HashSet<>();
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) mask.add(new VirtualComponentPosition(x, y));
        var corner = new VirtualComponentPosition(0, 0);
        var path = DashboardCanvasLayout.visiblePath(corner, corner, 0, mask, Set.of(corner));
        assertEquals(5, path.size());
        assertTrue(DashboardCanvasLayout.fits(path, mask));
        assertEquals(path.getFirst(), path.getLast());
    }
    @Test void signedOffsetsRoundTripForEveryFacing() {
        BlockPos core = new BlockPos(-17, 80, 31);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var orientation = new DashboardOrientation(facing);
            for (int x = -7; x <= 7; x++) for (int y = -7; y <= 7; y++) {
                BlockPos tile = orientation.position(core, x, y);
                assertEquals(new BlockPos(x, y, 0), orientation.offset(core, tile));
                assertTrue(orientation.coplanar(core, tile));
                assertFalse(orientation.coplanar(core, tile.relative(facing)));
            }
        }
    }
    @Test void yGrowsDownAndNegativeTilesUseFloorDivision() {
        assertEquals(79, new DashboardOrientation(Direction.NORTH).position(new BlockPos(0, 80, 0), 0, 1).getY());
        assertEquals(-1, Math.floorDiv(-1, 4));
        assertEquals(-2, Math.floorDiv(-5, 4));
    }
    @Test void countAndBoundingEnvelopeAreIndependent() {
        Set<BlockPos> tiles = new LinkedHashSet<>();
        for (int x = -4; x < 4; x++) for (int y = -3; y < 5; y++) tiles.add(new BlockPos(x, y, 0));
        assertTrue(DashboardAssemblyManager.withinLimits(tiles, 64, 8));
        assertFalse(DashboardAssemblyManager.withinLimits(tiles, 63, 8));
        tiles.remove(new BlockPos(-4, -3, 0));
        tiles.add(new BlockPos(4, -3, 0));
        assertFalse(DashboardAssemblyManager.withinLimits(tiles, 64, 8));
    }
    @Test void irregularContoursAndInsideCornersHaveCorrectEdges() {
        Set<BlockPos> tiles = Set.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(0, 1, 0));
        assertEquals(4 | 8, DashboardAssemblyManager.edgeMask(tiles, BlockPos.ZERO));
        assertEquals(1, DashboardAssemblyManager.edgeMask(tiles, new BlockPos(1, 0, 0)));
        assertEquals(2, DashboardAssemblyManager.edgeMask(tiles, new BlockPos(0, 1, 0)));
    }
    @Test void membershipRoundTripKeepsIdentityOriginAndMaskButNoBoard() {
        DashboardMembership data = new DashboardMembership();
        data.core = new BlockPos(-16, 70, 18); data.tileX = -2; data.tileY = 1;
        data.generation = 42; data.edges = 7; data.originX = -11; data.originY = 8;
        data.tiles.add(BlockPos.ZERO); data.tiles.add(new BlockPos(-2, 1, 0));
        CompoundTag root = new CompoundTag(); data.write(root);
        DashboardMembership copy = new DashboardMembership(); copy.read(root);
        assertEquals(data.identity, copy.identity); assertEquals(data.core, copy.core);
        assertEquals(data.tiles, copy.tiles); assertEquals(-11, copy.originX); assertEquals(8, copy.originY);
        assertEquals(42, copy.generation); assertFalse(root.contains("Components"));
    }
    @Test void legacyMigrationDoesNotRebaseOrInventMembers() {
        DashboardMembership data = new DashboardMembership();
        UUID identity = data.identity; data.read(new CompoundTag());
        assertEquals(identity, data.identity); assertEquals(0, data.originX); assertEquals(0, data.originY);
        assertTrue(data.tiles.isEmpty()); assertNull(data.core);
    }
    @Test void deterministicRoutingGoesAroundMissingCells() {
        Set<VirtualComponentPosition> mask = Set.of(p(0, 0), p(1, 0), p(2, 0), p(0, 1), p(2, 1), p(0, 2), p(1, 2), p(2, 2));
        var route = DashboardCanvasLayout.route(p(0, 1), p(2, 1), mask, Set.of());
        assertFalse(route.isEmpty()); assertTrue(DashboardCanvasLayout.fits(route, mask));
        assertEquals(route, DashboardCanvasLayout.route(p(0, 1), p(2, 1), mask, Set.of()));
        assertFalse(DashboardCanvasLayout.fits(List.of(new Vector2i(0, 1), new Vector2i(2, 1)), mask));
    }
    @Test void disconnectedSurfacesHaveNoVisualWire() {
        assertTrue(DashboardCanvasLayout.route(p(0, 0), p(2, 0), Set.of(p(0, 0), p(2, 0)), Set.of()).isEmpty());
    }
    @Test void occupiedCellsAreAvoidedWhenAnAlternativeExists() {
        Set<VirtualComponentPosition> mask = new HashSet<>();
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) mask.add(p(x, y));
        var route = DashboardCanvasLayout.route(p(0, 1), p(2, 1), mask, Set.of(p(1, 1)));
        assertTrue(DashboardCanvasLayout.fits(route, mask));
        assertTrue(route.stream().anyMatch(v -> v.y != 1));
    }
    private static VirtualComponentPosition p(int x, int y) { return new VirtualComponentPosition(x, y); }
}
