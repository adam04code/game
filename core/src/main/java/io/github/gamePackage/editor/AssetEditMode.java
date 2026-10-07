package io.github.gamePackage.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.FloatArray;
import io.github.gamePackage.assets.AssetMeta;
import io.github.gamePackage.assets.GameFiles;
import io.github.gamePackage.assets.ImageProcessor;
import io.github.gamePackage.assets.SpriteAsset;

import java.util.ArrayList;

/**
 * Edits one asset: collision and shadow polygons (any number of each), green-screen removal and resolution.
 * Saving writes a processed PNG to {@code assets/processed/} (the source image is never changed) and the
 * settings to {@code assets/data/asset_meta.json}.
 */
class AssetEditMode extends EditorMode {
    private static final float PREVIEW_DELAY = 0.2f;
    static final float MIN_SCALE = 0.1f;
    static final float MAX_SCALE = 4f;
    static final float SCALE_STEP = 1.1f;

    private enum Tool { VIEW, COLLISION, SHADOW }

    private final Runnable onSaved;
    private final Vector2 edgeStart = new Vector2();
    private final Vector2 edgeEnd = new Vector2();
    private final Vector2 point = new Vector2();

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
    /** Selected shape index per tool. */
    private final int[] activeShape = new int[Tool.values().length];
    private boolean metaDirty;
    private boolean bakeDirty;
    private float previewRebuildIn = -1f;
    private boolean fitPending;
    private int dragShape = -1;
    private int dragVertex = -1;

    private final Table panel = new Table();
    private final Table greenSection = new Table();
    private final Table greenSlot = new Table();
    private final Table flipSlot = new Table();
    private Label assetInfo;
    private Label checkInfo;
    private Label shapeLabel;
    private Label thresholdLabel;
    private Label softnessLabel;
    private TextButton viewFlippedButton;
    private TextButton noCollisionButton;
    private TextButton removeGreenButton;
    private final TextButton[] toolButtons = new TextButton[3];
    private Slider thresholdSlider;
    private Slider softnessSlider;
    private Label defaultScaleLabel;
    private Slider defaultScaleSlider;
    private boolean syncingUi;

    AssetEditMode(EditorContext ctx, Runnable onSaved) {
        super(ctx);
        this.onSaved = onSaved;
        buildPanel();
    }

    // ---------------------------------------------------------------- panel

