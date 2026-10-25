package io.github.nbcss.createfactorycontroller.content.gui.screen.controller;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.packet.SetDashboardOriginPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Explicit shared physical viewport, never coupled to a player's GUI pan or zoom. */
public class DashboardOriginScreen extends AbstractContainerScreen<FactoryControllerMenu> {
    private final ControllerEditorHost host;
    private EditBox x, y;
    public DashboardOriginScreen(ControllerEditorHost host) {
        super(host.getMenu(), Minecraft.getInstance().player.getInventory(),
                Component.translatable("createfactorycontroller.dashboard.origin"));
        this.host = host; imageWidth = 250; imageHeight = 135;
    }
    @Override protected void init() {
        super.init(); menu.repositionSlots(-10000, -10000, false);
        x = addRenderableWidget(new EditBox(font, leftPos + 28, topPos + 50, 80, 20, Component.literal("X")));
        y = addRenderableWidget(new EditBox(font, leftPos + 142, topPos + 50, 80, 20, Component.literal("Y")));
        x.setFilter(s -> s.matches("-?[0-9]{0,3}")); y.setFilter(s -> s.matches("-?[0-9]{0,3}"));
        x.setValue(Integer.toString(menu.state.dashboard.originX)); y.setValue(Integer.toString(menu.state.dashboard.originY));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            try {
                int dx = Integer.parseInt(x.getValue()), dy = Integer.parseInt(y.getValue());
                if (Math.abs(dx) > 64 || Math.abs(dy) > 64) return;
                PacketDistributor.sendToServer(new SetDashboardOriginPacket(menu.controllerPos, dx, dy));
                minecraft.setScreen(host.returnScreen());
            } catch (NumberFormatException ignored) {}
        }).bounds(leftPos + 140, topPos + 100, 82, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> minecraft.setScreen(host.returnScreen()))
                .bounds(leftPos + 28, topPos + 100, 82, 20).build());
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE252D32);
        g.drawString(font, title, leftPos + 10, topPos + 10, 0xFFFFFF);
        g.drawString(font, Component.translatable("createfactorycontroller.dashboard.origin_hint"), leftPos + 10, topPos + 28, 0xBFCBD0);
        g.drawString(font, "X", leftPos + 15, topPos + 56, 0xFFFFFF);
        g.drawString(font, "Y", leftPos + 130, topPos + 56, 0xFFFFFF);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) {}
    @Override public void onClose() { minecraft.setScreen(host.returnScreen()); }
}
