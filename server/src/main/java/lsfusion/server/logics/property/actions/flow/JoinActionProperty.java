package lsfusion.server.logics.property.actions.flow;

import lsfusion.base.Pair;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.*;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.ImFilterValueMap;
import lsfusion.server.caches.IdentityInstanceLazy;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.type.Type;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.debug.ActionDelegationType;
import lsfusion.server.logics.debug.WatchActionProperty;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.*;
import lsfusion.server.logics.property.derived.DerivedProperty;

import java.sql.SQLException;

public class JoinActionProperty<T extends PropertyInterface> extends KeepContextActionProperty {

    public final ActionPropertyImplement<T, CalcPropertyInterfaceImplement<PropertyInterface>> action; // action + mapping на calculate

    public <I extends PropertyInterface> JoinActionProperty(LocalizedString caption, ImOrderSet<I> listInterfaces, ActionPropertyImplement<T, CalcPropertyInterfaceImplement<I>> implement) {
        super(caption, listInterfaces.size());

        action = DerivedProperty.mapActionImplements(implement, getMapInterfaces(listInterfaces).reverse());

        finalizeInit();
    }

    public FlowResult aspectExecute(ExecutionContext<PropertyInterface> context) throws SQLException, SQLHandledException {
        ImFilterValueMap<T, ObjectValue> mvReadValues = action.mapping.mapFilterValues();
        for (int i=0,size=action.mapping.size();i<size;i++)
            mvReadValues.mapValue(i, action.mapping.getValue(i).readClasses(context, context.getKeys()));
        action.property.execute(context.override(mvReadValues.immutableValue(), action.mapping));
        return FlowResult.FINISH;
    }

    @Override
    public Type getFlowSimpleRequestInputType(boolean optimistic, boolean inRequest) {
        if(isRecursive) // recursion guard
            return null;
        return action.property.getSimpleRequestInputType(optimistic, inRequest);
    }

    @Override
    public CustomClass getSimpleAdd() {
        if(isRecursive) // recursion guard
            return null;
        return action.property.getSimpleAdd();
    }

    @Override
    public PropertyInterface getSimpleDelete() {
        if(!isRecursive) { // recursion guard
            T simpleRemove = action.property.getSimpleDelete();
            CalcPropertyInterfaceImplement<PropertyInterface> mapRemove;
            if (simpleRemove != null && ((mapRemove = action.mapping.get(simpleRemove)) instanceof PropertyInterface))
                return (PropertyInterface) mapRemove;
        }
        return super.getSimpleDelete();
    }

    @Override
    public boolean hasFlow(ChangeFlowType type) {
        if(isRecursive) // recursion guard
            return false;

        if (type == ChangeFlowType.RETURN)
            return false;
        return super.hasFlow(type);
    }

    public ImSet<ActionProperty> getDependActions() {
        return SetFact.singleton((ActionProperty)action.property);
    }

    @Override
    protected ImMap<CalcProperty, Boolean> aspectChangeExtProps() {
        if(isRecursive) // recursion guard
            return MapFact.EMPTY();
        return super.aspectChangeExtProps();
    }

    @Override
    public ImMap<CalcProperty, Boolean> aspectUsedExtProps() {
        MSet<CalcProperty> used = SetFact.mSet();
        for(CalcPropertyInterfaceImplement<PropertyInterface> value : action.mapping.valueIt())
            value.mapFillDepends(used);
        ImMap<CalcProperty, Boolean> result = used.immutable().toMap(false);
        if(!isRecursive)
            result = result.merge(super.aspectUsedExtProps(), addValue);
        return result;
    }

    @IdentityInstanceLazy
    public CalcPropertyMapImplement<?, PropertyInterface> calcWhereProperty() { // тут на recursive не смо
        return DerivedProperty.createJoin(action.property.getWhereProperty(true).mapImplement(action.mapping));
    }

    @Override
    public ImList<ActionPropertyMapImplement<?, PropertyInterface>> getList() {
        // если все интерфейсы однозначны и нет return'ов - inlin'им
        if(isRecursive || action.property.hasFlow(ChangeFlowType.RETURN))
            return super.getList();
        
        ImRevMap<T, PropertyInterface> identityMap = PropertyInterface.getIdentityMap(action.mapping);
        if(identityMap == null)
            return super.getList();

        return DerivedProperty.mapActionImplements(identityMap, action.property.getList());
    }

    private boolean isRecursive;
    // пока исходим из того что рекурсивными могут быть только abstract'ы
    @Override
    protected void markRecursions(ImSet<ListCaseActionProperty> recursiveActions) {
        ActionProperty<T> execAction = action.property;
        if(execAction instanceof ListCaseActionProperty && recursiveActions.contains((ListCaseActionProperty)execAction)) {
            assert ((ListCaseActionProperty) execAction).isAbstract();
            isRecursive = true;
        } else
            super.markRecursions(recursiveActions);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public ActionDelegationType getDelegationType(boolean modifyContext) {
        if(action.property instanceof WatchActionProperty)
            return super.getDelegationType(modifyContext);
        return ActionDelegationType.IN_DELEGATE; // jump to another LSF
    }

    @Override
    protected ImSet<Pair<String, Integer>> getRecInnerDebugActions() {
        if(isRecursive) // recursion guard
            return SetFact.EMPTY();
        return super.getRecInnerDebugActions();
    }

    @Override
    public boolean endsWithApplyAndNoChangesAfterBreaksBefore() {
        if(isRecursive) // recursion guard
            return false;
        
        return action.property.endsWithApplyAndNoChangesAfterBreaksBefore();
    }
}
