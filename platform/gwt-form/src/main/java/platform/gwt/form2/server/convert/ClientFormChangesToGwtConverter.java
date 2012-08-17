package platform.gwt.form2.server.convert;

import platform.client.logics.*;
import platform.gwt.utils.GwtSharedUtils;
import platform.gwt.form2.shared.view.changes.dto.GFormChangesDTO;
import platform.gwt.form2.shared.view.changes.dto.GGroupObjectValueDTO;
import platform.gwt.form2.shared.view.changes.dto.GPropertyReaderDTO;
import platform.interop.ClassViewType;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("UnusedDeclaration")
public class ClientFormChangesToGwtConverter extends ObjectConverter {
    private static final class InstanceHolder {
        private static final ClientFormChangesToGwtConverter instance = new ClientFormChangesToGwtConverter();
    }

    public static ClientFormChangesToGwtConverter getInstance() {
        return InstanceHolder.instance;
    }

    private ClientFormChangesToGwtConverter() {
    }

    @Converter(from = ClientFormChanges.class)
    public GFormChangesDTO convertFormChanges(ClientFormChanges changes) {
        GFormChangesDTO changesDTO = new GFormChangesDTO();

        for (Map.Entry<ClientGroupObject, ClassViewType> entry : changes.classViews.entrySet()) {
            changesDTO.classViews.put(entry.getKey().getID(), entry.getValue().name());
        }

        for (Map.Entry<ClientGroupObject, ClientGroupObjectValue> e : changes.objects.entrySet()) {
            GGroupObjectValueDTO groupObjectValueDTO = convertOrCast(e.getValue());
            changesDTO.objects.put(e.getKey().ID, groupObjectValueDTO);
        }

        for (Map.Entry<ClientGroupObject, List<ClientGroupObjectValue>> entry : changes.gridObjects.entrySet()) {
            ArrayList<GGroupObjectValueDTO> keys = new ArrayList<GGroupObjectValueDTO>();

            for (ClientGroupObjectValue keyValue : entry.getValue()) {
                GGroupObjectValueDTO groupObjectValueDTO = convertOrCast(keyValue);
                keys.add(groupObjectValueDTO);
            }

            changesDTO.gridObjects.put(entry.getKey().ID, keys);
        }

        for (Map.Entry<ClientGroupObject, List<ClientGroupObjectValue>> entry : changes.parentObjects.entrySet()) {
            ArrayList<GGroupObjectValueDTO> keys = new ArrayList<GGroupObjectValueDTO>();

            for (ClientGroupObjectValue keyValue : entry.getValue()) {
                GGroupObjectValueDTO groupObjectValueDTO = convertOrCast(keyValue);
                keys.add(groupObjectValueDTO);
            }

            changesDTO.parentObjects.put(entry.getKey().ID, keys);
        }

        for (Map.Entry<ClientPropertyReader, Map<ClientGroupObjectValue, Object>> entry : changes.properties.entrySet()) {
            HashMap<GGroupObjectValueDTO, Object> propValues = new HashMap<GGroupObjectValueDTO, Object>();
            for (Map.Entry<ClientGroupObjectValue, Object> clientValues : entry.getValue().entrySet()) {
                GGroupObjectValueDTO groupObjectValueDTO = convertOrCast(clientValues.getKey());

                propValues.put(groupObjectValueDTO, convertValue(clientValues.getValue()));
            }
            ClientPropertyReader reader = entry.getKey();
            changesDTO.properties.put(new GPropertyReaderDTO(reader.getID(), reader.getGroupObject() != null ? reader.getGroupObject().ID : -1, reader.getType()), propValues);
        }

        for (ClientPropertyReader dropProperty : changes.dropProperties) {
            if (dropProperty instanceof ClientPropertyDraw) {
                changesDTO.dropProperties.add(((ClientPropertyDraw) dropProperty).ID);
            }
        }

        for (ClientPropertyReader panelProperty : changes.panelProperties) {
            changesDTO.panelProperties.add(
                    new GPropertyReaderDTO(panelProperty.getID(), panelProperty.getGroupObject() != null ? panelProperty.getGroupObject().ID : -1, panelProperty.getType())
            );
        }

        return changesDTO;
    }

    private Object convertValue(Object value) {
        if (value instanceof Color) {
            return "#" + Integer.toHexString(((Color) value).getRGB()).substring(2, 8);
        } else if (value instanceof String) {
            return GwtSharedUtils.rtrim((String) value);
        } else {
            return value;
        }
    }


    @Converter(from = ClientGroupObjectValue.class)
    public GGroupObjectValueDTO convertIntegerClass(ClientGroupObjectValue groupObjectValue) {
        GGroupObjectValueDTO groupObjectValueDTO = new GGroupObjectValueDTO();
        for (Map.Entry<ClientObject, Object> keyPart : groupObjectValue.entrySet()) {
            groupObjectValueDTO.put(keyPart.getKey().ID, keyPart.getValue());
        }
        return groupObjectValueDTO;
    }
}
