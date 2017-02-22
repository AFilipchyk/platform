package lsfusion.server.session;

import lsfusion.base.FunctionSet;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.server.classes.ConcreteObjectClass;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.data.MutableClosedObject;
import lsfusion.server.data.QueryEnvironment;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.form.instance.PropertyObjectInterfaceInstance;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.*;
import lsfusion.server.logics.property.actions.FormEnvironment;
import lsfusion.server.logics.property.actions.flow.FlowResult;

import java.sql.SQLException;

public abstract class ExecutionEnvironment extends MutableClosedObject<Object> {

    public QueryEnvironment getQueryEnv() {
        return getSession().env;
    }

    public <P extends PropertyInterface> void change(CalcProperty<P> property, PropertyChange<P> change) throws SQLException, SQLHandledException {
        if(change.isEmpty()) // оптимизация
            return;
        
        DataChanges userDataChanges = null;
        if(property instanceof DataProperty) // оптимизация
            userDataChanges = getSession().getUserDataChanges((DataProperty)property, (PropertyChange<ClassPropertyInterface>) change, getQueryEnv());
        change(userDataChanges != null ? userDataChanges : property.getDataChanges(change, getModifier()));
    }

    public <P extends PropertyInterface> void change(DataChanges mapChanges) throws SQLException, SQLHandledException {
        for(DataProperty change : mapChanges.getProperties())
            getSession().changeProperty(change, mapChanges.get(change));
    }

    // в обход dataChanges (иначе проблема с классами, так как в новой сессии изменений по классам нет, и в итоге изменению внутрь не скопируются и при копировании назад затрут те что были) - чем то напоминает noClasses - но noClasses более общая штука
    public void copyDataTo(SessionDataProperty property, PropertyChange<ClassPropertyInterface> change) throws SQLException, SQLHandledException {
        getSession().changeProperty(property, change);
    }

    public <P extends PropertyInterface> FlowResult execute(ActionProperty<P> property, ImMap<P, ? extends ObjectValue> change, FormEnvironment<P> formEnv, ObjectValue pushUserInput, DataObject pushAddObject, ExecutionStack stack) throws SQLException, SQLHandledException {
        return property.execute(new ExecutionContext<>(change, pushUserInput, pushAddObject, this, null, formEnv, stack));
    }

    public boolean apply(BusinessLogics BL, ExecutionStack stack) throws SQLException, SQLHandledException {
        return apply(BL, stack, null);
    }

    public boolean apply(BusinessLogics BL, ExecutionContext context, ImOrderSet<ActionPropertyValueImplement> applyActions) throws SQLException, SQLHandledException {
        return apply(BL, context.stack, context, applyActions, SetFact.<SessionDataProperty>EMPTY(), null);
    }

    public boolean apply(BusinessLogics BL, ExecutionContext context) throws SQLException, SQLHandledException {
        return apply(BL, context.stack, context);
    }
    public boolean apply(ExecutionContext context) throws SQLException, SQLHandledException {
        return apply(context.getBL(), context);
    }

    public boolean apply(BusinessLogics BL, ExecutionStack stack, UserInteraction interaction) throws SQLException, SQLHandledException {
        return apply(BL, stack, interaction, SetFact.<ActionPropertyValueImplement>EMPTYORDER(), SetFact.<SessionDataProperty>EMPTY(), null);
    }

    public void cancel(ExecutionStack stack) throws SQLException, SQLHandledException {
        cancel(stack, SetFact.<SessionDataProperty>EMPTY());
    }

    public abstract DataSession getSession();

    public abstract Modifier getModifier();

    public abstract FormInstance getFormInstance();

    public abstract void changeClass(PropertyObjectInterfaceInstance objectInstance, DataObject dataObject, ConcreteObjectClass cls) throws SQLException, SQLHandledException;

    public abstract boolean apply(BusinessLogics BL, ExecutionStack stack, UserInteraction interaction, ImOrderSet<ActionPropertyValueImplement> applyActions, FunctionSet<SessionDataProperty> keepProperties, ExecutionEnvironment sessionEventFormEnv) throws SQLException, SQLHandledException;
    
    public abstract void cancel(ExecutionStack stack, FunctionSet<SessionDataProperty> keep) throws SQLException, SQLHandledException;
}
