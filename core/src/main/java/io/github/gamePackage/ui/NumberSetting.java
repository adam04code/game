package io.github.gamePackage.ui;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Align;

import java.util.Locale;

/**
 * One compact row: a label, a slider, and a box where the exact number can be typed (applied on Enter or when the
 * box loses focus). Typed values are clamped to the slider's range.
 */
public final class NumberSetting extends Table {
    public interface Listener {
        void changed(float value);
    }

    private static final float LABEL_WIDTH = 74f;
    private static final float FIELD_WIDTH = 54f;

    private final Slider slider;
    private final TextField field;
    private final Label label;
    private final String format;
    private final Listener listener;
    private boolean syncing;

    /** {@code format} is a String.format pattern for the box, e.g. "%.2f". */
    public NumberSetting(Skin skin, String name, float min, float max, float step, String format, Listener listener) {
        this.format = format;
        this.listener = listener;
        label = new Label(name, skin, "dim");
        label.setEllipsis(true);
        slider = new Slider(min, max, step, false, skin);
        field = new TextField("", skin);
        field.setAlignment(Align.right);
        field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '.' || c == '-');

        add(label).width(LABEL_WIDTH).left();
        add(slider).growX().padRight(6);
        add(field).width(FIELD_WIDTH);

        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (syncing) return;
                showText(slider.getValue());
                listener.changed(slider.getValue());
            }
        });
        field.setTextFieldListener((textField, c) -> {
            if (c == '\r' || c == '\n') {
                commit();
                Stage stage = getStage();
                if (stage != null) stage.setKeyboardFocus(null);
            }
        });
        field.addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (!focused) commit();
            }
        });
    }

    /** Shows a value without notifying the listener. */
    public void setValue(float value) {
        syncing = true;
        slider.setValue(value);
        syncing = false;
        showText(value);
    }

    public float getValue() {
        return slider.getValue();
    }

    public void setDisabled(boolean disabled) {
        slider.setDisabled(disabled);
        field.setDisabled(disabled);
        label.setColor(1f, 1f, 1f, disabled ? 0.5f : 1f);
    }

    private void commit() {
        float value;
        try {
            value = Float.parseFloat(field.getText().trim());
        } catch (NumberFormatException e) {
            showText(slider.getValue());
            return;
        }
        value = MathUtils.clamp(value, slider.getMinValue(), slider.getMaxValue());
        float before = slider.getValue();
        setValue(value);
        if (value != before) listener.changed(value);
    }

    private void showText(float value) {
        field.setText(String.format(Locale.ROOT, format, value));
    }
}
