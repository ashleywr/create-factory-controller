package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.nbcss.createfactorycontroller.ClientConfig;
import io.github.nbcss.createfactorycontroller.content.block.DashboardTile;
import io.github.nbcss.createfactorycontroller.content.block.DashboardOrientation;
import io.github.nbcss.createfactorycontroller.content.client.panel.FactoryControllerClientBoards;
import io.github.nbcss.createfactorycontroller.content.client.panel.DashboardRenderDiagnostics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One BER per cabinet. Vanilla chunk and frustum culling remain tile-sized. */
public class FactoryDashboardRenderer<T extends BlockEntity & DashboardTile> implements BlockEntityRenderer<T> {
    public FactoryDashboardRenderer(BlockEntityRendererProvider.Context context) { DashboardRenderDiagnostics.rendererConstructed(); }
    @Override public void render(T tile, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        DashboardRenderDiagnostics.renderStarted(tile);
        if (tile.getLevel() == null) return;
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(Axis.YP.rotationDegrees(DashboardOrientation.of(tile.getBlockState()).renderYawDegrees()));
        pose.translate(-.5, .5, .501);
        pose.scale(1f / PanelSurface.PIXELS_PER_BLOCK, -1f / PanelSurface.PIXELS_PER_BLOCK, 1f / PanelSurface.PIXELS_PER_BLOCK);
        var data = tile.dashboard();
        var board = FactoryControllerClientBoards.touch(tile);
        DashboardLod detail = board == null ? DashboardLod.FULL : DashboardWorldView.editingDetail(tile, board,
                DashboardWorldView.of(tile, board, net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()).detail());
        // Cabinet seams/status remain visible at every tier; only instruments are simplified.
        WorldPanelCanvasGraphics graphics = new WorldPanelCanvasGraphics(pose, buffers, light);
        // Connected edges retain only the fine seam; outside edges get an industrial perimeter bezel.
        for (int edge = 0; edge < 4; edge++) {
            int width = (data.edges & (1 << edge)) == 0 ? 2 : 1;
            switch (edge) {
                case 0 -> graphics.fill(PanelSurface.BACKPLATE, 0, 0, width, 64, 0xFF363C3F);
                case 1 -> graphics.fill(PanelSurface.BACKPLATE, 0, 0, 64, width, 0xFF50585B);
                case 2 -> graphics.fill(PanelSurface.BACKPLATE, 64 - width, 0, 64, 64, 0xFF363C3F);
                default -> graphics.fill(PanelSurface.BACKPLATE, 0, 64 - width, 64, 64, 0xFF232A2D);
            }
        }
        if (board == null || !board.ready || !board.available) {
            graphics.fill(PanelSurface.INDICATOR, 28, 28, 36, 36, board == null ? 0xFFFF5555 : 0xFFFFB84D);
        } else {
            if (tile instanceof io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity) {
                graphics.fill(PanelSurface.INDICATOR, 29, 0, 34, 2, board.state.controllerPowered ? 0xFFFF5555 : 0xFF779188);
                graphics.fill(PanelSurface.INDICATOR, 35, 0, 40, 2, board.state.missingLinkStatuses.isEmpty() ? 0xFF424B50 : 0xFFFFB84D);
            }
            int x0 = (board.state.dashboard.originX + data.tileX * 4) * 16;
            int y0 = (board.state.dashboard.originY + data.tileY * 4) * 16;
            pose.translate(-x0, -y0, 0);
            FactoryControllerCanvasRenderer.tile(board, data.tileX, data.tileY,
                    new WorldPanelCanvasGraphics(pose, buffers, light, 1f, detail));
        }
        pose.popPose();
        DashboardRenderDiagnostics.renderCompleted(tile);
    }
    @Override public int getViewDistance() { return ClientConfig.DASHBOARD_RENDER_DISTANCE.get(); }
    @Override public boolean shouldRender(T tile, Vec3 camera) {
        if (tile.getLevel() == null || tile.dashboard().core == null) {
            boolean allowed = BlockEntityRenderer.super.shouldRender(tile, camera);
            DashboardRenderDiagnostics.visibility(tile, allowed);
            return allowed;
        }
        var board = FactoryControllerClientBoards.get(tile.dashboard().core, tile.dashboard().identity);
        // Range is measured once from the nearest cabinet member, not independently per tile.
        boolean allowed = DashboardWorldView.of(tile, board, camera).distanceSquared() < (double) getViewDistance() * getViewDistance();
        DashboardRenderDiagnostics.visibility(tile, allowed);
        return allowed;
    }
    @Override public AABB getRenderBoundingBox(T tile) {
        // Include the raised preview stack without disabling normal tile/frustum culling.
        return new AABB(tile.getBlockPos()).inflate((PanelSurface.PREVIEW_LIFT + PanelSurface.LABEL.depth() + .5f)
                / PanelSurface.PIXELS_PER_BLOCK);
    }
}
