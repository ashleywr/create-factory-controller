package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Small render backend used by the same instrument widgets in menus and in the world. */
public interface PanelCanvasGraphics {
    PoseStack pose();
    MultiBufferSource bufferSource();
    void sprite(PanelSurface surface, ResourceLocation sprite, int x, int y, int width, int height, int color, boolean emissive);
    void fill(PanelSurface surface, int x0, int y0, int x1, int y1, int color);
    void renderItem(ItemStack stack, int x, int y);
    /** Menus and placement previews always retain full detail. */
    default DashboardLod detail() { return DashboardLod.FULL; }
    /** Scale icon presentation without scaling its world surface depth. */
    default void scaleItemIcon(float scale) { pose().scale(scale, scale, scale); }
    float labelDepth();
    int labelLight();
}
