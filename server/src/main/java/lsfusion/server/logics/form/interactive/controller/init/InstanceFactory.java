package lsfusion.server.logics.form.interactive.controller.init;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.col.interfaces.mutable.add.MAddExclMap;
import lsfusion.server.logics.form.interactive.instance.filter.RegularFilterGroupInstance;
import lsfusion.server.logics.form.interactive.instance.filter.RegularFilterInstance;
import lsfusion.server.logics.form.interactive.instance.object.GroupObjectInstance;
import lsfusion.server.logics.form.interactive.instance.object.ObjectInstance;
import lsfusion.server.logics.form.interactive.instance.object.TreeGroupInstance;
import lsfusion.server.logics.form.interactive.instance.property.ActionObjectInstance;
import lsfusion.server.logics.form.interactive.instance.property.ActionOrPropertyObjectInstance;
import lsfusion.server.logics.form.interactive.instance.property.PropertyDrawInstance;
import lsfusion.server.logics.form.interactive.instance.property.PropertyObjectInstance;
import lsfusion.server.logics.form.struct.action.ActionObjectEntity;
import lsfusion.server.logics.form.struct.filter.RegularFilterEntity;
import lsfusion.server.logics.form.struct.filter.RegularFilterGroupEntity;
import lsfusion.server.logics.form.struct.object.GroupObjectEntity;
import lsfusion.server.logics.form.struct.object.ObjectEntity;
import lsfusion.server.logics.form.struct.object.TreeGroupEntity;
import lsfusion.server.logics.form.struct.property.PropertyDrawEntity;
import lsfusion.server.logics.form.struct.property.PropertyDrawExtraType;
import lsfusion.server.logics.form.struct.property.PropertyObjectEntity;
import lsfusion.server.logics.form.struct.property.oraction.ActionOrPropertyObjectEntity;
import lsfusion.server.logics.property.implement.PropertyRevImplement;
import lsfusion.server.logics.property.oraction.PropertyInterface;
import org.apache.poi.ss.formula.functions.T;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static lsfusion.server.logics.form.struct.property.PropertyDrawExtraType.*;

public class InstanceFactory {

    public InstanceFactory() {
    }

    private final MAddExclMap<ObjectEntity, ObjectInstance> objectInstances = MapFact.mSmallStrongMap();
    private final MAddExclMap<GroupObjectEntity, GroupObjectInstance> groupInstances = MapFact.mSmallStrongMap();
    private final MAddExclMap<TreeGroupEntity, TreeGroupInstance> treeInstances = MapFact.mSmallStrongMap();
    private final MAddExclMap<ActionOrPropertyObjectEntity, ActionOrPropertyObjectInstance> propertyObjectInstances = MapFact.mSmallStrongMap();
    private final MAddExclMap<PropertyDrawEntity, PropertyDrawInstance> propertyDrawInstances = MapFact.mSmallStrongMap();


    public ObjectInstance getInstance(ObjectEntity entity) {
        if (!objectInstances.containsKey(entity)) {
            objectInstances.exclAdd(entity, entity.baseClass.newInstance(entity));
        }
        return objectInstances.get(entity);
    }

    public GroupObjectInstance getInstance(GroupObjectEntity entity) {

        if (entity == null) {
            return null;
        }

        if (!groupInstances.containsKey(entity)) {

            // последействие есть, но "статичное"
            ImOrderSet<ObjectInstance> objects = entity.getOrderObjects().mapOrderSetValues(this::getInstance);

            ImMap<ObjectInstance, PropertyObjectInstance> parentInstances = null;
            if(entity.isParent !=null) {
                parentInstances = entity.isParent.mapKeyValues(entity1 -> getInstance(entity1), entity2 -> getInstance(entity2));
            }

            groupInstances.exclAdd(entity, new GroupObjectInstance(entity, objects, entity.propertyBackground != null ? getInstance(entity.propertyBackground) : null,
                    entity.propertyForeground != null ? getInstance(entity.propertyForeground) : null, parentInstances,
                    getInstance(entity.getProperties())));
        }

        return groupInstances.get(entity);
    }

