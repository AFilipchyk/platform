package lsfusion.gwt.form.shared.view.classes;

import lsfusion.gwt.form.shared.view.GFont;
import lsfusion.gwt.form.shared.view.GPropertyDraw;
import lsfusion.gwt.form.shared.view.grid.EditManager;
import lsfusion.gwt.form.shared.view.grid.editor.ColorGridCellEditor;
import lsfusion.gwt.form.shared.view.grid.editor.GridCellEditor;
import lsfusion.gwt.form.shared.view.grid.renderer.ColorGridCellRenderer;
import lsfusion.gwt.form.shared.view.grid.renderer.GridCellRenderer;

import java.text.ParseException;

public class GColorType extends GDataType {
    public static GColorType instance = new GColorType();

    @Override
    public GridCellEditor createGridCellEditor(EditManager editManager, GPropertyDraw editProperty) {
        return new ColorGridCellEditor(editManager, editProperty);
    }

    @Override
    public GridCellRenderer createGridCellRenderer(GPropertyDraw property) {
        return new ColorGridCellRenderer();
    }

    @Override
    public String getMask(String pattern) {
        return "";
    }

    @Override
    public int getPixelWidth(int minimumCharWidth, GFont font, String pattern) {
        return 40;
    }

    @Override
    public Object parseString(String s, String pattern) throws ParseException {
        throw new ParseException("Color class doesn't support conversion from string", 0);
    }

    @Override
    public String toString() {
        return "Цвет";
    }
}
