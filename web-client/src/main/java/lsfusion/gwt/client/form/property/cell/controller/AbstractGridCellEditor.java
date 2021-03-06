package lsfusion.gwt.client.form.property.cell.controller;

import com.google.gwt.dom.client.DivElement;
import lsfusion.gwt.client.base.view.grid.DataGrid;
import lsfusion.gwt.client.base.view.grid.cell.Cell;

public abstract class AbstractGridCellEditor implements GridCellEditor {
    @Override
    public abstract void renderDom(Cell.Context context, DataGrid table, DivElement cellParent, Object value);

    @Override
    public boolean replaceCellRenderer() {
        return true;
    }
}
