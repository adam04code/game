package io.github.gamePackage.editor;

import io.github.gamePackage.assets.SpriteAsset;

/** An asset placed in the editor's test scene. (x, y) is the bottom-centre of the image. */
public class SceneObject {
    public String assetId;
    public float x;
    public float y;
    /** Shows the flipped side, i.e. the object rotated. */
    public boolean flipped;
    public transient SpriteAsset asset;

    public SceneObject() {
    }

    public SceneObject(SpriteAsset asset, float x, float y, boolean flipped) {
        this.asset = asset;
        this.assetId = asset.id;
        this.x = x;
        this.y = y;
        this.flipped = flipped;
    }
}
