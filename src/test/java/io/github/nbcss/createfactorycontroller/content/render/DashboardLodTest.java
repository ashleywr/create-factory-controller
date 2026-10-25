package io.github.nbcss.createfactorycontroller.content.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DashboardLodTest {
    private static final Vector3f RIGHT = new Vector3f(1, 0, 0);
    private static Matrix4f projection(float fov) {
        return new Matrix4f().perspective((float) Math.toRadians(fov), 16f / 9, .05f, 1000f);
    }
    private static double pixels(double distance, int height, float fov) {
        return DashboardLod.projectedPixels(projection(fov), new Vector3f(0, 0, (float) -distance),
                RIGHT, height * 16 / 9, height);
    }

    @Test void initialLevelsUseReadableInstrumentSize() {
        assertEquals(DashboardLod.FULL, new DashboardLod.State().update(24));
        assertEquals(DashboardLod.SIMPLIFIED, new DashboardLod.State().update(10));
        assertEquals(DashboardLod.OVERVIEW, new DashboardLod.State().update(9.9));
    }

    @Test void hysteresisDoesNotChatterAroundEitherBoundary() {
        var state = new DashboardLod.State();
        assertEquals(DashboardLod.FULL, state.update(30));
        for (int i = 0; i < 100; i++) assertEquals(DashboardLod.FULL, state.update(i % 2 == 0 ? 23 : 25));
        assertEquals(DashboardLod.SIMPLIFIED, state.update(20));
        for (int i = 0; i < 100; i++) assertEquals(DashboardLod.SIMPLIFIED, state.update(i % 2 == 0 ? 23 : 25));
        assertEquals(DashboardLod.OVERVIEW, state.update(8));
        for (int i = 0; i < 100; i++) assertEquals(DashboardLod.OVERVIEW, state.update(i % 2 == 0 ? 9 : 11));
        assertEquals(DashboardLod.SIMPLIFIED, state.update(12));
        for (int i = 0; i < 100; i++) assertEquals(DashboardLod.SIMPLIFIED, state.update(i % 2 == 0 ? 9 : 11));
        assertEquals(DashboardLod.FULL, state.update(28));
    }

    @Test void teleportsCanSkipIntermediateTiers() {
        var state = new DashboardLod.State();
        assertEquals(DashboardLod.FULL, state.update(100));
        assertEquals(DashboardLod.OVERVIEW, state.update(1));
        assertEquals(DashboardLod.FULL, state.update(100));
        assertEquals(DashboardLod.OVERVIEW, state.update(Double.NaN));
    }

    @Test void footprintUsesPhysicalPixelsDistanceAndActualProjection() {
        double close = pixels(4, 1080, 70);
        assertEquals(1080 * .25 / (8 * Math.tan(Math.toRadians(35))), close, .001);
        assertEquals(close / 2, pixels(8, 1080, 70), .001);
        assertEquals(close * 2, pixels(4, 2160, 70), .001);
        assertTrue(pixels(4, 1080, 110) < close);
    }

    @Test void grazingAnglesReduceDetailWithoutDependingOnMouseRotation() {
        var center = new Vector3f(0, 0, -8);
        double straight = DashboardLod.projectedPixels(projection(70), center, RIGHT, 1920, 1080);
        double oblique = DashboardLod.projectedPixels(projection(70), center,
                new Vector3f(.2f, 0, .98f).normalize(), 1920, 1080);
        assertTrue(oblique < straight * .3);
    }

    @Test void behindCameraAndInvalidViewportsHaveNoReadableDetail() {
        assertEquals(0, DashboardLod.projectedPixels(projection(70), new Vector3f(0, 0, 8), RIGHT, 1920, 1080));
        assertEquals(0, DashboardLod.projectedPixels(projection(70), new Vector3f(0, 0, -8), RIGHT, 0, 1080));
    }

    @Test void simplifiedSurfacePolicyPreservesFacesAndLightsNotTinySymbols() {
        for (var surface : PanelSurface.values()) assertTrue(DashboardLod.FULL.draws(surface));
        assertTrue(DashboardLod.SIMPLIFIED.draws(PanelSurface.FACE));
        assertTrue(DashboardLod.SIMPLIFIED.draws(PanelSurface.INDICATOR));
        for (var surface : new PanelSurface[] {PanelSurface.SYMBOL, PanelSurface.SYMBOL_BACKGROUND,
                PanelSurface.DETAIL_UP, PanelSurface.CABLE_HORIZONTAL, PanelSurface.ITEM, PanelSurface.LABEL})
            assertFalse(DashboardLod.SIMPLIFIED.draws(surface));
        for (var surface : PanelSurface.values()) assertFalse(DashboardLod.OVERVIEW.draws(surface));
    }

    @Test void previewsKeepFullDetailEvenWhenLiveCanvasIsSimplified() {
        assertEquals(DashboardLod.FULL, new WorldPanelCanvasGraphics(null, null, 0, .5f).detail());
        var medium = new WorldPanelCanvasGraphics(null, null, 0, 1, DashboardLod.SIMPLIFIED);
        // Skipped artwork never asks Minecraft for a texture or starts an item renderer.
        assertDoesNotThrow(() -> medium.sprite(PanelSurface.SYMBOL, null, 0, 0, 16, 16, -1, false));
        assertDoesNotThrow(() -> medium.renderItem(null, 0, 0));
    }

    @Test void onlyNearbyActiveEditingOverridesVisualDetail() {
        assertEquals(DashboardLod.FULL, DashboardLod.OVERVIEW.forEditing(true, 4 * 4, 4.5));
        assertEquals(DashboardLod.FULL, DashboardLod.SIMPLIFIED.forEditing(true, 4 * 4, 4.5));
        assertEquals(DashboardLod.OVERVIEW, DashboardLod.OVERVIEW.forEditing(false, 4 * 4, 4.5));
        assertEquals(DashboardLod.OVERVIEW, DashboardLod.OVERVIEW.forEditing(true, 20 * 20, 4.5));
    }
}
