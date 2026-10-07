package io.github.gamePackage.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ShortArray;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.gamePackage.Main;
import io.github.gamePackage.assets.GameAssets;

import java.util.Arrays;

/** What both editor modes share: the canvas camera, renderers, and small helpers for building the panels. */
class EditorContext {
    static final float LEFT_WIDTH = 320f;
    static final float RIGHT_WIDTH = 320f;
    static final float HANDLE_PIXELS = 6f;

    static final Color COLLISION_COLOR = new Color(1f, 0.3f, 0.3f, 1f);
    static final Color SHADOW_COLOR = new Color(0.4f, 0.7f, 1f, 1f);
    static final Color SHADOW_FILL = new Color(0f, 0f, 0f, 0.38f);
    static final Color FLAG_COLOR = new Color(1f, 0.62f, 0.25f, 1f);

    final Main game;
    final GameAssets assets;
    final Skin skin;
    final OrthographicCamera camera = new OrthographicCamera();
    final ScreenViewport worldViewport = new ScreenViewport(camera);
    final CameraControl cameraControl = new CameraControl(worldViewport);
    final SpriteBatch batch = new SpriteBatch();
    final ShapeRenderer shapes = new ShapeRenderer();
    final HitMasks hitMasks = new HitMasks();
    private final EarClippingTriangulator triangulator = new EarClippingTriangulator();
    final Label status;

    EditorContext(Main game) {
        this.game = game;
        this.assets = game.assets;
        this.skin = game.skin;
        status = new Label("", skin, "dim");
        status.setWrap(true);
    }

    void setStatus(String text) {
        status.setText(text);
    }

    // ---------------------------------------------------------------- panel building

    /** A coloured heading with a little space above it. */
    void section(Table panel, String title) {
        Label label = new Label(title, skin);
        label.setColor(0.55f, 0.75f, 1f, 1f);
        panel.add(label).padTop(10).row();
    }

    Label help(String text) {
        Label label = new Label(text, skin, "dim");
        label.setWrap(true);
        label.setFontScale(0.9f);
        return label;
    }

    Label wrapped(String text) {
        Label label = new Label(text, skin);
        label.setWrap(true);
        return label;
    }

    TextButton button(String text, Runnable action) {
        TextButton button = new TextButton(text, skin);
        onChange(button, action);
        return button;
    }

    TextButton toggle(String text) {
        return new TextButton(text, skin, "toggle");
    }

    /** Actors side by side with equal widths. */
    Table row(Actor... actors) {
        Table row = new Table();
        row.defaults().growX().uniformX();
        for (int i = 0; i < actors.length; i++) row.add(actors[i]).padRight(i < actors.length - 1 ? 4 : 0);
        return row;
    }

    static void onChange(Actor actor, Runnable action) {
        actor.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor changed) {
                action.run();
            }
        });
    }

    // ---------------------------------------------------------------- canvas drawing

    boolean inCanvas(int screenX) {
        return screenX >= LEFT_WIDTH && screenX < Gdx.graphics.getWidth() - RIGHT_WIDTH;
    }

    void beginShapes(ShapeRenderer.ShapeType type) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(type);
    }

    /** Fills a polygon given as x,y pairs (the first {@code length} floats are used). */
    void fillPolygon(float[] vertices, int length, Color color) {
        if (length < 6) return;
        float[] points = length == vertices.length ? vertices : Arrays.copyOf(vertices, length);
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
    void outline(float[] vertices, int length, Color color, float alpha) {
        int count = length / 2;
        if (count < 2) return;
        shapes.setColor(color.r, color.g, color.b, alpha);
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            if (j == 0 && count == 2) break;
            shapes.line(vertices[i * 2], vertices[i * 2 + 1], vertices[j * 2], vertices[j * 2 + 1]);
        }
    }

    void dispose() {
        batch.dispose();
        shapes.dispose();
    }
}
