package platform.gwt.form2.shared.view.panel;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.*;
import platform.gwt.form2.client.form.ui.GFormController;
import platform.gwt.form2.client.form.ui.GSinglePropertyTable;
import platform.gwt.form2.shared.view.GPropertyDraw;

public class DataPanelRenderer implements PanelRenderer {

    protected final Label label;
    protected final GSinglePropertyTable valueTable;
    protected final HorizontalPanel panel;

    public DataPanelRenderer(GFormController form, GPropertyDraw property) {
        label = new Label(property.getCaptionOrEmpty() + ": ");

        valueTable = new GSinglePropertyTable(form, property);

        valueTable.setTableWidth(250, Style.Unit.PX);
        valueTable.setWidth("100%");
        valueTable.setHeight("100%");

        ResizeLayoutPanel gridPanel = new ResizeLayoutPanel();
        gridPanel.setPixelSize(250, 27);
        gridPanel.addStyleName("dataPanelRendererGridPanel");
        gridPanel.add(valueTable);

        panel = new HorizontalPanel();
        panel.add(label);
        panel.add(gridPanel);
        panel.setCellVerticalAlignment(label, HasVerticalAlignment.ALIGN_MIDDLE);
    }

    @Override
    public Widget getComponent() {
        return panel;
    }

    @Override
    public void setValue(Object value) {
        valueTable.setValue(value);
    }

    @Override
    public void setCaption(String caption) {
        label.setText(caption);
    }

    @Override
    public void updateCellBackgroundValue(Object value) {
        valueTable.setBackgroundColor((String) value);
    }

    @Override
    public void updateCellForegroundValue(Object value) {
        valueTable.setForegroundColor((String) value);
    }
}
