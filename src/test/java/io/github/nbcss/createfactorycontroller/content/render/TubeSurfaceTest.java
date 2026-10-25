package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.arithmetic.ArithmeticOperator;
import io.github.nbcss.createfactorycontroller.content.component.arithmetic.ArithmeticTubeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.arithmetic.BuiltinOperator;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionGraph;
import io.github.nbcss.createfactorycontroller.content.component.gauge.LogicalTubeBehaviour;
import io.github.nbcss.createfactorycontroller.content.gui.widget.VirtualArithmeticTubeWidget;
import io.github.nbcss.createfactorycontroller.content.gui.widget.VirtualLogicalTubeWidget;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TubeSurfaceTest {
    @Test void everyLogicModeSeparatesTheSymbolFromItsFaceInLiveAndGhostDrawing() {
        for (var mode : LogicalTubeBehaviour.Mode.values()) for (boolean powered : new boolean[] {false, true}) {
            var behaviour = new LogicalTubeBehaviour(null, new VirtualComponentPosition(2, 3), null) {
                @Override public Mode getMode() { return mode; }
                @Override public boolean isPowered() { return powered; }
            };
            var widget = new VirtualLogicalTubeWidget(behaviour);
            var graphics = new RecordingGraphics();
            var params = new FactoryControllerCanvasRenderer.Parameters(graphics, new Vector2d(), false, Set.of());
            widget.renderBack(params);
            widget.renderFront(params);
            var live = List.copyOf(graphics.surfaces);
            assertEquals(List.of(PanelSurface.BACKPLATE, PanelSurface.FACE, PanelSurface.SYMBOL), live);
            graphics.surfaces.clear();
            widget.renderGhost(params);
            assertEquals(live, graphics.surfaces);
        }
    }

    @Test void everyArithmeticOperatorSeparatesItsBorderAndSymbolInLiveAndGhostDrawing() {
        for (var operator : BuiltinOperator.values()) {
            var behaviour = new ArithmeticTubeBehaviour(null, new VirtualComponentPosition(0, 0), null) {
                @Override public ArithmeticOperator getOperator() { return operator; }
            };
            behaviour.setGraph(new ConnectionGraph());
            var widget = new VirtualArithmeticTubeWidget(behaviour);
            var graphics = new RecordingGraphics();
            var params = new FactoryControllerCanvasRenderer.Parameters(graphics, new Vector2d(), false, Set.of());
            widget.renderBack(params);
            widget.renderFront(params);
            var live = List.copyOf(graphics.surfaces);
            assertEquals(List.of(PanelSurface.BACKPLATE, PanelSurface.FACE, PanelSurface.SYMBOL_BACKGROUND, PanelSurface.SYMBOL), live);
            graphics.surfaces.clear();
            widget.renderGhost(params);
            assertEquals(live, graphics.surfaces);
        }
    }

    private static final class RecordingGraphics implements PanelCanvasGraphics {
        final PoseStack pose = new PoseStack();
        final List<PanelSurface> surfaces = new ArrayList<>();
        @Override public PoseStack pose() { return pose; }
        @Override public MultiBufferSource bufferSource() { throw new AssertionError("Unexpected direct buffer access"); }
        @Override public void sprite(PanelSurface surface, ResourceLocation sprite, int x, int y, int width, int height, int color, boolean emissive) {
            surfaces.add(surface);
        }
        @Override public void fill(PanelSurface surface, int x0, int y0, int x1, int y1, int color) { surfaces.add(surface); }
        @Override public void renderItem(ItemStack stack, int x, int y) { throw new AssertionError("Unexpected item"); }
        @Override public float labelDepth() { return PanelSurface.LABEL.depth(); }
        @Override public int labelLight() { return 0; }
    }
}
