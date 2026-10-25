package io.github.nbcss.createfactorycontroller.content.gui.screen.controller;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintPlacement;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.gui.screen.PanelSyncListener;
import io.github.nbcss.createfactorycontroller.content.gui.widget.VirtualComponentWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Parent contract for conventional forms, independent of how the board is presented. */
public interface ControllerEditorHost extends PanelSyncListener {
    FactoryControllerMenu getMenu();
    default Screen returnScreen() { return (Screen) this; }
    void resize(Minecraft minecraft, int width, int height);
    int guiWidth();
    int guiHeight();
    void tickComponentWidgets();
    void renderBoard(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, boolean inOverlay);
    void clearSelection();
    void beginConnectionMode(VirtualComponentPosition target);
    void beginRelocateMode(VirtualComponentPosition target);
    void beginBlueprintPlacement(BlueprintPlacement placement);
    void abortBlueprintPlacement();
    void showBlueprintSaved(String fileName);
    VirtualComponentWidget componentWidgetAt(VirtualComponentPosition position);
}
