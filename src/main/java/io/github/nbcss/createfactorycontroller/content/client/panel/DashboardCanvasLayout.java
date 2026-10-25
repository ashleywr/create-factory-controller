package io.github.nbcss.createfactorycontroller.content.client.panel;

import io.github.nbcss.createfactorycontroller.content.block.SyncedPanelState;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionKey;
import io.github.nbcss.createfactorycontroller.content.gui.screen.ConnectionPathResolver;
import org.joml.Vector2i;
import net.minecraft.core.BlockPos;

import java.util.*;

/** Geometry-only cache: runtime count/signal changes do not trigger pathfinding. */
public final class DashboardCanvasLayout {
    public record Wire(ConnectionKey key, List<Vector2i> path, VirtualComponentPosition hidden) {}
    private long revision = Long.MIN_VALUE;
    private int generation = Integer.MIN_VALUE, originX, originY;
    public Set<VirtualComponentPosition> cells = Set.of();
    public Map<BlockPos, List<VirtualComponentPosition>> instruments = Map.of();
    public Map<BlockPos, List<Wire>> wires = Map.of();

    public void update(SyncedPanelState state) {
        var dashboard = state.dashboard;
        if (revision == state.structureRevision && generation == dashboard.generation
                && originX == dashboard.originX && originY == dashboard.originY) return;
        revision = state.structureRevision; generation = dashboard.generation;
        originX = dashboard.originX; originY = dashboard.originY;
        Set<VirtualComponentPosition> mask = new LinkedHashSet<>();
        for (BlockPos tile : dashboard.tiles) for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++)
            mask.add(new VirtualComponentPosition(originX + tile.getX() * 4 + x, originY + tile.getY() * 4 + y));
        cells = Collections.unmodifiableSet(mask);
        Map<BlockPos, List<VirtualComponentPosition>> componentIndex = new HashMap<>();
        Set<VirtualComponentPosition> occupied = new HashSet<>();
        for (var component : state.components) {
            occupied.add(component.position());
            if (mask.contains(component.position())) componentIndex.computeIfAbsent(tileOf(component.position()), k -> new ArrayList<>()).add(component.position());
        }
        instruments = componentIndex;
        Map<BlockPos, List<Wire>> wireIndex = new HashMap<>();
        for (Connection connection : state.connectionGraph().connections()) {
            boolean from = mask.contains(connection.from), to = mask.contains(connection.to);
            if (!from && !to) continue;
            VirtualComponentPosition hidden = from && to ? null : from ? connection.to : connection.from;
            List<Vector2i> path;
            if (hidden == null) {
                path = visiblePath(connection.from, connection.to, connection.arrowBendMode, mask, occupied);
            } else {
                VirtualComponentPosition visible = from ? connection.from : connection.to;
                VirtualComponentPosition boundary = mask.stream().filter(p -> isBoundary(p, mask))
                        .min(Comparator.<VirtualComponentPosition>comparingInt(p -> distance(p, hidden))
                                .thenComparingInt(VirtualComponentPosition::y).thenComparingInt(VirtualComponentPosition::x)).orElse(visible);
                path = route(visible, boundary, mask, occupied);
                if (!from) path = new ArrayList<>(path.reversed());
            }
            if (path.isEmpty()) continue;
            Wire wire = new Wire(new ConnectionKey(connection.from, connection.to, connection.type), List.copyOf(path), hidden);
            Set<BlockPos> touched = new HashSet<>();
            for (int i = 1; i < path.size(); i++) {
                Vector2i a = path.get(i - 1), b = path.get(i);
                int length = Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
                for (int j = 0; j <= length; j++)
                    touched.add(tileOf(new VirtualComponentPosition(a.x + Integer.signum(b.x - a.x) * j, a.y + Integer.signum(b.y - a.y) * j)));
            }
            if (path.size() == 1) touched.add(tileOf(new VirtualComponentPosition(path.getFirst().x, path.getFirst().y)));
            for (BlockPos tile : touched) wireIndex.computeIfAbsent(tile, k -> new ArrayList<>()).add(wire);
        }
        wires = wireIndex;
    }
    public BlockPos tileOf(VirtualComponentPosition position) {
        return new BlockPos(Math.floorDiv(position.x() - originX, 4), Math.floorDiv(position.y() - originY, 4), 0);
    }
    private static int distance(VirtualComponentPosition a, VirtualComponentPosition b) { return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()); }
    private static boolean isBoundary(VirtualComponentPosition p, Set<VirtualComponentPosition> mask) {
        return neighbors(p).stream().anyMatch(n -> !mask.contains(n));
    }
    private static List<VirtualComponentPosition> neighbors(VirtualComponentPosition p) {
        return List.of(new VirtualComponentPosition(p.x() - 1, p.y()), new VirtualComponentPosition(p.x(), p.y() - 1),
                new VirtualComponentPosition(p.x() + 1, p.y()), new VirtualComponentPosition(p.x(), p.y() + 1));
    }
    public static boolean fits(List<Vector2i> path, Set<VirtualComponentPosition> mask) {
        for (int i = 1; i < path.size(); i++) {
            Vector2i a = path.get(i - 1), b = path.get(i);
            int length = Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
            for (int j = 0; j <= length; j++) if (!mask.contains(new VirtualComponentPosition(
                    a.x + Integer.signum(b.x - a.x) * j, a.y + Integer.signum(b.y - a.y) * j))) return false;
        }
        return !path.isEmpty() && mask.contains(new VirtualComponentPosition(path.getFirst().x, path.getFirst().y));
    }
    /** Preserve chosen bends where possible; an edge loop can use another on-cabinet quadrant. */
    public static List<Vector2i> visiblePath(VirtualComponentPosition from, VirtualComponentPosition to, int bend,
                                            Set<VirtualComponentPosition> mask, Set<VirtualComponentPosition> occupied) {
        var path = ConnectionPathResolver.resolvePath(from, to, bend, occupied);
        if (fits(path, mask)) return path;
        if (from.equals(to)) {
            for (int quadrant = 0; quadrant < 4; quadrant++) {
                path = ConnectionPathResolver.buildLoopPath(from, quadrant * 2 | (bend < 0 ? 0 : bend & 1));
                if (fits(path, mask)) return path;
            }
            return List.of();
        }
        return route(from, to, mask, occupied);
    }
    public static List<Vector2i> route(VirtualComponentPosition start, VirtualComponentPosition end,
                                      Set<VirtualComponentPosition> mask, Set<VirtualComponentPosition> occupied) {
        List<Vector2i> result = search(start, end, mask, occupied);
        return result.isEmpty() ? search(start, end, mask, Set.of()) : result;
    }
    private static List<Vector2i> search(VirtualComponentPosition start, VirtualComponentPosition end,
                                        Set<VirtualComponentPosition> mask, Set<VirtualComponentPosition> occupied) {
        if (!mask.contains(start) || !mask.contains(end)) return List.of();
        Map<VirtualComponentPosition, VirtualComponentPosition> parents = new HashMap<>();
        Deque<VirtualComponentPosition> queue = new ArrayDeque<>();
        parents.put(start, start); queue.add(start);
        while (!queue.isEmpty() && !parents.containsKey(end)) {
            VirtualComponentPosition p = queue.removeFirst();
            for (VirtualComponentPosition n : neighbors(p)) if (mask.contains(n) && !parents.containsKey(n)
                    && (n.equals(end) || !occupied.contains(n))) { parents.put(n, p); queue.addLast(n); }
        }
        if (!parents.containsKey(end)) return List.of();
        List<Vector2i> route = new ArrayList<>();
        for (VirtualComponentPosition p = end; ; p = parents.get(p)) {
            route.add(new Vector2i(p.x(), p.y())); if (p.equals(start)) break;
        }
        Collections.reverse(route);
        // Compress collinear cells into waypoints; preserves the existing head/tail renderer's semantics.
        List<Vector2i> corners = new ArrayList<>();
        for (int i = 0; i < route.size(); i++) {
            if (i > 0 && i + 1 < route.size()) {
                Vector2i a = route.get(i - 1), b = route.get(i), c = route.get(i + 1);
                if ((b.x - a.x) == (c.x - b.x) && (b.y - a.y) == (c.y - b.y)) continue;
            }
            corners.add(route.get(i));
        }
        return corners;
    }
}
