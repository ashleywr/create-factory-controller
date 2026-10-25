package io.github.nbcss.createfactorycontroller.content.render;

/**
 * Physical surfaces in cabinet-pixel units (64 pixels = one block), not submission order.
 * Overlapping artwork must use different surfaces. The GUI backend deliberately ignores these depths.
 * Keep gaps meaningful: microscopic epsilons can collapse in the world depth buffer at oblique angles.
 */
public enum PanelSurface {
    BACKPLATE(.05f), BACKPLATE_DETAIL(.12f),
    CABLE_HORIZONTAL(.20f), CABLE_VERTICAL(.24f), CONNECTION_MARK(.30f),
    FACE(.55f),
    DETAIL_UP(.70f), DETAIL_RIGHT(.74f), DETAIL_DOWN(.78f), DETAIL_LEFT(.82f),
    SYMBOL_BACKGROUND(.95f), SYMBOL(1.10f), INDICATOR(1.25f),
    ITEM(1.45f), HOVER(2.00f), SELECTION(2.20f), LABEL(2.40f);

    public static final float PIXELS_PER_BLOCK = 64;
    /** One complete surface stack above the live instruments, shared by every preview path. */
    public static final float PREVIEW_LIFT = 2.60f;
    public static final float BODY_DEPTH = .40f;

    private final float depth;
    PanelSurface(float depth) { this.depth = depth; }
    public float depth() { return depth; }
}
