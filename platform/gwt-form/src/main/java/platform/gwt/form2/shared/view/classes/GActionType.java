package platform.gwt.form2.shared.view.classes;

import platform.gwt.form2.client.form.ui.GFormController;
import platform.gwt.form2.shared.view.GPropertyDraw;
import platform.gwt.form2.shared.view.panel.ActionPanelRenderer;
import platform.gwt.form2.shared.view.panel.PanelRenderer;
import platform.gwt.form2.shared.view.grid.renderer.GridCellRenderer;
import platform.gwt.form2.shared.view.grid.renderer.ActionGridRenderer;

public class GActionType extends GDataType {
    public static GActionType instance = new GActionType();

    @Override
    public PanelRenderer createPanelRenderer(GFormController form, GPropertyDraw property) {
        return new ActionPanelRenderer(form, property);
    }

    @Override
    public GridCellRenderer createGridCellRenderer(GPropertyDraw property) {
        return new ActionGridRenderer(property);
    }
}
