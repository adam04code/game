package io.github.gamePackage.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.gamePackage.Main;
import io.github.gamePackage.editor.AssetEditorScreen;

/** Main menu: New Game, Load Game and Edit Assets. */
public class TitleScreen extends ScreenAdapter {
    private final Main game;
    private final Stage stage = new Stage(new ScreenViewport());
    private final SpriteBatch batch = new SpriteBatch();
    private final Texture background;

    public TitleScreen(Main game) {
        this.game = game;
        background = game.assets.catalog.terrains().isEmpty()
            ? null : game.assets.terrain(game.assets.catalog.terrains().first());

        Skin skin = game.skin;
        Table root = new Table();
        root.setFillParent(true);
        root.add(new Label("GAME", skin, "title")).padBottom(48).row();

        TextButton newGame = new TextButton("New Game", skin, "menu");
        TextButton loadGame = new TextButton("Load Game", skin, "menu");
        TextButton editAssets = new TextButton("Edit Assets", skin, "menu");
        // New Game and Load Game do nothing yet.
        editAssets.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                // Switch after the click finishes; switching disposes this stage, which is handling the click.
                Gdx.app.postRunnable(() -> game.setScreen(new AssetEditorScreen(game)));
            }
        });
        root.defaults().width(260).padBottom(12);
        root.add(newGame).row();
        root.add(loadGame).row();
        root.add(editAssets).row();
        stage.addActor(root);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.06f, 0.07f, 0.09f, 1f);
        if (background != null) drawBackground();
        stage.act(delta);
        stage.getViewport().apply();
        stage.draw();
    }

    /** Terrain map scaled to cover the screen, darkened so the menu stands out. */
    private void drawBackground() {
        ScreenViewport viewport = (ScreenViewport) stage.getViewport();
        viewport.apply(true);
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float scale = Math.max(width / background.getWidth(), height / background.getHeight());
        float drawWidth = background.getWidth() * scale;
        float drawHeight = background.getHeight() * scale;
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        batch.setColor(0.45f, 0.45f, 0.5f, 1f);
        batch.draw(background, (width - drawWidth) / 2f, (height - drawHeight) / 2f, drawWidth, drawHeight);
        batch.setColor(1f, 1f, 1f, 1f);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        dispose();
    }

    @Override
    public void dispose() {
        stage.dispose();
        batch.dispose();
    }
}
