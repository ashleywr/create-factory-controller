package io.github.nbcss.createfactorycontroller.content.gui.screen.controller;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nbcss.createfactorycontroller.CreateFactoryControllerClient;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintPlacement;
import io.github.nbcss.createfactorycontroller.content.client.panel.DashboardCursor;
import io.github.nbcss.createfactorycontroller.content.client.panel.PanelInteractionController;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.gui.screen.ControllerSettingScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.NetworkSettingsScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.blueprint.BlueprintLibraryScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.blueprint.BlueprintSaveScreen;
import io.github.nbcss.createfactorycontroller.content.gui.widget.CollapsiblePlayerInventory;
import io.github.nbcss.createfactorycontroller.content.gui.widget.NetworkSelectorWidget;
import io.github.nbcss.createfactorycontroller.content.gui.widget.VirtualComponentWidget;
import io.github.nbcss.createfactorycontroller.content.packet.RetuneCarriedPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Only captures cursor/inventory/forms; the instruments are always rendered by block entities. */
public class FactoryControllerInteractionScreen extends AbstractContainerScreen<FactoryControllerMenu> implements ControllerEditorHost {
    public final PanelInteractionController interaction;
    private CollapsiblePlayerInventory drawer;
    private NetworkSelectorWidget networks;
    private int toolbarY, toolbarBottom;
    public FactoryControllerInteractionScreen(FactoryControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        interaction = new PanelInteractionController(menu);
        interaction.board.interaction = interaction;
    }
    @Override protected void init() {
        imageWidth = width; imageHeight = height; super.init();
        leftPos = 0; topPos = 0; inventoryLabelY = -10000; titleLabelY = -10000;
        drawer = addRenderableWidget(new CollapsiblePlayerInventory(menu, font, playerInventoryTitle));
        drawer.layout(0, 0, width, height);
        toolbarY = 8;
        int x = 8;
        x = button(x, 78, "createfactorycontroller.dashboard.full_editor", () -> minecraft.setScreen(new FactoryControllerScreen(menu, minecraft.player.getInventory(), title)));
        x = button(x, 62, "createfactorycontroller.dashboard.settings", () -> minecraft.setScreen(new ControllerSettingScreen(this)));
        x = button(x, 66, "createfactorycontroller.dashboard.origin", () -> minecraft.setScreen(new DashboardOriginScreen(this)));
        x = button(x, 68, "createfactorycontroller.dashboard.blueprints", () -> minecraft.setScreen(new BlueprintLibraryScreen(this)));
        button(x, 80, "createfactorycontroller.dashboard.save", () -> minecraft.setScreen(new BlueprintSaveScreen(this, interaction.board.selection)));
        toolbarBottom = toolbarY + 24;
        networks = addRenderableWidget(new NetworkSelectorWidget(8, toolbarBottom + 2, menu,
                (clear, network) -> PacketDistributor.sendToServer(new RetuneCarriedPacket(clear, network)), n -> {}));
    }
    private int button(int x, int w, String key, Runnable action) {
        if (x + w > width - 8) { x = 8; toolbarY += 24; }
        addRenderableWidget(Button.builder(Component.translatable(key), b -> action.run()).bounds(x, toolbarY, w, 20).build());
        return x + w + 4;
    }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {}
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}
    private boolean blocked(double x, double y) { return y < toolbarBottom || drawer.blocksCanvas(x, y) || networks.isMouseOver(x, y); }
    private void cursor(double x, double y) { interaction.update(blocked(x, y) ? null : DashboardCursor.hit(menu, x, y)); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        cursor(mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        var prompt = font.split(interaction.prompt(), Math.max(100, width - 16));
        int lineY = height - 8 - prompt.size() * font.lineHeight;
        for (var line : prompt) {
            graphics.drawCenteredString(font, line, width / 2, lineY, 0xFFFFFF);
            lineY += font.lineHeight;
        }
        if (interaction.board.hover != null) {
            var widget = componentWidgetAt(interaction.board.hover);
            if (interaction.hoveredWire != null) graphics.renderTooltip(font, interaction.hoveredWire.getTooltip(menu, 1, 0, false), mouseX, mouseY);
            else if (widget != null) graphics.renderTooltip(font, widget.getTooltip(menu, interaction.board.selection.contains(widget.position())), mouseX, mouseY);
        }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        cursor(x, y);
        if (blocked(x, y)) {
            if (networks.isMouseOver(x, y) && menu.getCarried().isEmpty() && networks.getSelectedNetwork() != null) {
                minecraft.setScreen(new NetworkSettingsScreen(this, networks.getSelectedNetwork())); return true;
            }
            return super.mouseClicked(x, y, button);
        }
        boolean selecting = InputConstants.isKeyDown(minecraft.getWindow().getWindow(), CreateFactoryControllerClient.SELECTION_MODE.getKey().getValue());
        // Consume misses too: never drop the carried stack into the world by clicking outside the cabinet.
        interaction.click(this, button, networks.getSelectedNetwork(), selecting);
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (interaction.rectangleStart != null || interaction.move.isActive()) { cursor(x, y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        cursor(x, y); interaction.release(); return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        cursor(x, y);
        if (interaction.connection.isActive()) { interaction.connection.cycleType(menu, interaction.board.hover, dy > 0 ? 1 : -1); return true; }
        return super.mouseScrolled(x, y, dx, dy);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && interaction.cancel()) return true;
        if (interaction.key(key, scan)) return true;
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void onClose() {
        interaction.cancel(); interaction.board.hover = null; interaction.board.interaction = null;
        super.onClose();
    }
    @Override public void removed() {
        interaction.board.hover = null;
        super.removed();
    }
    @Override public void onPanelSync() { interaction.synced(); }
    @Override public int guiWidth() { return Math.max(215, width - 320); }
    @Override public int guiHeight() { return Math.max(200, height - 20); }
    @Override public void tickComponentWidgets() {} // cache tick owns animations, including while forms are open
    @Override public void renderBoard(GuiGraphics graphics, int x, int y, float partial, boolean overlay) {} // world remains the background
    @Override public void clearSelection() { interaction.board.selection.clear(); }
    @Override public void beginConnectionMode(VirtualComponentPosition p) { interaction.cancel(); interaction.connection.begin(p); }
    @Override public void beginRelocateMode(VirtualComponentPosition p) { interaction.cancel(); interaction.relocate = p; }
    @Override public void beginBlueprintPlacement(BlueprintPlacement p) { interaction.cancel(); clearSelection(); interaction.blueprint = p; }
    @Override public void abortBlueprintPlacement() { interaction.blueprint = null; }
    @Override public void showBlueprintSaved(String name) { minecraft.player.displayClientMessage(Component.translatable("createfactorycontroller.gui.blueprint.saved", name), true); }
    @Override public VirtualComponentWidget componentWidgetAt(VirtualComponentPosition p) { return interaction.board.widgets.get(p); }
}
