package io.github.gamePackage;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private static final String TERRAIN_PATH = "2Kterrain/Isometric_game_terrain_map_2K_20261007014021.jpg";
    private static final float MIN_ZOOM = 0.25f;
    private static final float ZOOM_STEP = 1.1f;

    private SpriteBatch batch;
    private Texture terrain;
    private OrthographicCamera camera;
    private ScreenViewport viewport;

    private int lastDragX, lastDragY;
    private boolean dragging;

    @Override
    public void create() {
        batch = new SpriteBatch();
        terrain = new Texture(Gdx.files.internal(TERRAIN_PATH), true);
        terrain.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);

        camera = new OrthographicCamera();
        viewport = new ScreenViewport(camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(terrain.getWidth() / 2f, terrain.getHeight() / 2f, 0f);

        Gdx.input.setInputProcessor(new CameraController());
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
        clampCamera();
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        viewport.apply();
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.draw(terrain, 0f, 0f);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        terrain.dispose();
    }

    /** Largest zoom at which the map still covers the whole screen. */
    private float maxZoom() {
        float fitX = terrain.getWidth() / (float) viewport.getScreenWidth();
        float fitY = terrain.getHeight() / (float) viewport.getScreenHeight();
        return Math.max(MIN_ZOOM, Math.min(fitX, fitY));
    }

    /** Keeps the zoom in range and the view inside the map; centers the map on an axis it doesn't fill. */
    private void clampCamera() {
        camera.zoom = MathUtils.clamp(camera.zoom, MIN_ZOOM, maxZoom());
        float halfWidth = camera.viewportWidth * camera.zoom / 2f;
        float halfHeight = camera.viewportHeight * camera.zoom / 2f;
        camera.position.x = clampAxis(camera.position.x, halfWidth, terrain.getWidth());
        camera.position.y = clampAxis(camera.position.y, halfHeight, terrain.getHeight());
        camera.update();
    }

    private static float clampAxis(float value, float halfView, float mapSize) {
        if (halfView * 2f >= mapSize) return mapSize / 2f;
        return MathUtils.clamp(value, halfView, mapSize - halfView);
    }

    /** Click-and-drag pans the camera; the mouse wheel zooms toward the cursor. */
    private class CameraController extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            dragging = true;
            lastDragX = screenX;
            lastDragY = screenY;
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (!dragging) return false;
            // Move opposite to the drag so the map follows the cursor. Screen y points down, world y points up.
            camera.position.add(-(screenX - lastDragX) * camera.zoom, (screenY - lastDragY) * camera.zoom, 0f);
            clampCamera();
            lastDragX = screenX;
            lastDragY = screenY;
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            dragging = false;
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (amountY == 0f) return false;
            int mouseX = Gdx.input.getX();
            int mouseY = Gdx.input.getY();
            Vector3 before = camera.unproject(new Vector3(mouseX, mouseY, 0f));

            camera.zoom *= (float) Math.pow(ZOOM_STEP, amountY);
            camera.zoom = MathUtils.clamp(camera.zoom, MIN_ZOOM, maxZoom());
            camera.update();

            // Shift the camera so the world point under the cursor stays put.
            Vector3 after = camera.unproject(new Vector3(mouseX, mouseY, 0f));
            camera.position.add(before.x - after.x, before.y - after.y, 0f);
            clampCamera();
            return true;
        }
    }
}
