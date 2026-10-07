package io.github.gamePackage.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.ShortArray;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.gamePackage.Main;
import io.github.gamePackage.assets.AssetMeta;
import io.github.gamePackage.assets.GameAssets;
import io.github.gamePackage.assets.GameFiles;
import io.github.gamePackage.assets.ImageProcessor;
import io.github.gamePackage.assets.SpriteAsset;
import io.github.gamePackage.screens.TitleScreen;

import java.util.Comparator;

/**
 * Asset editor with two modes:
 * <ul>
 *   <li><b>Edit Asset</b>: draw collision and shadow polygons on an asset, remove its green-screen background and
 *   halve its resolution. Saving writes a processed PNG to {@code assets/processed/} (the source image is never
 *   changed) and the settings to {@code assets/data/asset_meta.json}.</li>
 *   <li><b>Scene</b>: spawn assets on the terrain, move, rotate (swap to the flipped side) and delete them. The
 *   layout is saved to {@code assets/data/editor_scene.json}.</li>
 * </ul>
 */
public class AssetEditorScreen extends ScreenAdapter {
    private static final float LEFT_WIDTH = 320f;
    private static final float RIGHT_WIDTH = 290f;
    private static final float HANDLE_PIXELS = 6f;
    private static final float PREVIEW_DELAY = 0.2f;
    private static final String SCENE_PATH = "data/editor_scene.json";

    private static final Color COLLISION_COLOR = new Color(1f, 0.3f, 0.3f, 1f);
    private static final Color SHADOW_COLOR = new Color(0.4f, 0.7f, 1f, 1f);
    private static final Color SHADOW_FILL = new Color(0f, 0f, 0f, 0.38f);

    private enum Mode { ASSET, SCENE }

    private enum Tool { VIEW, COLLISION, SHADOW }

    public static class SceneFile {
        public String terrain;
        public Array<SceneObject> objects = new Array<>();
    }

    private final Main game;
    private final GameAssets assets;
    private final Skin skin;
    private final Stage stage = new Stage(new ScreenViewport());
    private final OrthographicCamera camera = new OrthographicCamera();
    private final ScreenViewport worldViewport = new ScreenViewport(camera);
    private final CameraControl cameraControl = new CameraControl(worldViewport);
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final EarClippingTriangulator triangulator = new EarClippingTriangulator();
    private final HitMasks hitMasks = new HitMasks();
    private final Json json = new Json(JsonWriter.OutputType.json);
    private final Vector2 cursor = new Vector2();
    private final float[] worldPolygon = new float[256];

    private Mode mode = Mode.ASSET;
    private final float[] assetCamera = {0f, 0f, 1f};
    private float[] sceneCamera;

    // Edit Asset mode
    private SpriteAsset current;
    private AssetMeta meta;
    private Pixmap originalBase;
    private Pixmap originalFlipped;
    private Pixmap previewBase;
    private Pixmap previewFlipped;
    private Texture previewBaseTexture;
    private Texture previewFlippedTexture;
    private boolean viewFlipped;
    private Tool tool = Tool.VIEW;
    private boolean metaDirty;
    private boolean bakeDirty;
    private float previewRebuildIn = -1f;
    private int dragVertex = -1;
    /** Fit the camera to the asset once the viewport has a size (the screen isn't sized when constructed). */
    private boolean fitPending;

    // Scene mode
    private final Array<SceneObject> objects = new Array<>();
    private final Array<SceneObject> drawOrder = new Array<>();
    private SceneObject selected;
    private SpriteAsset placing;
    private boolean placingFlipped;
    private int terrainIndex;
    private boolean showShadows = true;
    private boolean showCollision = true;
    private boolean sceneDirty;
    private boolean movingSelected;
    private float grabOffsetX;
    private float grabOffsetY;

    private boolean panning;
    private int lastScreenX;
    private int lastScreenY;

    // UI
    private List<SpriteAsset> assetList;
    private Table rightPanel;
    private Table assetPanel;
    private Table scenePanel;
    private Label status;
    private Label assetInfo;
    private Label thresholdLabel;
    private Label softnessLabel;
    private TextButton assetModeButton;
    private TextButton sceneModeButton;
    private TextButton viewFlippedButton;
    private TextButton removeGreenButton;
    private final TextButton[] toolButtons = new TextButton[3];
    private Slider thresholdSlider;
    private Slider softnessSlider;
    private boolean syncingUi;

