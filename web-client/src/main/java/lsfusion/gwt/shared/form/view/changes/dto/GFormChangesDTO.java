package lsfusion.gwt.shared.form.view.changes.dto;

import lsfusion.gwt.shared.form.view.GClassViewType;
import lsfusion.gwt.shared.form.view.changes.GGroupObjectValue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class GFormChangesDTO implements Serializable {
    public int requestIndex;

    public int[] classViewsGroupIds;
    public GClassViewType[] classViews;

    public int[] objectsGroupIds;
    public GGroupObjectValue[] objects;

    public int[] gridObjectsGroupIds;
    public ArrayList<GGroupObjectValue>[] gridObjects;

    public int[] parentObjectsGroupIds;
    public ArrayList<GGroupObjectValue>[] parentObjects;

    public int[] expandablesGroupIds;
    public HashMap<GGroupObjectValue, Boolean>[] expandables;

    public GPropertyReaderDTO[] properties;
    public HashMap<GGroupObjectValue, Object>[] propertiesValues;

    public int[] panelPropertiesIds;
    public int[] dropPropertiesIds;

    public int[] activateTabsIds;
    public int[] activatePropsIds;
}