    private void buildPanel() {
        panel.top().padRight(10);
        panel.defaults().growX().padBottom(5);
        // Created first: checking a tool button (which ButtonGroup does on add) updates it.
        shapeLabel = new Label("", ctx.skin);
        shapeLabel.setAlignment(Align.center);

        ctx.section(panel, "Asset");
        assetInfo = ctx.wrapped("");
        panel.add(assetInfo).row();
        checkInfo = ctx.wrapped("");
        panel.add(checkInfo).row();
        viewFlippedButton = ctx.toggle("View flipped side (R)");
        EditorContext.onChange(viewFlippedButton, () -> {
            if (!syncingUi) setViewFlipped(viewFlippedButton.isChecked());
        });
        flipSlot.add(viewFlippedButton).growX();
        panel.add(flipSlot).row();

        ctx.section(panel, "Bounds");
        String[] toolNames = {"View (1)", "Collision (2)", "Shadow (3)"};
        ButtonGroup<TextButton> toolGroup = new ButtonGroup<>();
        for (int i = 0; i < toolButtons.length; i++) {
            final Tool buttonTool = Tool.values()[i];
            TextButton toolButton = ctx.toggle(toolNames[i]);
            toolButton.getLabel().setFontScale(0.9f);
            EditorContext.onChange(toolButton, () -> {
                if (toolButton.isChecked()) setTool(buttonTool);
            });
            toolGroup.add(toolButton);
            toolButtons[i] = toolButton;
        }
        panel.add(ctx.row(toolButtons)).row();

        Table shapeRow = new Table();
        shapeRow.add(ctx.button("<", () -> cycleShape(-1))).width(36);
        shapeRow.add(shapeLabel).growX();
        shapeRow.add(ctx.button(">", () -> cycleShape(1))).width(36);
        panel.add(shapeRow).row();
        panel.add(ctx.row(ctx.button("New shape (N)", this::newShape), ctx.button("Delete shape", this::deleteShape)))
            .row();
        noCollisionButton = ctx.toggle("No collision needed");
        EditorContext.onChange(noCollisionButton, () -> {
            if (syncingUi || meta == null) return;
            meta.noCollision = noCollisionButton.isChecked();
            markMetaDirty();
        });
        panel.add(noCollisionButton).row();
        panel.add(ctx.help("Click: add point.  Drag point: move.  Right-click point: delete.\n"
            + "Click a point of another shape to select it.  Tab: next shape.")).row();

        // Terrain maps have no green screen, so this section is only shown for objects.
        greenSection.defaults().growX().padBottom(5);
        ctx.section(greenSection, "Green screen");
        removeGreenButton = ctx.toggle("Remove green background");
        EditorContext.onChange(removeGreenButton, () -> {
            if (syncingUi || meta == null) return;
            meta.removeGreen = removeGreenButton.isChecked();
            processingChanged(0f);
        });
        greenSection.add(removeGreenButton).row();
        thresholdLabel = new Label("", ctx.skin, "dim");
        greenSection.add(thresholdLabel).row();
        thresholdSlider = new Slider(0.05f, 0.95f, 0.01f, false, ctx.skin);
        EditorContext.onChange(thresholdSlider, () -> {
            if (syncingUi || meta == null) return;
            meta.keyThreshold = thresholdSlider.getValue();
            updateSliderLabels();
            if (meta.removeGreen) processingChanged(PREVIEW_DELAY);
        });
        greenSection.add(thresholdSlider).row();
        softnessLabel = new Label("", ctx.skin, "dim");
        greenSection.add(softnessLabel).row();
        softnessSlider = new Slider(0.02f, 0.8f, 0.01f, false, ctx.skin);
        EditorContext.onChange(softnessSlider, () -> {
            if (syncingUi || meta == null) return;
            meta.keySoftness = softnessSlider.getValue();
            updateSliderLabels();
            if (meta.removeGreen) processingChanged(PREVIEW_DELAY);
        });
        greenSection.add(softnessSlider).row();
        panel.add(greenSlot).row();

        ctx.section(panel, "Resolution");
        panel.add(ctx.row(ctx.button("Halve", this::halveResolution), ctx.button("Original size", () -> {
            if (meta == null || meta.halvings == 0) return;
            meta.halvings = 0;
            processingChanged(0f);
        }))).row();

        ctx.section(panel, "Size in scene");
        defaultScaleLabel = new Label("", ctx.skin, "dim");
        panel.add(defaultScaleLabel).row();
        defaultScaleSlider = new Slider(MIN_SCALE, MAX_SCALE, 0.01f, false, ctx.skin);
        EditorContext.onChange(defaultScaleSlider, () -> {
            if (!syncingUi) setDefaultScale(defaultScaleSlider.getValue());
        });
        panel.add(defaultScaleSlider).row();
        panel.add(ctx.row(
            ctx.button("Smaller", () -> {
                if (meta != null) setDefaultScale(meta.defaultScale / SCALE_STEP);
            }),
            ctx.button("Bigger", () -> {
                if (meta != null) setDefaultScale(meta.defaultScale * SCALE_STEP);
            }),
            ctx.button("1x", () -> setDefaultScale(1f)))).row();

        panel.add(ctx.button("Save asset (Ctrl+S)", this::save)).padTop(14).row();
        toolButtons[0].setChecked(true);
    }

    @Override
    Table panel() {
        return panel;
    }

    private void setTool(Tool newTool) {
        tool = newTool;
        updateShapeLabel();
    }

    private void setViewFlipped(boolean flipped) {
        viewFlipped = flipped;
        syncingUi = true;
        viewFlippedButton.setChecked(flipped);
        syncingUi = false;
    }

    private void syncPanel() {
        boolean object = current == null || !current.terrain;
        greenSlot.clearChildren();
        if (object) greenSlot.add(greenSection).growX();
        flipSlot.clearChildren();
        if (object) flipSlot.add(viewFlippedButton).growX();
        syncingUi = true;
        viewFlippedButton.setChecked(viewFlipped);
        boolean hasMeta = meta != null;
        noCollisionButton.setChecked(hasMeta && meta.noCollision);
        removeGreenButton.setChecked(hasMeta && meta.removeGreen);
        if (hasMeta) {
            thresholdSlider.setValue(meta.keyThreshold);
            softnessSlider.setValue(meta.keySoftness);
            defaultScaleSlider.setValue(meta.defaultScale);
        }
        syncingUi = false;
        updateSliderLabels();
        updateInfo();
    }

