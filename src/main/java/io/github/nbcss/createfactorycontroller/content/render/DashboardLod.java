package io.github.nbcss.createfactorycontroller.content.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Visual policy only: screen pixels per instrument, independent of GUI scale and simulation. */
public enum DashboardLod {
    FULL, SIMPLIFIED, OVERVIEW;

    public boolean draws(PanelSurface surface) {
        return this == FULL || this == SIMPLIFIED && (surface == PanelSurface.BACKPLATE
                || surface == PanelSurface.BACKPLATE_DETAIL || surface == PanelSurface.FACE
                || surface == PanelSurface.INDICATOR);
    }

    public DashboardLod forEditing(boolean activeSession, double distanceSquared, double reach) {
        return activeSession && distanceSquared <= (reach + 1) * (reach + 1) ? FULL : this;
    }

    public static final class State {
        private DashboardLod current;
        public DashboardLod update(double pixels) {
            if (!Double.isFinite(pixels) || pixels < 0) pixels = 0;
            if (current == null) current = pixels >= 24 ? FULL : pixels >= 10 ? SIMPLIFIED : OVERVIEW;
            else current = switch (current) {
                case FULL -> pixels >= 20.4 ? FULL : pixels >= 8.5 ? SIMPLIFIED : OVERVIEW;
                case SIMPLIFIED -> pixels >= 27.6 ? FULL : pixels < 8.5 ? OVERVIEW : SIMPLIFIED;
                case OVERVIEW -> pixels >= 27.6 ? FULL : pixels >= 11.5 ? SIMPLIFIED : OVERVIEW;
            };
            return current;
        }
    }

    /** Project a quarter-block instrument; the smaller edge suppresses grazing-angle detail. */
    public static double projectedPixels(Matrix4f viewProjection, Vector3f center, Vector3f right,
                                         int width, int height) {
        if (width <= 0 || height <= 0) return 0;
        Vector4f[] corners = new Vector4f[4];
        for (int i = 0; i < 4; i++) {
            float x = (i == 0 || i == 3) ? -.125f : .125f;
            float y = i < 2 ? -.125f : .125f;
            var point = new Vector4f(center.x + right.x * x, center.y + y, center.z + right.z * x, 1);
            viewProjection.transform(point);
            if (point.w <= .0001f || !Float.isFinite(point.w)) return 0;
            point.div(point.w);
            point.x *= width * .5f; point.y *= height * .5f;
            corners[i] = point;
        }
        double pixels = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            var a = corners[i]; var b = corners[(i + 1) % 4];
            pixels = Math.min(pixels, Math.hypot(a.x - b.x, a.y - b.y));
        }
        return Double.isFinite(pixels) ? pixels : 0;
    }
}
