package lsfusion.server.form.instance;

import com.google.common.base.Throwables;
import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.interop.form.property.ClassViewType;
import lsfusion.server.form.view.ComponentView;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static lsfusion.base.BaseUtils.serializeObject;

// появляется по сути для отделения клиента, именно он возвращается назад клиенту
public class FormChanges {

    private final ImMap<GroupObjectInstance, ClassViewType> classViews;

    // current (panel) objects
    private final ImMap<GroupObjectInstance, ImMap<ObjectInstance, ? extends ObjectValue>> objects;

    // list (grid) objects
    private final ImMap<GroupObjectInstance, ImOrderSet<ImMap<ObjectInstance, DataObject>>> gridObjects;

    // tree objects
    private final ImMap<GroupObjectInstance, ImList<ImMap<ObjectInstance, DataObject>>> parentObjects;
    // tree object has + 
    private final ImMap<GroupObjectInstance, ImMap<ImMap<ObjectInstance, DataObject>, Boolean>> expandables;

    // properties
    private final ImMap<PropertyReaderInstance, ImMap<ImMap<ObjectInstance, DataObject>, ObjectValue>> properties;
    // property in list / current objects
    private final ImSet<PropertyDrawInstance> panelProperties;
    // property has to be hidden
    private final ImSet<PropertyDrawInstance> dropProperties;

    private final ImList<ComponentView> activateTabs;
    private final ImList<PropertyDrawInstance> activateProps;

    public static FormChanges EMPTY = new MFormChanges().immutable();

    public FormChanges(ImMap<GroupObjectInstance, ClassViewType> classViews, ImMap<GroupObjectInstance, ImMap<ObjectInstance, ? extends ObjectValue>> objects,
                       ImMap<GroupObjectInstance, ImOrderSet<ImMap<ObjectInstance, DataObject>>> gridObjects,
                       ImMap<GroupObjectInstance, ImList<ImMap<ObjectInstance, DataObject>>> parentObjects,
                       ImMap<GroupObjectInstance, ImMap<ImMap<ObjectInstance, DataObject>, Boolean>> expandables,
                       ImMap<PropertyReaderInstance, ImMap<ImMap<ObjectInstance, DataObject>, ObjectValue>> properties,
                       ImSet<PropertyDrawInstance> panelProperties, ImSet<PropertyDrawInstance> dropProperties, 
                       ImList<ComponentView> activateTabs, ImList<PropertyDrawInstance> activateProps) {
        this.classViews = classViews;
        this.objects = objects;
        this.gridObjects = gridObjects;
        this.parentObjects = parentObjects;
        this.expandables = expandables;
        this.properties = properties;
        this.panelProperties = panelProperties;
        this.dropProperties = dropProperties;
        this.activateTabs = activateTabs;
        this.activateProps = activateProps;
    }

    void out(FormInstance bv) {
        System.out.println(" ------- GROUPOBJECTS ---------------");
        for (GroupObjectInstance group : bv.getGroups()) {
            ImList<ImMap<ObjectInstance, DataObject>> groupGridObjects = gridObjects.get(group);
            if (groupGridObjects != null) {
                System.out.println(group.getID() + " - GRID Changes");
                for (ImMap<ObjectInstance, DataObject> value : groupGridObjects)
                    System.out.println(value);
            }

            ImMap<ObjectInstance, ? extends ObjectValue> value = objects.get(group);
            if (value != null)
                System.out.println(group.getID() + " - Object Changes " + value);
        }

        System.out.println(" ------- PROPERTIES ---------------");
        System.out.println(" ------- Group ---------------");
        for (PropertyReaderInstance property : properties.keyIt()) {
            ImMap<ImMap<ObjectInstance, DataObject>, ObjectValue> propertyValues = properties.get(property);
            System.out.println(property + " ---- property");
            for (ImMap<ObjectInstance, DataObject> gov : propertyValues.keyIt())
                System.out.println(gov + " - " + propertyValues.get(gov));
        }

        System.out.println(" ------- PANEL ---------------");
        for (PropertyDrawInstance property : panelProperties)
            System.out.println(property);

        System.out.println(" ------- Drop ---------------");
        for (PropertyDrawInstance property : dropProperties)
            System.out.println(property);

        System.out.println(" ------- Activate tab ---------------");
        for (ComponentView tab : activateTabs)
            System.out.println(tab);

        System.out.println(" ------- Activate property ---------------");
        for (PropertyDrawInstance prop : activateProps)
            System.out.println(prop);

        System.out.println(" ------- CLASSVIEWS ---------------");
        for (int i=0,size=classViews.size();i<size;i++) {
            System.out.println(classViews.getKey(i) + " - " + classViews.getValue(i));
        }
    }

