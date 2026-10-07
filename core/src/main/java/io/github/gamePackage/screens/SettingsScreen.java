package io.github.gamePackage.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.gamePackage.GameSettings;
import io.github.gamePackage.Main;
import io.github.gamePackage.ui.NumberSetting;

/** Settings menu, opened from the title screen. Tabs group the settings; Graphics is the first. */
public class SettingsScreen extends ScreenAdapter {
    private static final float NAME_WIDTH = 130f;

    private final Main game;
    private final GameSettings settings;
    private final Skin skin;
    private final Stage stage = new Stage(new ScreenViewport());
    private final SpriteBatch batch = new SpriteBatch();
    private final Texture background;
    private Window shadowWindow;
    private NumberSetting darkness;
    private NumberSetting softness;
    private NumberSetting passes;
    private TextButton[] qualityButtons;

    public SettingsScreen(Main game) {
        this.game = game;
        this.settings = game.settings;
        this.skin = game.skin;
        background = game.assets.catalog.terrains().isEmpty()
            ? null : game.assets.texture(game.assets.catalog.terrains().first(), false);
        build();
    }

    private void build() {
        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        Label title = new Label("Settings", skin, "title");
        title.setFontScale(2.2f);
        root.add(title).padBottom(24).row();

        TextButton graphicsTab = new TextButton("Graphics", skin, "toggle");
        graphicsTab.setChecked(true);
        new ButtonGroup<>(graphicsTab);
        Table tabs = new Table();
        tabs.add(graphicsTab).width(130);
        root.add(tabs).left().row();

        Table panel = new Table();
        panel.setBackground(skin.getDrawable("panel"));
        panel.pad(18);
        panel.defaults().left().padBottom(12);
        buildGraphicsTab(panel);
        root.add(panel).width(560).row();

        TextButton back = new TextButton("Back", skin, "menu");
        onChange(back, this::back);
        root.add(back).width(200).padTop(20).row();

        shadowWindow = buildShadowWindow();
    }

    private void buildGraphicsTab(Table panel) {
        TextButton windowed = new TextButton("Windowed", skin, "toggle");
        TextButton fullscreen = new TextButton("Fullscreen", skin, "toggle");
        new ButtonGroup<>(windowed, fullscreen);
        (settings.fullscreen ? fullscreen : windowed).setChecked(true);
        onChange(fullscreen, () -> setFullscreen(fullscreen.isChecked()));
        panel.add(name("Display")).width(NAME_WIDTH);
        panel.add(pair(windowed, fullscreen)).growX().row();

        TextButton off = new TextButton("Off", skin, "toggle");
        TextButton on = new TextButton("On", skin, "toggle");
        new ButtonGroup<>(off, on);
        (settings.softShadows ? on : off).setChecked(true);
        onChange(on, () -> {
            settings.softShadows = on.isChecked();
            settings.save();
        });
        TextButton adjust = new TextButton("Adjust...", skin);
        onChange(adjust, this::openShadowWindow);
        Table shadowRow = pair(off, on);
        shadowRow.add(adjust).width(110).padLeft(10);
        panel.add(name("Soft shadows")).width(NAME_WIDTH);
        panel.add(shadowRow).growX().row();

        Label note = new Label("Soft shadows blur the edges of shadows on the ground and on objects standing in "
            + "another object's shadow.", skin, "dim");
        note.setWrap(true);
        panel.add(note).colspan(2).growX().padBottom(0).row();
    }

