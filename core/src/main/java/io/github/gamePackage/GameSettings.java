package io.github.gamePackage;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Preferences;

/** Player settings, kept in libGDX preferences so they survive restarts. */
public class GameSettings {
    public static final int WINDOW_WIDTH = 1440;
    public static final int WINDOW_HEIGHT = 900;

    public static final boolean DEFAULT_FULLSCREEN = false;
    public static final boolean DEFAULT_SOFT_SHADOWS = true;
    public static final float DEFAULT_SHADOW_DARKNESS = 0.4f;
    /** Blur radius in world pixels. */
    public static final float DEFAULT_SHADOW_SOFTNESS = 24f;
    /** The shadow mask is drawn at 1 / this of the screen resolution. */
    public static final int DEFAULT_SHADOW_QUALITY = 2;
    public static final int DEFAULT_BLUR_PASSES = 2;

    private static final String PREFERENCES = "io.github.gamePackage.settings";

    public boolean fullscreen = DEFAULT_FULLSCREEN;
    public boolean softShadows = DEFAULT_SOFT_SHADOWS;
    /** How dark shadows are, 0 (none) to 1 (black). */
    public float shadowDarkness = DEFAULT_SHADOW_DARKNESS;
    public float shadowSoftness = DEFAULT_SHADOW_SOFTNESS;
    /** Resolution divisor for soft shadows: 1 full, 2 half, 4 quarter. */
    public int shadowQuality = DEFAULT_SHADOW_QUALITY;
    public int blurPasses = DEFAULT_BLUR_PASSES;

    public void load() {
        Preferences prefs = Gdx.app.getPreferences(PREFERENCES);
        fullscreen = prefs.getBoolean("fullscreen", DEFAULT_FULLSCREEN);
        softShadows = prefs.getBoolean("softShadows", DEFAULT_SOFT_SHADOWS);
        shadowDarkness = prefs.getFloat("shadowDarkness", DEFAULT_SHADOW_DARKNESS);
        shadowSoftness = prefs.getFloat("shadowSoftness", DEFAULT_SHADOW_SOFTNESS);
        shadowQuality = prefs.getInteger("shadowQuality", DEFAULT_SHADOW_QUALITY);
        blurPasses = prefs.getInteger("blurPasses", DEFAULT_BLUR_PASSES);
    }

    public void save() {
        Preferences prefs = Gdx.app.getPreferences(PREFERENCES);
        prefs.putBoolean("fullscreen", fullscreen);
        prefs.putBoolean("softShadows", softShadows);
        prefs.putFloat("shadowDarkness", shadowDarkness);
        prefs.putFloat("shadowSoftness", shadowSoftness);
        prefs.putInteger("shadowQuality", shadowQuality);
        prefs.putInteger("blurPasses", blurPasses);
        prefs.flush();
    }

    public void resetShadowDefaults() {
        softShadows = DEFAULT_SOFT_SHADOWS;
        shadowDarkness = DEFAULT_SHADOW_DARKNESS;
        shadowSoftness = DEFAULT_SHADOW_SOFTNESS;
        shadowQuality = DEFAULT_SHADOW_QUALITY;
        blurPasses = DEFAULT_BLUR_PASSES;
    }

    /** Switches between fullscreen (at the monitor's resolution) and a window. */
    public void applyDisplayMode() {
        if (fullscreen) {
            if (!Gdx.graphics.isFullscreen()) Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        } else if (Gdx.graphics.isFullscreen()) {
            Graphics.DisplayMode display = Gdx.graphics.getDisplayMode();
            Gdx.graphics.setWindowedMode(Math.min(WINDOW_WIDTH, display.width), Math.min(WINDOW_HEIGHT, display.height));
        }
    }
}
