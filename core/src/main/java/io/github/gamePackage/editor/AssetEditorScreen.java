package io.github.gamePackage.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.gamePackage.Main;
import io.github.gamePackage.assets.AssetBaker;
import io.github.gamePackage.assets.GameFiles;
import io.github.gamePackage.assets.SpriteAsset;
import io.github.gamePackage.screens.TitleScreen;

import java.util.Locale;

/**
 * The asset editor: an asset list on the left, the canvas in the middle, and the current mode's panel on the right.
 * <ul>
 *   <li>{@link AssetEditMode}: bounds, green-screen removal and resolution of one asset, plus a check of all
 *   assets that flags unfinished ones.</li>
 *   <li>{@link SceneEditMode}: spawn, move, resize and rotate assets on the terrain.</li>
 * </ul>
 */
public class AssetEditorScreen extends ScreenAdapter {
    private final Main game;
    private final EditorContext ctx;
    private final Stage stage = new Stage(new ScreenViewport());
    private final AssetEditMode assetMode;
    private final SceneEditMode sceneMode;
    private EditorMode mode;

    /** Issues per asset id, filled by "Check all assets". */
    private final ObjectMap<String, String> flags = new ObjectMap<>();
    private boolean checkRun;
    private boolean flaggedOnly;

    private FlagList assetList;
    private Label listTitle;
    private TextField filterField;
    private Table footerSlot;
    private Table checkSlot;
    private Table checkControls;
    private Label checkSummary;
    private TextButton flaggedOnlyButton;
    private ScrollPane modePanelPane;
    private boolean syncingList;

    /** Objects still waiting for automatic green removal while the editor opens. */
    private final Array<SpriteAsset> pending = new Array<>();
    private int pendingIndex;
    private int greenRemoved;
    private Table overlay;
    private Label overlayLabel;
    private Label zoomLabel;

    private boolean modeDragging;
    private boolean panning;
    private int lastScreenX;
    private int lastScreenY;

    /** List entry: an asset plus whatever the last check found wrong with it. */
    static class AssetListItem {
        final SpriteAsset asset;
        String issues;

        AssetListItem(SpriteAsset asset) {
            this.asset = asset;
        }

        @Override
        public String toString() {
            return issues != null ? "! " + asset : asset.toString();
        }
    }

    /** The asset list, drawing flagged assets in orange. */
    private static class FlagList extends List<AssetListItem> {
        FlagList(Skin skin) {
            super(skin);
        }

        @Override
        protected GlyphLayout drawItem(Batch batch, BitmapFont font, int index, AssetListItem item, float x, float y,
                                       float width) {
            if (item.issues == null) return super.drawItem(batch, font, index, item, x, y, width);
            // List only resets the colour after selected rows, so restore it or the rows below stay orange.
            Color normal = new Color(font.getColor());
            font.setColor(EditorContext.FLAG_COLOR.r, EditorContext.FLAG_COLOR.g, EditorContext.FLAG_COLOR.b,
                normal.a);
            GlyphLayout layout = super.drawItem(batch, font, index, item, x, y, width);
            font.setColor(normal);
            return layout;
        }
    }

    public AssetEditorScreen(Main game) {
        this.game = game;
        ctx = new EditorContext(game);
        assetMode = new AssetEditMode(ctx, this::assetSaved);
        sceneMode = new SceneEditMode(ctx);
        mode = assetMode;
        buildUi();
        refreshList();
    }

    // ---------------------------------------------------------------- opening: scan, remove green, check

    /** Scans the assets folder and queues every object whose green background hasn't been removed yet. */
    private void beginStartup() {
        ctx.assets.scanAndQueue();
        ctx.assets.manager.finishLoading();
        refreshList();
        pending.clear();
        for (SpriteAsset asset : ctx.assets.catalog.sprites()) {
            if (AssetBaker.needsAutoProcess(asset, ctx.assets.meta.find(asset.id))) pending.add(asset);
        }
        pendingIndex = 0;
        greenRemoved = 0;
        if (pending.notEmpty()) {
            overlayLabel.setText("Preparing assets...");
            stage.addActor(overlay);
        } else {
            finishStartup();
        }
    }

