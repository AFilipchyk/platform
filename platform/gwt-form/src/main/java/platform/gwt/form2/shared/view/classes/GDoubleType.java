package platform.gwt.form2.shared.view.classes;

import platform.gwt.form2.shared.view.GPropertyDraw;
import platform.gwt.form2.shared.view.grid.EditManager;
import platform.gwt.form2.shared.view.grid.editor.DoubleGridEditor;
import platform.gwt.form2.shared.view.grid.editor.GridCellEditor;

public class GDoubleType extends GIntegralType {
    public static GDoubleType instance = new GDoubleType();

    @Override
    public Object parseString(String strValue) {
        return Double.parseDouble(strValue);
    }

//    @Override
//    public PanelRenderer createPanelRenderer(GFormController form, GPropertyDraw property) {
//        return new DoublePanelRenderer(property);
//    }

    @Override
    public GridCellEditor createGridCellEditor(EditManager editManager, GPropertyDraw editProperty, Object oldValue) {
        return new DoubleGridEditor(editManager, oldValue);
    }
}
