package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record GuiPanelCanvasGraphics(GuiGraphics graphics) implements PanelCanvasGraphics {
    @Override public PoseStack pose() { return graphics.pose(); }
    @Override public MultiBufferSource bufferSource() { return graphics.bufferSource(); }
    @Override public void sprite(PanelSurface surface, ResourceLocation sprite, int x, int y, int width, int height, int color, boolean emissive) {
        BatchedBlitter.forSprite(sprite).setColorARGB(color).blit(graphics.bufferSource(), pose(), x, y, width, height);
    }
    @Override public void fill(PanelSurface surface, int x0, int y0, int x1, int y1, int color) { graphics.fill(x0, y0, x1, y1, color); }
    @Override public void renderItem(ItemStack stack, int x, int y) { graphics.renderItem(stack, x, y); }
    @Override public float labelDepth() { return 200; }
    @Override public int labelLight() { return LightTexture.FULL_BRIGHT; }
}
