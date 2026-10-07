package io.github.gamePackage.assets;

/**
 * Editor settings for one asset. Bounds are lists of polygons, each stored as x,y pairs normalised to 0..1 over the
 * base image (origin bottom-left), so they survive resolution changes; the flipped side uses them mirrored.
 */
public class AssetMeta {
    public String id;
    public boolean removeGreen;
    /** How green (relative to the background colour) a pixel must be before it starts turning transparent. */
    public float keyThreshold = 0.45f;
    /** Width of the fade from opaque to fully transparent. */
    public float keySoftness = 0.3f;
    /** How many times the resolution has been halved. */
    public int halvings;
    /** The asset intentionally has no collision (grass, decals...), so the asset check doesn't flag it. */
    public boolean noCollision;
    /** The image has no green screen (checked automatically), so it needs no green removal. */
    public boolean noGreenBackground;
    /** Size multiplier newly spawned instances get. */
    public float defaultScale = 1f;
    public float[][] collisions = new float[0][];
    public float[][] shadows = new float[0][];

    /** Old single-polygon format; read once and moved into {@link #collisions}. */
    @Deprecated
    public float[] collision;
    /** Old single-polygon format; read once and moved into {@link #shadows}. */
    @Deprecated
    public float[] shadow;

    public AssetMeta() {
    }

    public AssetMeta(String id) {
        this.id = id;
    }

    /** Whether the game should use a processed copy instead of the source image. */
    public boolean needsProcessing() {
        return removeGreen || halvings > 0;
    }

    /** Whether at least one collision polygon is complete (3+ points). */
    public boolean hasCollision() {
        for (float[] shape : collisions) if (shape.length >= 6) return true;
        return false;
    }

    /** True when nothing differs from a freshly created entry, so it needn't be saved. */
    public boolean isDefault() {
        AssetMeta fresh = new AssetMeta();
        return !removeGreen && halvings == 0 && !noCollision && !noGreenBackground
            && collisions.length == 0 && shadows.length == 0
            && keyThreshold == fresh.keyThreshold && keySoftness == fresh.keySoftness
            && defaultScale == fresh.defaultScale;
    }

    /** Converts data saved before assets could have several bounds. */
    @SuppressWarnings("deprecation")
    void migrate() {
        if (collision != null && collision.length > 0 && collisions.length == 0) collisions = new float[][] {collision};
        if (shadow != null && shadow.length > 0 && shadows.length == 0) shadows = new float[][] {shadow};
        collision = null;
        shadow = null;
        if (collisions == null) collisions = new float[0][];
        if (shadows == null) shadows = new float[0][];
        if (defaultScale <= 0f) defaultScale = 1f;
    }
}