    public void serialize(DataOutputStream outStream) throws IOException {

        outStream.writeInt(classViews.size());
        for (int i=0,size=classViews.size();i<size;i++) {
            outStream.writeInt(classViews.getKey(i).getID());
            outStream.writeInt(classViews.getValue(i).ordinal());
        }

        outStream.writeInt(objects.size());
        for (int i=0,size=objects.size();i<size;i++) {
            outStream.writeInt(objects.getKey(i).getID());
            serializeGroupObjectValue(outStream, objects.getValue(i));
        }

        serializeKeyObjectsMap(outStream, gridObjects);
        serializeKeyObjectsMap(outStream, parentObjects);

        outStream.writeInt(expandables.size());
        for (int i = 0; i < expandables.size(); ++i) {
            outStream.writeInt(expandables.getKey(i).getID());

            ImMap<ImMap<ObjectInstance, DataObject>, Boolean> groupExpandables = expandables.getValue(i);
            outStream.writeInt(groupExpandables.size());
            for (int j = 0; j < groupExpandables.size(); ++j) {
                serializeGroupObjectValue(outStream, groupExpandables.getKey(j));
                outStream.writeBoolean(groupExpandables.getValue(j));
            }
        }

        outStream.writeInt(properties.size());
        for (int i=0,size=properties.size();i<size;i++) {
            PropertyReaderInstance propertyReadInstance = properties.getKey(i);
            ImMap<ImMap<ObjectInstance, DataObject>, ObjectValue> rows = properties.getValue(i);

            // сериализация PropertyReadInterface
            outStream.writeByte(propertyReadInstance.getTypeID());
            outStream.writeInt(propertyReadInstance.getID());

            outStream.writeInt(rows.size());
            for (int j=0,sizeJ=rows.size();j<sizeJ;j++) {
                ImMap<ObjectInstance, DataObject> objectValues = rows.getKey(j);

                serializeGroupObjectValue(outStream, objectValues);

                serializeObject(outStream, rows.getValue(j).getValue());
            }
        }

        outStream.writeInt(panelProperties.size());
        for (PropertyDrawInstance propertyDraw : panelProperties) {
            outStream.writeInt(propertyDraw.getID());
        }

        outStream.writeInt(dropProperties.size());
        for (PropertyDrawInstance propertyView : dropProperties) {
            outStream.writeInt(propertyView.getID());
        }

        outStream.writeInt(activateTabs.size());
        for (ComponentView activateTab : activateTabs) {
            outStream.writeInt(activateTab.getID());
        }

        outStream.writeInt(activateProps.size());
        for (PropertyDrawInstance propertyView : activateProps) {
            outStream.writeInt(propertyView.getID());
        }
    }

    private void serializeGroupObjectValue(DataOutputStream outStream, ImMap<ObjectInstance,? extends ObjectValue> values) throws IOException {
        outStream.writeInt(values.size());
        for (int i=0,size=values.size();i<size;i++) {
            outStream.writeInt(values.getKey(i).getID());
            serializeObject(outStream, values.getValue(i).getValue());
        }
    }

    private void serializeKeyObjectsMap(DataOutputStream outStream, ImMap<GroupObjectInstance, ? extends ImList<ImMap<ObjectInstance, DataObject>>> keyObjects) throws IOException {
        outStream.writeInt(keyObjects.size());
        for (int i=0,size=keyObjects.size();i<size;i++) {

            outStream.writeInt(keyObjects.getKey(i).getID());

            ImList<ImMap<ObjectInstance, DataObject>> rows = keyObjects.getValue(i);
            outStream.writeInt(rows.size());
            for (ImMap<ObjectInstance, DataObject> groupObjectValue : rows) {
                serializeGroupObjectValue(outStream, groupObjectValue);
            }
        }
    }

    public byte[] serialize() {
        try {
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            serialize(new DataOutputStream(outStream));
            return outStream.toByteArray();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public void logChanges(FormInstance bv, Logger logger) {
        logger.trace("getFormChanges:");
        logger.trace("  GROUPOBJECTS ---------------");
        for (GroupObjectInstance group : bv.getGroups()) {
            ImOrderSet<ImMap<ObjectInstance, DataObject>> groupGridObjects = gridObjects.get(group);
            if (groupGridObjects != null) {
                logger.trace("   " + group.getID() + " - Current grid objects chaned to:");
                for (ImMap<ObjectInstance, DataObject> value : groupGridObjects)
                    logger.trace("     " + value);
            }

            ImMap<ObjectInstance, ? extends ObjectValue> value = objects.get(group);
            if (value != null) {
                logger.trace("   " + group.getID() + " - Current object changed to:  " + value);
            }
        }

        logger.trace("  PROPERTIES ---------------");
        logger.trace("   Values ---------------");
        for (PropertyReaderInstance property : properties.keyIt()) {
            ImMap<ImMap<ObjectInstance, DataObject>, ObjectValue> propertyValues = properties.get(property);
            logger.trace("    " + property + " ---- property");
            for (int i=0,size=propertyValues.size();i<size;i++)
                logger.trace("      " + propertyValues.getKey(i) + " -> " + propertyValues.getValue(i));
        }

        logger.trace("   Goes to panel ---------------");
        for (PropertyDrawInstance property : panelProperties) {
            logger.trace("     " + property);
        }

        logger.trace("   Droped ---------------");
        for (PropertyDrawInstance property : dropProperties)
            logger.trace("     " + property);

        logger.trace("   Activate tabs ---------------");
        for (ComponentView tab : activateTabs) {
            logger.trace("     " + tab);
        }

        logger.trace("   Activate props ---------------");
        for (PropertyDrawInstance property : activateProps)
            logger.trace("     " + property);

        logger.trace("  CLASSVIEWS ---------------");
        for (int i=0,size=classViews.size();i<size;i++) {
            logger.trace("     " + classViews.getKey(i) + " - " + classViews.getValue(i));
        }
    }
}
