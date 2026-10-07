package io.github.gamePackage.editor;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Drag-to-pan and zoom-towards-cursor for an orthographic camera. */
class CameraControl {
    private static final float MIN_ZOOM = 0.05f;
    private static final float MAX_ZOOM = 20f;
    private static final float ZOOM_STEP = 1.1f;

    private final Viewport viewport;
    private final OrthographicCamera camera;
    private final Vector2 before = new Vector2();
    private final Vector2 after = new Vector2();

    CameraControl(Viewport viewport) {
        this.viewport = viewport;
        this.camera = (OrthographicCamera) viewport.getCamera();
    }

    /** Moves the camera so the world follows the cursor by the given screen-pixel delta. */
    void pan(float screenDeltaX, float screenDeltaY) {
        camera.position.add(-screenDeltaX * camera.zoom, screenDeltaY * camera.zoom, 0f);
        camera.update();
    }

    /** Zooms while keeping the world point under the cursor fixed. */
    void zoomAt(int screenX, int screenY, float amount) {
        viewport.unproject(before.set(screenX, screenY));
        camera.zoom = MathUtils.clamp(camera.zoom * (float) Math.pow(ZOOM_STEP, amount), MIN_ZOOM, MAX_ZOOM);
        camera.update();
        viewport.unproject(after.set(screenX, screenY));
        camera.position.add(before.x - after.x, before.y - after.y, 0f);
        camera.update();
    }

    /** Centres on a rectangle and zooms so it fills most of the view. */
    void fit(float x, float y, float width, float height) {
        float zoomX = width / (viewport.getScreenWidth() * 0.85f);
        float zoomY = height / (viewport.getScreenHeight() * 0.85f);
        camera.zoom = MathUtils.clamp(Math.max(zoomX, zoomY), MIN_ZOOM, MAX_ZOOM);
        camera.position.set(x + width / 2f, y + height / 2f, 0f);
        camera.update();
    }
}