    private Window buildShadowWindow() {
        Window window = new Window("Soft shadow settings", skin);
        window.setModal(true);
        window.setMovable(true);
        window.getTitleLabel().setFontScale(1f);
        window.defaults().growX().padBottom(8);

        darkness = new NumberSetting(skin, "Darkness", 0f, 1f, 0.01f, "%.2f", value -> {
            settings.shadowDarkness = value;
            settings.save();
        });
        softness = new NumberSetting(skin, "Softness", 0f, 96f, 1f, "%.0f", value -> {
            settings.shadowSoftness = value;
            settings.save();
        });
        passes = new NumberSetting(skin, "Passes", 1f, 4f, 1f, "%.0f", value -> {
            settings.blurPasses = Math.round(value);
            settings.save();
        });
        window.add(darkness).row();
        window.add(softness).row();
        window.add(passes).row();

        String[] names = {"Full", "Half", "Quarter"};
        int[] divisors = {1, 2, 4};
        qualityButtons = new TextButton[names.length];
        ButtonGroup<TextButton> qualityGroup = new ButtonGroup<>();
        Table qualityRow = new Table();
        qualityRow.add(new Label("Quality", skin, "dim")).width(74).left();
        for (int i = 0; i < names.length; i++) {
            final int divisor = divisors[i];
            TextButton button = new TextButton(names[i], skin, "toggle");
            qualityGroup.add(button);
            onChange(button, () -> {
                if (!button.isChecked()) return;
                settings.shadowQuality = divisor;
                settings.save();
            });
            qualityButtons[i] = button;
            qualityRow.add(button).growX().uniformX().padLeft(i == 0 ? 0 : 4);
        }
        window.add(qualityRow).row();

        Label note = new Label("Darkness also applies with soft shadows off. Higher quality and more passes look "
            + "smoother but cost performance.", skin, "dim");
        note.setWrap(true);
        window.add(note).width(420).row();

        TextButton defaults = new TextButton("Defaults", skin);
        onChange(defaults, () -> {
            boolean softOn = settings.softShadows;
            settings.resetShadowDefaults();
            settings.softShadows = softOn;
            settings.save();
            syncShadowWindow();
        });
        TextButton close = new TextButton("Close", skin);
        onChange(close, () -> shadowWindow.remove());
        window.add(pair(defaults, close)).padTop(6).padBottom(0).row();
        window.pack();
        return window;
    }

    private void openShadowWindow() {
        syncShadowWindow();
        stage.addActor(shadowWindow);
        shadowWindow.pack();
        shadowWindow.setPosition((stage.getWidth() - shadowWindow.getWidth()) / 2f,
            (stage.getHeight() - shadowWindow.getHeight()) / 2f);
    }

    private void syncShadowWindow() {
        darkness.setValue(settings.shadowDarkness);
        softness.setValue(settings.shadowSoftness);
        passes.setValue(settings.blurPasses);
        int index = settings.shadowQuality >= 4 ? 2 : settings.shadowQuality >= 2 ? 1 : 0;
        qualityButtons[index].setChecked(true);
    }

    private void setFullscreen(boolean fullscreen) {
        settings.fullscreen = fullscreen;
        settings.save();
        settings.applyDisplayMode();
    }

    private void back() {
        // Switch after the click finishes; switching disposes this stage, which is handling the click.
        Gdx.app.postRunnable(() -> game.setScreen(new TitleScreen(game)));
    }

    private Label name(String text) {
        return new Label(text, skin);
    }

    private Table pair(Actor first, Actor second) {
        Table row = new Table();
        row.defaults().growX().uniformX();
        row.add(first).padRight(4);
        row.add(second);
        return row;
    }

    private static void onChange(Actor actor, Runnable action) {
        actor.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor changed) {
                action.run();
            }
        });
    }

    @Override
    public void show() {
        InputAdapter keys = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode != Input.Keys.ESCAPE) return false;
                if (shadowWindow.hasParent()) shadowWindow.remove();
                else back();
                return true;
            }
        };
        Gdx.input.setInputProcessor(new InputMultiplexer(stage, keys));
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.06f, 0.07f, 0.09f, 1f);
        if (background != null) TitleScreen.drawDimmedBackground(batch, stage, background);
        stage.act(delta);
        stage.getViewport().apply();
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        if (shadowWindow.hasParent()) {
            shadowWindow.setPosition((width - shadowWindow.getWidth()) / 2f, (height - shadowWindow.getHeight()) / 2f);
        }
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
