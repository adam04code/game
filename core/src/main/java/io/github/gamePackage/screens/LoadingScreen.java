package io.github.gamePackage.screens;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.gamePackage.Main;

/** Scans the assets folder, loads everything with a progress bar, then opens the title screen. */
public class LoadingScreen extends ScreenAdapter {
    private static final float MIN_SHOW_SECONDS = 0.4f;

    private final Main game;
    private final ScreenViewport viewport = new ScreenViewport();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final GlyphLayout layout = new GlyphLayout();
    private float elapsed;
    private float shownProgress;

    public LoadingScreen(Main game) {
        this.game = game;
    }

    @Override
    public void show() {
        game.assets.meta.load();
        game.assets.scanAndQueue();
    }

    @Override
    public void render(float delta) {
        elapsed += delta;
        boolean done = game.assets.manager.update(16);
        float progress = game.assets.manager.getProgress();
        shownProgress += (progress - shownProgress) * Math.min(1f, delta * 10f);
        if (done && elapsed >= MIN_SHOW_SECONDS) {
            game.setScreen(new TitleScreen(game));
            return;
        }

        ScreenUtils.clear(0.06f, 0.07f, 0.09f, 1f);
        viewport.apply(true);
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float barWidth = Math.min(480f, width - 64f);
        float barX = (width - barWidth) / 2f;
        float barY = height / 2f - 6f;

        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.18f, 0.19f, 0.23f, 1f);
        shapes.rect(barX, barY, barWidth, 12f);
        shapes.setColor(0.35f, 0.62f, 0.90f, 1f);
        shapes.rect(barX, barY, barWidth * shownProgress, 12f);
        shapes.end();

        BitmapFont font = game.skin.getFont("default");
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        font.setColor(Color.LIGHT_GRAY);
        layout.setText(font, "Loading assets...  " + Math.round(progress * 100f) + "%");
        font.draw(batch, layout, (width - layout.width) / 2f, barY + 40f);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
    }
}
