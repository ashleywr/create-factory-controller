package io.github.nbcss.createfactorycontroller.content.block;

import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionGraph;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.gauge.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import io.github.nbcss.createfactorycontroller.content.network.MissingLinkStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;


import java.util.*;

public class SyncedPanelState implements ComponentHolder {
    public final DashboardMembership dashboard = new DashboardMembership();
    public ConnectionGraph connectionGraph() { return connectionGraph; }
    public final List<VirtualComponentBehaviour> components = new ArrayList<>();
    /** Position → component index for O(1) lookup; mirrors {@link #components}. */
    private final Map<VirtualComponentPosition, VirtualComponentBehaviour> componentsByPosition = new HashMap<>();
    /** Client-side connections. */
    private final ConnectionGraph connectionGraph = new ConnectionGraph();
    public final List<UUID> knownNetworks = new ArrayList<>();
    public final Map<UUID, NetworkSettings> networkSettings = new HashMap<>();
    /** Runtime-only unloaded-link cache */
    public List<MissingLinkStatus> missingLinkStatuses = List.of();
    /** Controller's custom display name (synced); blank means the default translated block name. */
    public String controllerName = "";
    public boolean controllerPowered = false;
    public final BlockPos controllerPos;
    public final UUID identity;
    public boolean clientMirror = true;
    public long structureRevision;
    public SyncedPanelState(BlockPos pos, UUID identity) { this.controllerPos = pos; this.identity = identity; }

    // ── Client-side sync tokens ──
    /** BE-instance token; a delta from a different BE load can never match it. */
    public int syncEpoch;
    /** Revision this menu's state reflects. A delta applies only when its base equals this. */
    public int syncRevision;
    /** True while a full-resync request is in flight, so a burst of bad deltas asks only once.
     *  Cleared when the full snapshot arrives ({@link #applyFullSync}). */
    public boolean resyncPending = false;
    public boolean acceptsDelta(int epoch, int baseRevision) { return epoch == syncEpoch && baseRevision == syncRevision; }

    /** Adds a component, indexing it by position. A later component at the same cell wins. */
    public void addComponent(VirtualComponentBehaviour component) {
        components.add(component);
        componentsByPosition.put(component.position(), component);
        structureRevision++;
        // Server menus index the BE's live objects only for menu bookkeeping
        if (clientMirror) {
            component.setHolder(this);
            component.setGraph(connectionGraph);
        }
    }

    /** Replaces all components (used on the full sync), rebuilding the position index. */
    public void setComponents(Collection<? extends VirtualComponentBehaviour> newComponents) {
        clearComponents();
        for (VirtualComponentBehaviour c : newComponents) addComponent(c);
    }

    /** Removes every component and clears the position index + the (client) connection graph. */
    public void clearComponents() {
        structureRevision++;
        components.clear();
        componentsByPosition.clear();
        connectionGraph.clear();
    }

    /** Replaces the client connection graph from a synced central edge list (the components' positions are already in). */
    public void loadConnections(List<Connection> edges) {
        structureRevision++;
        connectionGraph.clear();
        for (Connection conn : edges) connectionGraph.add(conn);
        bindConnectionHooks();
    }

    // ── Sync apply (full snapshot + per-item deltas; called by the sync packet handlers) ─────────

    /** Wholesale board replace from a full snapshot (menu-open equivalent). Authoritative: resets the sync
     *  tokens unconditionally, which also ends any pending resync round-trip. */
    public void applyFullSync(int epoch, int revision,
                              Collection<? extends VirtualComponentBehaviour> newComponents,
                              List<Connection> connections,
                              List<NetworkSettings> networks,
                              List<MissingLinkStatus> missingLinks,
                              String name, boolean powered) {
        setComponents(newComponents);
        loadConnections(connections);
        applyNetworkSettings(networks);
        applyMissingLinkStatuses(missingLinks);
        controllerName = name;
        controllerPowered = powered;
        syncEpoch = epoch;
        syncRevision = revision;
        resyncPending = false;
    }

