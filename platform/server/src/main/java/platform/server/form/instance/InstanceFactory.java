package platform.server.form.instance;

import platform.server.form.entity.*;
import platform.server.form.entity.filter.*;
import platform.server.form.instance.filter.*;
import platform.server.logics.property.PropertyInterface;

import java.util.*;

public class InstanceFactory {

    public InstanceFactory(PropertyObjectInterfaceInstance computer) {
        this.computer = computer;
    }

    private final Map<ObjectEntity, ObjectInstance> objectInstances = new HashMap<ObjectEntity, ObjectInstance>();
    private final Map<GroupObjectEntity, GroupObjectInstance> groupInstances = new HashMap<GroupObjectEntity, GroupObjectInstance>();
    private final Map<TreeGroupEntity, TreeGroupInstance> treeInstances = new HashMap<TreeGroupEntity, TreeGroupInstance>();
    private final Map<PropertyObjectEntity, PropertyObjectInstance> propertyObjectInstances = new HashMap<PropertyObjectEntity, PropertyObjectInstance>();
    private final Map<PropertyDrawEntity, PropertyDrawInstance> propertyDrawInstances = new HashMap<PropertyDrawEntity, PropertyDrawInstance>();


    public ObjectInstance getInstance(ObjectEntity entity) {
        if (!objectInstances.containsKey(entity)) {
            objectInstances.put(entity, entity.baseClass.newInstance(entity));
        }
        return objectInstances.get(entity);
    }

    public GroupObjectInstance getInstance(GroupObjectEntity entity) {

        if (entity == null) {
            return null;
        }

        if (!groupInstances.containsKey(entity)) {

            Collection<ObjectInstance> objects = new ArrayList<ObjectInstance>();
            for (ObjectEntity object : entity.objects) {
                objects.add(getInstance(object));
            }

            Map<ObjectInstance, PropertyObjectInstance> parentInstances = null;
            if(entity.isParent !=null) {
                parentInstances = new HashMap<ObjectInstance, PropertyObjectInstance>();
                for(Map.Entry<ObjectEntity,PropertyObjectEntity> parentObject : entity.isParent.entrySet())
                    parentInstances.put(getInstance(parentObject.getKey()), getInstance(parentObject.getValue()));
            }

            groupInstances.put(entity, new GroupObjectInstance(entity, objects, entity.propertyHighlight != null ? getInstance(entity.propertyHighlight) : null, parentInstances));
        }

        return groupInstances.get(entity);
    }

    public TreeGroupInstance getInstance(TreeGroupEntity entity) {

        if (entity == null) {
            return null;
        }

        if (!treeInstances.containsKey(entity)) {

            List<GroupObjectInstance> groups = new ArrayList<GroupObjectInstance>();
            for (GroupObjectEntity group : entity.groups) {
                groups.add(getInstance(group));
            }

            treeInstances.put(entity, new TreeGroupInstance(entity, groups));
        }

        return treeInstances.get(entity);
    }

    public <P extends PropertyInterface> PropertyObjectInstance getInstance(PropertyObjectEntity<P> entity) {

        if (!propertyObjectInstances.containsKey(entity)) {
            Map<P, PropertyObjectInterfaceInstance> propertyMap = new HashMap<P, PropertyObjectInterfaceInstance>();
            for (Map.Entry<P, PropertyObjectInterfaceEntity> propertyImplement : entity.mapping.entrySet()) {
                propertyMap.put(propertyImplement.getKey(), propertyImplement.getValue().getInstance(this));
            }
            propertyObjectInstances.put(entity, new PropertyObjectInstance<P>(entity.property, propertyMap));
        }

        return propertyObjectInstances.get(entity);
    }

    public <T extends PropertyInterface> PropertyDrawInstance getInstance(PropertyDrawEntity<T> entity) {

        if (!propertyDrawInstances.containsKey(entity)) {
            List<GroupObjectInstance> columnGroupObjects = new ArrayList<GroupObjectInstance>();
            for (GroupObjectEntity columnGroupObject : entity.columnGroupObjects) {
                columnGroupObjects.add(getInstance(columnGroupObject));
            }

            propertyDrawInstances.put(entity, new PropertyDrawInstance<T>(entity, getInstance(entity.propertyObject), getInstance(entity.toDraw), columnGroupObjects,
                    entity.propertyCaption == null ? null : getInstance(entity.propertyCaption),
                    entity.propertyReadOnly == null ? null : getInstance(entity.propertyReadOnly),
                    entity.propertyFooter == null ? null : getInstance(entity.propertyFooter),
                    entity.propertyHighlight == null ? null : getInstance(entity.propertyHighlight)));
        }

        return propertyDrawInstances.get(entity);
    }

    public final PropertyObjectInterfaceInstance computer;

    public <P extends PropertyInterface> FilterInstance getInstance(CompareFilterEntity<P> entity) {
        return new CompareFilterInstance<P>(getInstance(entity.property), entity.compare, entity.value.getInstance(this));
    }

    public <P extends PropertyInterface> FilterInstance getInstance(IsClassFilterEntity<P> entity) {
        return new IsClassFilterInstance<P>(getInstance(entity.property), entity.isClass);
    }

    public <P extends PropertyInterface> FilterInstance getInstance(NotNullFilterEntity<P> entity) {
        return new NotNullFilterInstance<P>(getInstance(entity.property), entity.checkChange);
    }

    public FilterInstance getInstance(NotFilterEntity entity) {
        return new NotFilterInstance(entity.filter.getInstance(this));
    }

    public OrFilterInstance getInstance(OrFilterEntity entity) {
        return new OrFilterInstance(entity.op1.getInstance(this), entity.op2.getInstance(this));
    }

    public AndFilterInstance getInstance(AndFilterEntity entity) {
        return new AndFilterInstance(entity.op1.getInstance(this), entity.op2.getInstance(this));
    }

    public RegularFilterGroupInstance getInstance(RegularFilterGroupEntity entity) {

        RegularFilterGroupInstance group = new RegularFilterGroupInstance(entity);

        for (RegularFilterEntity filter : entity.filters) {
            group.addFilter(getInstance(filter));
        }

        return group;
    }

    public RegularFilterInstance getInstance(RegularFilterEntity entity) {
        return new RegularFilterInstance(entity, entity.filter.getInstance(this));
    }
}
