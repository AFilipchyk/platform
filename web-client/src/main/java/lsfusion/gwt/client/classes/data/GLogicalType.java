package lsfusion.gwt.client.classes.data;

import lsfusion.gwt.client.ClientMessages;
import lsfusion.gwt.client.base.GwtSharedUtils;
import lsfusion.gwt.client.form.design.GFont;
import lsfusion.gwt.client.form.design.GWidthStringProcessor;
import lsfusion.gwt.client.form.property.GPropertyDraw;
import lsfusion.gwt.client.form.property.cell.classes.controller.LogicalGridCellEditor;
import lsfusion.gwt.client.form.property.cell.classes.view.LogicalGridCellRenderer;
import lsfusion.gwt.client.form.property.cell.controller.EditManager;
import lsfusion.gwt.client.form.property.cell.controller.GridCellEditor;
import lsfusion.gwt.client.form.property.cell.view.GridCellRenderer;

import java.text.ParseException;

public class GLogicalType extends GDataType {
    public static GLogicalType instance = new GLogicalType();

    @Override
    public GridCellRenderer createGridCellRenderer(GPropertyDraw property) {
        return new LogicalGridCellRenderer(property);
}

    @Override
    public GridCellEditor createGridCellEditor(EditManager editManager, GPropertyDraw editProperty) {
        return new LogicalGridCellEditor(editManager);
    }

    @Override
    public int getDefaultWidth(GFont font, GPropertyDraw propertyDraw, GWidthStringProcessor widthStringProcessor) {
        return 30;
    }

    @Override
    public Object parseString(String s, String pattern) throws ParseException {
        try {
            return GwtSharedUtils.nullBoolean(Boolean.parseBoolean(s));
        } catch (NumberFormatException nfe) {
            throw new ParseException("string " + s + "can not be converted to logical", 0);
        }
    }

    @Override
    public String toString() {
        return ClientMessages.Instance.get().typeLogicalCaption();
    }
}
