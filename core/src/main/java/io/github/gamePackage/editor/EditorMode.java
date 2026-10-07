package io.github.gamePackage.editor;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.gamePackage.assets.SpriteAsset;

/** One tab of the asset editor. Clicks the mode doesn't take pan the camera. */
abstract class EditorMode {
    final EditorContext ctx;
    /** Camera x, y and zoom while this mode isn't shown. */
    final float[] cameraState = new float[3];
    boolean cameraPlaced;

    EditorMode(EditorContext ctx) {
        this.ctx = ctx;
    }

    /** The mode's settings panel, shown on the right. */
    abstract Table panel();

    /** Centres the camera on the mode's content. */
    abstract void fitCamera();

    abstract void render();

    void update(float delta) {
    }

    void enter() {
    }

    /** Called before switching away; save pending work here. */
    void exit() {
    }

    /** An asset was picked in the list. */
    void assetSelected(SpriteAsset asset) {
    }

    /** The asset whose row the list should highlight in this mode. */
    SpriteAsset listSelection() {
        return null;
    }

    /** Returns true to take the click (and the drag that follows); false lets the camera pan. */
    boolean touchDown(float x, float y, int button) {
        return false;
    }

    void touchDragged(float x, float y) {
    }

    void touchUp() {
    }

    boolean keyDown(int keycode, boolean ctrl) {
        return false;
    }

    void save() {
    }

    /** The catalog was rebuilt; swap asset references for the new objects. */
    void afterRescan() {
    }

    void dispose() {
    }
}
