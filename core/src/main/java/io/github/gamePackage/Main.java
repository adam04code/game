package io.github.gamePackage;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.gamePackage.assets.GameAssets;
import io.github.gamePackage.screens.LoadingScreen;
import io.github.gamePackage.ui.UiSkin;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends Game {
    public GameAssets assets;
    public Skin skin;
    public final GameSettings settings = new GameSettings();

    @Override
    public void create() {
        settings.load();
        settings.applyDisplayMode();
        assets = new GameAssets();
        skin = UiSkin.create();
        setScreen(new LoadingScreen(this));
    }

    @Override
    public void dispose() {
        if (screen != null) screen.hide();
        assets.dispose();
        skin.dispose();
    }
}
