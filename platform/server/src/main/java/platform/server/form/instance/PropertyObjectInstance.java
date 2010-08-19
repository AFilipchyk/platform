package platform.server.form.instance;

import platform.interop.action.ClientAction;
import platform.server.classes.ConcreteClass;
import platform.server.classes.CustomClass;
import platform.server.classes.sets.AndClassSet;
import platform.server.data.expr.Expr;
import platform.server.data.type.Type;
import platform.server.logics.DataObject;
import platform.server.logics.property.Property;
import platform.server.logics.property.PropertyImplement;
import platform.server.logics.property.PropertyInterface;
import platform.server.logics.property.PropertyValueImplement;
import platform.server.session.Changes;
import platform.server.session.DataSession;
import platform.server.session.Modifier;
import platform.server.form.instance.remote.RemoteForm;

import java.sql.SQLException;
import java.util.*;

public class PropertyObjectInstance<P extends PropertyInterface> extends PropertyImplement<PropertyObjectInterfaceInstance,P> implements OrderInstance {

    // получает Grid в котором рисоваться
    public GroupObjectInstance getApplyObject() {
        GroupObjectInstance applyObject=null;
        for(ObjectInstance intObject : getObjectInstances())
            if(applyObject==null || intObject.groupTo.order >applyObject.order)
                applyObject = intObject.getApplyObject();

        return applyObject;
    }

    public Collection<ObjectInstance> getObjectInstances() {
        Collection<ObjectInstance> result = new ArrayList<ObjectInstance>();
        for(PropertyObjectInterfaceInstance object : mapping.values())
            if(object instanceof ObjectInstance)
                result.add((ObjectInstance) object);
        return result;
    }

    // в интерфейсе
    public boolean isInInterface(GroupObjectInstance classGroup) {

        Map<P, AndClassSet> classImplement = new HashMap<P, AndClassSet>();
        for(P propertyInterface : property.interfaces)
            classImplement.put(propertyInterface, mapping.get(propertyInterface).getClassSet(classGroup));
        return property.allInInterface(classImplement);
    }

    // проверяет на то что изменился верхний объект
    public boolean objectUpdated(GroupObjectInstance classGroup) {
        for(PropertyObjectInterfaceInstance intObject : mapping.values())
            if(intObject.objectUpdated(classGroup)) return true;

        return false;
    }

    public boolean classUpdated(GroupObjectInstance classGroup) {
        for(PropertyObjectInterfaceInstance intObject : mapping.values())
            if(intObject.classUpdated(classGroup))
                return true;

        return false;
    }

    public PropertyObjectInstance(Property<P> property,Map<P,? extends PropertyObjectInterfaceInstance> mapping) {
        super(property, (Map<P, PropertyObjectInterfaceInstance>) mapping);
    }

    public boolean dataUpdated(Collection<Property> changedProps) {
        return changedProps.contains(property);
    }

    public void fillProperties(Set<Property> properties) {
        properties.add(property);
    }

    public Map<P, ConcreteClass> getInterfaceClasses(P overrideInterface, ConcreteClass overrideClass) {
        Map<P,ConcreteClass> mapInterface = new HashMap<P,ConcreteClass>();
        for(Map.Entry<P, PropertyObjectInterfaceInstance> implement : mapping.entrySet())
            if(overrideInterface!=null && implement.getKey().equals(overrideInterface))
                mapInterface.put(overrideInterface, overrideClass);
            else
                mapInterface.put(implement.getKey(),implement.getValue().getCurrentClass());
        return mapInterface;
    }
    
    public Map<P, ConcreteClass> getInterfaceClasses() {
        return getInterfaceClasses(null, null);
    }

    public Map<P, DataObject> getInterfaceValues() {
        Map<P,DataObject> mapInterface = new HashMap<P,DataObject>();
        for(Map.Entry<P, PropertyObjectInterfaceInstance> implement : mapping.entrySet())
            mapInterface.put(implement.getKey(),implement.getValue().getDataObject());
        return mapInterface;
    }

    public PropertyValueImplement<?> getChangeProperty() {
        return property.getChangeImplement().mapValues(getInterfaceValues());
    }

    public List<ClientAction> execute(DataSession session, Object value, Modifier<? extends Changes> modifier, RemoteForm executeForm, GroupObjectInstance groupObject) throws SQLException {
        return property.execute(getInterfaceValues(), session, value, modifier, executeForm, mapping, groupObject);
    }


    public Expr getExpr(Map<ObjectInstance, ? extends Expr> classSource, Modifier<? extends Changes> modifier) throws SQLException {

        Map<P, Expr> joinImplement = new HashMap<P, Expr>();
        for(P propertyInterface : property.interfaces)
            joinImplement.put(propertyInterface, mapping.get(propertyInterface).getExpr(classSource, modifier));
        return property.getExpr(joinImplement,modifier,null);
    }

    public Type getType() {
        return property.getType();
    }

    public CustomClass getDialogClass() {
        return property.getDialogClass(getInterfaceValues(), getInterfaceClasses());
    }
}
