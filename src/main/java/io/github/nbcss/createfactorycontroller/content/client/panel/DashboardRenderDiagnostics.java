package io.github.nbcss.createfactorycontroller.content.client.panel;

import com.mojang.logging.LogUtils;
import io.github.nbcss.createfactorycontroller.ClientConfig;
import io.github.nbcss.createfactorycontroller.content.block.DashboardOrientation;
import io.github.nbcss.createfactorycontroller.content.block.DashboardTile;
import io.github.nbcss.createfactorycontroller.content.render.DashboardWorldView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.commands.Commands.literal;

/** Opt-in observation outside the BER, so skipped BERs still produce useful evidence. */
public final class DashboardRenderDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TRACKED = 128, MAX_REPORTED = 8;
    private static final long REPORT_INTERVAL_NANOS = 5_000_000_000L;
    private record Key(BlockPos pos, UUID identity) {}
    private static final Map<Key, DashboardDiagnosticCounters> COUNTERS = new HashMap<>();
    private static ClientLevel observedLevel;
    private static Boolean enabledOverride;
    private static long lastReport, lodCaptures, cursorCaptures;
    private static int session, rendererConstructions;
    private DashboardRenderDiagnostics() {}

    public static boolean enabled() {
        return enabledOverride != null ? enabledOverride
                : ClientConfig.SPEC.isLoaded() && ClientConfig.DASHBOARD_RENDER_DIAGNOSTICS.get();
    }
    public static void rendererConstructed() { rendererConstructions++; }
    public static void cameraCaptured(boolean cursor) {
        if (!enabled()) return;
        observeLevel();
        if (cursor) cursorCaptures++; else lodCaptures++;
    }
    private static DashboardDiagnosticCounters counters(BlockPos pos, UUID identity) {
        observeLevel();
        var key = new Key(pos.immutable(), identity);
        var existing = COUNTERS.get(key);
        if (existing != null) return existing;
        if (COUNTERS.size() >= MAX_TRACKED) return null;
        var created = new DashboardDiagnosticCounters(); COUNTERS.put(key, created); return created;
    }
    private static DashboardDiagnosticCounters counters(DashboardTile tile) {
        return counters(tile.getBlockPos(), tile.dashboard().identity);
    }
    public static void visibility(DashboardTile tile, boolean allowed) {
        if (!enabled()) return;
        var c = counters(tile); if (c != null) c.visibility(allowed);
    }
    public static void renderStarted(DashboardTile tile) {
        if (!enabled()) return;
        var c = counters(tile); if (c != null) c.renderStarted();
    }
    public static void renderCompleted(DashboardTile tile) {
        if (!enabled()) return;
        var c = counters(tile); if (c != null) c.renderCompleted();
    }
    public static void request(BlockPos core, UUID identity) {
        if (!enabled()) return;
        var c = counters(core, identity); if (c != null) c.request();
    }
    public static void snapshot(BlockPos core, UUID identity) {
        if (!enabled()) return;
        var c = counters(core, identity); if (c != null) c.snapshot();
    }
    public static void delta(BlockPos core, UUID identity) {
        if (!enabled()) return;
        var c = counters(core, identity); if (c != null) c.delta();
    }
    public static void unavailable(BlockPos core, UUID identity) {
        if (!enabled()) return;
        var c = counters(core, identity); if (c != null) c.unavailable();
    }
    /** Resets diagnostic observations only; intentionally does not change the production camera/cache lifecycle. */
    public static void clear() {
        observedLevel = null; COUNTERS.clear(); lastReport = 0; lodCaptures = cursorCaptures = 0;
    }
    private static void observeLevel() {
        var level = Minecraft.getInstance().level;
        if (level == observedLevel) return;
        clear(); observedLevel = level; session++;
        if (level != null) LOGGER.info("[CFC-DashboardDebug] session={} dimension={} rendererConstructions={}",
                session, level.dimension().location(), rendererConstructions);
    }
    public static void tick() {
        if (!enabled()) { clear(); return; }
        observeLevel();
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long now = System.nanoTime();
        if (lastReport != 0 && now - lastReport < REPORT_INTERVAL_NANOS) return;
        lastReport = now;
        report();
    }
    public static void report() {
        if (!enabled()) return;
        observeLevel();
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var camera = mc.gameRenderer.getMainCamera().getPosition();
        var nearby = new ArrayList<BlockEntity>();
        var chunkPos = mc.player.chunkPosition();
        // Inspect only already-loaded chunks; never load/generate chunks or create/request board mirrors.
        for (int x = chunkPos.x - 2; x <= chunkPos.x + 2; x++) for (int z = chunkPos.z - 2; z <= chunkPos.z + 2; z++) {
            var chunk = mc.level.getChunkSource().getChunkNow(x, z);
            if (chunk == null) continue;
            for (var be : chunk.getBlockEntities().values()) if (be instanceof DashboardTile
                    && be.getBlockPos().distToCenterSqr(camera) <= 48 * 48) nearby.add(be);
        }
        nearby.sort(Comparator.comparingDouble(be -> be.getBlockPos().distToCenterSqr(camera)));
        LOGGER.info("[CFC-DashboardDebug] session={} tick={} camera={} screen={} nearby={} tracked={} lodCaptures={} cursorCaptures={} renderDistance={}",
                session, mc.level.getGameTime(), camera, mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(),
                nearby.size(), COUNTERS.size(), lodCaptures, cursorCaptures, ClientConfig.DASHBOARD_RENDER_DISTANCE.get());
        for (var be : nearby.subList(0, Math.min(MAX_REPORTED, nearby.size()))) {
            var tile = (DashboardTile) be;
            var data = tile.dashboard();
            var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(be);
            var board = data.core == null ? null : FactoryControllerClientBoards.peek(data.core, data.identity);
            var membership = board != null && board.ready ? board.state.dashboard : data;
            double distance = data.core == null ? Math.sqrt(be.getBlockPos().distToCenterSqr(camera))
                    : Math.sqrt(DashboardWorldView.geometry(data.core, DashboardOrientation.of(be.getBlockState()), membership.tiles, camera).distanceSquared());
            var counter = counters(tile);
            var counts = counter == null ? null : counter.drain();
            var coreCounter = data.core == null || data.core.equals(be.getBlockPos()) ? null : COUNTERS.get(new Key(data.core, data.identity));
            LOGGER.info("[CFC-DashboardDebug] tile={} id={} core={} local=({}, {}) origin=({}, {}) tiles={} renderer={} valid={} removed={} distance={} ready={} available={} components={} widgets={} lod={} counts={} coreTracked={}",
                    be.getBlockPos().toShortString(), data.identity, data.core, data.tileX, data.tileY, data.originX, data.originY,
                    membership.tiles.size(), renderer == null ? "MISSING" : renderer.getClass().getName(),
                    be.getType().isValid(be.getBlockState()), be.isRemoved(), distance,
                    board != null && board.ready, board != null && board.available,
                    board == null ? -1 : board.state.components.size(), board == null ? -1 : board.widgets.size(),
                    board == null || board.view == null ? "unknown" : board.view.detail(), counts, coreCounter != null);
        }
        COUNTERS.clear(); lodCaptures = cursorCaptures = 0;
    }
    public static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(literal("cfc").then(literal("dashboarddebug")
                .then(literal("on").executes(ctx -> { enabledOverride = true; clear(); report();
                    ctx.getSource().sendSuccess(() -> Component.literal("Dashboard diagnostics enabled; inspect [CFC-DashboardDebug] in latest.log."), false); return 1; }))
                .then(literal("off").executes(ctx -> { enabledOverride = false; clear();
                    ctx.getSource().sendSuccess(() -> Component.literal("Dashboard diagnostics disabled for this session."), false); return 1; }))
                .then(literal("status").executes(ctx -> { report();
                    ctx.getSource().sendSuccess(() -> Component.literal("Dashboard diagnostics " + (enabled() ? "enabled" : "disabled") + "; renderer constructions=" + rendererConstructions), false); return 1; }))));
    }
}
