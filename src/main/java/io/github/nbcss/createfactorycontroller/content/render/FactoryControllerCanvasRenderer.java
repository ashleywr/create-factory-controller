package io.github.nbcss.createfactorycontroller.content.render;

import io.github.nbcss.createfactorycontroller.content.client.panel.FactoryControllerClientBoards;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.gui.widget.VirtualComponentWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2d;

import java.util.List;
import java.util.Set;

/** Shared instruments with a tile-local index and a structurally cached world cable layout. */
public final class FactoryControllerCanvasRenderer {
    private static final ResourceLocation TARGET = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/target/regular");
    private FactoryControllerCanvasRenderer() {}
    /** Preview artwork uses the same physical body and surface contract as live instruments. */
    public static void ghost(VirtualComponentWidget widget, WorldPanelCanvasGraphics graphics,
                             Set<VirtualComponentPosition> occupiedCells) {
        ghostBody(widget, graphics);
        ghostArtwork(widget, graphics, occupiedCells);
    }
    public static void ghostBody(VirtualComponentWidget widget, WorldPanelCanvasGraphics graphics) {
        DashboardInstrumentModel.render(graphics, widget.position().x() * 16, widget.position().y() * 16);
    }
    public static void ghostArtwork(VirtualComponentWidget widget, WorldPanelCanvasGraphics graphics,
                                    Set<VirtualComponentPosition> occupiedCells) {
        widget.renderGhost(new Parameters(graphics, new Vector2d(Double.NaN, Double.NaN), false, occupiedCells));
    }
    public record Parameters(PanelCanvasGraphics graphics, Vector2d mousePosition, boolean renderOverlay,
                              Set<VirtualComponentPosition> occupiedCells) implements VirtualComponentWidget.RenderingParameters {
        @Override public void addBatchRenderedItem(ItemStack stack, int x, int y) { graphics.renderItem(stack, x, y); }
    }
    public static void tile(FactoryControllerClientBoards.Board board, int tx, int ty, WorldPanelCanvasGraphics gfx) {
        var state = board.state;
        board.layout.update(state);
        int x0 = (state.dashboard.originX + tx * 4) * 16, y0 = (state.dashboard.originY + ty * 4) * 16;
        BlockPos tile = new BlockPos(tx, ty, 0);
        var instruments = board.layout.instruments.getOrDefault(tile, List.of());
        if (gfx.detail() == DashboardLod.OVERVIEW) {
            overview(board, instruments, gfx, x0, y0);
            return;
        }
        Vector2d mouse = board.interaction == null ? new Vector2d(Double.NaN, Double.NaN) : board.interaction.mousePosition();
        // Chunk-format partial models and entity-format artwork have different buffers. Group the bodies
        // instead of alternating buffers per instrument (which flushes the non-fixed solid buffer).
        for (var position : instruments) if (board.widgets.containsKey(position))
            DashboardInstrumentModel.render(gfx, position.x() * 16, position.y() * 16);
        for (var position : instruments) {
            var widget = board.widgets.get(position);
            if (widget != null) widget.renderBack(new Parameters(gfx, mouse, false, board.layout.cells));
        }
        for (var wire : gfx.detail() == DashboardLod.FULL ? board.layout.wires.getOrDefault(tile, List.of())
                : List.<io.github.nbcss.createfactorycontroller.content.client.panel.DashboardCanvasLayout.Wire>of()) {
            var key = wire.key();
            var connection = state.connectionGraph().get(key.from(), key.to(), key.type());
            if (connection == null) continue;
            int color = connection.getConnectionColor(state);
            long elapsed = connection.getFlashTick(state, net.minecraft.client.Minecraft.getInstance().level.getGameTime());
            boolean animated = elapsed >= 0 && elapsed < 8 && connection.hasFlowAnimation();
            VirtualConnectionRenderer.create(wire.path(), color, animated).drawPath(gfx, x0, y0);
            if (wire.hidden() != null) {
                var end = wire.hidden().equals(key.to()) ? wire.path().getLast() : wire.path().getFirst();
                if (end.x * 16 >= x0 && end.x * 16 < x0 + 64 && end.y * 16 >= y0 && end.y * 16 < y0 + 64)
                    gfx.fill(PanelSurface.CONNECTION_MARK, end.x * 16 + 6, end.y * 16 + 6, end.x * 16 + 10, end.y * 16 + 10, 0xFFFFB84D);
            }
        }
        for (var position : instruments) {
            var widget = board.widgets.get(position);
            if (widget == null) continue;
            int x = position.x() * 16, y = position.y() * 16;
            widget.renderFront(new Parameters(gfx, mouse, position.equals(board.hover), board.layout.cells));
            if (gfx.detail() == DashboardLod.FULL) widget.renderOverlay(new Parameters(gfx, mouse, true, board.layout.cells));
            if (board.selection.contains(position) || position.equals(board.hover))
                gfx.sprite(PanelSurface.SELECTION, TARGET, x, y, 16, 16, board.selection.contains(position) ? 0xFFFFCC66 : 0xFFFFFFFF, true);
        }
        if (board.hover != null && !instruments.contains(board.hover) && board.layout.tileOf(board.hover).equals(tile)) {
            gfx.sprite(PanelSurface.SELECTION, TARGET, board.hover.x() * 16, board.hover.y() * 16, 16, 16, 0xFFFFFFFF, true);
        }
        if (board.interaction != null) board.interaction.renderPreview(gfx, x0, y0);
    }

    /** Quiet tile aggregate: no glyphs, item models, animated cables or individual micro-details. */
    private static void overview(FactoryControllerClientBoards.Board board, List<VirtualComponentPosition> instruments,
                                 WorldPanelCanvasGraphics gfx, int x0, int y0) {
        if (instruments.isEmpty()) return;
        boolean active = false, warning = false;
        for (var position : instruments) {
            var widget = board.widgets.get(position);
            if (widget == null) continue;
            var behaviour = widget.behaviour();
            if (behaviour instanceof io.github.nbcss.createfactorycontroller.content.component.gauge.VirtualGaugeBehaviour gauge) {
                warning |= gauge.isMissingAddress() || gauge.isRedstonePaused() || gauge.waitingForNetwork;
                active |= gauge.isActive() && gauge.satisfied;
            } else if (behaviour instanceof io.github.nbcss.createfactorycontroller.content.component.gauge.LogicalTubeBehaviour logic) {
                active |= logic.isPowered();
            } else if (behaviour instanceof io.github.nbcss.createfactorycontroller.content.component.VirtualRedstoneLinkBehaviour link) {
                active |= link.isPowered();
            }
        }
        gfx.fill(PanelSurface.FACE, x0 + 8, y0 + 28, x0 + 56, y0 + 36, 0xFF232A2D);
        gfx.fill(PanelSurface.INDICATOR, x0 + 10, y0 + 30, x0 + 54, y0 + 34,
                warning ? 0xFFFFB84D : active ? 0xFF9EFF7F : 0xFF66756E);
    }
}
