package io.github.gamePackage.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.gamePackage.assets.AssetMeta;
import io.github.gamePackage.assets.GameFiles;
import io.github.gamePackage.assets.SpriteAsset;
import io.github.gamePackage.render.Polygons;
import io.github.gamePackage.render.ShadowMask;

import java.util.Comparator;

/**
 * A test scene on the terrain: spawn assets, move, resize, rotate (swap to the flipped side) and delete them.
 * The layout is saved to {@code assets/data/editor_scene.json}.
 */
class SceneEditMode extends EditorMode {
    private static final String SCENE_PATH = "data/editor_scene.json";
    private static final float MIN_SCALE = 0.1f;
    private static final float MAX_SCALE = 4f;
    private static final float SCALE_STEP = 1.1f;

    public static class SceneFile {
        public String terrain;
        public Array<SceneObject> objects = new Array<>();
    }

    private final Json json = new Json(JsonWriter.OutputType.json);
    private final Vector2 cursor = new Vector2();
    private static final float[][] NO_SHAPES = new float[0][];
    private final Array<SceneObject> renderList = new Array<>();
    /** Stand-in object for the placement preview, so it casts and receives shadows like the real thing. */
    private final SceneObject ghost = new SceneObject();
    private final Array<float[]> receivedShadows = new Array<>();
    /** The terrain's shadow polygons in world space, for this frame. */
    private float[][] terrainShadows = NO_SHAPES;
    private final ShadowMask shadowMask = new ShadowMask();
    private final Array<SceneObject> objects = new Array<>();
    private final Array<SceneObject> drawOrder = new Array<>();
    private final Comparator<SceneObject> backToFront = new Comparator<SceneObject>() {
        @Override
        public int compare(SceneObject a, SceneObject b) {
            return Float.compare(b.y, a.y);
        }
    };

    private SceneObject selected;
    private SpriteAsset placing;
    private boolean placingFlipped;
    private int terrainIndex;
    private boolean showShadows = true;
    private boolean showCollision = true;
    private boolean dirty;
    private boolean moving;
    private float grabOffsetX;
    private float grabOffsetY;

    private final Table panel = new Table();
    private Label selectedLabel;
    private Label sizeLabel;
    private Slider sizeSlider;
    private final Array<TextButton> selectionButtons = new Array<>();
    private boolean syncingUi;

    SceneEditMode(EditorContext ctx) {
        super(ctx);
        json.setUsePrototypes(false);
        json.setElementType(SceneFile.class, "objects", SceneObject.class);
        buildPanel();
        load();
        syncSelection();
    }

    // ---------------------------------------------------------------- panel

    private void buildPanel() {
        panel.top();
        panel.defaults().growX().padBottom(5);

        ctx.section(panel, "Place");
        panel.add(ctx.button("Stop placing (Esc)", this::stopPlacing)).row();
        panel.add(ctx.button("Clear scene", () -> {
            objects.clear();
            select(null);
            dirty = true;
        })).row();

        ctx.section(panel, "Selected object");
        selectedLabel = ctx.wrapped("");
        panel.add(selectedLabel).row();
        TextButton rotate = selectionButton("Rotate (R)", this::rotate);
        TextButton delete = selectionButton("Delete (Del)", this::deleteSelected);
        panel.add(ctx.row(rotate, delete)).row();
        sizeLabel = new Label("", ctx.skin, "dim");
        panel.add(sizeLabel).row();
        sizeSlider = new Slider(MIN_SCALE, MAX_SCALE, 0.01f, false, ctx.skin);
        EditorContext.onChange(sizeSlider, () -> {
            if (!syncingUi) setScale(sizeSlider.getValue());
        });
        panel.add(sizeSlider).row();
        TextButton smaller = selectionButton("Smaller (-)", () -> stepScale(1f / SCALE_STEP));
        TextButton bigger = selectionButton("Bigger (+)", () -> stepScale(SCALE_STEP));
        panel.add(ctx.row(smaller, bigger)).row();
        TextButton setDefault = selectionButton("Set as default", this::saveDefaultScale);
        TextButton resetDefault = selectionButton("Reset to default", this::resetToDefaultScale);
        panel.add(ctx.row(setDefault, resetDefault)).row();

        ctx.section(panel, "View");
        TextButton shadowsButton = ctx.toggle("Shadows");
        shadowsButton.setChecked(true);
        EditorContext.onChange(shadowsButton, () -> showShadows = shadowsButton.isChecked());
        TextButton collisionButton = ctx.toggle("Collision");
        collisionButton.setChecked(true);
        EditorContext.onChange(collisionButton, () -> showCollision = collisionButton.isChecked());
        panel.add(ctx.row(shadowsButton, collisionButton)).row();
        panel.add(ctx.button("Next terrain", () -> {
            if (ctx.assets.catalog.terrains().isEmpty()) return;
            terrainIndex = (terrainIndex + 1) % ctx.assets.catalog.terrains().size;
            dirty = true;
        })).row();

        panel.add(ctx.button("Save scene (Ctrl+S)", this::save)).padTop(14).row();
        panel.add(ctx.help("Click an asset in the list, then click the map to spawn it; right-click or Esc stops. "
            + "Clicking a terrain in the list switches to it. "
            + "Drag objects to move them, drag empty ground to pan, wheel to zoom.")).padTop(6).row();
    }

