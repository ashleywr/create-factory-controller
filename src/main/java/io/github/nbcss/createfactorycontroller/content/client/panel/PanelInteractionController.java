package io.github.nbcss.createfactorycontroller.content.client.panel;

import io.github.nbcss.createfactorycontroller.CreateFactoryControllerClient;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintPlacement;
import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.gui.screen.blueprint.BlueprintPlaceScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.ControllerEditorHost;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.states.BatchMoveState;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.states.ConnectionModeState;
import io.github.nbcss.createfactorycontroller.content.gui.widget.ConnectionWidget;
import io.github.nbcss.createfactorycontroller.content.packet.*;
import io.github.nbcss.createfactorycontroller.content.render.PanelSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** World gestures reuse the established connection and batch-move validators and mutation packets. */
public final class PanelInteractionController {
    public final FactoryControllerClientBoards.Board board;
    public final FactoryControllerMenu menu;
    public final ConnectionModeState connection = new ConnectionModeState();
    public final BatchMoveState move = new BatchMoveState();
    public VirtualComponentPosition relocate, rectangleStart, rectangleEnd;
    public BlueprintPlacement blueprint;
    public ConnectionWidget hoveredWire;
    private DashboardCursor.Hit hit;
    private io.github.nbcss.createfactorycontroller.content.gui.GhostPreview movePreview;
    private Set<VirtualComponentPosition> previewSources = Set.of();
    private io.github.nbcss.createfactorycontroller.content.gui.widget.VirtualComponentWidget attachPreview;
    private net.minecraft.world.item.Item previewItem;
    private VirtualComponentPosition previewCell;
    public PanelInteractionController(FactoryControllerMenu menu) {
        this.menu = menu; board = FactoryControllerClientBoards.get(menu.controllerPos, menu.controllerIdentity);
    }
    public void update(DashboardCursor.Hit hit) {
        this.hit = hit;
        board.hover = hit == null ? null : hit.cell();
        hoveredWire = null;
        if (hit == null || !board.ready) return;
        board.layout.update(board.state);
        if (move.isActive()) move.update(hit.cell());
        if (rectangleStart != null) rectangleEnd = hit.cell();
        var tile = board.layout.tileOf(hit.cell());
        for (var wire : board.layout.wires.getOrDefault(tile, List.of())) {
            var key = wire.key(); var edge = board.state.connectionGraph().get(key.from(), key.to(), key.type());
            if (edge == null) continue;
            ConnectionWidget widget = new ConnectionWidget(edge, wire.path());
            if (widget.hitTest(hit.pixelX(), hit.pixelY())) { hoveredWire = widget; break; }
        }
    }
    public boolean click(ControllerEditorHost host, int button, UUID network, boolean selecting) {
        if (hit == null || !board.ready || !board.available || (button != 0 && button != 1)) return false;
        VirtualComponentPosition cell = hit.cell();
        var widget = board.widgets.get(cell);
        if (blueprint != null) {
            var anchor = blueprint.anchorFor(cell);
            if (blueprint.fits(anchor, menu) && blueprint.info().placements().stream()
                    .allMatch(p -> board.layout.cells.contains(BlueprintPlacement.cellFor(p, anchor))))
                Minecraft.getInstance().setScreen(new BlueprintPlaceScreen(host, blueprint, anchor));
            return true;
        }
        if (connection.isActive()) {
            var completion = connection.finish(menu, cell, widget == null ? null : widget.behaviour());
            var result = completion.result();
            if (result != null && result.ok()) PacketDistributor.sendToServer(new AddConnectionPacket(menu.controllerPos,
                    result.type().name(), result.source(), result.sink(), completion.bendMode()));
            return true;
        }
        if (relocate != null) {
            if (widget == null && !FactoryControllerBlockEntity.isOutBoard(cell))
                PacketDistributor.sendToServer(new MoveComponentPacket(menu.controllerPos, relocate, cell));
            relocate = null; return true;
        }
        if (Screen.hasShiftDown()) {
            if (hoveredWire != null) {
                var c = hoveredWire.connection;
                PacketDistributor.sendToServer(new RemoveConnectionPacket(menu.controllerPos, c.from, c.to, c.type.name()));
            } else if (widget != null) {
                Collection<VirtualComponentPosition> targets = board.selection.contains(cell) ? List.copyOf(board.selection) : List.of(cell);
                for (var position : targets) PacketDistributor.sendToServer(new RemoveComponentPacket(menu.controllerPos, position));
                board.selection.clear();
            }
            return true;
        }
        if (selecting && menu.getCarried().isEmpty()) {
            rectangleStart = cell; rectangleEnd = cell;
            return true;
        }
        if (widget == null && !menu.getCarried().isEmpty()) {
            if (ComponentRegistry.containsItem(menu.getCarried()) && !FactoryControllerBlockEntity.isOutBoard(cell))
                PacketDistributor.sendToServer(new AttachComponentPacket(menu.controllerPos, cell, network));
            return true;
        }
        if (widget != null && button == 0 && board.selection.contains(cell) && menu.getCarried().isEmpty()) {
            move.begin(cell); return true;
        }
        if (widget != null && hoveredWire == null) return widget.onClick(host, menu.getCarried(), hit.pixelX(), hit.pixelY(), button);
        if (widget == null && menu.getCarried().isEmpty()) {
            board.selection.clear(); rectangleStart = cell; rectangleEnd = cell; return true;
        }
        return true;
    }
    public void release() {
        if (rectangleStart != null && rectangleEnd != null) {
            int minX = Math.min(rectangleStart.x(), rectangleEnd.x()), maxX = Math.max(rectangleStart.x(), rectangleEnd.x());
            int minY = Math.min(rectangleStart.y(), rectangleEnd.y()), maxY = Math.max(rectangleStart.y(), rectangleEnd.y());
            board.widgets.keySet().stream().filter(p -> p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY
                    && board.layout.cells.contains(p)).forEach(board.selection::add);
            rectangleStart = null; rectangleEnd = null;
        }
        if (move.isActive()) {
            boolean visible = board.selection.stream().allMatch(p -> board.layout.cells.contains(move.destinationOf(p)));
            if (!visible || hit == null) { move.cancel(); return; }
            var result = move.finish(board.selection, board.widgets.keySet());
            if (result.request() != null) {
                var request = result.request();
                PacketDistributor.sendToServer(new BatchMoveComponentPacket(menu.controllerPos, request.sources(), request.dx(), request.dy()));
            }
        }
    }
    public boolean cancel() {
        boolean active = connection.isActive() || move.isActive() || relocate != null || blueprint != null || rectangleStart != null;
        connection.clear(); move.cancel(); relocate = null; blueprint = null; rectangleStart = null; rectangleEnd = null;
        return active;
    }
    public boolean key(int key, int scan) {
        if (!board.ready || !board.available) return false;
        VirtualComponentPosition cell = board.hover;
        if (CreateFactoryControllerClient.START_CONNECTION.matches(key, scan) && cell != null && menu.componentAt(cell) != null) {
            cancel(); connection.begin(cell); return true;
        }
        if (CreateFactoryControllerClient.RELOCATE_COMPONENT.matches(key, scan) && cell != null && menu.componentAt(cell) != null) {
            cancel(); relocate = cell; return true;
        }
        if (CreateFactoryControllerClient.CYCLE_ARROW_MODE.matches(key, scan)) {
            if (connection.isActive()) connection.cyclePath(menu, cell, () -> 0);
            else if (hoveredWire != null) {
                var c = hoveredWire.connection;
                PacketDistributor.sendToServer(new CycleConnectionArrowModePacket(menu.controllerPos, c.from, c.to, c.type.name(), 1));
            } else if (cell != null) PacketDistributor.sendToServer(new CycleArrowModePacket(menu.controllerPos, cell));
            return true;
        }
        if (CreateFactoryControllerClient.CYCLE_OPERATION_MODE.matches(key, scan) && cell != null) {
            if (connection.flipLoopDirection(menu, cell, () -> 0)) return true;
            PacketDistributor.sendToServer(new CycleOperationModePacket(menu.controllerPos, cell)); return true;
        }
        return false;
    }
    public void synced() {
        movePreview = null;
        move.reconcile(board.widgets.keySet()).ifPresent(selection -> { board.selection.clear(); board.selection.addAll(selection); });
        board.selection.retainAll(board.widgets.keySet());
    }
    public org.joml.Vector2d mousePosition() {
        return hit == null ? new org.joml.Vector2d(Double.NaN, Double.NaN) : new org.joml.Vector2d(hit.pixelX(), hit.pixelY());
    }
    public void renderPreview(io.github.nbcss.createfactorycontroller.content.render.WorldPanelCanvasGraphics gfx, int x0, int y0) {
        if (board.hover == null || !board.ready || !board.available) return;
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, PanelSurface.PREVIEW_LIFT);
        if (connection.isActive()) {
            var result = connection.resolve(menu, board.hover);
            if (result != null && result.ok()) {
                var path = DashboardCanvasLayout.visiblePath(result.source(), result.sink(), connection.previewBendMode(),
                        board.layout.cells, board.widgets.keySet());
                io.github.nbcss.createfactorycontroller.content.render.VirtualConnectionRenderer.create(
                        path, 0xB0000000 | result.type().color(), false).drawPath(gfx, x0, y0);
            }
        }
        Set<VirtualComponentPosition> sources = move.isActive() ? Set.copyOf(board.selection) : relocate != null ? Set.of(relocate) : Set.of();
        if (!sources.isEmpty()) {
            if (movePreview == null || !sources.equals(previewSources)) {
                movePreview = io.github.nbcss.createfactorycontroller.content.gui.GhostPreview.fromSelection(
                        sources.stream().map(menu::componentAt).filter(Objects::nonNull).toList(), sources, Minecraft.getInstance().level.registryAccess());
                previewSources = sources;
            }
            VirtualComponentPosition delta = move.isActive() ? new VirtualComponentPosition(move.dx(), move.dy())
                    : new VirtualComponentPosition(board.hover.x() - relocate.x(), board.hover.y() - relocate.y());
            movePreview.renderWorld(gfx, delta, board.layout.cells, x0, y0);
        } else if (blueprint != null) {
            blueprint.ghostPreview().renderWorld(gfx, blueprint.anchorFor(board.hover), board.layout.cells, x0, y0);
        } else if (!connection.isActive() && !board.widgets.containsKey(board.hover)
                && ComponentRegistry.containsItem(menu.getCarried()) && !FactoryControllerBlockEntity.isOutBoard(board.hover)
                && owns(board.hover, x0, y0)) {
            if (previewItem != menu.getCarried().getItem() || !board.hover.equals(previewCell)) {
                previewItem = menu.getCarried().getItem(); previewCell = board.hover;
                var component = ComponentRegistry.createFromItem(null, previewCell, previewItem, null);
                attachPreview = component == null ? null : io.github.nbcss.createfactorycontroller.content.gui.widget.ComponentWidgetRegistry.create(component);
            }
            if (attachPreview != null) io.github.nbcss.createfactorycontroller.content.render.FactoryControllerCanvasRenderer.ghost(attachPreview,
                    new io.github.nbcss.createfactorycontroller.content.render.WorldPanelCanvasGraphics(gfx.pose(), gfx.bufferSource(), gfx.light(), .55f),
                    board.layout.cells);
        }
        if (rectangleStart != null && rectangleEnd != null) {
            for (var cell : board.layout.cells) if (cell.x() >= Math.min(rectangleStart.x(), rectangleEnd.x())
                    && cell.x() <= Math.max(rectangleStart.x(), rectangleEnd.x())
                    && cell.y() >= Math.min(rectangleStart.y(), rectangleEnd.y())
                    && cell.y() <= Math.max(rectangleStart.y(), rectangleEnd.y())
                    && owns(cell, x0, y0))
                gfx.sprite(PanelSurface.SELECTION, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/target/regular"),
                        cell.x() * 16, cell.y() * 16, 16, 16, 0xFFFFCC66, true);
        }
        gfx.pose().popPose();
    }
    private static boolean owns(VirtualComponentPosition cell, int x0, int y0) {
        return cell.x() * 16 >= x0 && cell.x() * 16 < x0 + 64 && cell.y() * 16 >= y0 && cell.y() * 16 < y0 + 64;
    }
    public Component prompt() {
        if (blueprint != null) return Component.translatable("createfactorycontroller.dashboard.place_blueprint", blueprint.name());
        if (relocate != null || move.isActive()) return Component.translatable("createfactorycontroller.dashboard.relocate");
        if (connection.isActive()) return Component.translatable("createfactorycontroller.dashboard.connect");
        return Component.translatable("createfactorycontroller.dashboard.controls");
    }
}
