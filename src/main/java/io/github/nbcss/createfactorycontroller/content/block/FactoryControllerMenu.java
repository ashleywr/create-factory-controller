package io.github.nbcss.createfactorycontroller.content.block;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionGraph;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.gauge.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import io.github.nbcss.createfactorycontroller.content.network.MissingLinkStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FactoryControllerMenu extends AbstractContainerMenu implements ComponentHolder {
    public final SyncedPanelState state;
    public final List<VirtualComponentBehaviour> components;
    public final List<UUID> knownNetworks;
    public final Map<UUID, NetworkSettings> networkSettings;
    public List<MissingLinkStatus> missingLinkStatuses = List.of();
    public String controllerName = "";
    public boolean controllerPowered;
    public final BlockPos controllerPos;
    public final UUID controllerIdentity;
    public int syncEpoch, syncRevision;
    public boolean resyncPending;

    public void refreshFromState() {
        missingLinkStatuses = state.missingLinkStatuses;
        controllerName = state.controllerName; controllerPowered = state.controllerPowered;
        syncEpoch = state.syncEpoch; syncRevision = state.syncRevision; resyncPending = state.resyncPending;
    }
    // Server-side: reference to the actual BE
    @Nullable private final FactoryControllerBlockEntity blockEntity;

    // Inventory state used for slot rebuilding.
    // Slots are re-created on each repositionSlots() call because Slot.x/y are final.
    private final Inventory cachedPlayerInventory;
    private static final int OFF_SCREEN = -2000;

    /** Server-side constructor. */
    public FactoryControllerMenu(int syncId, Inventory playerInventory, FactoryControllerBlockEntity be) {
        super(CreateFactoryController.FACTORY_CONTROLLER_MENU.get(), syncId);
        this.blockEntity = be;
        this.controllerPos = be.getBlockPos();
        this.controllerIdentity = be.dashboard().identity;
        this.state = new SyncedPanelState(controllerPos, controllerIdentity);
        state.clientMirror = false;
        components = state.components; knownNetworks = state.knownNetworks; networkSettings = state.networkSettings;
        this.cachedPlayerInventory = playerInventory;

        // Snapshot data from BE for client sync
        setComponents(be.components.values());
        this.knownNetworks.addAll(be.networks);
        this.missingLinkStatuses = be.missingLinkStatuses();
        this.controllerName = be.customName;
        this.controllerPowered = be.isRedstonePowered();

        addExtraSlots(OFF_SCREEN, OFF_SCREEN, false);
    }

    /** Client-side constructor (called via IMenuTypeExtension). */
    public FactoryControllerMenu(int syncId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(CreateFactoryController.FACTORY_CONTROLLER_MENU.get(), syncId);
        this.blockEntity = null;
        this.controllerPos = buf.readBlockPos();
        this.controllerIdentity = buf.readUUID();
        this.state = io.github.nbcss.createfactorycontroller.content.client.panel.FactoryControllerClientBoards.get(controllerPos, controllerIdentity).state;
        components = state.components; knownNetworks = state.knownNetworks; networkSettings = state.networkSettings;
        this.cachedPlayerInventory = playerInventory;
        this.syncEpoch = buf.readVarInt();
        this.syncRevision = buf.readVarInt();

        int componentCount = buf.readVarInt();
        // Menu-open metadata must not erase a cached full snapshot.

        for (int i = 0; i < componentCount; i++) {
            VirtualComponentBehaviour b = ComponentRegistry.readComponent(buf);
            if (b != null) addComponent(b);
        }
        int connectionCount = buf.readVarInt();
        List<Connection> connections = new ArrayList<>(connectionCount);
        for (int i = 0; i < connectionCount; i++) {
            Connection c = Connection.fromClient(buf);
            if (c != null) connections.add(c);
        }
        if (!connections.isEmpty()) loadConnections(connections);   // build the client graph from the synced central edge list

        int networkCount = buf.readVarInt();
        for (int i = 0; i < networkCount; i++) {
            NetworkSettings settings = NetworkSettings.STREAM_CODEC.decode(buf);
            knownNetworks.add(settings.network());
            networkSettings.put(settings.network(), settings);
        }
        missingLinkStatuses = MissingLinkStatus.readList(buf);

        this.controllerName = buf.readUtf();
        this.controllerPowered = buf.readBoolean();
        if (state.syncRevision > 0 || !state.components.isEmpty()) refreshFromState();

        addExtraSlots(OFF_SCREEN, OFF_SCREEN, false);
    }

    private int extraSlotsStart = -1;
    private int invSlotsStart = -1;

    private void addExtraSlots(int originX, int hotbarY, boolean expanded) {
        extraSlotsStart = slots.size();
        invSlotsStart = slots.size();
        // Main inventory (3 rows × 9): off-screen when collapsed, above hotbar when expanded.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = expanded ? originX + col * 18 : OFF_SCREEN;
                int sy = expanded ? hotbarY - 4 - (3 - row) * 18 : OFF_SCREEN;
                addSlot(new Slot(cachedPlayerInventory, col + row * 9 + 9, sx, sy));
            }
        }
        // Hotbar (always visible once positioned).
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(cachedPlayerInventory, col, originX + col * 18, hotbarY));
    }

    /** Rebuilds the inventory slots at new positions (client-side only). */
    public void rebuildSlots(int originX, int hotbarY, boolean expanded) {
        if (extraSlotsStart >= 0)
            slots.subList(extraSlotsStart, slots.size()).clear();
        addExtraSlots(originX, hotbarY, expanded);
    }

    /** Reposition the player inventory for the active overlay. */
    public void repositionSlots(int originX, int hotbarY, boolean expanded) {
        rebuildSlots(originX, hotbarY, expanded);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (blockEntity == null) return true;
        return DashboardAssemblyManager.coreAt(player.level(), controllerPos) == blockEntity
                && controllerIdentity.equals(blockEntity.dashboard().identity)
                && DashboardAssemblyManager.nearMember(blockEntity, player, 8);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        if (invSlotsStart < 0) return ItemStack.EMPTY;

        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack original = slot.getItem().copy();
        ItemStack stack = slot.getItem();

        int mainStart   = invSlotsStart;        // 27 main inventory slots
        int mainEnd     = invSlotsStart + 27;
        int hotbarStart = invSlotsStart + 27;   // 9 hotbar slots
        int hotbarEnd   = invSlotsStart + 36;

        boolean moved;
        if (index >= mainStart && index < hotbarEnd) {
            // Player inventory → no container slots to move to in this menu,
            // so just shuffle between hotbar and main inventory.
            if (index < mainEnd) {
                // main inventory → hotbar
                moved = moveItemStackTo(stack, hotbarStart, hotbarEnd, false);
            } else {
                // hotbar → main inventory
                moved = moveItemStackTo(stack, mainStart, mainEnd, false);
            }
        } else {
            // Container slot (gauge slot etc.) → player inventory (hotbar first, then main)
            moved = moveItemStackTo(stack, hotbarStart, hotbarEnd, false)
                 || moveItemStackTo(stack, mainStart, mainEnd, false);
        }

        if (!moved) return ItemStack.EMPTY;
        slot.setByPlayer(stack);
        slot.onTake(player, stack);
        return original;
    }

    // ── Component access (keep the list + position index in sync) ─────────────

    public void addComponent(VirtualComponentBehaviour c) { state.addComponent(c); }
    public void setComponents(Collection<? extends VirtualComponentBehaviour> c) { state.setComponents(c); }
    public void clearComponents() { state.clearComponents(); }
    public void loadConnections(List<Connection> c) { state.loadConnections(c); }
    public void applyFullSync(int epoch, int revision, Collection<? extends VirtualComponentBehaviour> c,
            List<Connection> edges, List<NetworkSettings> networks, List<MissingLinkStatus> missing, String name, boolean powered) {
        state.applyFullSync(epoch, revision, c, edges, networks, missing, name, powered); refreshFromState();
    }
    public void applyNetworkSettings(List<NetworkSettings> settings) { state.applyNetworkSettings(settings); }
    public void applyMissingLinkStatuses(List<MissingLinkStatus> statuses) { state.applyMissingLinkStatuses(statuses); refreshFromState(); }
    public void upsertComponent(VirtualComponentBehaviour c) { state.upsertComponent(c); }
    public void removeComponentAt(VirtualComponentPosition p) { state.removeComponentAt(p); }
    public void putConnection(Connection c, long time) { state.putConnection(c, time); }
    public void removeConnectionAt(VirtualComponentPosition from, VirtualComponentPosition to, Connection.Type type) { state.removeConnectionAt(from, to, type); }
    public void refoldSink(VirtualComponentPosition p, Connection.Type type) { state.refoldSink(p, type); }
    @Override public VirtualComponentBehaviour componentAt(VirtualComponentPosition p) { return state.componentAt(p); }
    public int componentCountIn(UUID network) { return state.componentCountIn(network); }

    // ── Data serialization for menu open ──────────────────────────────────

    /** Called by NeoForge when the server opens this menu for a player. */
    public static void writeExtraData(FactoryControllerBlockEntity be, RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(be.getBlockPos());
        buf.writeUUID(be.dashboard().identity);
        buf.writeVarInt(be.syncEpoch());
        buf.writeVarInt(be.syncRevision());

        buf.writeVarInt(0);
        buf.writeVarInt(0);
        buf.writeVarInt(0);
        MissingLinkStatus.writeList(buf, List.of());

        buf.writeUtf(be.customName);
        buf.writeBoolean(be.isRedstonePowered());
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
