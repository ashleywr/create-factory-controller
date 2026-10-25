package io.github.nbcss.createfactorycontroller.content.render;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/** Shared baked geometry; Catnip owns its buffer cache and resource-reload invalidation. */
public final class DashboardInstrumentModel {
    private static final PartialModel BEZEL = PartialModel.of(ResourceLocation.fromNamespaceAndPath(
            "createfactorycontroller", "block/dashboard_instrument_bezel"));
    private DashboardInstrumentModel() {}
    /** Register the partial before models bake, rather than lazily on the first rendered instrument. */
    public static void init() {}

    public static void render(WorldPanelCanvasGraphics graphics, int x, int y) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, PanelSurface.BACKPLATE.depth());
        // Model XY is one block wide; Z is deliberately expressed in cabinet pixels.
        pose.scale(16, 16, 1);
        CachedBuffers.partial(BEZEL, Blocks.IRON_BLOCK.defaultBlockState())
                .light(graphics.light())
                .color(105, 115, 119, Math.round(255 * graphics.opacity()))
                .renderInto(pose, graphics.bufferSource().getBuffer(graphics.opacity() < 1
                        ? RenderType.translucent() : RenderType.solid()));
        pose.popPose();
    }
}