    public AssetEditorScreen(Main game) {
        this.game = game;
        this.assets = game.assets;
        this.skin = game.skin;
        json.setUsePrototypes(false);
        json.setElementType(SceneFile.class, "objects", SceneObject.class);
        buildUi();
        loadScene();
        if (assets.catalog.sprites().notEmpty()) {
            selectInList(assets.catalog.sprites().first());
            selectAsset(assets.catalog.sprites().first());
        } else setStatus("No assets found. Put images in sub-folders of assets/ and press Rescan.");
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        Table left = new Table();
        left.setBackground(skin.getDrawable("panel"));
        left.top().pad(10);
        left.defaults().growX().padBottom(6);

        assetModeButton = new TextButton("Edit Asset", skin, "toggle");
        sceneModeButton = new TextButton("Scene", skin, "toggle");
        new ButtonGroup<Button>(assetModeButton, sceneModeButton);
        assetModeButton.setChecked(true);
        onChange(assetModeButton, () -> {
            if (assetModeButton.isChecked()) setMode(Mode.ASSET);
        });
        onChange(sceneModeButton, () -> {
            if (sceneModeButton.isChecked()) setMode(Mode.SCENE);
        });
        Table modes = new Table();
        modes.defaults().growX().uniformX();
        modes.add(assetModeButton).padRight(4);
        modes.add(sceneModeButton);
        left.add(modes).row();

        left.add(new Label("Assets", skin, "dim")).padTop(6).row();
        assetList = new List<>(skin);
        assetList.setItems(assets.catalog.sprites());
        assetList.getSelection().setRequired(false);
        assetList.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (syncingUi) return;
                SpriteAsset asset = assetList.getSelected();
                if (asset == null) return;
                if (mode == Mode.ASSET) selectAsset(asset);
                else startPlacing(asset);
            }
        });
        ScrollPane listPane = new ScrollPane(assetList, skin);
        listPane.setFadeScrollBars(false);
        listPane.setScrollingDisabled(true, false);
        left.add(listPane).grow().row();
        left.add(button("Rescan assets folder", this::rescan)).row();
        left.add(button("Back to title", this::exitToTitle)).row();

        buildAssetPanel();
        buildScenePanel();
        status = new Label("", skin, "dim");
        status.setWrap(true);
        rightPanel = new Table();
        rightPanel.setBackground(skin.getDrawable("panel"));
        rightPanel.pad(10);
        showRightPanel();

        root.add(left).width(LEFT_WIDTH).growY();
        root.add().grow();
        root.add(rightPanel).width(RIGHT_WIDTH).growY();
    }

    private void buildAssetPanel() {
        assetPanel = new Table();
        assetPanel.top();
        assetPanel.defaults().growX().padBottom(6);

        assetInfo = new Label("", skin);
        assetInfo.setWrap(true);
        assetPanel.add(assetInfo).row();
        viewFlippedButton = new TextButton("View flipped side (R)", skin, "toggle");
        onChange(viewFlippedButton, () -> {
            if (!syncingUi) setViewFlipped(viewFlippedButton.isChecked());
        });
        assetPanel.add(viewFlippedButton).row();

        assetPanel.add(section("Bounds")).row();
        String[] toolNames = {"View (1)", "Collision (2)", "Shadow (3)"};
        Table tools = new Table();
        tools.defaults().growX().uniformX();
        ButtonGroup<TextButton> toolGroup = new ButtonGroup<>();
        for (int i = 0; i < toolButtons.length; i++) {
            final Tool buttonTool = Tool.values()[i];
            TextButton toolButton = new TextButton(toolNames[i], skin, "toggle");
            toolButton.getLabel().setFontScale(0.9f);
            onChange(toolButton, () -> {
                if (toolButton.isChecked()) tool = buttonTool;
            });
            toolGroup.add(toolButton);
            toolButtons[i] = toolButton;
            tools.add(toolButton).padRight(i < toolButtons.length - 1 ? 3 : 0);
        }
        toolButtons[0].setChecked(true);
        assetPanel.add(tools).row();
        Table clear = new Table();
        clear.defaults().growX().uniformX();
        clear.add(button("Clear collision", () -> clearPolygon(Tool.COLLISION))).padRight(3);
        clear.add(button("Clear shadow", () -> clearPolygon(Tool.SHADOW)));
        assetPanel.add(clear).row();
        assetPanel.add(help("Left-click: add point\nDrag a point: move it\nRight-click a point: delete it\n"
            + "Right/middle-drag: pan, wheel: zoom, F: fit")).row();

        assetPanel.add(section("Green screen")).row();
        removeGreenButton = new TextButton("Remove green background", skin, "toggle");
        onChange(removeGreenButton, () -> {
            if (syncingUi || meta == null) return;
            meta.removeGreen = removeGreenButton.isChecked();
            processingChanged(0f);
        });
        assetPanel.add(removeGreenButton).row();
        thresholdLabel = new Label("", skin, "dim");
        assetPanel.add(thresholdLabel).row();
        thresholdSlider = new Slider(0.05f, 0.95f, 0.01f, false, skin);
        onChange(thresholdSlider, () -> {
            if (syncingUi || meta == null) return;
            meta.keyThreshold = thresholdSlider.getValue();
            updateSliderLabels();
            if (meta.removeGreen) processingChanged(PREVIEW_DELAY);
        });
        assetPanel.add(thresholdSlider).row();
        softnessLabel = new Label("", skin, "dim");
        assetPanel.add(softnessLabel).row();
        softnessSlider = new Slider(0.02f, 0.8f, 0.01f, false, skin);
        onChange(softnessSlider, () -> {
            if (syncingUi || meta == null) return;
            meta.keySoftness = softnessSlider.getValue();
            updateSliderLabels();
            if (meta.removeGreen) processingChanged(PREVIEW_DELAY);
        });
        assetPanel.add(softnessSlider).row();

        assetPanel.add(section("Resolution")).row();
        Table resolution = new Table();
        resolution.defaults().growX().uniformX();
        resolution.add(button("Halve", this::halveResolution)).padRight(3);
        resolution.add(button("Original size", () -> {
            if (meta == null || meta.halvings == 0) return;
            meta.halvings = 0;
            processingChanged(0f);
        }));
        assetPanel.add(resolution).row();

        assetPanel.add(button("Save asset (Ctrl+S)", this::saveAsset)).padTop(10).row();
    }

    private void buildScenePanel() {
        scenePanel = new Table();
        scenePanel.top();
        scenePanel.defaults().growX().padBottom(6);
        scenePanel.add(section("Objects")).row();
        scenePanel.add(button("Place selected asset", () -> startPlacing(assetList.getSelected()))).row();
        scenePanel.add(button("Rotate (R)", this::rotate)).row();
        scenePanel.add(button("Delete selected (Del)", this::deleteSelected)).row();
        scenePanel.add(button("Clear scene", () -> {
            objects.clear();
            selected = null;
            sceneDirty = true;
        })).row();

        scenePanel.add(section("View")).row();
        TextButton shadowsButton = new TextButton("Show shadows", skin, "toggle");
        shadowsButton.setChecked(true);
        onChange(shadowsButton, () -> showShadows = shadowsButton.isChecked());
        scenePanel.add(shadowsButton).row();
        TextButton collisionButton = new TextButton("Show collision bounds", skin, "toggle");
        collisionButton.setChecked(true);
        onChange(collisionButton, () -> showCollision = collisionButton.isChecked());
        scenePanel.add(collisionButton).row();
        scenePanel.add(button("Next terrain", () -> {
            if (assets.catalog.terrains().isEmpty()) return;
            terrainIndex = (terrainIndex + 1) % assets.catalog.terrains().size;
            sceneDirty = true;
        })).row();

        scenePanel.add(button("Save scene (Ctrl+S)", this::saveScene)).padTop(10).row();
        scenePanel.add(help("Pick an asset in the list, then click the map to spawn it. "
            + "Right-click or Esc stops placing.\n\nDrag an object to move it. Drag empty ground to pan, "
            + "wheel to zoom.\n\nR rotates (uses the flipped side).")).row();
    }

    private void showRightPanel() {
        rightPanel.clearChildren();
        rightPanel.add(mode == Mode.ASSET ? assetPanel : scenePanel).growX().top().row();
        rightPanel.add().grow().row();
        rightPanel.add(status).growX();
    }

    private Label section(String text) {
        Label label = new Label(text, skin);
        label.setColor(0.55f, 0.75f, 1f, 1f);
        return label;
    }

    private Label help(String text) {
        Label label = new Label(text, skin, "dim");
        label.setWrap(true);
        label.setFontScale(0.9f);
        return label;
    }

    private TextButton button(String text, Runnable action) {
        TextButton button = new TextButton(text, skin);
        onChange(button, action);
        return button;
    }

    private static void onChange(Actor actor, Runnable action) {
        actor.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor changed) {
                action.run();
            }
        });
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    // ---------------------------------------------------------------- modes

    private void setMode(Mode newMode) {
        if (newMode == mode) return;
        if (mode == Mode.ASSET) {
            saveAssetIfDirty();
            storeCamera(assetCamera);
        } else {
            storeCamera(sceneCamera);
        }
        mode = newMode;
        dragVertex = -1;
        movingSelected = false;
        panning = false;
        if (mode == Mode.SCENE) {
            if (sceneCamera == null) {
                sceneCamera = new float[3];
                fitScene();
            } else {
                restoreCamera(sceneCamera);
            }
        } else {
            placing = null;
            restoreCamera(assetCamera);
            selectInList(current);
        }
        showRightPanel();
    }

    private void storeCamera(float[] state) {
        state[0] = camera.position.x;
        state[1] = camera.position.y;
        state[2] = camera.zoom;
    }

    private void restoreCamera(float[] state) {
        camera.position.set(state[0], state[1], 0f);
        camera.zoom = state[2];
        camera.update();
    }

    private void selectInList(SpriteAsset asset) {
        syncingUi = true;
        if (asset == null) assetList.getSelection().clear();
        else assetList.setSelected(asset);
        syncingUi = false;
    }

    // ---------------------------------------------------------------- Edit Asset mode

    private void selectAsset(SpriteAsset asset) {
        if (asset == current) return;
        saveAssetIfDirty();
        disposeAssetImages();
        current = asset;
        AssetMeta stored = assets.meta.find(asset.id);
        meta = stored != null ? stored : new AssetMeta(asset.id);
        metaDirty = false;
        bakeDirty = false;
        viewFlipped = false;
        try {
            originalBase = new Pixmap(GameFiles.resolve(asset.basePath()));
            if (asset.hasFlipped()) originalFlipped = new Pixmap(GameFiles.resolve(asset.flippedPath()));
        } catch (Exception e) {
            Gdx.app.error("AssetEditor", "Could not open " + asset.id, e);
            setStatus("Could not open " + asset.basePath() + ": " + e.getMessage());
            disposeAssetImages();
            current = null;
            meta = null;
            return;
        }
        rebuildPreview();
        fitPending = true;
        syncAssetPanel();
        setStatus("");
    }

    /** Re-runs green removal and resizing on the source images for the on-screen preview. */
    private void rebuildPreview() {
        previewRebuildIn = -1f;
        int oldWidth = previewBase != null ? previewBase.getWidth() : -1;
        disposePreviews();
        if (originalBase == null) return;
        previewBase = ImageProcessor.process(originalBase, meta);
        if (oldWidth != previewBase.getWidth()) fitPending = true;
        previewBaseTexture = createTexture(previewBase);
        if (originalFlipped != null) {
            previewFlipped = ImageProcessor.process(originalFlipped, meta);
            previewFlippedTexture = createTexture(previewFlipped);
        }
        updateAssetInfo();
    }

    private static Texture createTexture(Pixmap pixmap) {
        Texture texture = new Texture(pixmap, true);
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        return texture;
    }

    /** Texture for the side being viewed. Without a flipped file it is the base texture, drawn mirrored. */
    private Texture previewTexture() {
        return viewFlipped && previewFlippedTexture != null ? previewFlippedTexture : previewBaseTexture;
    }

    private void processingChanged(float delay) {
        bakeDirty = true;
        metaDirty = true;
        previewRebuildIn = delay;
        updateAssetInfo();
    }

    private void halveResolution() {
        if (meta == null) return;
        int smallest = Math.min(originalBase.getWidth(), originalBase.getHeight()) >> (meta.halvings + 1);
        if (smallest < 8) {
            setStatus("The asset is already as small as it can go.");
            return;
        }
        meta.halvings++;
        processingChanged(0f);
    }

    private void setViewFlipped(boolean flipped) {
        viewFlipped = flipped;
        syncingUi = true;
        viewFlippedButton.setChecked(flipped);
        syncingUi = false;
    }

    private void syncAssetPanel() {
        syncingUi = true;
        viewFlippedButton.setChecked(viewFlipped);
        removeGreenButton.setChecked(meta != null && meta.removeGreen);
        if (meta != null) {
            thresholdSlider.setValue(meta.keyThreshold);
            softnessSlider.setValue(meta.keySoftness);
        }
        syncingUi = false;
        updateSliderLabels();
        updateAssetInfo();
    }

    private void updateSliderLabels() {
        if (meta == null) return;
        thresholdLabel.setText(String.format("Threshold: %.2f  (lower removes more)", meta.keyThreshold));
        softnessLabel.setText(String.format("Edge softness: %.2f", meta.keySoftness));
    }

    private void updateAssetInfo() {
        if (current == null || previewBase == null) {
            assetInfo.setText("No asset selected");
            return;
        }
        StringBuilder text = new StringBuilder(current.name);
        text.append("\n").append(current.category.isEmpty() ? "(assets root)" : current.category);
        text.append(current.hasFlipped() ? "\nHas a flipped side" : "\nNo flipped file (mirrored in game)");
        text.append("\nSize: ").append(originalBase.getWidth()).append("x").append(originalBase.getHeight());
        if (meta.halvings > 0) {
            text.append(" -> ").append(previewBase.getWidth()).append("x").append(previewBase.getHeight());
        }
        text.append("\nCollision points: ").append(meta.collision.length / 2);
        text.append("   Shadow points: ").append(meta.shadow.length / 2);
        if (metaDirty) text.append("\n* unsaved changes");
        assetInfo.setText(text);
    }

    private void saveAssetIfDirty() {
        if (current != null && metaDirty) saveAsset();
    }

    /** Writes the processed PNGs (or deletes them when no processing is needed) and the asset settings. */
    private void saveAsset() {
        if (current == null) return;
        if (previewRebuildIn >= 0f) rebuildPreview();
        try {
            if (bakeDirty) {
                writeProcessed(current.basePath(), previewBase);
                if (current.hasFlipped()) writeProcessed(current.flippedPath(), previewFlipped);
            }
            assets.meta.put(meta);
            assets.meta.save();
        } catch (Exception e) {
            Gdx.app.error("AssetEditor", "Saving " + current.id + " failed", e);
            setStatus("Saving failed: " + e.getMessage());
            return;
        }
        if (bakeDirty) {
            invalidateHitMasks(current);
            assets.reload(current);
        }
        metaDirty = false;
        bakeDirty = false;
        updateAssetInfo();
        setStatus("Saved " + current.name);
    }

    private void writeProcessed(String sourcePath, Pixmap processed) {
        FileHandle file = GameFiles.writable(GameFiles.processedPath(sourcePath));
        if (meta.needsProcessing()) {
            file.parent().mkdirs();
            PixmapIO.writePNG(file, processed);
        } else if (file.exists()) {
            file.delete();
        }
    }

    private void invalidateHitMasks(SpriteAsset asset) {
        for (int side = 0; side < 2; side++) {
            String source = asset.sourcePath(side == 1);
            hitMasks.invalidate(source);
            hitMasks.invalidate(GameFiles.processedPath(source));
        }
    }

    private float[] polygon(Tool polygonTool) {
        return polygonTool == Tool.COLLISION ? meta.collision : meta.shadow;
    }

    private void setPolygon(Tool polygonTool, float[] points) {
        if (polygonTool == Tool.COLLISION) meta.collision = points;
        else meta.shadow = points;
        metaDirty = true;
        updateAssetInfo();
    }

    private void clearPolygon(Tool polygonTool) {
        if (meta == null) return;
        setPolygon(polygonTool, new float[0]);
    }

    /** Converts a normalised point to world coordinates on the asset canvas (mirrored when viewing the flip). */
    private float toCanvasX(float normalizedX) {
        float x = viewFlipped ? 1f - normalizedX : normalizedX;
        return x * previewTexture().getWidth();
    }

    private float toCanvasY(float normalizedY) {
        return normalizedY * previewTexture().getHeight();
    }

    private float toNormalizedX(float canvasX) {
        float x = canvasX / previewTexture().getWidth();
        return viewFlipped ? 1f - x : x;
    }

    private float toNormalizedY(float canvasY) {
        return canvasY / previewTexture().getHeight();
    }

    private int hitVertex(float worldX, float worldY) {
        float[] points = polygon(tool);
        float radius = HANDLE_PIXELS * 1.6f * camera.zoom;
        for (int i = points.length / 2 - 1; i >= 0; i--) {
            float dx = toCanvasX(points[i * 2]) - worldX;
            float dy = toCanvasY(points[i * 2 + 1]) - worldY;
            if (dx * dx + dy * dy <= radius * radius) return i;
        }
        return -1;
    }

    /** Adds a point, inserting it into the nearest edge once the polygon has three points. */
    private int insertVertex(float worldX, float worldY) {
        float[] points = polygon(tool);
        int count = points.length / 2;
        int insertAt = count;
        if (count >= 3) {
            float best = Float.MAX_VALUE;
            Vector2 a = new Vector2();
            Vector2 b = new Vector2();
            Vector2 p = new Vector2(worldX, worldY);
            for (int i = 0; i < count; i++) {
                int j = (i + 1) % count;
                a.set(toCanvasX(points[i * 2]), toCanvasY(points[i * 2 + 1]));
                b.set(toCanvasX(points[j * 2]), toCanvasY(points[j * 2 + 1]));
                float distance = Intersector.distanceSegmentPoint(a, b, p);
                if (distance < best) {
                    best = distance;
                    insertAt = i + 1;
                }
            }
        }
        FloatArray edited = new FloatArray(points);
        edited.insert(insertAt * 2, toNormalizedY(worldY));
        edited.insert(insertAt * 2, toNormalizedX(worldX));
        setPolygon(tool, edited.toArray());
        return insertAt;
    }

    private void moveVertex(int index, float worldX, float worldY) {
        float[] points = polygon(tool);
        points[index * 2] = toNormalizedX(worldX);
        points[index * 2 + 1] = toNormalizedY(worldY);
        metaDirty = true;
    }

    private void removeVertex(int index) {
        FloatArray edited = new FloatArray(polygon(tool));
        edited.removeRange(index * 2, index * 2 + 1);
        setPolygon(tool, edited.toArray());
    }

    private void renderAsset() {
        if (current == null || previewBaseTexture == null) return;
        Texture texture = previewTexture();
        boolean mirror = viewFlipped && previewFlippedTexture == null;
        int width = texture.getWidth();
        int height = texture.getHeight();

        beginShapes(ShapeRenderer.ShapeType.Filled);
        float tile = Math.max(8f, Math.max(width, height) / 32f);
        for (float y = 0; y < height; y += tile) {
            for (float x = 0; x < width; x += tile) {
                boolean dark = (((int) (x / tile) + (int) (y / tile)) & 1) == 0;
                shapes.setColor(dark ? 0.32f : 0.40f, dark ? 0.32f : 0.40f, dark ? 0.34f : 0.42f, 1f);
                shapes.rect(x, y, Math.min(tile, width - x), Math.min(tile, height - y));
            }
        }
        shapes.end();

        batch.begin();
        batch.draw(texture, 0, 0, width, height, 0, 0, width, height, mirror, false);
        batch.end();

        float[] shadow = canvasPolygon(meta.shadow);
        float[] collision = canvasPolygon(meta.collision);
        if (meta.shadow.length >= 6) fillPolygon(shadow, meta.shadow.length, SHADOW_FILL);

        beginShapes(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.6f, 0.6f, 0.65f, 1f);
        shapes.rect(0, 0, width, height);
        outline(shadow, meta.shadow.length, SHADOW_COLOR, tool == Tool.SHADOW ? 1f : 0.6f);
        outline(collision, meta.collision.length, COLLISION_COLOR, tool == Tool.COLLISION ? 1f : 0.6f);
        shapes.end();

        if (tool != Tool.VIEW) {
            float[] active = tool == Tool.COLLISION ? collision : shadow;
            int length = polygon(tool).length;
            float size = HANDLE_PIXELS * camera.zoom;
            beginShapes(ShapeRenderer.ShapeType.Filled);
            for (int i = 0; i < length; i += 2) {
                shapes.setColor(i == 0 ? Color.WHITE : (tool == Tool.COLLISION ? COLLISION_COLOR : SHADOW_COLOR));
                shapes.rect(active[i] - size / 2f, active[i + 1] - size / 2f, size, size);
            }
            shapes.end();
        }
    }

    /** Normalised asset polygon to canvas coordinates, in a separate array per polygon. */
    private float[] canvasPolygon(float[] normalized) {
        float[] out = new float[normalized.length];
        for (int i = 0; i < normalized.length; i += 2) {
            out[i] = toCanvasX(normalized[i]);
            out[i + 1] = toCanvasY(normalized[i + 1]);
        }
        return out;
    }

    // ---------------------------------------------------------------- Scene mode

    private void startPlacing(SpriteAsset asset) {
        if (asset == null || mode != Mode.SCENE) return;
        placing = asset;
        selected = null;
        setStatus("Placing " + asset.name + ". Click to spawn, right-click or Esc to stop, R to rotate.");
    }

    private void rotate() {
        if (placing != null) {
            placingFlipped = !placingFlipped;
        } else if (selected != null) {
            selected.flipped = !selected.flipped;
            sceneDirty = true;
        }
    }

    private void deleteSelected() {
        if (selected == null) return;
        objects.removeValue(selected, true);
        selected = null;
        sceneDirty = true;
    }

    private void fitScene() {
        Texture terrain = currentTerrain();
        if (terrain != null) cameraControl.fit(0f, 0f, terrain.getWidth(), terrain.getHeight());
        else cameraControl.fit(-512f, -512f, 1024f, 1024f);
    }

    private Texture currentTerrain() {
        Array<String> terrains = assets.catalog.terrains();
        if (terrains.isEmpty()) return null;
        return assets.terrain(terrains.get(Math.min(terrainIndex, terrains.size - 1)));
    }

    /** Front-most object under the point, ignoring transparent pixels. */
    private SceneObject pick(float worldX, float worldY) {
        sortDrawOrder();
        for (int i = drawOrder.size - 1; i >= 0; i--) {
            SceneObject object = drawOrder.get(i);
            Texture texture = assets.texture(object.asset, object.flipped);
            if (texture == null) continue;
            float left = object.x - texture.getWidth() / 2f;
            float u = (worldX - left) / texture.getWidth();
            float v = 1f - (worldY - object.y) / texture.getHeight();
            if (u < 0f || u > 1f || v < 0f || v > 1f) continue;
            if (object.flipped && !object.asset.hasFlipped()) u = 1f - u;
            String path = assets.loadedPath(object.asset, object.flipped);
            if (path == null || hitMasks.isOpaque(path, u, v)) return object;
        }
        return null;
    }

    /** Objects lower on the screen are in front, so draw from the top of the map down. */
    private void sortDrawOrder() {
        drawOrder.clear();
        drawOrder.addAll(objects);
        drawOrder.sort(new Comparator<SceneObject>() {
            @Override
            public int compare(SceneObject a, SceneObject b) {
                return Float.compare(b.y, a.y);
            }
        });
    }

    private void renderScene() {
        Texture terrain = currentTerrain();
        if (terrain != null) {
            batch.begin();
            batch.draw(terrain, 0, 0);
            batch.end();
        }
        sortDrawOrder();

        if (showShadows) {
            for (SceneObject object : drawOrder) {
                AssetMeta objectMeta = assets.meta.find(object.assetId);
                if (objectMeta == null || objectMeta.shadow.length < 6) continue;
                int length = objectPolygon(object, objectMeta.shadow);
                if (length > 0) fillPolygon(worldPolygon, length, SHADOW_FILL);
            }
        }

        batch.begin();
        for (SceneObject object : drawOrder) drawObject(object.asset, object.x, object.y, object.flipped);
        boolean overCanvas = inCanvas(Gdx.input.getX());
        if (placing != null && overCanvas) {
            worldViewport.unproject(cursor.set(Gdx.input.getX(), Gdx.input.getY()));
            batch.setColor(1f, 1f, 1f, 0.6f);
            drawObject(placing, cursor.x, cursor.y, placingFlipped);
            batch.setColor(Color.WHITE);
        }
        batch.end();

        beginShapes(ShapeRenderer.ShapeType.Line);
        if (showCollision) {
            for (SceneObject object : drawOrder) {
                AssetMeta objectMeta = assets.meta.find(object.assetId);
                if (objectMeta == null || objectMeta.collision.length < 4) continue;
                int length = objectPolygon(object, objectMeta.collision);
                if (length > 0) outline(worldPolygon, length, COLLISION_COLOR, 1f);
            }
        }
        if (selected != null) {
            Texture texture = assets.texture(selected.asset, selected.flipped);
            if (texture != null) {
                shapes.setColor(Color.YELLOW);
                shapes.rect(selected.x - texture.getWidth() / 2f, selected.y, texture.getWidth(), texture.getHeight());
            }
        }
        shapes.end();
    }

    private void drawObject(SpriteAsset asset, float x, float y, boolean flipped) {
        Texture texture = assets.texture(asset, flipped);
        if (texture == null) return;
        int width = texture.getWidth();
        int height = texture.getHeight();
        boolean mirror = flipped && !asset.hasFlipped();
        batch.draw(texture, x - width / 2f, y, width, height, 0, 0, width, height, mirror, false);
    }

    /** Fills {@link #worldPolygon} with an object's polygon in world space; returns the float count used. */
    private int objectPolygon(SceneObject object, float[] normalized) {
        Texture texture = assets.texture(object.asset, object.flipped);
        if (texture == null) return 0;
        int length = Math.min(normalized.length, worldPolygon.length);
        float left = object.x - texture.getWidth() / 2f;
        for (int i = 0; i < length; i += 2) {
            float x = object.flipped ? 1f - normalized[i] : normalized[i];
            worldPolygon[i] = left + x * texture.getWidth();
            worldPolygon[i + 1] = object.y + normalized[i + 1] * texture.getHeight();
        }
        return length;
    }

    private void loadScene() {
        FileHandle file = GameFiles.resolve(SCENE_PATH);
        if (!file.exists()) return;
        try {
            SceneFile scene = json.fromJson(SceneFile.class, file);
            for (SceneObject object : scene.objects) {
                object.asset = assets.catalog.find(object.assetId);
                if (object.asset != null) objects.add(object);
            }
            int index = assets.catalog.terrains().indexOf(scene.terrain, false);
            if (index >= 0) terrainIndex = index;
        } catch (Exception e) {
            Gdx.app.error("AssetEditor", "Could not read " + SCENE_PATH, e);
        }
    }

    private void saveScene() {
        SceneFile scene = new SceneFile();
        Array<String> terrains = assets.catalog.terrains();
        scene.terrain = terrains.isEmpty() ? null : terrains.get(Math.min(terrainIndex, terrains.size - 1));
        scene.objects.addAll(objects);
        try {
            GameFiles.writable(SCENE_PATH).writeString(json.prettyPrint(scene), false, "UTF-8");
        } catch (Exception e) {
            Gdx.app.error("AssetEditor", "Saving the scene failed", e);
            setStatus("Saving the scene failed: " + e.getMessage());
            return;
        }
        sceneDirty = false;
        setStatus("Scene saved (" + objects.size + " objects)");
    }

    // ---------------------------------------------------------------- shared

    private void rescan() {
        saveAssetIfDirty();
        int before = assets.catalog.sprites().size;
        assets.scanAndQueue();
        assets.manager.finishLoading();
        // The catalog rebuilt its asset objects; swap our references for the new ones.
        if (current != null) current = assets.catalog.find(current.id);
        if (placing != null) placing = assets.catalog.find(placing.id);
        for (int i = objects.size - 1; i >= 0; i--) {
            SceneObject object = objects.get(i);
            object.asset = assets.catalog.find(object.assetId);
            if (object.asset == null) objects.removeIndex(i);
        }
        if (selected != null && selected.asset == null) selected = null;
        syncingUi = true;
        assetList.setItems(assets.catalog.sprites());
        syncingUi = false;
        selectInList(mode == Mode.ASSET ? current : placing);
        int found = assets.catalog.sprites().size;
        setStatus("Found " + found + " assets (" + Math.max(0, found - before) + " new)");
    }

    private void exitToTitle() {
        saveAssetIfDirty();
        if (sceneDirty) saveScene();
        // Switch after the click finishes; switching disposes this screen's stage, which is handling the click.
        Gdx.app.postRunnable(() -> game.setScreen(new TitleScreen(game)));
    }

    private boolean inCanvas(int screenX) {
        return screenX >= LEFT_WIDTH && screenX < Gdx.graphics.getWidth() - RIGHT_WIDTH;
    }

    private void beginShapes(ShapeRenderer.ShapeType type) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(type);
    }

    private void fillPolygon(float[] vertices, int length, Color color) {
        float[] points = length == vertices.length ? vertices : java.util.Arrays.copyOf(vertices, length);
        ShortArray triangles = triangulator.computeTriangles(points);
        beginShapes(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(color);
        for (int i = 0; i + 2 < triangles.size; i += 3) {
            int a = triangles.get(i) * 2;
            int b = triangles.get(i + 1) * 2;
            int c = triangles.get(i + 2) * 2;
            shapes.triangle(points[a], points[a + 1], points[b], points[b + 1], points[c], points[c + 1]);
        }
        shapes.end();
    }

    /** Draws a closed outline; must be called between {@code begin(Line)} and {@code end()}. */
    private void outline(float[] vertices, int length, Color color, float alpha) {
        int count = length / 2;
        if (count < 2) return;
        shapes.setColor(color.r, color.g, color.b, alpha);
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            if (j == 0 && count == 2) break;
            shapes.line(vertices[i * 2], vertices[i * 2 + 1], vertices[j * 2], vertices[j * 2 + 1]);
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputMultiplexer(stage, new CanvasInput()));
    }

    @Override
    public void render(float delta) {
        if (previewRebuildIn >= 0f) {
            previewRebuildIn -= delta;
            if (previewRebuildIn < 0f) rebuildPreview();
        }
        if (fitPending && worldViewport.getScreenWidth() > 0 && previewBaseTexture != null && mode == Mode.ASSET) {
            fitPending = false;
            cameraControl.fit(0f, 0f, previewTexture().getWidth(), previewTexture().getHeight());
        }
        ScreenUtils.clear(0.05f, 0.055f, 0.07f, 1f);
        worldViewport.apply();
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapes.setProjectionMatrix(camera.combined);
        if (mode == Mode.ASSET) renderAsset();
        else renderScene();

        stage.getViewport().apply();
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        int canvasWidth = Math.max(1, width - (int) LEFT_WIDTH - (int) RIGHT_WIDTH);
        worldViewport.update(canvasWidth, height, false);
        worldViewport.setScreenBounds((int) LEFT_WIDTH, 0, canvasWidth, height);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        dispose();
    }

    private void disposePreviews() {
        if (previewBaseTexture != null) previewBaseTexture.dispose();
        if (previewFlippedTexture != null) previewFlippedTexture.dispose();
        if (previewBase != null) previewBase.dispose();
        if (previewFlipped != null) previewFlipped.dispose();
        previewBaseTexture = null;
        previewFlippedTexture = null;
        previewBase = null;
        previewFlipped = null;
    }

    private void disposeAssetImages() {
        disposePreviews();
        if (originalBase != null) originalBase.dispose();
        if (originalFlipped != null) originalFlipped.dispose();
        originalBase = null;
        originalFlipped = null;
    }

    @Override
    public void dispose() {
        disposeAssetImages();
        stage.dispose();
        batch.dispose();
        shapes.dispose();
    }

    /** Mouse and keyboard on the canvas between the two panels. */
    private class CanvasInput extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (!inCanvas(screenX)) return false;
            stage.setKeyboardFocus(null);
            lastScreenX = screenX;
            lastScreenY = screenY;
            worldViewport.unproject(cursor.set(screenX, screenY));
            float x = cursor.x;
            float y = cursor.y;

            if (mode == Mode.ASSET && meta != null && tool != Tool.VIEW) {
                int hit = hitVertex(x, y);
                if (button == Input.Buttons.LEFT) {
                    dragVertex = hit >= 0 ? hit : insertVertex(x, y);
                    return true;
                }
                if (button == Input.Buttons.RIGHT && hit >= 0) {
                    removeVertex(hit);
                    return true;
                }
            }
            if (mode == Mode.SCENE) {
                if (button == Input.Buttons.LEFT) {
                    if (placing != null) {
                        SceneObject object = new SceneObject(placing, x, y, placingFlipped);
                        objects.add(object);
                        sceneDirty = true;
                        return true;
                    }
                    selected = pick(x, y);
                    if (selected != null) {
                        movingSelected = true;
                        grabOffsetX = selected.x - x;
                        grabOffsetY = selected.y - y;
                        return true;
                    }
                } else if (button == Input.Buttons.RIGHT && placing != null) {
                    placing = null;
                    setStatus("");
                    return true;
                }
            }
            panning = true;
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            worldViewport.unproject(cursor.set(screenX, screenY));
            if (dragVertex >= 0) {
                moveVertex(dragVertex, cursor.x, cursor.y);
            } else if (movingSelected && selected != null) {
                selected.x = cursor.x + grabOffsetX;
                selected.y = cursor.y + grabOffsetY;
                sceneDirty = true;
            } else if (panning) {
                cameraControl.pan(screenX - lastScreenX, screenY - lastScreenY);
            }
            lastScreenX = screenX;
            lastScreenY = screenY;
            return dragVertex >= 0 || movingSelected || panning;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            boolean handled = dragVertex >= 0 || movingSelected || panning;
            if (dragVertex >= 0) updateAssetInfo();
            dragVertex = -1;
            movingSelected = false;
            panning = false;
            return handled;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            int x = Gdx.input.getX();
            if (!inCanvas(x) || amountY == 0f) return false;
            cameraControl.zoomAt(x, Gdx.input.getY(), amountY);
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            if (ctrl && keycode == Input.Keys.S) {
                if (mode == Mode.ASSET) saveAsset();
                else saveScene();
                return true;
            }
            switch (keycode) {
                case Input.Keys.R:
                    if (mode == Mode.ASSET) setViewFlipped(!viewFlipped);
                    else rotate();
                    return true;
                case Input.Keys.F:
                    if (mode == Mode.SCENE) fitScene();
                    else fitPending = true;
                    return true;
                case Input.Keys.NUM_1:
                case Input.Keys.NUM_2:
                case Input.Keys.NUM_3:
                    if (mode != Mode.ASSET) return false;
                    toolButtons[keycode - Input.Keys.NUM_1].setChecked(true);
                    return true;
                case Input.Keys.FORWARD_DEL:
                case Input.Keys.DEL:
                    if (mode != Mode.SCENE) return false;
                    deleteSelected();
                    return true;
                case Input.Keys.ESCAPE:
                    if (placing != null) setStatus("");
                    placing = null;
                    selected = null;
                    return true;
                default:
                    return false;
            }
        }
    }
}