    private TextButton selectionButton(String text, Runnable action) {
        TextButton button = ctx.button(text, action);
        selectionButtons.add(button);
        return button;
    }

    private void select(SceneObject object) {
        selected = object;
        syncSelection();
    }

    private void syncSelection() {
        boolean has = selected != null;
        for (TextButton button : selectionButtons) button.setDisabled(!has);
        sizeSlider.setDisabled(!has);
        syncingUi = true;
        if (has) {
            selectedLabel.setText(selected.asset.name.replace('_', ' '));
            sizeSlider.setValue(selected.scale);
            sizeLabel.setText(String.format("Size: %.2fx   (default %.2fx)", selected.scale,
                defaultScale(selected.asset)));
        } else {
            selectedLabel.setText("Nothing selected. Click an object.");
            sizeLabel.setText("Size: -");
        }
        syncingUi = false;
    }

    @Override
    Table panel() {
        return panel;
    }

    // ---------------------------------------------------------------- actions

    private float defaultScale(SpriteAsset asset) {
        AssetMeta meta = ctx.assets.meta.find(asset.id);
        return meta != null ? meta.defaultScale : 1f;
    }

    private void startPlacing(SpriteAsset asset) {
        if (asset == null) return;
        placing = asset;
        select(null);
        ctx.setStatus("Placing " + asset.name + ". Click to spawn, right-click or Esc to stop, R to rotate.");
    }

    private void stopPlacing() {
        if (placing != null) ctx.setStatus("");
        placing = null;
    }

    private void rotate() {
        if (placing != null) {
            placingFlipped = !placingFlipped;
        } else if (selected != null) {
            selected.flipped = !selected.flipped;
            dirty = true;
        }
    }

    private void deleteSelected() {
        if (selected == null) return;
        objects.removeValue(selected, true);
        select(null);
        dirty = true;
    }

    private void setScale(float scale) {
        if (selected == null) return;
        selected.scale = MathUtils.clamp(scale, MIN_SCALE, MAX_SCALE);
        dirty = true;
        syncSelection();
    }

    private void stepScale(float factor) {
        if (selected != null) setScale(selected.scale * factor);
    }

    /** Makes the selected object's size the default for its asset (used when spawning it). */
    private void saveDefaultScale() {
        if (selected == null) return;
        AssetMeta meta = ctx.assets.meta.getOrCreate(selected.assetId);
        meta.defaultScale = selected.scale;
        try {
            ctx.assets.meta.save();
        } catch (Exception e) {
            Gdx.app.error("AssetEditor", "Saving the default size failed", e);
            ctx.setStatus("Saving the default size failed: " + e.getMessage());
            return;
        }
        ctx.setStatus(String.format("Default size of %s is now %.2fx", selected.asset.name, meta.defaultScale));
        syncSelection();
    }

    private void resetToDefaultScale() {
        if (selected != null) setScale(defaultScale(selected.asset));
    }

    @Override
    void assetSelected(SpriteAsset asset) {
        if (asset.terrain) {
            terrainIndex = Math.max(0, ctx.assets.catalog.terrains().indexOf(asset, true));
            dirty = true;
            ctx.setStatus("Terrain: " + asset.name);
        } else {
            startPlacing(asset);
        }
    }

    @Override
    SpriteAsset listSelection() {
        return placing;
    }

    // ---------------------------------------------------------------- input