    /** Size new copies of this asset get when spawned in the scene. */
    private void setDefaultScale(float scale) {
        if (meta == null) return;
        meta.defaultScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        syncingUi = true;
        defaultScaleSlider.setValue(meta.defaultScale);
        syncingUi = false;
        updateSliderLabels();
        markMetaDirty();
    }

    private void updateSliderLabels() {
        if (meta == null) return;
        int sceneWidth = previewBase == null ? 0 : Math.round(previewBase.getWidth() * meta.defaultScale);
        int sceneHeight = previewBase == null ? 0 : Math.round(previewBase.getHeight() * meta.defaultScale);
        defaultScaleLabel.setText(String.format("Default: %.2fx  (%dx%d in the scene)", meta.defaultScale,
            sceneWidth, sceneHeight));
        thresholdLabel.setText(String.format("Threshold: %.2f  (lower removes more)", meta.keyThreshold));
        softnessLabel.setText(String.format("Edge softness: %.2f", meta.keySoftness));
    }

    private void updateInfo() {
        updateShapeLabel();
        updateSliderLabels();
        if (current == null || previewBase == null) {
            assetInfo.setText("No asset selected");
            checkInfo.setText("");
            return;
        }
        StringBuilder text = new StringBuilder(current.name.replace('_', ' '));
        text.append("\nFolder: ").append(current.category.isEmpty() ? "(assets root)" : current.category);
        if (current.terrain) text.append("\nTerrain map");
        else text.append(current.hasFlipped() ? "\nHas a flipped side" : "\nNo flipped file (mirrored in game)");
        text.append("\nSize: ").append(originalBase.getWidth()).append("x").append(originalBase.getHeight());
        if (meta.halvings > 0) {
            text.append(" -> ").append(previewBase.getWidth()).append("x").append(previewBase.getHeight());
            if (current.terrain) text.append("\n(scene still draws it at full size)");
        }
        if (!current.terrain) {
            text.append("\nGreen screen: ").append(meta.removeGreen ? "removed"
                : meta.noGreenBackground ? "none detected" : "not removed");
        }
        text.append("\nShapes: ").append(meta.collisions.length).append(" collision, ")
            .append(meta.shadows.length).append(" shadow");
        if (metaDirty) text.append("\n* unsaved changes");
        assetInfo.setText(text);

        String issues = AssetCheck.issues(current, meta);
        checkInfo.setText(issues == null ? "Check: OK" : "Needs: " + issues);
        checkInfo.setColor(issues == null ? new Color(0.5f, 0.85f, 0.5f, 1f) : EditorContext.FLAG_COLOR);
    }

    private void updateShapeLabel() {
        if (meta == null || tool == Tool.VIEW) {
            shapeLabel.setText("Pick Collision or Shadow");
            return;
        }
        float[][] list = shapes(tool);
        String kind = tool == Tool.COLLISION ? "Collision" : "Shadow";
        if (list.length == 0) shapeLabel.setText(kind + ": no shapes");
        else shapeLabel.setText(kind + " " + (active() + 1) + " of " + list.length);
    }

    // ---------------------------------------------------------------- asset loading and processing

    @Override
    void assetSelected(SpriteAsset asset) {
        if (asset == current) return;
        saveIfDirty();
        disposeImages();
        current = asset;
        meta = ctx.assets.meta.getOrCreate(asset.id);
        metaDirty = false;
        bakeDirty = false;
        viewFlipped = false;
        activeShape[Tool.COLLISION.ordinal()] = 0;
        activeShape[Tool.SHADOW.ordinal()] = 0;
        try {
            originalBase = new Pixmap(GameFiles.resolve(asset.basePath()));
            if (asset.hasFlipped()) originalFlipped = new Pixmap(GameFiles.resolve(asset.flippedPath()));
        } catch (Exception e) {
            Gdx.app.error("AssetEditor", "Could not open " + asset.id, e);
            ctx.setStatus("Could not open " + asset.basePath() + ": " + e.getMessage());
            disposeImages();
            current = null;
            meta = null;
            syncPanel();
            return;
        }
        rebuildPreview();
        fitPending = true;
        syncPanel();
        ctx.setStatus("");
    }

