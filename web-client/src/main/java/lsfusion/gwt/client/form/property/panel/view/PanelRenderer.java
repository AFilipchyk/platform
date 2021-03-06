package lsfusion.gwt.client.form.property.panel.view;

import com.google.gwt.user.client.ui.Widget;

public interface PanelRenderer {
    Widget getComponent();
    void setValue(Object value);
    void setReadOnly(boolean readOnly);
    void setCaption(String caption);
    void setDefaultIcon();
    void setImage(String iconPath);

    void updateCellBackgroundValue(Object value);
    void updateCellForegroundValue(Object value);

    void focus();
}
