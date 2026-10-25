package io.github.nbcss.createfactorycontroller.content.block;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/** Event-driven, bounded planar topology. An unloaded chunk is not a missing cabinet. */
@EventBusSubscriber(modid = CreateFactoryController.MODID)
public final class DashboardAssemblyManager {
    private static final Map<ServerLevel, Set<BlockPos>> PENDING = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<FactoryControllerBlockEntity>> CORES = new WeakHashMap<>();
    private static final int SCAN_LIMIT = 257;
    private DashboardAssemblyManager() {}
    public static void loaded(FactoryControllerBlockEntity core) {
        if (core.getLevel() instanceof ServerLevel level)
            CORES.computeIfAbsent(level, k -> Collections.newSetFromMap(new WeakHashMap<>())).add(core);
    }
    public static void unloaded(FactoryControllerBlockEntity core) {
        if (core.getLevel() instanceof ServerLevel level && CORES.containsKey(level)) CORES.get(level).remove(core);
    }
    public static void networkChanged(net.minecraft.server.MinecraftServer server, UUID network) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) for (FactoryControllerBlockEntity core : CORES.getOrDefault(level, Set.of()))
            if (!core.isRemoved() && core.networks.contains(network)) core.syncNetworks();
    }
    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        PENDING.clear(); CORES.clear();
    }

    public static DashboardTile tileAt(Level level, BlockPos pos) {
        return level != null && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof DashboardTile tile ? tile : null;
    }
    public static void schedule(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) PENDING.computeIfAbsent(server, k -> new LinkedHashSet<>()).add(pos.immutable());
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        Map<ServerLevel, Set<BlockPos>> changes = new LinkedHashMap<>(PENDING);
        PENDING.clear();
        changes.forEach((level, positions) -> {
            Set<BlockPos> handled = new HashSet<>();
            for (BlockPos position : positions) {
                rebuild(level, position, handled);
                for (Direction direction : Direction.values()) rebuild(level, position.relative(direction), handled);
            }
        });
    }

    public static Direction placementFacing(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        DashboardTile clicked = tileAt(context.getLevel(), pos.relative(context.getClickedFace().getOpposite()));
        if (clicked != null) {
            DashboardOrientation orientation = DashboardOrientation.of(clicked.getBlockState());
            if (orientation.coplanar(clicked.getBlockPos(), pos)) return orientation.facing();
        }
        for (Direction direction : Direction.values()) {
            DashboardTile neighbor = tileAt(context.getLevel(), pos.relative(direction));
            if (neighbor == null) continue;
            DashboardOrientation orientation = DashboardOrientation.of(neighbor.getBlockState());
            if (orientation.coplanar(neighbor.getBlockPos(), pos)) return orientation.facing();
        }
        return context.getHorizontalDirection().getOpposite();
    }

    private static Map<BlockPos, DashboardTile> discover(Level level, BlockPos start, DashboardOrientation orientation,
                                                         BlockPos proposed) {
        Map<BlockPos, DashboardTile> found = new LinkedHashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>(); queue.add(start);
        while (!queue.isEmpty() && found.size() < SCAN_LIMIT) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos)) continue;
            DashboardTile tile = tileAt(level, pos);
            if (!pos.equals(proposed) && (tile == null || DashboardOrientation.of(tile.getBlockState()).facing() != orientation.facing())) continue;
            found.put(pos, pos.equals(proposed) ? null : tile);
            for (Direction edge : orientation.edges()) queue.addLast(pos.relative(edge));
        }
        return found;
    }

    /** Runs before BlockItem placement, so an invalid bridge never consumes an item. */
    public static boolean canPlace(Level level, BlockPos pos, BlockState state, boolean core, Player player) {
        DashboardOrientation orientation = DashboardOrientation.of(state);
        Map<BlockPos, DashboardTile> group = discover(level, pos, orientation, pos);
        Set<UUID> identities = new HashSet<>();
        BlockPos anchor = pos;
        for (DashboardTile tile : group.values()) {
            if (tile == null) continue;
            if (tile instanceof FactoryControllerBlockEntity) anchor = tile.getBlockPos();
            if (tile instanceof FactoryControllerBlockEntity || tile.dashboard().core != null)
                identities.add(tile.dashboard().identity);
        }
        String failure = null;
        for (DashboardTile tile : group.values()) {
            if (tile != null && tile.dashboard().core != null && !level.hasChunkAt(tile.dashboard().core))
                failure = "unavailable";
        }
        if (identities.size() > (core ? 0 : 1)) failure = "conflict";
        Set<BlockPos> offsets = new LinkedHashSet<>();
        for (BlockPos position : group.keySet()) offsets.add(orientation.offset(anchor, position));
        // Include known unloaded membership so placing near a chunk edge cannot evade capacity limits.
        DashboardTile anchorTile = tileAt(level, anchor);
        if (anchorTile instanceof FactoryControllerBlockEntity controller) offsets.addAll(controller.dashboard().tiles);
        if (!withinLimits(offsets, ServerConfig.MAX_DASHBOARD_TILES.get(), ServerConfig.MAX_DASHBOARD_SPAN.get())) failure = "limit";
        if (failure != null && player != null && !level.isClientSide)
            player.displayClientMessage(Component.translatable("createfactorycontroller.dashboard." + failure), true);
        return failure == null;
    }

    public static boolean withinLimits(Set<BlockPos> offsets, int count, int span) {
        if (offsets.size() > count) return false;
        IntSummaryStatistics xs = offsets.stream().mapToInt(BlockPos::getX).summaryStatistics();
        IntSummaryStatistics ys = offsets.stream().mapToInt(BlockPos::getY).summaryStatistics();
        return offsets.isEmpty() || (xs.getMax() - xs.getMin() < span && ys.getMax() - ys.getMin() < span);
    }

    private static void rebuild(ServerLevel level, BlockPos pos, Set<BlockPos> handled) {
        DashboardTile seed = tileAt(level, pos);
        if (seed == null || !handled.add(pos)) return;
        DashboardOrientation orientation = DashboardOrientation.of(seed.getBlockState());
        Map<BlockPos, DashboardTile> group = discover(level, pos, orientation, null);
        handled.addAll(group.keySet());
        List<FactoryControllerBlockEntity> cores = group.values().stream().filter(FactoryControllerBlockEntity.class::isInstance)
                .map(FactoryControllerBlockEntity.class::cast).toList();
        if (cores.size() == 1) {
            reconcile(level, cores.getFirst(), orientation, group);
        } else if (cores.isEmpty()) {
            // The core may be reachable only through an unloaded chunk. Reconcile its saved mask first.
            Set<FactoryControllerBlockEntity> oldCores = new LinkedHashSet<>();
            for (DashboardTile tile : group.values()) {
                BlockPos old = tile.dashboard().core;
                if (old != null && tileAt(level, old) instanceof FactoryControllerBlockEntity core) oldCores.add(core);
            }
            for (FactoryControllerBlockEntity core : oldCores)
                reconcile(level, core, DashboardOrientation.of(core.getBlockState()),
                        discover(level, core.getBlockPos(), DashboardOrientation.of(core.getBlockState()), null));
            for (DashboardTile tile : group.values()) {
                BlockPos old = tile.dashboard().core;
                if (old != null && !level.hasChunkAt(old)) continue;
                if (coreAt(level, tile.getBlockPos()) == null) unlink(tile);
            }
        } else {
            // Commands can bypass BlockItem checks. Never choose a winner or overwrite another core.
            for (DashboardTile tile : group.values()) if (!(tile instanceof FactoryControllerBlockEntity)) unlink(tile);
            for (FactoryControllerBlockEntity core : cores) {
                core.dashboard().tiles.clear(); core.dashboard().tiles.add(BlockPos.ZERO);
                core.dashboard().edges = 0; core.dashboard().generation++; core.setChanged(); core.sendData();
            }
        }
    }

    private static void reconcile(ServerLevel level, FactoryControllerBlockEntity core, DashboardOrientation orientation,
                                  Map<BlockPos, DashboardTile> loaded) {
        DashboardMembership data = core.dashboard();
        Set<BlockPos> candidates = new LinkedHashSet<>(data.tiles);
        for (BlockPos pos : loaded.keySet()) candidates.add(orientation.offset(core.getBlockPos(), pos));
        candidates.add(BlockPos.ZERO);
        Set<BlockPos> reached = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>(); queue.add(BlockPos.ZERO);
        while (!queue.isEmpty() && reached.size() < SCAN_LIMIT) {
            BlockPos offset = queue.removeFirst();
            if (!candidates.contains(offset) || reached.contains(offset)) continue;
            BlockPos world = orientation.position(core.getBlockPos(), offset.getX(), offset.getY());
            if (level.hasChunkAt(world)) {
                DashboardTile tile = tileAt(level, world);
                if (tile == null || DashboardOrientation.of(tile.getBlockState()).facing() != orientation.facing()) continue;
                if (tile instanceof FactoryControllerBlockEntity && tile != core) continue;
                // A newly placed core gets a fresh identity; extensions of the old core at
                // this position should join it, not remain permanently stale.
                BlockPos claimedCore = tile.dashboard().core;
                if (claimedCore != null && !tile.dashboard().identity.equals(data.identity) && tile != core
                        && !claimedCore.equals(core.getBlockPos())
                        && (!level.hasChunkAt(claimedCore) || coreAt(level, world) != null)) continue;
            }
            reached.add(offset);
            queue.add(offset.offset(-1, 0, 0)); queue.add(offset.offset(0, -1, 0));
            queue.add(offset.offset(1, 0, 0)); queue.add(offset.offset(0, 1, 0));
        }
        if (!withinLimits(reached, ServerConfig.MAX_DASHBOARD_TILES.get(), ServerConfig.MAX_DASHBOARD_SPAN.get())) return;
        Set<BlockPos> previous = new LinkedHashSet<>(data.tiles);
        boolean changed = !previous.equals(reached) || data.core == null;
        if (changed) data.generation++;
        data.tiles.clear(); data.tiles.addAll(reached); data.core = core.getBlockPos();
        for (BlockPos offset : reached) {
            DashboardTile tile = tileAt(level, orientation.position(core.getBlockPos(), offset.getX(), offset.getY()));
            if (tile == null) continue;
            DashboardMembership member = tile.dashboard();
            int edges = edgeMask(reached, offset);
            boolean dirty = changed || member.core == null || !member.identity.equals(data.identity)
                    || member.generation != data.generation
                    || member.edges != edges || member.originX != data.originX || member.originY != data.originY;
            member.core = core.getBlockPos(); member.identity = data.identity;
            member.tileX = offset.getX(); member.tileY = offset.getY(); member.generation = data.generation;
            member.edges = edges; member.originX = data.originX; member.originY = data.originY;
            if (dirty) { tile.setChanged(); tile.sendData(); }
        }
        previous.removeAll(reached);
        for (BlockPos offset : previous) {
            DashboardTile tile = tileAt(level, orientation.position(core.getBlockPos(), offset.getX(), offset.getY()));
            if (tile != null && !(tile instanceof FactoryControllerBlockEntity) && tile.dashboard().identity.equals(data.identity)) unlink(tile);
        }
    }

    public static int edgeMask(Set<BlockPos> tiles, BlockPos offset) {
        return (tiles.contains(offset.offset(-1, 0, 0)) ? 1 : 0) | (tiles.contains(offset.offset(0, -1, 0)) ? 2 : 0)
                | (tiles.contains(offset.offset(1, 0, 0)) ? 4 : 0) | (tiles.contains(offset.offset(0, 1, 0)) ? 8 : 0);
    }
    private static void unlink(DashboardTile tile) {
        if (tile.dashboard().core == null && tile.dashboard().edges == 0) return;
        tile.dashboard().core = null; tile.dashboard().edges = 0; tile.dashboard().generation++;
        tile.setChanged(); tile.sendData();
    }
    public static FactoryControllerBlockEntity coreAt(Level level, BlockPos memberPos) {
        DashboardTile tile = tileAt(level, memberPos);
        if (tile instanceof FactoryControllerBlockEntity core) return core;
        if (tile == null || tile.dashboard().core == null) return null;
        if (!(tileAt(level, tile.dashboard().core) instanceof FactoryControllerBlockEntity core)) return null;
        DashboardOrientation orientation = DashboardOrientation.of(core.getBlockState());
        BlockPos offset = orientation.offset(core.getBlockPos(), memberPos);
        return tile.dashboard().identity.equals(core.dashboard().identity) && orientation.coplanar(core.getBlockPos(), memberPos)
                && orientation.facing() == DashboardOrientation.of(tile.getBlockState()).facing()
                && core.dashboard().tiles.contains(offset) ? core : null;
    }
    public static boolean nearMember(FactoryControllerBlockEntity core, Player player, double radius) {
        if (player.level() != core.getLevel() || core.isRemoved()) return false;
        DashboardOrientation orientation = DashboardOrientation.of(core.getBlockState());
        Set<BlockPos> tiles = core.dashboard().tiles.isEmpty() ? Set.of(BlockPos.ZERO) : core.dashboard().tiles;
        for (BlockPos offset : tiles) {
            BlockPos pos = orientation.position(core.getBlockPos(), offset.getX(), offset.getY());
            if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) < radius * radius
                    && coreAt(player.level(), pos) == core) return true;
        }
        return false;
    }
}
