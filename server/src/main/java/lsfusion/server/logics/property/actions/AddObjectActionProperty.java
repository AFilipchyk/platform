package lsfusion.server.logics.property.actions;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.*;
import lsfusion.base.col.interfaces.mutable.MExclMap;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetExValue;
import lsfusion.server.classes.AbstractCustomClass;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.ObjectClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.where.Where;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.debug.ActionDelegationType;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.*;
import lsfusion.server.logics.property.actions.flow.ChangeFlowType;
import lsfusion.server.logics.property.actions.flow.ExtendContextActionProperty;
import lsfusion.server.logics.property.actions.flow.FlowResult;
import lsfusion.server.logics.property.derived.DerivedProperty;
import lsfusion.server.session.*;

import java.sql.SQLException;

public class AddObjectActionProperty<T extends PropertyInterface, I extends PropertyInterface> extends ExtendContextActionProperty<I> {

    protected final CustomClass valueClass; // обозначает класс объекта, который нужно добавить
    private final boolean autoSet;

    protected CalcPropertyMapImplement<T, I> where;
    private CalcPropertyMapImplement<?, I> result; // только extend интерфейсы

    private final ImOrderMap<CalcPropertyInterfaceImplement<I>, Boolean> orders; // calculate
    private final boolean ordersNotNull;

    public <T extends PropertyInterface> AddObjectActionProperty(CustomClass valueClass, CalcProperty<T> result, boolean autoSet) {
        this(valueClass, SetFact.<I>EMPTY(), SetFact.<I>EMPTYORDER(), null, result!=null ? new CalcPropertyMapImplement<T, I>(result) : null, MapFact.<CalcPropertyInterfaceImplement<I>, Boolean>EMPTYORDER(), false, autoSet);
    }

    public AddObjectActionProperty(CustomClass valueClass, ImSet<I> innerInterfaces, ImOrderSet<I> mapInterfaces, CalcPropertyMapImplement<T, I> where, CalcPropertyMapImplement<?, I> result, ImOrderMap<CalcPropertyInterfaceImplement<I>, Boolean> orders, boolean ordersNotNull, boolean autoSet) {
        super(LocalizedString.create("{logics.add}"), innerInterfaces, mapInterfaces);
        
        this.valueClass = valueClass;
        
        this.autoSet = autoSet;
        
        this.where = where;
        this.result = result;
        
        this.orders = orders;
        this.ordersNotNull = ordersNotNull;
        
        assert where==null || !needDialog();
        
        assert where==null || !autoSet;

        assert where==null || result==null || innerInterfaces.containsAll(where.mapping.valuesSet().merge(result.mapping.valuesSet()));
    }
    
    protected boolean needDialog() {
        return valueClass instanceof AbstractCustomClass;  // || (forceDialog && valueClass.hasChildren())
    }

    public ImSet<ActionProperty> getDependActions() {
        return SetFact.EMPTY();
    }

    @Override
    public ImMap<CalcProperty, Boolean> aspectUsedExtProps() {
        if(where==null)
            return MapFact.EMPTY();
        return getUsedProps(where);
    }

    @Override
    public ImMap<CalcProperty, Boolean> aspectChangeExtProps() {
        ImMap<CalcProperty, Boolean> result = getChangeExtProps(valueClass);
        if(this.result!=null)
            result = result.merge(this.result.property.getChangeProps().toMap(false), addValue);
        return result;
    }
    
    public static ImMap<CalcProperty, Boolean> getChangeExtProps(CustomClass valueClass) {
        MExclMap<CalcProperty, Boolean> mResult = MapFact.mExclMap();
        mResult.exclAddAll(valueClass.getParentSetProps().toMap(false));
        mResult.exclAddAll(valueClass.getDataProps().toMap(false));
        mResult.exclAdd(valueClass.getBaseClass().getObjectClassProperty(), false);
        return mResult.immutable();
    }

    @Override
    public CustomClass getSimpleAdd() {
        if(where==null && !needDialog())
            return valueClass;
        return null;
    }

    protected FlowResult executeExtend(ExecutionContext<PropertyInterface> context, ImRevMap<I, KeyExpr> innerKeys, ImMap<I, ? extends ObjectValue> innerValues, ImMap<I, Expr> innerExprs) throws SQLException, SQLHandledException {
        ObjectClass readClass;
        if (needDialog()) {
            ObjectValue objectValue = context.requestUserClass(valueClass, valueClass, true);
            if (!(objectValue instanceof DataObject)) // cancel
                return FlowResult.FINISH;
            readClass = valueClass.getBaseClass().findClassID((Integer) ((DataObject) objectValue).object);
        } else
            readClass = valueClass;

        executeRead(context, innerKeys, innerExprs, (ConcreteCustomClass) readClass);

        return FlowResult.FINISH;
    }

    protected void executeRead(ExecutionContext<PropertyInterface> context, ImRevMap<I, KeyExpr> innerKeys, ImMap<I, Expr> innerExprs, ConcreteCustomClass readClass) throws SQLException, SQLHandledException {
        SinglePropertyTableUsage<I> addedTable = null;
        DataSession session = context.getSession();
        try {
            PropertyChange<I> resultChange;
            if(where==null) { // оптимизация, один объект добавляем
                DataObject addObject = context.addObject(readClass, autoSet);
                resultChange = new PropertyChange<>(addObject);
            } else {
                if(result!=null)
                    session.dropChanges((DataProperty) result.property);
    
                final Modifier modifier = context.getModifier();
                Where exprWhere = where.mapExpr(innerExprs, modifier).getWhere();
                if(exprWhere.isFalse()) // оптимизация, важна так как во многих event'ах может учавствовать
                    return;
    
                final ImMap<I, ? extends Expr> fInnerExprs = PropertyChange.simplifyExprs(innerExprs, exprWhere);
                ImOrderMap<Expr, Boolean> orderExprs = orders.mapMergeOrderKeysEx(new GetExValue<Expr, CalcPropertyInterfaceImplement<I>, SQLException, SQLHandledException>() {
                    public Expr getMapValue(CalcPropertyInterfaceImplement<I> value) throws SQLException, SQLHandledException {
                        return value.mapExpr(fInnerExprs, modifier);
                    }
                });
    
                addedTable = context.addObjects("addobjap", readClass, new PropertyOrderSet<>(innerKeys, exprWhere, orderExprs, ordersNotNull));
                resultChange = SinglePropertyTableUsage.getChange(addedTable);
            }
    
            if(result != null)
                result.change(context.getEnv(), resultChange);
        } finally {
            if(addedTable!=null)
                addedTable.drop(session.sql, session.getOwner());
        }
    }

    protected CalcPropertyMapImplement<?, I> calcGroupWhereProperty() {
        if(where==null)
            return DerivedProperty.createTrue();
        return where;
    }

    @Override
    public boolean hasFlow(ChangeFlowType type) {
        if(type == ChangeFlowType.CHANGE)
            return true;
        return super.hasFlow(type);
    }

    @Override
    public ActionDelegationType getDelegationType(boolean modifyContext) {
        return ActionDelegationType.IN_DELEGATE;
    }
}
