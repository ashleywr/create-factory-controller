package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Lit, render-call-local world drawing. No GUI lighting, scissors, depth clears or global batches. */
public record WorldPanelCanvasGraphics(PoseStack pose, MultiBufferSource bufferSource, int light, float opacity,
                                       DashboardLod detail) implements PanelCanvasGraphics {
    public WorldPanelCanvasGraphics(PoseStack pose, MultiBufferSource source, int light) { this(pose, source, light, 1f); }
    public WorldPanelCanvasGraphics(PoseStack pose, MultiBufferSource source, int light, float opacity) {
        this(pose, source, light, opacity, DashboardLod.FULL);
    }
    @Override public void sprite(PanelSurface surface, ResourceLocation id, int x, int y, int w, int h, int color, boolean emissive) {
        if (!detail.draws(surface)) return;
        var sprite = WorldDashboardSprites.sprite(id);
        quad(surface, sprite.atlasLocation(), x, y, x + w, y + h, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), color,
                emissive ? LightTexture.FULL_BRIGHT : light);
    }
    public void quad(PanelSurface surface, ResourceLocation texture, float x0, float y0, float x1, float y1,
                     float u0, float v0, float u1, float v1, int color, int packedLight) {
        color = (Math.round(((color >>> 24) & 255) * opacity) << 24) | (color & 0xFFFFFF);
        boolean translucent = (color >>> 24) < 255;
        VertexConsumer out = bufferSource.getBuffer(texture.equals(TextureAtlas.LOCATION_BLOCKS)
                ? translucent ? com.simibubi.create.foundation.render.RenderTypes.entityTranslucentBlockMipped()
                    : com.simibubi.create.foundation.render.RenderTypes.entityCutoutBlockMipped()
                : translucent ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture));
        var p = pose.last();
        float z = surface.depth();
        out.addVertex(p, x0, y0, z).setColor(color).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(p, 0, 0, 1);
        out.addVertex(p, x0, y1, z).setColor(color).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(p, 0, 0, 1);
        out.addVertex(p, x1, y1, z).setColor(color).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(p, 0, 0, 1);
        out.addVertex(p, x1, y0, z).setColor(color).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(p, 0, 0, 1);
    }
    @Override public void fill(PanelSurface surface, int x0, int y0, int x1, int y1, int color) {
        // Ordinary entity material: normals/light/UVs are available to shader packs, unlike debugQuads.
        var white = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(ResourceLocation.withDefaultNamespace("block/white_concrete"));
        float u = (white.getU0() + white.getU1()) * .5f;
        float v = (white.getV0() + white.getV1()) * .5f;
        quad(surface, white.atlasLocation(), x0, y0, x1, y1, u, v, u, v, color,
                surface == PanelSurface.INDICATOR || surface == PanelSurface.HOVER || surface == PanelSurface.SELECTION
                        || surface == PanelSurface.CONNECTION_MARK ? LightTexture.FULL_BRIGHT : light);
    }
    @Override public void scaleItemIcon(float scale) { pose.scale(scale, scale, 1); }
    @Override public void renderItem(ItemStack stack, int x, int y) {
        if (detail != DashboardLod.FULL || stack.isEmpty()) return;
        pose.pushPose();
        pose.translate(x + 8, y + 8, PanelSurface.ITEM.depth());
        // Flatten the existing GUI item presentation into shallow, world-lit cabinet relief.
        pose.scale(16, -16, .6f);
        var fluid = FluidCompat.getFilterFluid(stack);
        if (!fluid.isEmpty()) {
            NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid, -.5f, -.5f, -.015f, .5f, .5f, 0,
                    bufferSource, pose, light, true, false);
        } else {
            var mc = Minecraft.getInstance();
            mc.getItemRenderer().render(stack, ItemDisplayContext.GUI, false, pose, bufferSource,
                    light, OverlayTexture.NO_OVERLAY, mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0));
        }
        pose.popPose();
    }
    @Override public float labelDepth() { return PanelSurface.LABEL.depth(); }
    @Override public int labelLight() { return LightTexture.FULL_BRIGHT; }
}