    public TreeGroupInstance getInstance(TreeGroupEntity entity) {

        if (entity == null) {
            return null;
        }

        if (!treeInstances.containsKey(entity)) {

            // тут как бы с последействием, но "статичным"
            ImOrderSet<GroupObjectInstance> groups = entity.getGroups().mapOrderSetValues(this::getInstance);
            treeInstances.exclAdd(entity, new TreeGroupInstance(entity, groups));
        }

        return treeInstances.get(entity);
    }

    private <P extends PropertyInterface> ImMap<P, ObjectInstance> getInstanceMap(ActionOrPropertyObjectEntity<P, ?> entity) {
        return entity.mapping.mapValues(value -> value.getInstance(InstanceFactory.this));
    }

    public <P extends PropertyInterface> PropertyObjectInstance<P> getInstance(PropertyObjectEntity<P> entity) {

        if (!propertyObjectInstances.containsKey(entity))
            propertyObjectInstances.exclAdd(entity, new PropertyObjectInstance<>(entity.property, getInstanceMap(entity)));

        return (PropertyObjectInstance<P>) propertyObjectInstances.get(entity);
    }

    private <P extends PropertyInterface> ImRevMap<P, ObjectInstance> getInstanceMap(PropertyRevImplement<P, ObjectEntity> entity) {
        return entity.mapping.mapRevValues((Function<ObjectEntity, ObjectInstance>) InstanceFactory.this::getInstance);
    }

    public <T, P extends PropertyInterface> ImMap<T, PropertyRevImplement<P, ObjectInstance>> getInstance(ImMap<T, PropertyRevImplement<P, ObjectEntity>> entities) {
        return entities.mapValues(entity -> new PropertyRevImplement<>(entity.property, getInstanceMap(entity)));
    }

        // временно
    public <P extends PropertyInterface> ActionOrPropertyObjectInstance<P, ?> getInstance(ActionOrPropertyObjectEntity<P, ?> entity) {
        if(entity instanceof PropertyObjectEntity)
            return getInstance((PropertyObjectEntity<P>)entity);
        else
            return getInstance((ActionObjectEntity<P>)entity);
    }

    public <P extends PropertyInterface> ActionObjectInstance<P> getInstance(ActionObjectEntity<P> entity) {

        if (!propertyObjectInstances.containsKey(entity))
            propertyObjectInstances.exclAdd(entity, new ActionObjectInstance<>(entity.property, getInstanceMap(entity)));

        return (ActionObjectInstance<P>) propertyObjectInstances.get(entity);
    }

    public PropertyDrawInstance getInstance(PropertyDrawEntity<? extends PropertyInterface> entity) {

        if (!propertyDrawInstances.containsKey(entity)) {
            ImOrderSet<GroupObjectInstance> columnGroupObjects = entity.getColumnGroupObjects().mapOrderSetValues(this::getInstance);

            Map<PropertyDrawExtraType, PropertyObjectInstance<?>> propertyExtras = getPropertyExtras(entity);
            propertyDrawInstances.exclAdd(entity, new PropertyDrawInstance<>(
                    entity,
                    getInstance(entity.getValueActionOrProperty()),
                    getInstance(entity.toDraw),
                    columnGroupObjects,
                    propertyExtras,
                    entity.lastAggrColumns.mapListValues((Function<PropertyObjectEntity, PropertyObjectInstance<?>>) this::getInstance)
            ));
        }

        return propertyDrawInstances.get(entity);
    }

    private Map<PropertyDrawExtraType, PropertyObjectInstance<?>> getPropertyExtras(PropertyDrawEntity<? extends PropertyInterface> entity) {
        Map<PropertyDrawExtraType, PropertyObjectInstance<?>> extras = new HashMap<>();
        for (PropertyDrawExtraType type : PropertyDrawExtraType.values()) {
            extras.put(type, entity.hasPropertyExtra(type) ? getInstance(entity.getPropertyExtra(type)) : null);
        }
        return extras;
    }
    
    public RegularFilterGroupInstance getInstance(RegularFilterGroupEntity entity) {

        RegularFilterGroupInstance group = new RegularFilterGroupInstance(entity);

        for (RegularFilterEntity filter : entity.getFiltersList()) {
            group.addFilter(getInstance(filter));
        }

        return group;
    }

    public RegularFilterInstance getInstance(RegularFilterEntity entity) {
        return new RegularFilterInstance(entity, entity.filter.getInstance(this));
    }
}