    /** Replaces the synced network-settings mirror (full snapshot, or a delta's networks section). */
    public void applyNetworkSettings(List<NetworkSettings> settings) {
        knownNetworks.clear();
        networkSettings.clear();
        for (NetworkSettings s : settings) {
            knownNetworks.add(s.network());
            networkSettings.put(s.network(), s);
        }
    }

    public void applyMissingLinkStatuses(List<MissingLinkStatus> statuses) {
        missingLinkStatuses = List.copyOf(statuses);
    }

    /** Delta apply: replace-or-add one component (a FULL upsert). Idempotent. */
    public void upsertComponent(VirtualComponentBehaviour component) {
        VirtualComponentBehaviour old = componentsByPosition.remove(component.position());
        if (old != null) components.remove(old);
        addComponent(component);
    }

    /** Delta apply: drop the component at {@code pos} and every wire touching it — mirroring the server's
     *  remove path, whose packet also carries those wires as explicit removals (re-removal is a no-op). */
    public void removeComponentAt(VirtualComponentPosition pos) {
        VirtualComponentBehaviour old = componentsByPosition.remove(pos);
        if (old != null) components.remove(old);
        connectionGraph.disconnect(pos);
        structureRevision++;
    }

    /** Delta apply: add-or-replace one wire (the graph keeps one wire per endpoint pair per type). */
    public void putConnection(Connection conn, long gameTime) {
        Connection previous = connectionGraph.get(conn.from, conn.to, conn.type);
        conn.onClientSynced(previous, gameTime);
        connectionGraph.add(conn);
        if (previous == null || previous.arrowBendMode != conn.arrowBendMode) structureRevision++;
    }

    /** Delta apply: remove the {@code type} wire {@code from → to}, if present. */
    public void removeConnectionAt(VirtualComponentPosition from, VirtualComponentPosition to, Connection.Type type) {
        connectionGraph.remove(to, from, type);
        structureRevision++;
    }

    /** Re-folds one sink after its incoming wires changed (the client-side mirror of the server's settle). */
    public void refoldSink(VirtualComponentPosition pos, Connection.Type type) {
        VirtualComponentBehaviour sink = componentAt(pos);
        if (sink != null) sink.onInputChanged(type);
    }

    private void bindConnectionHooks() {
        // Edges arrive with their value; each sink re-folds from them (no callbacks to rebind in the new model).
        for (Connection conn : connectionGraph.connections()) {
            VirtualComponentBehaviour sink = componentAt(conn.to);
            if (sink != null) sink.onInputChanged(conn.type);
        }
    }

    /** O(1) lookup of the component occupying {@code pos}, or {@code null} if the cell is empty. */
    @Override
    public VirtualComponentBehaviour componentAt(VirtualComponentPosition position) {
        return position == null ? null : componentsByPosition.get(position);
    }

    /** Number of components currently on the given network (used by the network selector). */
    public int componentCountIn(UUID network) {
        int n = 0;
        for (VirtualComponentBehaviour c : components)
            if (c instanceof VirtualGaugeBehaviour g && network.equals(g.networkId)) n++;
        return n;
    }

    public boolean isRedstonePowered() {
        return controllerPowered;
    }

    /** Controller display name: the custom name, or the default translated block name when unset. */
    public Component controllerDisplayName() {
        return controllerName.isBlank()
            ? Component.translatable("block.createfactorycontroller.factory_controller")
            : Component.literal(controllerName);
    }

    /** The shared settings for a network — never null; a network with no synced entry yields
     *  {@link NetworkSettings#defaultFor}. */
    public NetworkSettings networkSettings(UUID network) {
        return networkSettings.getOrDefault(network, NetworkSettings.defaultFor(network));
    }

    /** Display name for a network: its custom name, or a default "Network #XXXX". */
    public Component networkName(UUID network) {
        return networkSettings(network).displayName();
    }

    /** Custom icon item for a network, or {@link ItemStack#EMPTY} when unset (⇒ draw the default network icon). */
    public ItemStack networkIcon(UUID network) {
        return networkSettings(network).icon();
    }

}
