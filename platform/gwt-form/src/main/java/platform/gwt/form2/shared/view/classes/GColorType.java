package platform.gwt.form2.shared.view.classes;

import platform.gwt.form2.shared.view.GPropertyDraw;
import platform.gwt.form2.shared.view.grid.EditManager;
import platform.gwt.form2.shared.view.grid.editor.ColorGridEditor;
import platform.gwt.form2.shared.view.grid.editor.GridCellEditor;
import platform.gwt.form2.shared.view.grid.renderer.ColorGridRenderer;
import platform.gwt.form2.shared.view.grid.renderer.GridCellRenderer;

public class GColorType extends GDataType {
    public static GColorType instance = new GColorType();

    @Override
    public GridCellEditor createGridCellEditor(EditManager editManager, GPropertyDraw editProperty, Object oldValue) {
        return new ColorGridEditor(editManager, oldValue);
    }

    @Override
    public GridCellRenderer createGridCellRenderer() {
        return new ColorGridRenderer();
    }
}
