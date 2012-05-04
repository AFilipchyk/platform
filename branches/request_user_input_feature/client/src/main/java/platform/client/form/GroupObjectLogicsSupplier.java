package platform.client.form;

import platform.client.form.cell.PropertyController;
import platform.client.logics.ClientGroupObject;
import platform.client.logics.ClientGroupObjectValue;
import platform.client.logics.ClientPropertyDraw;
import platform.interop.Order;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface GroupObjectLogicsSupplier extends LogicsSupplier {

    ClientGroupObject getGroupObject();
    List<ClientPropertyDraw> getGroupObjectProperties();

    // пока зафигачим в этот интерфейс, хотя может быть в дальнейшем выделим в отдельный
    // данный интерфейс отвечает за получение текущих выбранных значений
    ClientPropertyDraw getSelectedProperty();
    Object getSelectedValue(ClientPropertyDraw property, ClientGroupObjectValue columnKey);
    
    ClientFormController getForm();

    void changeOrder(ClientPropertyDraw property, Order modiType) throws IOException;

    ClientGroupObject getSelectedGroupObject();

    void updateToolbar();

    void addPropertyToToolbar(PropertyController property);

    void removePropertyFromToolbar(PropertyController property);

    void addPropertyToShortcut(PropertyController property);

    void removePropertyFromShortcut(PropertyController property);

    void showShortcut(Component component, Point point, ClientPropertyDraw currentProperty);

    void updateDrawPropertyCaptions(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> captions);

    void updateRowBackgroundValues(Map<ClientGroupObjectValue, Object> rowBackground);

    void updateRowForegroundValues(Map<ClientGroupObjectValue, Object> rowForeground);

    void updateDrawPropertyValues(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> values);

    void updateCellBackgroundValues(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> cellBackgroundValues);

    void updateCellForegroundValues(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> cellForegroundValues);
}
