package io.github.nbcss.createfactorycontroller.content.render;

import io.github.nbcss.createfactorycontroller.content.block.DashboardOrientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DashboardWorldViewTest {
    @Test void allCabinetMembersShareRangeEvenWhenFarEdgesExceedTheLimit() {
        var offsets = new LinkedHashSet<BlockPos>();
        for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) offsets.add(new BlockPos(x, y, 0));
        var orientation = new DashboardOrientation(Direction.SOUTH);
        Vec3 camera = new Vec3(.5, .5, 32);
        var geometry = DashboardWorldView.geometry(BlockPos.ZERO, orientation, offsets, camera);
        assertTrue(geometry.distanceSquared() < 32 * 32);
        assertTrue(camera.distanceToSqr(Vec3.atCenterOf(orientation.position(BlockPos.ZERO, 7, 7))) > 32 * 32);
        var reverse = new LinkedHashSet<BlockPos>();
        offsets.stream().toList().reversed().forEach(reverse::add);
        assertEquals(geometry, DashboardWorldView.geometry(BlockPos.ZERO, orientation, reverse, camera));
        assertTrue(DashboardWorldView.geometry(BlockPos.ZERO, orientation, offsets, new Vec3(.5, .5, 33)).distanceSquared() > 32 * 32);
    }

    @Test void assemblyCenterFollowsEveryHorizontalOrientation() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var orientation = new DashboardOrientation(facing);
            var core = new BlockPos(100, -40, 200);
            var geometry = DashboardWorldView.geometry(core, orientation,
                    Set.of(BlockPos.ZERO, new BlockPos(2, 2, 0)), Vec3.ZERO);
            Vec3 expected = Vec3.atCenterOf(core).add(orientation.right().getStepX(), -1, orientation.right().getStepZ())
                    .add(facing.getStepX() * .501, 0, facing.getStepZ() * .501);
            assertEquals(expected, geometry.center());
        }
    }

    @Test void holesDoNotExtendTheRangeFromAnImaginaryMember() {
        var geometry = DashboardWorldView.geometry(BlockPos.ZERO, new DashboardOrientation(Direction.SOUTH),
                Set.of(new BlockPos(-7, 0, 0), new BlockPos(7, 0, 0)), new Vec3(.5, .5, 32));
        assertTrue(geometry.distanceSquared() > 32 * 32);
    }
}