    /** Processes one queued asset per frame so the progress shows. */
    private void stepStartup() {
        SpriteAsset asset = pending.get(pendingIndex++);
        overlayLabel.setText("Removing green backgrounds\n" + pendingIndex + " / " + pending.size + "\n\n"
            + asset.name.replace('_', ' '));
        try {
            if (AssetBaker.autoProcess(asset, ctx.assets.meta.getOrCreate(asset.id))) {
                greenRemoved++;
                for (int side = 0; side < 2; side++) {
                    String source = asset.sourcePath(side == 1);
                    ctx.hitMasks.invalidate(source);
                    ctx.hitMasks.invalidate(GameFiles.processedPath(source));
                }
                ctx.assets.reload(asset);
            }
        } catch (Exception e) {
            Gdx.app.error("AssetEditor", "Automatic green removal failed for " + asset.id, e);
        }
        if (pendingIndex < pending.size) return;
        try {
            ctx.assets.meta.save();
        } catch (Exception e) {
            Gdx.app.error("AssetEditor", "Saving asset settings failed", e);
        }
        overlay.remove();
        finishStartup();
    }

    /** Scans again (so the processed images are picked up), runs the asset check and opens the first asset. */
    private void finishStartup() {
        pending.clear();
        rescan();
        runCheck();
        Array<SpriteAsset> all = allAssets();
        if (all.notEmpty()) {
            SpriteAsset first = ctx.assets.catalog.sprites().notEmpty() ? ctx.assets.catalog.sprites().first()
                : all.first();
            assetMode.assetSelected(first);
            selectInList(first);
        }
        if (all.isEmpty()) {
            ctx.setStatus("No assets found. Put images in sub-folders of assets/ and press Rescan.");
        } else {
            String removed = greenRemoved > 0 ? "Removed green from " + greenRemoved + " assets. " : "";
            ctx.setStatus(removed + (flags.size == 0 ? "All assets pass the check."
                : flags.size + " assets need work (orange)."));
        }
    }

    private boolean starting() {
        return pending.notEmpty();
    }

