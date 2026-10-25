package io.github.nbcss.createfactorycontroller.content.client.panel;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.DashboardAssemblyManager;
import io.github.nbcss.createfactorycontroller.content.block.DashboardOrientation;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Cursor unprojection uses the last world camera matrices, not the GUI's orthographic projection. */
@EventBusSubscriber(modid = CreateFactoryController.MODID, value = Dist.CLIENT)
public final class DashboardCursor {
    private static Matrix4f inverse;
    private static Vec3 cameraPosition;
    private DashboardCursor() {}
    @SubscribeEvent public static void capture(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        DashboardRenderDiagnostics.cameraCaptured(true);
        inverse = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert();
        cameraPosition = event.getCamera().getPosition();
    }
    public record Hit(VirtualComponentPosition cell, double pixelX, double pixelY) {}
    public static Hit hit(FactoryControllerMenu menu, double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (inverse == null || cameraPosition == null || mc.level == null || mc.player == null) return null;
        Vector4f far = new Vector4f((float) (mouseX / mc.getWindow().getGuiScaledWidth() * 2 - 1),
                (float) (1 - mouseY / mc.getWindow().getGuiScaledHeight() * 2), 1, 1);
        inverse.transform(far); far.div(far.w);
        Vec3 direction = new Vec3(far.x, far.y, far.z).normalize();
        double reach = mc.player.blockInteractionRange();
        var result = mc.level.clip(new ClipContext(cameraPosition, cameraPosition.add(direction.scale(reach + 6)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (result.getType() != HitResult.Type.BLOCK || mc.player.getEyePosition().distanceTo(result.getLocation()) > reach) return null;
        var tile = DashboardAssemblyManager.tileAt(mc.level, result.getBlockPos());
        if (tile == null || tile.dashboard().core == null || !tile.dashboard().core.equals(menu.controllerPos)
                || !tile.dashboard().identity.equals(menu.controllerIdentity)) return null;
        var orientation = DashboardOrientation.of(tile.getBlockState());
        if (result.getDirection() != orientation.facing()) return null;
        Vec3 local = result.getLocation().subtract(Vec3.atCenterOf(result.getBlockPos()));
        double u = .5 + local.x * orientation.right().getStepX() + local.z * orientation.right().getStepZ();
        double v = .5 - local.y;
        int edges = tile.dashboard().edges;
        double inset = 2 / 64.0;
        if (((edges & 1) == 0 && u < inset) || ((edges & 4) == 0 && u > 1 - inset)
                || ((edges & 2) == 0 && v < inset) || ((edges & 8) == 0 && v > 1 - inset)) return null;
        double px = (tile.dashboard().originX + tile.dashboard().tileX * 4) * 16 + u * 64;
        double py = (tile.dashboard().originY + tile.dashboard().tileY * 4) * 16 + v * 64;
        return new Hit(new VirtualComponentPosition((int) Math.floor(px / 16), (int) Math.floor(py / 16)), px, py);
    }
}
