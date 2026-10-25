package io.github.nbcss.createfactorycontroller.content.render;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PanelSurfaceTest {
    @Test void smallItemIconsKeepTheirPhysicalDepth() {
        for (float scale : new float[] {.375f, .5f, 1f}) {
            var pose = new PoseStack();
            var graphics = new WorldPanelCanvasGraphics(pose, null, 0);
            graphics.scaleItemIcon(scale);
            var item = pose.last().pose().transformPosition(new Vector3f(8, 8, PanelSurface.ITEM.depth()));
            assertEquals(8 * scale, item.x(), .00001f);
            assertEquals(PanelSurface.ITEM.depth(), item.z(), .00001f);
            assertTrue(item.z() > PanelSurface.INDICATOR.depth());
        }
    }
    @Test void overlappingSurfacesHaveDistinctMeaningfulDepths() {
        float previous = 0;
        for (PanelSurface surface : PanelSurface.values()) {
            assertTrue(surface.depth() - previous >= .039f, surface.name());
            previous = surface.depth();
        }
        assertTrue(PanelSurface.SYMBOL.depth() > PanelSurface.FACE.depth());
        assertTrue(PanelSurface.SYMBOL.depth() > PanelSurface.SYMBOL_BACKGROUND.depth());
        assertTrue(PanelSurface.LABEL.depth() > PanelSurface.SELECTION.depth());
    }

    @Test void ghostStackDoesNotIntersectLiveStack() {
        assertTrue(PanelSurface.PREVIEW_LIFT + PanelSurface.BACKPLATE.depth() > PanelSurface.LABEL.depth());
        assertTrue((PanelSurface.PREVIEW_LIFT + PanelSurface.LABEL.depth()) / PanelSurface.PIXELS_PER_BLOCK < .08f);
    }

    @Test void sharedBodyModelEndsBehindInstrumentFace() throws Exception {
        var resource = getClass().getResourceAsStream("/assets/createfactorycontroller/models/block/dashboard_instrument_bezel.json");
        assertNotNull(resource);
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            var model = JsonParser.parseReader(reader).getAsJsonObject();
            float maximumZ = 0;
            for (var element : model.getAsJsonArray("elements"))
                maximumZ = Math.max(maximumZ, element.getAsJsonObject().getAsJsonArray("to").get(2).getAsFloat() / 16);
            assertEquals(PanelSurface.BODY_DEPTH, maximumZ, .00001f);
            assertTrue(PanelSurface.BACKPLATE.depth() + maximumZ < PanelSurface.FACE.depth());
        }
    }
}
