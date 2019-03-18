package lsfusion.server.logics.action.implement;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.*;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.action.Action;
import lsfusion.server.logics.action.ExecutionContext;
import lsfusion.server.logics.action.session.changed.OldProperty;
import lsfusion.server.logics.event.Event;
import lsfusion.server.logics.form.struct.property.ActionPropertyObjectEntity;
import lsfusion.server.logics.form.struct.object.ObjectEntity;
import lsfusion.server.logics.form.interactive.instance.FormInstance;
import lsfusion.server.logics.form.interactive.instance.property.PropertyObjectInterfaceInstance;
import lsfusion.server.logics.LogicsModule;
import lsfusion.server.data.ObjectValue;
import lsfusion.server.logics.property.Property;
import lsfusion.server.logics.property.implement.PropertyInterfaceImplement;
import lsfusion.server.logics.property.implement.PropertyMapImplement;
import lsfusion.server.logics.property.oraction.PropertyInterface;
import lsfusion.server.physics.dev.debug.DebugInfo;
import lsfusion.server.language.linear.LAP;
import lsfusion.server.logics.action.flow.CaseActionProperty;
import lsfusion.server.logics.action.flow.ChangeFlowType;
import lsfusion.server.logics.action.flow.FlowResult;
import lsfusion.server.logics.property.cases.ActionCase;
import lsfusion.server.logics.property.cases.graph.Graph;
import lsfusion.server.logics.property.derived.DerivedProperty;

import java.sql.SQLException;

public class ActionPropertyMapImplement<P extends PropertyInterface, T extends PropertyInterface> implements lsfusion.server.logics.property.oraction.PropertyInterfaceImplement {

    public Action<P> property;
    public ImRevMap<P, T> mapping;

    public ActionPropertyMapImplement(Action<P> property) {
        this.property = property;
        mapping = MapFact.EMPTYREV();
    }

    public ActionPropertyMapImplement(Action<P> property, ImRevMap<P, T> mapping) {
        this.property = property;
        this.mapping = mapping;
    }

    public <K extends PropertyInterface> ActionPropertyMapImplement<P, K> map(ImRevMap<T, K> remap) {
        return new ActionPropertyMapImplement<>(property, mapping.join(remap));
    }

    public <L extends PropertyInterface> void mapEventAction(LogicsModule lm, PropertyMapImplement<L, T> where, Event event, boolean resolve, DebugInfo.DebugPoint debugInfo) {
        lm.addEventAction(property, where.map(mapping.reverse()), MapFact.<PropertyInterfaceImplement<P>, Boolean>EMPTYORDER(), false, event, resolve, debugInfo);
    }

    public ActionPropertyObjectEntity<P> mapObjects(ImRevMap<T, ObjectEntity> mapObjects) {
        return new ActionPropertyObjectEntity<>(property, mapping.join(mapObjects));
    }

    public PropertyMapImplement<?, T> mapWhereProperty() {
        return property.getWhereProperty().map(mapping);
    }

    public PropertyMapImplement<?, T> mapCalcWhereProperty() {
        return property.getWhereProperty(true).map(mapping);
    }

    public LAP<P> createLP(ImOrderSet<T> listInterfaces) {
        return new LAP<>(property, listInterfaces.mapOrder(mapping.reverse()));
    }

    public FlowResult execute(ExecutionContext<T> context) throws SQLException, SQLHandledException {
        return property.execute(context.map(mapping));
    }

    public T mapSimpleDelete() {
        P simpleDelete = property.getSimpleDelete();
        if(simpleDelete!=null)
            return mapping.get(simpleDelete);
        return null;
    }

    public ImList<ActionPropertyMapImplement<?, T>> getList() {
        return DerivedProperty.mapActionImplements(mapping, property.getList());
    }
/*    public ActionPropertyMapImplement<?, T> compile() {
        return property.compile().map(mapping);
    }*/
    public boolean hasPushFor(ImSet<T> context, boolean ordersNotNull) {
        return property.hasPushFor(mapping, context, ordersNotNull);
    }
    public Property getPushWhere(ImSet<T> context, boolean ordersNotNull) {
        return property.getPushWhere(mapping, context, ordersNotNull);
    }
    public ActionPropertyMapImplement<?, T> pushFor(ImSet<T> context, PropertyMapImplement<?, T> where, ImOrderMap<PropertyInterfaceImplement<T>, Boolean> orders, boolean ordersNotNull) {
        return property.pushFor(mapping, context, where, orders, ordersNotNull);
    }
    public boolean hasFlow(ChangeFlowType... types) {
        for(ChangeFlowType type : types)
            if(property.hasFlow(type))
                return true;
        return false;
    }

    public ImSet<OldProperty> mapParseOldDepends() {
        return property.getParseOldDepends();
    }

    public ActionPropertyValueImplement<P> getValueImplement(ImMap<T, ? extends ObjectValue> mapValues, ImMap<T, PropertyObjectInterfaceInstance> mapObjects, FormInstance formInstance) {
        return new ActionPropertyValueImplement<>(property, mapping.join(mapValues), mapObjects != null ? mapping.innerJoin(mapObjects) : null, formInstance);
    }

    public Graph<ActionCase<T>> mapAbstractGraph() {
        if(property instanceof CaseActionProperty) {
            Graph<ActionCase<PropertyInterface>> absGraph = ((CaseActionProperty) property).getAbstractGraph();
            if(absGraph != null)
                return absGraph.map(new GetValue<ActionCase<T>, ActionCase<PropertyInterface>>() {
                    public ActionCase<T> getMapValue(ActionCase<PropertyInterface> value) {
                        return value.map((ImRevMap<PropertyInterface, T>) mapping);
                    }
                });
        }
        return null;        
    }

    public boolean equalsMap(lsfusion.server.logics.property.oraction.PropertyInterfaceImplement object) {
        if(!(object instanceof ActionPropertyMapImplement))
            return false;

        ActionPropertyMapImplement<?, T> mapProp = (ActionPropertyMapImplement<?, T>) object;
        return property.equals(mapProp.property) && mapping.equals(mapProp.mapping);
    }

    public int hashMap() {
        return 31 * property.hashCode() + mapping.hashCode();
    }

    public String toString() {
        return property.toString() + " {" + mapping + "}";
    }

}
