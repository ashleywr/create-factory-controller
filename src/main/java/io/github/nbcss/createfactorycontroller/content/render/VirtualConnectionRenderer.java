package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.util.List;
import java.util.Map;

/**
 * Draws connection arrows between virtual components on the canvas, reusing Create's own
 * factory-panel connection sprite ({@code create:block/factory_panel_connections}).
 */
@OnlyIn(Dist.CLIENT)
public class VirtualConnectionRenderer {

    private static final int CELL = 16;
    private static final int FRAME_SIZE = 16; // pixels
    private static final float FRAME_TIME = 2f; // ticks per frame
    private static final int N_FRAMES = 8;

    private static final ResourceLocation TEX_STATIC =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/connection/static.png");
    private static final ResourceLocation TEX_ANIMATED =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/connection/animated.png");

    private enum Direction {
        UP(0, -1),
        DOWN(0, 1),
        LEFT(-1, 0),
        RIGHT(1, 0);

        final Vector2ic vector;
        Direction(int x, int y) {
            vector = new Vector2i(x, y);
        }

        Vector2i vector(int scalar) {
            return new Vector2i(vector).mul(scalar);
        }
    }

    // Subtexture rect coordinates in a frame.
    // (ox, oy) = for heads and tails: origin which is aligned with the center of a cell
    private record Subtexture(int u, int v, int w, int h, int ox, int oy) {
        public static final Map<Direction, Subtexture> LINES = Map.of(
                Direction.UP,    new Subtexture( 0,  0,  4,  8),
                Direction.DOWN,  new Subtexture( 4,  0,  4,  8),
                Direction.LEFT,  new Subtexture( 0,  8,  8,  4),
                Direction.RIGHT, new Subtexture( 0, 12,  8,  4)
        );
        public static final Map<Direction, Subtexture> HEADS = Map.of(
                Direction.UP,    new Subtexture( 8,  0,  4,  4,  2, -6),
                Direction.DOWN,  new Subtexture(12,  0,  4,  4,  2, 10),
                Direction.LEFT,  new Subtexture( 8,  4,  4,  4, -6,  2),
                Direction.RIGHT, new Subtexture(12,  4,  4,  4, 10,  2)
        );
        public static final Map<Direction, Subtexture> TAILS = Map.of(
                Direction.UP,    new Subtexture( 0,  0,  4,  2,  2,  8),
                Direction.DOWN,  new Subtexture( 4,  6,  4,  2,  2, -6),
                Direction.LEFT,  new Subtexture( 0,  8,  2,  4,  8,  2),
                Direction.RIGHT, new Subtexture( 6, 12,  2,  4, -6,  2)
        );

        private Subtexture(int u, int v, int w, int h) {
            this(u, v, w, h, 0, 0);
        }
    }

    private final List<Vector2i> path;
    private final int color;
    private final boolean animated;

    protected VirtualConnectionRenderer(List<Vector2i> path, int color, boolean animated) {
        this.path = path;
        this.color = FastColor.ARGB32.alpha(color) == 0 ? FastColor.ARGB32.opaque(color) : color;
        this.animated = animated;
    }

    public static VirtualConnectionRenderer create(List<Vector2i> path, int color, boolean animated) {
        return new VirtualConnectionRenderer(path, color, animated);
    }

    /** Draws the connection path in its configured color. Batched. */
    public void drawPath(MultiBufferSource bufferSource, PoseStack pose) {

        int frameIndex = !animated ? 0 : (int) (AnimationTickHolder.getRenderTime() / FRAME_TIME) % N_FRAMES;

        // Walk every line of the path.
        for (int i = 0; i < path.size() - 1; i++) {
            Vector2ic a = path.get(i), b = path.get(i + 1);

            Direction direction;
            if      (a.y() > b.y()) direction = Direction.UP;
            else if (a.y() < b.y()) direction = Direction.DOWN;
            else if (a.x() > b.x()) direction = Direction.LEFT;
            else if (a.x() < b.x()) direction = Direction.RIGHT;
            else continue;

            boolean tail = i == 0;
            boolean head = i == path.size() - 2;

            // Pixel coordinates of cell centers
            Vector2i pa = new Vector2i(a).mul(CELL).add(CELL/2, CELL/2);
            Vector2i pb = new Vector2i(b).mul(CELL).add(CELL/2, CELL/2);

            // Draw line, each line texture has length of half a cell.
            Vector2i lineStart = new Vector2i(pa).add(direction.vector(CELL/4));
            Vector2i lineEnd   = new Vector2i(pb).sub(direction.vector(CELL/4));

            if (tail) lineStart.add(direction.vector(CELL/2));
            if (head)   lineEnd.sub(direction.vector(CELL/2));

            int lineLength = (lineEnd.x - lineStart.x) * direction.vector.x()
                    + (lineEnd.y - lineStart.y) * direction.vector.y();
            if (lineLength >= 0) {
                Vector2i lineMin = new Vector2i(lineStart).min(lineEnd);
                Vector2i lineSize = new Vector2i(lineStart).sub(lineEnd).absolute();

                var lineSt = Subtexture.LINES.get(direction);
                getSpriteRenderer(lineSt, frameIndex).render(bufferSource, pose,
                        lineMin.x - lineSt.w/2, lineMin.y - lineSt.h/2,
                        lineSize.x + lineSt.w, lineSize.y + lineSt.h);
            }

            // Draw tail and head
            if (tail) {
                var tailSt = Subtexture.TAILS.get(direction);
                getSpriteRenderer(tailSt, frameIndex).render(bufferSource, pose,
                        pa.x - tailSt.ox, pa.y - tailSt.oy,
                        tailSt.w, tailSt.h);
            }
            if (head) {
                var headSt = Subtexture.HEADS.get(direction);
                getSpriteRenderer(headSt, frameIndex).render(bufferSource, pose,
                        pb.x - headSt.ox, pb.y - headSt.oy,
                        headSt.w, headSt.h);
            }
        }
    }