    @Override
    SpriteAsset listSelection() {
        return current;
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
        updateInfo();
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
        previewRebuildIn = delay;
        markMetaDirty();
    }

    private void markMetaDirty() {
        metaDirty = true;
        updateInfo();
    }

    private void halveResolution() {
        if (meta == null) return;
        int smallest = Math.min(originalBase.getWidth(), originalBase.getHeight()) >> (meta.halvings + 1);
        if (smallest < 8) {
            ctx.setStatus("The asset is already as small as it can go.");
            return;
        }
        meta.halvings++;
        processingChanged(0f);
    }

    void saveIfDirty() {
        if (current != null && metaDirty) save();
    }

    /** Writes the processed PNGs (or deletes them when no processing is needed) and the asset settings. */
    @Override
    void save() {
        if (current == null) return;
        if (previewRebuildIn >= 0f) rebuildPreview();
        meta.collisions = withoutEmpty(meta.collisions);
        meta.shadows = withoutEmpty(meta.shadows);
        try {
            if (bakeDirty) {
                writeProcessed(current.basePath(), previewBase);
                if (current.hasFlipped()) writeProcessed(current.flippedPath(), previewFlipped);
            }
            ctx.assets.meta.save();
        } catch (Exception e) {
            Gdx.app.error("AssetEditor", "Saving " + current.id + " failed", e);
            ctx.setStatus("Saving failed: " + e.getMessage());
            return;
        }
        if (bakeDirty) {
            invalidateHitMasks(current);
            ctx.assets.reload(current);
        }
        metaDirty = false;
        bakeDirty = false;
        updateInfo();
        ctx.setStatus("Saved " + current.name);
        onSaved.run();
    }

