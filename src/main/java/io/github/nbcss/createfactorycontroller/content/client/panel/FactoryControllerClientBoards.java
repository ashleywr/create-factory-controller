package io.github.nbcss.createfactorycontroller.content.client.panel;

import io.github.nbcss.createfactorycontroller.content.block.DashboardTile;
import io.github.nbcss.createfactorycontroller.content.block.SyncedPanelState;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.gui.widget.ComponentWidgetRegistry;
import io.github.nbcss.createfactorycontroller.content.gui.widget.VirtualComponentWidget;
import io.github.nbcss.createfactorycontroller.content.packet.RequestDashboardStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** Session-only client mirrors. No block entity or dedicated-server code owns render objects. */
public final class FactoryControllerClientBoards {
    private record Key(ResourceLocation dimension, BlockPos core, UUID identity) {}
    private static final Map<Key, Board> BOARDS = new HashMap<>();
    private static ResourceLocation currentDimension;
    private FactoryControllerClientBoards() {}
    public static void clear() { BOARDS.clear(); currentDimension = null; }
    /** Read-only diagnostic lookup: must not create mirrors or trigger subscriptions. */
    public static Board peek(BlockPos pos, UUID identity) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : BOARDS.get(new Key(level.dimension().location(), pos, identity));
    }
    public static Board get(BlockPos pos, UUID identity) {
        var level = Objects.requireNonNull(Minecraft.getInstance().level);
        enterDimension(level.dimension().location());
        return BOARDS.computeIfAbsent(new Key(level.dimension().location(), pos.immutable(), identity), k -> new Board(pos, identity));
    }
    private static void enterDimension(ResourceLocation dimension) {
        if (!dimension.equals(currentDimension)) { BOARDS.clear(); currentDimension = dimension; }
    }
    public static Board touch(DashboardTile tile) {
        var data = tile.dashboard();
        if (data.core == null) return null;
        Board board = get(data.core, data.identity);
        long previousSeen = board.lastSeen;
        board.lastSeen = System.currentTimeMillis();
        // Assembly-wide rendering can include a far edge beyond the subscription radius. Request
        // through the nearest loaded member, not whichever tile happened to render last.
        var player = Minecraft.getInstance().player;
        if (player == null || board.lastSeen - previousSeen > 2_000 || !tile.getLevel().hasChunkAt(board.member)
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(tile.getBlockPos()))
                    < player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(board.member)))
            board.member = tile.getBlockPos();
        if (!board.ready || !board.available || board.lastSeen - previousSeen > 2_000 || board.lastSeen - board.lastRequest > 10_000) board.request();
        return board;
    }
    public static void tick() {
        var level = Minecraft.getInstance().level;
        if (level == null) { clear(); return; }
        enterDimension(level.dimension().location());
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.containerMenu
                instanceof io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu menu)
            get(menu.controllerPos, menu.controllerIdentity).lastSeen = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        BOARDS.entrySet().removeIf(e -> now - e.getValue().lastSeen > 60_000);
        for (Board board : BOARDS.values()) board.widgets.values().forEach(VirtualComponentWidget::tick);
    }
    public static final class Board {
        public final SyncedPanelState state;
        public final Map<VirtualComponentPosition, VirtualComponentWidget> widgets = new LinkedHashMap<>();
        public boolean ready;
        public boolean available = true;
        public BlockPos member;
        public long lastSeen = System.currentTimeMillis(), lastRequest;
        public final Set<VirtualComponentPosition> selection = new LinkedHashSet<>();
        public VirtualComponentPosition hover;
        public final DashboardCanvasLayout layout = new DashboardCanvasLayout();
        public PanelInteractionController interaction;
        public final io.github.nbcss.createfactorycontroller.content.render.DashboardLod.State lod =
                new io.github.nbcss.createfactorycontroller.content.render.DashboardLod.State();
        public io.github.nbcss.createfactorycontroller.content.render.DashboardWorldView.View view;
        public long viewFrame = Long.MIN_VALUE;
        public net.minecraft.world.phys.Vec3 viewCamera;
        public Board(BlockPos core, UUID identity) { state = new SyncedPanelState(core, identity); member = core; }
        public void changed() {
            view = null;
            Map<VirtualComponentPosition, VirtualComponentWidget> previous = new HashMap<>(widgets);
            widgets.clear();
            for (var component : state.components) {
                var old = previous.get(component.position());
                var widget = old != null && old.behaviour() == component ? old : ComponentWidgetRegistry.create(component, old);
                if (widget != null) widgets.put(component.position(), widget);
            }
            lastSeen = System.currentTimeMillis();
        }
        public void request() {
            long now = System.currentTimeMillis();
            if (now - lastRequest < 1_000) return; // retries recover dropped/rate-limited requests
            lastRequest = now;
            DashboardRenderDiagnostics.request(state.controllerPos, state.identity);
            PacketDistributor.sendToServer(new RequestDashboardStatePacket(member, state.identity));
        }
    }
}
