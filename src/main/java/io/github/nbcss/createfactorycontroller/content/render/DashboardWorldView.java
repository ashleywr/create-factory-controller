package io.github.nbcss.createfactorycontroller.content.render;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.ClientConfig;
import io.github.nbcss.createfactorycontroller.content.block.DashboardOrientation;
import io.github.nbcss.createfactorycontroller.content.block.DashboardTile;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.client.panel.FactoryControllerClientBoards;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Set;

/** One view decision per board/frame. Chunk visibility and tile-sized frustum culling remain vanilla. */
@EventBusSubscriber(modid = CreateFactoryController.MODID, value = Dist.CLIENT)
public final class DashboardWorldView {
    public record View(DashboardLod detail, double distanceSquared) {}
    public record Geometry(Vec3 center, double distanceSquared) {}
    private static Matrix4f projection;
    private static Vec3 camera;
    private static long frame;
    private DashboardWorldView() {}

    @SubscribeEvent public static void capture(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        io.github.nbcss.createfactorycontroller.content.client.panel.DashboardRenderDiagnostics.cameraCaptured(false);
        projection = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        camera = event.getCamera().getPosition();
        frame++;
    }

    public static View of(DashboardTile tile, FactoryControllerClientBoards.Board board, Vec3 cameraPosition) {
        // Camera comparison also handles shader passes which do not emit a new stage event.
        if (board.view != null && board.viewFrame == frame && cameraPosition.equals(board.viewCamera)) return board.view;
        var membership = board.ready ? board.state.dashboard : tile.dashboard();
        if (!board.ready && tile.getLevel().getBlockEntity(board.state.controllerPos) instanceof FactoryControllerBlockEntity core
                && core.dashboard().identity.equals(board.state.identity)) membership = core.dashboard();
        Set<BlockPos> offsets = membership.tiles.isEmpty() ? Set.of(new BlockPos(tile.dashboard().tileX, tile.dashboard().tileY, 0))
                : membership.tiles;
        var orientation = DashboardOrientation.of(tile.getBlockState());
        var geometry = geometry(board.state.controllerPos, orientation, offsets, cameraPosition);
        var mc = Minecraft.getInstance();
        double pixels = projection == null || camera == null ? 24 : DashboardLod.projectedPixels(projection,
                geometry.center().subtract(cameraPosition).toVector3f(),
                new Vector3f(orientation.right().getStepX(), 0, orientation.right().getStepZ()),
                mc.getWindow().getWidth(), mc.getWindow().getHeight());
        // An extension has no full topology before its snapshot arrives. Do not cache its local
        // distance as the range of the whole board: a far tile could otherwise suppress the near
        // tile's first render/request, preventing that snapshot from ever being requested.
        board.viewFrame = membership.tiles.isEmpty() ? Long.MIN_VALUE : frame;
        board.viewCamera = cameraPosition;
        board.view = new View(ClientConfig.DASHBOARD_LOD.get() ? board.lod.update(pixels) : DashboardLod.FULL, geometry.distanceSquared());
        return board.view;
    }

    /** Shared assembly anchor/range calculation, with no dependency on which BER is visited first. */
    public static Geometry geometry(BlockPos core, DashboardOrientation orientation, Set<BlockPos> offsets, Vec3 cameraPosition) {
        if (offsets.isEmpty()) offsets = Set.of(BlockPos.ZERO);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        double distance = Double.POSITIVE_INFINITY;
        for (BlockPos offset : offsets) {
            minX = Math.min(minX, offset.getX()); maxX = Math.max(maxX, offset.getX());
            minY = Math.min(minY, offset.getY()); maxY = Math.max(maxY, offset.getY());
            distance = Math.min(distance, cameraPosition.distanceToSqr(Vec3.atCenterOf(
                    orientation.position(core, offset.getX(), offset.getY()))));
        }
        Vec3 center = Vec3.atCenterOf(core)
                .add(orientation.right().getStepX() * (minX + maxX) * .5, -(minY + maxY) * .5,
                        orientation.right().getStepZ() * (minX + maxX) * .5)
                .add(orientation.facing().getStepX() * .501, 0, orientation.facing().getStepZ() * .501);
        return new Geometry(center, distance);
    }

    public static DashboardLod editingDetail(DashboardTile tile, FactoryControllerClientBoards.Board board, DashboardLod detail) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return detail;
        boolean editing = board.interaction != null && mc.screen != null
                && player.containerMenu instanceof io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu menu
                && menu.controllerPos.equals(board.state.controllerPos) && menu.controllerIdentity.equals(board.state.identity);
        return detail.forEditing(editing, player.getEyePosition().distanceToSqr(Vec3.atCenterOf(tile.getBlockPos())),
                player.blockInteractionRange());
    }
}