    public void drawPath(WorldPanelCanvasGraphics graphics, int clipX, int clipY) {

        int frameIndex = !animated ? 0 : (int) (AnimationTickHolder.getRenderTime() / FRAME_TIME) % N_FRAMES;

        // Walk every line of the path.
        for (int i = 0; i < path.size() - 1; i++) {
            Vector2ic a = path.get(i), b = path.get(i + 1);

            Direction direction;
            if      (a.y() > b.y()) direction = Direction.UP;
            else if (a.y() < b.y()) direction = Direction.DOWN;
            else if (a.x() > b.x()) direction = Direction.LEFT;
            else if (a.x() < b.x()) direction = Direction.RIGHT;
            else continue;

            boolean tail = i == 0;
            boolean head = i == path.size() - 2;

            // Pixel coordinates of cell centers
            Vector2i pa = new Vector2i(a).mul(CELL).add(CELL/2, CELL/2);
            Vector2i pb = new Vector2i(b).mul(CELL).add(CELL/2, CELL/2);

            // Draw line, each line texture has length of half a cell.
            Vector2i lineStart = new Vector2i(pa).add(direction.vector(CELL/4));
            Vector2i lineEnd   = new Vector2i(pb).sub(direction.vector(CELL/4));

            if (tail) lineStart.add(direction.vector(CELL/2));
            if (head)   lineEnd.sub(direction.vector(CELL/2));

            int lineLength = (lineEnd.x - lineStart.x) * direction.vector.x()
                    + (lineEnd.y - lineStart.y) * direction.vector.y();
            if (lineLength >= 0) {
                Vector2i lineMin = new Vector2i(lineStart).min(lineEnd);
                Vector2i lineSize = new Vector2i(lineStart).sub(lineEnd).absolute();

                var lineSt = Subtexture.LINES.get(direction);
                drawWorld(graphics, lineSt, frameIndex, clipX, clipY,
                        lineMin.x - lineSt.w/2, lineMin.y - lineSt.h/2,
                        lineSize.x + lineSt.w, lineSize.y + lineSt.h);
            }

            // Draw tail and head
            if (tail) {
                var tailSt = Subtexture.TAILS.get(direction);
                drawWorld(graphics, tailSt, frameIndex, clipX, clipY,
                        pa.x - tailSt.ox, pa.y - tailSt.oy,
                        tailSt.w, tailSt.h);
            }
            if (head) {
                var headSt = Subtexture.HEADS.get(direction);
                drawWorld(graphics, headSt, frameIndex, clipX, clipY,
                        pb.x - headSt.ox, pb.y - headSt.oy,
                        headSt.w, headSt.h);
            }
        }
    }


    private void drawWorld(WorldPanelCanvasGraphics gfx, Subtexture st, int frame, int clipX, int clipY,
                           int x, int y, int width, int height) {
        ResourceLocation texture = animated ? TEX_ANIMATED : TEX_STATIC;
        float textureHeight = animated ? 128f : 16f;
        for (int dy = 0; dy < height; dy += st.h) for (int dx = 0; dx < width; dx += st.w) {
            float x0 = Math.max(x + dx, clipX), y0 = Math.max(y + dy, clipY);
            float x1 = Math.min(Math.min(x + dx + st.w, x + width), clipX + 64);
            float y1 = Math.min(Math.min(y + dy + st.h, y + height), clipY + 64);
            if (x1 <= x0 || y1 <= y0) continue;
            gfx.quad(width > height ? PanelSurface.CABLE_HORIZONTAL : PanelSurface.CABLE_VERTICAL,
                    texture, x0, y0, x1, y1,
                    (st.u + x0 - x - dx) / 16f, (st.v + frame * 16 + y0 - y - dy) / textureHeight,
                    (st.u + x1 - x - dx) / 16f, (st.v + frame * 16 + y1 - y - dy) / textureHeight,
                    color, gfx.light());
        }
    }

    private TiledSpriteRenderer getSpriteRenderer(Subtexture st, int frameIndex) {
        return TiledSpriteRenderer.create(
                animated ? TEX_ANIMATED : TEX_STATIC,
                st.u, st.v + frameIndex * FRAME_SIZE,
                new GuiSpriteScaling.Tile(st.w, st.h)
        ).setColorARGB(color);
    }

}
