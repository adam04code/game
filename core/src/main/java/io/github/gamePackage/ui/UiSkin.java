package io.github.gamePackage.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/** A flat UI skin built in code, so the UI needs no skin files. */
public final class UiSkin {
    private UiSkin() {
    }

    public static Skin create() {
        Skin skin = new Skin();
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        skin.add("white", new Texture(pixmap));
        pixmap.dispose();

        BitmapFont font = new BitmapFont();
        skin.add("default", font);
        BitmapFont titleFont = new BitmapFont();
        titleFont.getData().setScale(3f);
        skin.add("title", titleFont);

        skin.add("panel", drawable(skin, 0.09f, 0.10f, 0.13f, 0.94f, 0, 0), Drawable.class);

        TextButton.TextButtonStyle button = new TextButton.TextButtonStyle();
        button.up = drawable(skin, 0.22f, 0.24f, 0.30f, 1f, 10, 6);
        button.over = drawable(skin, 0.30f, 0.33f, 0.41f, 1f, 10, 6);
        button.down = drawable(skin, 0.16f, 0.36f, 0.56f, 1f, 10, 6);
        button.font = font;
        button.fontColor = Color.WHITE;
        button.disabledFontColor = Color.GRAY;
        skin.add("default", button);

        // scene2d buttons flip their checked state on every click; only this style shows it.
        TextButton.TextButtonStyle toggle = new TextButton.TextButtonStyle(button);
        toggle.checked = drawable(skin, 0.20f, 0.45f, 0.70f, 1f, 10, 6);
        toggle.checkedOver = drawable(skin, 0.26f, 0.52f, 0.78f, 1f, 10, 6);
        skin.add("toggle", toggle);

        TextButton.TextButtonStyle menuButton = new TextButton.TextButtonStyle(button);
        menuButton.up = drawable(skin, 0.12f, 0.13f, 0.17f, 0.85f, 24, 12);
        menuButton.over = drawable(skin, 0.24f, 0.30f, 0.40f, 0.95f, 24, 12);
        menuButton.down = drawable(skin, 0.16f, 0.36f, 0.56f, 1f, 24, 12);
        skin.add("menu", menuButton);

        skin.add("default", new Label.LabelStyle(font, Color.WHITE));
        skin.add("dim", new Label.LabelStyle(font, new Color(0.7f, 0.72f, 0.78f, 1f)));
        skin.add("title", new Label.LabelStyle(titleFont, Color.WHITE));

        List.ListStyle list = new List.ListStyle();
        list.font = font;
        list.fontColorSelected = Color.WHITE;
        list.fontColorUnselected = new Color(0.8f, 0.82f, 0.86f, 1f);
        list.selection = drawable(skin, 0.20f, 0.45f, 0.70f, 1f, 6, 3);
        list.over = drawable(skin, 0.18f, 0.20f, 0.26f, 1f, 6, 3);
        skin.add("default", list);

        ScrollPane.ScrollPaneStyle scroll = new ScrollPane.ScrollPaneStyle();
        scroll.vScroll = drawable(skin, 0.13f, 0.14f, 0.18f, 1f, 0, 0);
        scroll.vScrollKnob = drawable(skin, 0.45f, 0.47f, 0.53f, 1f, 0, 0);
        ((BaseDrawable) scroll.vScrollKnob).setMinWidth(8);
        ((BaseDrawable) scroll.vScroll).setMinWidth(8);
        skin.add("default", scroll);

        Slider.SliderStyle slider = new Slider.SliderStyle();
        slider.background = drawable(skin, 0.20f, 0.22f, 0.27f, 1f, 0, 0);
        ((BaseDrawable) slider.background).setMinHeight(6);
        slider.knob = drawable(skin, 0.80f, 0.82f, 0.88f, 1f, 0, 0);
        ((BaseDrawable) slider.knob).setMinWidth(10);
        ((BaseDrawable) slider.knob).setMinHeight(18);
        skin.add("default-horizontal", slider);
        return skin;
    }

    private static Drawable drawable(Skin skin, float r, float g, float b, float a, float padX, float padY) {
        Drawable drawable = skin.newDrawable("white", new Color(r, g, b, a));
        BaseDrawable base = (BaseDrawable) drawable;
        base.setLeftWidth(padX);
        base.setRightWidth(padX);
        base.setTopHeight(padY);
        base.setBottomHeight(padY);
        return drawable;
    }
}
