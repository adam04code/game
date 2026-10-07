package io.github.gamePackage.assets;

/**
 * Editor settings for one asset. Bounds are stored as x,y pairs normalised to 0..1 over the base image
 * (origin bottom-left), so they survive resolution changes; the flipped side uses them mirrored.
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
    public float[] collision = new float[0];
    public float[] shadow = new float[0];

    public AssetMeta() {
    }

    public AssetMeta(String id) {
        this.id = id;
    }

    /** Whether the game should use a processed copy instead of the source image. */
    public boolean needsProcessing() {
        return removeGreen || halvings > 0;
    }
}
