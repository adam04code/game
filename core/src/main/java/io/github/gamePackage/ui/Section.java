package io.github.gamePackage.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;

/** A panel section with a clickable header that folds its contents away. Add rows to {@link #body}. */
public final class Section extends Table {
    public final Table body = new Table();
    private final TextButton header;
    private final Cell<Table> bodyCell;
    private final String title;
    private boolean open;

    public Section(Skin skin, String title, boolean open) {
        this.title = title;
        header = new TextButton("", skin, "section");
        header.getLabel().setAlignment(Align.left);
        header.getLabelCell().growX();
        header.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setOpen(!Section.this.open);
            }
        });
        body.defaults().growX().padBottom(4);
        body.pad(6, 2, 2, 2);
        add(header).growX().row();
        bodyCell = add(body).growX();
        setOpen(open);
    }

    public void setOpen(boolean open) {
        this.open = open;
        header.setText((open ? "-  " : "+  ") + title);
        bodyCell.setActor(open ? body : null);
        invalidateHierarchy();
    }
}