    private static float[][] withoutEmpty(float[][] list) {
        ArrayList<float[]> kept = new ArrayList<>();
        for (float[] shape : list) if (shape.length > 0) kept.add(shape);
        return kept.toArray(new float[0][]);
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
            ctx.hitMasks.invalidate(source);
            ctx.hitMasks.invalidate(GameFiles.processedPath(source));
        }
    }

    // ---------------------------------------------------------------- shapes

    private float[][] shapes(Tool shapeTool) {
        return shapeTool == Tool.COLLISION ? meta.collisions : meta.shadows;
    }

    private void setShapes(Tool shapeTool, float[][] list) {
        if (shapeTool == Tool.COLLISION) meta.collisions = list;
        else meta.shadows = list;
        markMetaDirty();
    }

    /** Index of the selected shape for the current tool, kept in range. */
    private int active() {
        int count = shapes(tool).length;
        int index = Math.max(0, Math.min(activeShape[tool.ordinal()], count - 1));
        activeShape[tool.ordinal()] = index;
        return index;
    }

    private void setActive(int index) {
        activeShape[tool.ordinal()] = index;
        updateShapeLabel();
    }

    private boolean editingShapes() {
        if (meta != null && tool != Tool.VIEW) return true;
        ctx.setStatus("Choose Collision (2) or Shadow (3) first.");
        return false;
    }

    private void newShape() {
        if (!editingShapes()) return;
        float[][] list = shapes(tool);
        // Reuse an empty shape at the end instead of piling up empty ones.
        if (list.length > 0 && list[list.length - 1].length == 0) {
            setActive(list.length - 1);
            return;
        }
        float[][] grown = new float[list.length + 1][];
        System.arraycopy(list, 0, grown, 0, list.length);
        grown[list.length] = new float[0];
        setShapes(tool, grown);
        setActive(list.length);
        ctx.setStatus("New shape: click to add its points.");
    }

    private void deleteShape() {
        if (!editingShapes()) return;
        float[][] list = shapes(tool);
        if (list.length == 0) return;
        int index = active();
        float[][] shrunk = new float[list.length - 1][];
        System.arraycopy(list, 0, shrunk, 0, index);
        System.arraycopy(list, index + 1, shrunk, index, list.length - index - 1);
        setShapes(tool, shrunk);
        setActive(Math.max(0, index - 1));
    }

    private void cycleShape(int direction) {
        if (!editingShapes()) return;
        int count = shapes(tool).length;
        if (count == 0) return;
        setActive((active() + direction + count) % count);
    }

    /** Converts a normalised point to world coordinates on the canvas (mirrored when viewing the flip). */
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

    /** Finds a vertex under the point, preferring the selected shape. Returns {shape, vertex} or null. */
    private int[] hitVertex(float worldX, float worldY) {
        float[][] list = shapes(tool);
        if (list.length == 0) return null;
        int first = active();
        float radius = EditorContext.HANDLE_PIXELS * 1.6f * ctx.camera.zoom;
        for (int n = 0; n < list.length; n++) {
            int shape = (first + n) % list.length;
            float[] points = list[shape];
            for (int i = points.length / 2 - 1; i >= 0; i--) {
                float dx = toCanvasX(points[i * 2]) - worldX;
                float dy = toCanvasY(points[i * 2 + 1]) - worldY;
                if (dx * dx + dy * dy <= radius * radius) return new int[] {shape, i};
            }
        }
        return null;
    }

    /** Adds a point to the selected shape, inserting it into the nearest edge once the shape has three points. */
    private int insertVertex(float worldX, float worldY) {
        if (shapes(tool).length == 0) newShape();
        int shape = active();
        float[] points = shapes(tool)[shape];
        int count = points.length / 2;
        int insertAt = count;
        if (count >= 3) {
            float best = Float.MAX_VALUE;
            point.set(worldX, worldY);
            for (int i = 0; i < count; i++) {
                int j = (i + 1) % count;
                edgeStart.set(toCanvasX(points[i * 2]), toCanvasY(points[i * 2 + 1]));
                edgeEnd.set(toCanvasX(points[j * 2]), toCanvasY(points[j * 2 + 1]));
                float distance = Intersector.distanceSegmentPoint(edgeStart, edgeEnd, point);
                if (distance < best) {
                    best = distance;
                    insertAt = i + 1;
                }
            }
        }
        FloatArray edited = new FloatArray(points);
        edited.insert(insertAt * 2, toNormalizedY(worldY));
        edited.insert(insertAt * 2, toNormalizedX(worldX));
        shapes(tool)[shape] = edited.toArray();
        markMetaDirty();
        return insertAt;
    }

    private void removeVertex(int shape, int index) {
        FloatArray edited = new FloatArray(shapes(tool)[shape]);
        edited.removeRange(index * 2, index * 2 + 1);
        shapes(tool)[shape] = edited.toArray();
        markMetaDirty();
    }

    // ---------------------------------------------------------------- input

    @Override
    boolean touchDown(float x, float y, int button) {
        if (meta == null || tool == Tool.VIEW) return false;
        int[] hit = hitVertex(x, y);
        if (button == Input.Buttons.LEFT) {
            if (hit != null) {
                setActive(hit[0]);
                dragShape = hit[0];
                dragVertex = hit[1];
            } else {
                dragVertex = insertVertex(x, y);
                dragShape = active();
            }
            return true;
        }
        if (button == Input.Buttons.RIGHT && hit != null) {
            setActive(hit[0]);
            removeVertex(hit[0], hit[1]);
            return true;
        }
        return false;
    }

    @Override
    void touchDragged(float x, float y) {
        if (dragVertex < 0) return;
        float[] points = shapes(tool)[dragShape];
        points[dragVertex * 2] = toNormalizedX(x);
        points[dragVertex * 2 + 1] = toNormalizedY(y);
        metaDirty = true;
    }

    @Override
    void touchUp() {
        if (dragVertex >= 0) updateInfo();
        dragShape = -1;
        dragVertex = -1;
    }

    @Override
    boolean keyDown(int keycode, boolean ctrl) {
        switch (keycode) {
            case Input.Keys.R:
                if (current != null && !current.terrain) setViewFlipped(!viewFlipped);
                return true;
            case Input.Keys.NUM_1:
            case Input.Keys.NUM_2:
            case Input.Keys.NUM_3:
                toolButtons[keycode - Input.Keys.NUM_1].setChecked(true);
                return true;
            case Input.Keys.N:
                newShape();
                return true;
            case Input.Keys.TAB:
                cycleShape(1);
                return true;
            default:
                return false;
        }
    }

    // ---------------------------------------------------------------- frame

    @Override
    void fitCamera() {
        fitPending = true;
    }

    @Override
    void update(float delta) {
        if (previewRebuildIn >= 0f) {
            previewRebuildIn -= delta;
            if (previewRebuildIn < 0f) rebuildPreview();
        }
        if (fitPending && ctx.worldViewport.getScreenWidth() > 0 && previewBaseTexture != null) {
            fitPending = false;
            ctx.cameraControl.fit(0f, 0f, previewTexture().getWidth(), previewTexture().getHeight());
        }
    }

    @Override
    void render() {
        if (current == null || previewBaseTexture == null) return;
        Texture texture = previewTexture();
        boolean mirror = viewFlipped && previewFlippedTexture == null;
        int width = texture.getWidth();
        int height = texture.getHeight();
        ShapeRenderer shapes = ctx.shapes;

        ctx.beginShapes(ShapeRenderer.ShapeType.Filled);
        float tile = Math.max(8f, Math.max(width, height) / 32f);
        for (float y = 0; y < height; y += tile) {
            for (float x = 0; x < width; x += tile) {
                boolean dark = (((int) (x / tile) + (int) (y / tile)) & 1) == 0;
                shapes.setColor(dark ? 0.32f : 0.40f, dark ? 0.32f : 0.40f, dark ? 0.34f : 0.42f, 1f);
                shapes.rect(x, y, Math.min(tile, width - x), Math.min(tile, height - y));
            }
        }
        shapes.end();

        ctx.batch.begin();
        ctx.batch.draw(texture, 0, 0, width, height, 0, 0, width, height, mirror, false);
        ctx.batch.end();

        float[][] shadowShapes = canvasShapes(meta.shadows);
        float[][] collisionShapes = canvasShapes(meta.collisions);
        for (float[] shadow : shadowShapes) ctx.fillPolygon(shadow, shadow.length, EditorContext.SHADOW_FILL);

        ctx.beginShapes(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.6f, 0.6f, 0.65f, 1f);
        shapes.rect(0, 0, width, height);
        drawOutlines(shadowShapes, Tool.SHADOW, EditorContext.SHADOW_COLOR);
        drawOutlines(collisionShapes, Tool.COLLISION, EditorContext.COLLISION_COLOR);
        shapes.end();

        if (tool == Tool.VIEW) return;
        float[][] editing = tool == Tool.COLLISION ? collisionShapes : shadowShapes;
        Color color = tool == Tool.COLLISION ? EditorContext.COLLISION_COLOR : EditorContext.SHADOW_COLOR;
        int selectedShape = active();
        ctx.beginShapes(ShapeRenderer.ShapeType.Filled);
        for (int s = 0; s < editing.length; s++) {
            boolean isSelected = s == selectedShape;
            float size = EditorContext.HANDLE_PIXELS * ctx.camera.zoom * (isSelected ? 1f : 0.7f);
            float[] points = editing[s];
            for (int i = 0; i < points.length; i += 2) {
                if (isSelected && i == 0) shapes.setColor(Color.WHITE);
                else shapes.setColor(color.r, color.g, color.b, isSelected ? 1f : 0.55f);
                shapes.rect(points[i] - size / 2f, points[i + 1] - size / 2f, size, size);
            }
        }
        shapes.end();
    }

    private void drawOutlines(float[][] list, Tool shapeTool, Color color) {
        for (int s = 0; s < list.length; s++) {
            float alpha = shapeTool != tool ? 0.45f : (s == active() ? 1f : 0.6f);
            ctx.outline(list[s], list[s].length, color, alpha);
        }
    }

    private float[][] canvasShapes(float[][] normalized) {
        float[][] out = new float[normalized.length][];
        for (int s = 0; s < normalized.length; s++) {
            float[] points = normalized[s];
            out[s] = new float[points.length];
            for (int i = 0; i < points.length; i += 2) {
                out[s][i] = toCanvasX(points[i]);
                out[s][i + 1] = toCanvasY(points[i + 1]);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    void exit() {
        saveIfDirty();
    }

    @Override
    void afterRescan() {
        if (current == null) return;
        SpriteAsset refreshed = ctx.assets.catalog.find(current.id);
        if (refreshed != null) {
            current = refreshed;
        } else {
            disposeImages();
            current = null;
            meta = null;
            syncPanel();
        }
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

    private void disposeImages() {
        disposePreviews();
        if (originalBase != null) originalBase.dispose();
        if (originalFlipped != null) originalFlipped.dispose();
        originalBase = null;
        originalFlipped = null;
    }

    @Override
    void dispose() {
        disposeImages();
    }
}
