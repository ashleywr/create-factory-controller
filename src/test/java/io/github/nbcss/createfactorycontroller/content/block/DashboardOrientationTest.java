package io.github.nbcss.createfactorycontroller.content.block;

import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DashboardOrientationTest {
    @Test void canvasFrontPointsOutOfTheCabinetForEveryFacing() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var orientation = new DashboardOrientation(facing);
            var front = new Vector3f(0, 0, 1).rotate(Axis.YP.rotationDegrees(orientation.renderYawDegrees()));
            assertDirection(facing, front, "canvas front for " + facing);
        }
    }

    @Test void canvasRightMatchesTopologyAndCursorForEveryFacing() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var orientation = new DashboardOrientation(facing);
            var right = new Vector3f(1, 0, 0).rotate(Axis.YP.rotationDegrees(orientation.renderYawDegrees()));
            assertDirection(orientation.right(), right, "canvas right for " + facing);
        }
    }

    @Test void verticalFacingsCannotSilentlyBecomeAnEastFacingCanvas() {
        assertThrows(IllegalStateException.class, () -> new DashboardOrientation(Direction.UP).renderYawDegrees());
        assertThrows(IllegalStateException.class, () -> new DashboardOrientation(Direction.DOWN).renderYawDegrees());
    }

    private static void assertDirection(Direction expected, Vector3f actual, String message) {
        assertAll(message,
                () -> assertEquals(expected.getStepX(), actual.x, 1e-6f),
                () -> assertEquals(expected.getStepY(), actual.y, 1e-6f),
                () -> assertEquals(expected.getStepZ(), actual.z, 1e-6f));
    }
}