    @Override
    boolean touchDown(float x, float y, int button) {
        if (button == Input.Buttons.LEFT) {
            if (placing != null) {
                objects.add(new SceneObject(placing, x, y, placingFlipped, defaultScale(placing)));
                dirty = true;
                return true;
            }
            select(pick(x, y));
            if (selected != null) {
                moving = true;
                grabOffsetX = selected.x - x;
                grabOffsetY = selected.y - y;
                return true;
            }
        } else if (button == Input.Buttons.RIGHT && placing != null) {
            stopPlacing();
            return true;
        }
        return false;
    }

    @Override
    void touchDragged(float x, float y) {
        if (!moving || selected == null) return;
        selected.x = x + grabOffsetX;
        selected.y = y + grabOffsetY;
        dirty = true;
    }

    @Override
    void touchUp() {
        moving = false;
    }

    @Override
    boolean keyDown(int keycode, boolean ctrl) {
        switch (keycode) {
            case Input.Keys.R:
                rotate();
                return true;
            case Input.Keys.FORWARD_DEL:
            case Input.Keys.DEL:
                deleteSelected();
                return true;
            case Input.Keys.MINUS:
            case Input.Keys.NUMPAD_SUBTRACT:
                stepScale(1f / SCALE_STEP);
                return true;
            case Input.Keys.PLUS:
            case Input.Keys.EQUALS:
            case Input.Keys.NUMPAD_ADD:
                stepScale(SCALE_STEP);
                return true;
            case Input.Keys.ESCAPE:
                stopPlacing();
                select(null);
                return true;
            default:
                return false;
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    void fitCamera() {
        Texture terrain = currentTerrainTexture();
        if (terrain != null) {
            float[] size = terrainSize(terrain);
            ctx.cameraControl.fit(0f, 0f, size[0], size[1]);
        } else {
            ctx.cameraControl.fit(-512f, -512f, 1024f, 1024f);
        }
    }

    private SpriteAsset currentTerrain() {
        Array<SpriteAsset> terrains = ctx.assets.catalog.terrains();
        if (terrains.isEmpty()) return null;
        return terrains.get(Math.min(terrainIndex, terrains.size - 1));
    }

    private Texture currentTerrainTexture() {
        SpriteAsset terrain = currentTerrain();
        return terrain == null ? null : ctx.assets.texture(terrain, false);
    }

    /** World size of the terrain: always its full-resolution size, so halving it doesn't move the world. */
    private float[] terrainSize(Texture texture) {
        AssetMeta meta = ctx.assets.meta.find(currentTerrain().id);
        int factor = meta == null ? 1 : 1 << meta.halvings;
        return new float[] {texture.getWidth() * factor, texture.getHeight() * factor};
    }

    /** The terrain's polygons (normalised to its image) in world space; the terrain sits at the origin. */
    private float[][] terrainShapes(float[] size, boolean shadows) {
        AssetMeta meta = ctx.assets.meta.find(currentTerrain().id);
        if (meta == null) return NO_SHAPES;
        float[][] normalized = shadows ? meta.shadows : meta.collisions;
        float[][] world = new float[normalized.length][];
        for (int s = 0; s < normalized.length; s++) {
            float[] points = normalized[s];
            world[s] = new float[points.length];
            for (int i = 0; i + 1 < points.length; i += 2) {
                world[s][i] = points[i] * size[0];
                world[s][i + 1] = points[i + 1] * size[1];
            }
        }
        return world;
    }

    /** Objects lower on the screen are in front, so draw from the top of the map down. */
    private void sortDrawOrder() {
        drawOrder.clear();
        drawOrder.addAll(objects);
        drawOrder.sort(backToFront);
    }

    /** Front-most object under the point, ignoring transparent pixels. */
    private SceneObject pick(float worldX, float worldY) {
        sortDrawOrder();
        for (int i = drawOrder.size - 1; i >= 0; i--) {
            SceneObject object = drawOrder.get(i);
            Texture texture = ctx.assets.texture(object.asset, object.flipped);
            if (texture == null) continue;
            float width = texture.getWidth() * object.scale;
            float height = texture.getHeight() * object.scale;
            float u = (worldX - (object.x - width / 2f)) / width;
            float v = 1f - (worldY - object.y) / height;
            if (u < 0f || u > 1f || v < 0f || v > 1f) continue;
            if (object.flipped && !object.asset.hasFlipped()) u = 1f - u;
            String path = ctx.assets.loadedPath(object.asset, object.flipped);
            if (path == null || ctx.hitMasks.isOpaque(path, u, v)) return object;
        }
        return null;
    }

    @Override
    void render() {
        Texture terrain = currentTerrainTexture();
        terrainShadows = NO_SHAPES;
        float[][] terrainCollisions = NO_SHAPES;
        if (terrain != null) {
            float[] size = terrainSize(terrain);
            ctx.batch.begin();
            ctx.batch.draw(terrain, 0, 0, size[0], size[1]);
            ctx.batch.end();
            terrainShadows = terrainShapes(size, true);
            terrainCollisions = terrainShapes(size, false);
        }

        // Everything drawn this frame, including the placement preview, back to front.
        renderList.clear();
        renderList.addAll(objects);
        if (placing != null && ctx.inCanvas(Gdx.input.getX())) {
            ctx.worldViewport.unproject(cursor.set(Gdx.input.getX(), Gdx.input.getY()));
            ghost.asset = placing;
            ghost.assetId = placing.id;
            ghost.x = cursor.x;
            ghost.y = cursor.y;
            ghost.flipped = placingFlipped;
            ghost.scale = defaultScale(placing);
            renderList.add(ghost);
        }
        renderList.sort(backToFront);

        // World-space bounds are rebuilt every frame, so moved objects cast and receive shadows where they are now.
        int count = renderList.size;
        float[][][] shadowShapes = new float[count][][];
        float[][][] collisionShapes = new float[count][][];
        for (int i = 0; i < count; i++) {
            SceneObject object = renderList.get(i);
            AssetMeta meta = ctx.assets.meta.find(object.assetId);
            shadowShapes[i] = worldShapes(object, meta == null ? null : meta.shadows);
            collisionShapes[i] = worldShapes(object, meta == null ? null : meta.collisions);
        }

        if (showShadows) {
            for (float[] shadow : terrainShadows) ctx.fillPolygon(shadow, shadow.length, EditorContext.SHADOW_FILL);
            for (float[][] shapes : shadowShapes) {
                for (float[] shadow : shapes) ctx.fillPolygon(shadow, shadow.length, EditorContext.SHADOW_FILL);
            }
        }

        boolean receiveShadows = showShadows && ShadowMask.isSupported();
        ctx.batch.begin();
        for (int i = 0; i < count; i++) {
            SceneObject object = renderList.get(i);
            float alpha = object == ghost ? 0.6f : 1f;
            ctx.batch.setColor(1f, 1f, 1f, alpha);
            drawObject(object.asset, object.x, object.y, object.flipped, object.scale);
            if (!receiveShadows || !collectReceivedShadows(i, shadowShapes, collisionShapes)) continue;
            // Draw the object again, darkened, only where the shadows fall on it.
            ctx.batch.end();
            shadowMask.begin(ctx.shapes, receivedShadows);
            ctx.batch.begin();
            ctx.batch.setColor(0f, 0f, 0f, EditorContext.SHADOW_FILL.a * alpha);
            drawObject(object.asset, object.x, object.y, object.flipped, object.scale);
            ctx.batch.end();
            shadowMask.end();
            ctx.batch.begin();
        }
        ctx.batch.setColor(Color.WHITE);
        ctx.batch.end();

        ctx.beginShapes(ShapeRenderer.ShapeType.Line);
        if (showCollision) {
            for (float[] collision : terrainCollisions) {
                ctx.outline(collision, collision.length, EditorContext.COLLISION_COLOR, 1f);
            }
            for (float[][] shapes : collisionShapes) {
                for (float[] collision : shapes) {
                    ctx.outline(collision, collision.length, EditorContext.COLLISION_COLOR, 1f);
                }
            }
        }
        if (selected != null) {
            Texture texture = ctx.assets.texture(selected.asset, selected.flipped);
            if (texture != null) {
                float width = texture.getWidth() * selected.scale;
                ctx.shapes.setColor(Color.YELLOW);
                ctx.shapes.rect(selected.x - width / 2f, selected.y, width, texture.getHeight() * selected.scale);
            }
        }
        ctx.shapes.end();
    }

    /**
     * Fills {@link #receivedShadows} with the shadows of the terrain and other objects that fall on object
     * {@code index}: those
     * overlapping its collision bounds, or, if it has none, containing the point it stands on.
     */
    private boolean collectReceivedShadows(int index, float[][][] shadowShapes, float[][][] collisionShapes) {
        receivedShadows.clear();
        SceneObject object = renderList.get(index);
        float[][] collisions = collisionShapes[index];
        for (float[] shadow : terrainShadows) {
            if (receives(collisions, object, shadow)) receivedShadows.add(shadow);
        }
        for (int other = 0; other < shadowShapes.length; other++) {
            if (other == index) continue;
            for (float[] shadow : shadowShapes[other]) {
                if (receives(collisions, object, shadow)) receivedShadows.add(shadow);
            }
        }
        return receivedShadows.notEmpty();
    }

    private static boolean receives(float[][] collisions, SceneObject object, float[] shadow) {
        boolean hasCollision = false;
        for (float[] collision : collisions) {
            if (collision.length < 6) continue;
            hasCollision = true;
            if (Polygons.overlap(collision, shadow)) return true;
        }
        return !hasCollision && Polygons.contains(shadow, object.x, object.y);
    }

    private void drawObject(SpriteAsset asset, float x, float y, boolean flipped, float scale) {
        Texture texture = ctx.assets.texture(asset, flipped);
        if (texture == null) return;
        int width = texture.getWidth();
        int height = texture.getHeight();
        boolean mirror = flipped && !asset.hasFlipped();
        ctx.batch.draw(texture, x - width * scale / 2f, y, width * scale, height * scale, 0, 0, width, height,
            mirror, false);
    }

    /** An object's polygons (normalised to its image) in world space, mirrored when it is flipped. */
    private float[][] worldShapes(SceneObject object, float[][] normalized) {
        Texture texture = ctx.assets.texture(object.asset, object.flipped);
        if (texture == null || normalized == null) return NO_SHAPES;
        float width = texture.getWidth() * object.scale;
        float height = texture.getHeight() * object.scale;
        float left = object.x - width / 2f;
        float[][] world = new float[normalized.length][];
        for (int s = 0; s < normalized.length; s++) {
            float[] points = normalized[s];
            float[] out = new float[points.length];
            for (int i = 0; i + 1 < points.length; i += 2) {
                float x = object.flipped ? 1f - points[i] : points[i];
                out[i] = left + x * width;
                out[i + 1] = object.y + points[i + 1] * height;
            }
            world[s] = out;
        }
        return world;
    }

    // ---------------------------------------------------------------- saving

    private void load() {
        FileHandle file = GameFiles.resolve(SCENE_PATH);
        if (!file.exists()) return;
        try {
            SceneFile scene = json.fromJson(SceneFile.class, file);
            for (SceneObject object : scene.objects) {
                object.asset = ctx.assets.catalog.find(object.assetId);
                if (object.scale <= 0f) object.scale = 1f;
                if (object.asset != null) objects.add(object);
            }
            Array<SpriteAsset> terrains = ctx.assets.catalog.terrains();
            for (int i = 0; i < terrains.size; i++) {
                SpriteAsset terrain = terrains.get(i);
                if (terrain.basePath().equals(scene.terrain) || terrain.id.equals(scene.terrain)) terrainIndex = i;
            }
        } catch (Exception e) {
            Gdx.app.error("AssetEditor", "Could not read " + SCENE_PATH, e);
        }
    }

    @Override
    void save() {
        SceneFile scene = new SceneFile();
        SpriteAsset terrain = currentTerrain();
        scene.terrain = terrain == null ? null : terrain.basePath();
        scene.objects.addAll(objects);
        try {
            GameFiles.writable(SCENE_PATH).writeString(json.prettyPrint(scene), false, "UTF-8");
        } catch (Exception e) {
            Gdx.app.error("AssetEditor", "Saving the scene failed", e);
            ctx.setStatus("Saving the scene failed: " + e.getMessage());
            return;
        }
        dirty = false;
        ctx.setStatus("Scene saved (" + objects.size + " objects)");
    }

    void saveIfDirty() {
        if (dirty) save();
    }

    @Override
    void exit() {
        stopPlacing();
        moving = false;
    }

    @Override
    void afterRescan() {
        if (placing != null) placing = ctx.assets.catalog.find(placing.id);
        for (int i = objects.size - 1; i >= 0; i--) {
            SceneObject object = objects.get(i);
            object.asset = ctx.assets.catalog.find(object.assetId);
            if (object.asset == null) objects.removeIndex(i);
        }
        if (selected != null && selected.asset == null) select(null);
    }
}
