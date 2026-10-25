package io.github.nbcss.createfactorycontroller.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The single coordinate convention shared by topology, rendering and input. Y grows down. */
public record DashboardOrientation(Direction facing) {
    public static final int CELLS = 4;
    public static final int PIXELS = 64;

    public static DashboardOrientation of(BlockState state) {
        return new DashboardOrientation(state.getValue(HorizontalDirectionalBlock.FACING));
    }

    public Direction right() { return facing.getCounterClockWise(); }
    /** Positive-Y rotation of a canvas whose local front is +Z and right is +X. */
    public float renderYawDegrees() {
        return switch (facing) {
            case SOUTH -> 0;
            case EAST -> 90;
            case NORTH -> 180;
            case WEST -> 270;
            default -> throw new IllegalStateException("Dashboard facing must be horizontal: " + facing);
        };
    }
    public BlockPos position(BlockPos core, int x, int y) {
        return core.relative(right(), x).below(y);
    }
    public BlockPos offset(BlockPos core, BlockPos tile) {
        BlockPos delta = tile.subtract(core);
        return new BlockPos(delta.getX() * right().getStepX() + delta.getZ() * right().getStepZ(),
                -delta.getY(), 0);
    }
    public boolean coplanar(BlockPos a, BlockPos b) {
        return (b.getX() - a.getX()) * facing.getStepX() + (b.getZ() - a.getZ()) * facing.getStepZ() == 0;
    }
    public Direction[] edges() { return new Direction[]{right().getOpposite(), Direction.UP, right(), Direction.DOWN}; }
}
