package lsfusion.gwt.client.form.object.table.grid.user.toolbar.view;

import lsfusion.gwt.client.base.view.ImageButton;

public abstract class GToolbarButton extends ImageButton {
    public GToolbarButton(String imagePath) {
        this(imagePath, "");
    }

    public GToolbarButton(String caption, String imagePath, String tooltipText) {
        super(caption, imagePath);

        addStyleName("toolbarButton");
        setTitle(tooltipText);
        addListener();
        setFocusable(false);

    }
    public GToolbarButton(String imagePath, String tooltipText) {
        this(null, imagePath, tooltipText);
    }

    public abstract void addListener();

    public void showBackground(boolean showBackground) {
        getElement().getStyle().setBackgroundColor(showBackground ? "var(--selection-color)" : "");
        getElement().getStyle().setProperty("border", showBackground ? "1px solid var(--border-color)" : "");
    }
}