    /** Terrains first, then objects. */
    private Array<SpriteAsset> allAssets() {
        Array<SpriteAsset> all = new Array<>(ctx.assets.catalog.terrains());
        all.addAll(ctx.assets.catalog.sprites());
        return all;
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        Table left = new Table();
        left.setBackground(ctx.skin.getDrawable("panel"));
        left.top().pad(10);
        left.defaults().growX().padBottom(6);

        TextButton assetTab = ctx.toggle("Edit Asset");
        TextButton sceneTab = ctx.toggle("Scene");
        new ButtonGroup<Button>(assetTab, sceneTab);
        assetTab.setChecked(true);
        EditorContext.onChange(assetTab, () -> {
            if (assetTab.isChecked()) setMode(assetMode);
        });
        EditorContext.onChange(sceneTab, () -> {
            if (sceneTab.isChecked()) setMode(sceneMode);
        });
        left.add(ctx.row(assetTab, sceneTab)).padBottom(8).row();

        filterField = new TextField("", ctx.skin);
        filterField.setMessageText("Filter assets...");
        filterField.setTextFieldListener((field, c) -> refreshList());
        left.add(filterField).row();
        listTitle = new Label("", ctx.skin, "dim");
        left.add(listTitle).row();
        assetList = new FlagList(ctx.skin);
        assetList.getSelection().setRequired(false);
        assetList.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AssetListItem item = assetList.getSelected();
                if (!syncingList && item != null) mode.assetSelected(item.asset);
            }
        });
        ScrollPane listPane = new ScrollPane(assetList, ctx.skin);
        listPane.setFadeScrollBars(false);
        listPane.setFlickScroll(false);
        listPane.setScrollingDisabled(true, false);
        left.add(listPane).grow().row();

        // Asset checking only appears in Edit Asset mode.
        checkSummary = ctx.wrapped("");
        checkSummary.setColor(EditorContext.FLAG_COLOR);
        flaggedOnlyButton = ctx.toggle("Flagged only");
        EditorContext.onChange(flaggedOnlyButton, () -> {
            flaggedOnly = flaggedOnlyButton.isChecked();
            refreshList();
        });
        checkControls = new Table();
        checkControls.defaults().growX().padBottom(6);
        checkControls.add(ctx.row(ctx.button("Check all", this::runCheck), flaggedOnlyButton)).row();
        checkControls.add(checkSummary).row();
        checkSlot = new Table();
        left.add(checkSlot).padTop(2).row();

        left.add(ctx.row(ctx.button("Rescan folder", this::rescan), ctx.button("Back to title", this::exitToTitle)))
            .padTop(2).row();

        Table right = new Table();
        right.setBackground(ctx.skin.getDrawable("panel"));
        right.pad(10).padRight(4);
        modePanelPane = new ScrollPane(null, ctx.skin);
        modePanelPane.setFadeScrollBars(false);
        modePanelPane.setFlickScroll(false);
        modePanelPane.setScrollingDisabled(true, false);
        modePanelPane.setScrollbarsOnTop(false);
        right.add(modePanelPane).grow().row();
        footerSlot = new Table();
        right.add(footerSlot).growX().padTop(8).padRight(8).row();
        right.add(ctx.status).growX().padTop(6).padRight(8);

        root.add(left).width(EditorContext.LEFT_WIDTH).growY();
        root.add(buildZoomBar()).expand().top().left().pad(10);
        root.add(right).width(EditorContext.RIGHT_WIDTH).growY();
        showModeUi();

        // Covers the editor and swallows input while assets are processed on opening.
        overlay = new Table();
        overlay.setFillParent(true);
        overlay.setBackground(ctx.skin.newDrawable("white", new Color(0f, 0f, 0f, 0.75f)));
        overlay.setTouchable(Touchable.enabled);
        overlay.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                return true;
            }
        });
        overlayLabel = new Label("", ctx.skin);
        overlayLabel.setAlignment(Align.center);
        overlay.add(overlayLabel);
    }

    /** Zoom buttons floating over the top-left of the canvas. */
    private Table buildZoomBar() {
        zoomLabel = new Label("", ctx.skin, "dim");
        Table bar = new Table();
        bar.setBackground(ctx.skin.getDrawable("panel"));
        bar.pad(4);
        bar.defaults().width(40).padRight(4);
        bar.add(ctx.button("-", () -> zoomCanvasCentre(1f)));
        bar.add(ctx.button("+", () -> zoomCanvasCentre(-1f)));
        bar.add(ctx.button("Fit", () -> mode.fitCamera())).width(48);
        bar.add(zoomLabel).width(54).padLeft(4).padRight(2);
        return bar;
    }

    private void zoomCanvasCentre(float amount) {
        int left = (int) EditorContext.LEFT_WIDTH;
        int canvasWidth = Gdx.graphics.getWidth() - left - (int) EditorContext.RIGHT_WIDTH;
        ctx.cameraControl.zoomAt(left + canvasWidth / 2, Gdx.graphics.getHeight() / 2, amount * 2f);
    }

    private void showModeUi() {
        modePanelPane.setActor(mode.panel());
        footerSlot.clearChildren();
        if (mode.footer() != null) footerSlot.add(mode.footer()).growX();
        checkSlot.clearChildren();
        if (mode == assetMode) checkSlot.add(checkControls).growX();
        updateListTitle();
    }

    private void updateListTitle() {
        String action = mode == assetMode ? "pick one to edit" : "pick one to place";
        listTitle.setText("Assets (" + assetList.getItems().size + "): " + action);
    }

    // ---------------------------------------------------------------- asset list and checking

    /** Rebuilds the list from the catalog, applying flags and the "flagged only" filter. */
    private void refreshList() {
        String filter = filterField.getText().trim().toLowerCase(Locale.ROOT);
        Array<AssetListItem> items = new Array<>();
        for (SpriteAsset asset : allAssets()) {
            AssetListItem item = new AssetListItem(asset);
            item.issues = checkRun ? flags.get(asset.id) : null;
            if (flaggedOnly && item.issues == null) continue;
            if (!filter.isEmpty() && !asset.toString().toLowerCase(Locale.ROOT).contains(filter)) continue;
            items.add(item);
        }
        syncingList = true;
        assetList.setItems(items);
        syncingList = false;
        selectInList(mode.listSelection());
        updateCheckSummary();
        updateListTitle();
    }

    private void selectInList(SpriteAsset asset) {
        syncingList = true;
        AssetListItem match = null;
        if (asset != null) {
            for (AssetListItem item : assetList.getItems()) if (item.asset.id.equals(asset.id)) match = item;
        }
        if (match == null) assetList.getSelection().clear();
        else assetList.setSelected(match);
        syncingList = false;
    }

    /** Flags every asset whose saved settings still need work. */
    private void runCheck() {
        assetMode.saveIfDirty();
        computeFlags();
        checkRun = true;
        refreshList();
        ctx.setStatus(flags.size == 0 ? "All assets pass the check." : flags.size + " assets need work (orange).");
    }

    private void computeFlags() {
        flags.clear();
        for (SpriteAsset asset : allAssets()) {
            String issues = AssetCheck.issues(asset, ctx.assets.meta.find(asset.id));
            if (issues != null) flags.put(asset.id, issues);
        }
    }

    private void updateCheckSummary() {
        if (!checkRun) {
            checkSummary.setText("Flags assets with green backgrounds or no collision.");
            checkSummary.setColor(0.7f, 0.72f, 0.78f, 1f);
            return;
        }
        int total = allAssets().size;
        if (flags.size == 0) {
            checkSummary.setText("All " + total + " assets pass.");
            checkSummary.setColor(0.5f, 0.85f, 0.5f, 1f);
        } else {
            checkSummary.setText(flags.size + " of " + total + " assets flagged.");
            checkSummary.setColor(EditorContext.FLAG_COLOR);
        }
    }

    /** Keeps flags current once a check has run. */
    private void assetSaved() {
        if (!checkRun) return;
        computeFlags();
        refreshList();
    }

    // ---------------------------------------------------------------- modes and actions

    private void setMode(EditorMode newMode) {
        if (newMode == mode) return;
        mode.exit();
        storeCamera(mode);
        mode = newMode;
        if (mode.cameraPlaced) {
            restoreCamera(mode);
        } else {
            mode.cameraPlaced = true;
            mode.fitCamera();
        }
        modeDragging = false;
        panning = false;
        mode.enter();
        showModeUi();
        selectInList(mode.listSelection());
    }

    private void storeCamera(EditorMode m) {
        m.cameraState[0] = ctx.camera.position.x;
        m.cameraState[1] = ctx.camera.position.y;
        m.cameraState[2] = ctx.camera.zoom;
        m.cameraPlaced = true;
    }

    private void restoreCamera(EditorMode m) {
        ctx.camera.position.set(m.cameraState[0], m.cameraState[1], 0f);
        ctx.camera.zoom = m.cameraState[2];
        ctx.camera.update();
    }

    private void rescan() {
        assetMode.saveIfDirty();
        int before = allAssets().size;
        ctx.assets.scanAndQueue();
        ctx.assets.manager.finishLoading();
        assetMode.afterRescan();
        sceneMode.afterRescan();
        if (checkRun) computeFlags();
        refreshList();
        int found = allAssets().size;
        ctx.setStatus("Found " + found + " assets (" + Math.max(0, found - before) + " new)");
    }

    private void exitToTitle() {
        mode.exit();
        assetMode.saveIfDirty();
        sceneMode.saveIfDirty();
        // Switch after the click finishes; switching disposes this screen's stage, which is handling the click.
        Gdx.app.postRunnable(() -> game.setScreen(new TitleScreen(game)));
    }

    // ---------------------------------------------------------------- screen

    @Override
    public void show() {
        // The wheel goes to the canvas before the stage: a side panel keeps the wheel ("scroll focus") after it is
        // clicked, which would otherwise scroll the list instead of zooming.
        InputAdapter zoomInput = new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                int x = Gdx.input.getX();
                if (starting() || !ctx.inCanvas(x) || amountY == 0f) return false;
                ctx.cameraControl.zoomAt(x, Gdx.input.getY(), amountY);
                return true;
            }
        };
        Gdx.input.setInputProcessor(new InputMultiplexer(zoomInput, stage, new CanvasInput()));
        beginStartup();
    }

    @Override
    public void render(float delta) {
        if (starting()) stepStartup();
        mode.update(delta);
        // Keep the highlight in step with the mode (e.g. cleared when placing stops) so the same asset can be
        // clicked again.
        AssetListItem highlighted = assetList.getSelected();
        SpriteAsset wanted = mode.listSelection();
        if ((highlighted == null ? null : highlighted.asset) != wanted) selectInList(wanted);
        ScreenUtils.clear(0.05f, 0.055f, 0.07f, 1f);
        ctx.worldViewport.apply();
        ctx.camera.update();
        ctx.batch.setProjectionMatrix(ctx.camera.combined);
        ctx.shapes.setProjectionMatrix(ctx.camera.combined);
        mode.render();

        zoomLabel.setText(Math.round(100f / ctx.camera.zoom) + "%");
        stage.getViewport().apply();
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        int left = (int) EditorContext.LEFT_WIDTH;
        int canvasWidth = Math.max(1, width - left - (int) EditorContext.RIGHT_WIDTH);
        ctx.worldViewport.update(canvasWidth, height, false);
        ctx.worldViewport.setScreenBounds(left, 0, canvasWidth, height);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        dispose();
    }

    @Override
    public void dispose() {
        assetMode.dispose();
        sceneMode.dispose();
        stage.dispose();
        ctx.dispose();
    }

    /** Mouse and keyboard on the canvas between the two panels. Clicks a mode doesn't take pan the camera. */
    private class CanvasInput extends InputAdapter {
        private final Vector2 world = new Vector2();

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (starting() || !ctx.inCanvas(screenX)) return false;
            stage.setKeyboardFocus(null);
            stage.setScrollFocus(null);
            lastScreenX = screenX;
            lastScreenY = screenY;
            ctx.worldViewport.unproject(world.set(screenX, screenY));
            modeDragging = mode.touchDown(world.x, world.y, button);
            panning = !modeDragging;
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (modeDragging) {
                ctx.worldViewport.unproject(world.set(screenX, screenY));
                mode.touchDragged(world.x, world.y);
            } else if (panning) {
                ctx.cameraControl.pan(screenX - lastScreenX, screenY - lastScreenY);
            }
            lastScreenX = screenX;
            lastScreenY = screenY;
            return modeDragging || panning;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            boolean handled = modeDragging || panning;
            if (modeDragging) mode.touchUp();
            modeDragging = false;
            panning = false;
            return handled;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (starting()) return true;
            // Typing in a text box (filter, number fields) must not trigger editor shortcuts.
            if (stage.getKeyboardFocus() instanceof TextField) return false;
            boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            if (ctrl && keycode == Input.Keys.S) {
                mode.save();
                return true;
            }
            if (keycode == Input.Keys.F) {
                mode.fitCamera();
                return true;
            }
            return mode.keyDown(keycode, ctrl);
        }
    }
}
