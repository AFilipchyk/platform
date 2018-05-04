package lsfusion.server.logics.property;

import lsfusion.base.Result;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.*;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.base.col.interfaces.mutable.mapvalue.ImValueMap;
import lsfusion.interop.Compare;
import lsfusion.server.Settings;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.expr.query.GroupType;
import lsfusion.server.data.expr.where.cases.CaseExpr;
import lsfusion.server.data.where.Where;
import lsfusion.server.data.where.WhereBuilder;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.session.DataChanges;
import lsfusion.server.session.PropertyChange;
import lsfusion.server.session.PropertyChanges;
import lsfusion.server.session.StructChanges;

public class SumGroupProperty<I extends PropertyInterface> extends AddGroupProperty<I> {

    private CalcPropertyMapImplement<ClassPropertyInterface, Interface<I>> nullImplement;
    public CalcPropertyMapImplement<?, I> distribute;

    public SumGroupProperty(LocalizedString caption, ImSet<I> innerInterfaces, ImCol<? extends CalcPropertyInterfaceImplement<I>> groupInterfaces, CalcPropertyInterfaceImplement<I> property) {
        super(caption, innerInterfaces, groupInterfaces, property);

        finalizeInit();
    }

    public SumGroupProperty(LocalizedString caption, ImSet<I> innerInterfaces, ImList<? extends CalcPropertyInterfaceImplement<I>> groupInterfaces, CalcPropertyInterfaceImplement<I> property) {
        super(caption, innerInterfaces, groupInterfaces, property);

        finalizeInit();
    }
    
    public Expr getChangedExpr(Expr changedExpr, Expr changedPrevExpr, Expr prevExpr, ImMap<Interface<I>, ? extends Expr> joinImplement, PropertyChanges propChanges, WhereBuilder changedWhere) {
        if(changedWhere!=null) changedWhere.add(changedExpr.getWhere().or(changedPrevExpr.getWhere())); // если хоть один не null
        return changedExpr.diff(changedPrevExpr).sum(getExpr(joinImplement));
    }

    @Override
    protected ImSet<CalcProperty> calculateUsedDataChanges(StructChanges propChanges) {
        if(distribute != null) {
            MSet<CalcProperty> mImplementDepends = SetFact.mSet();
            groupProperty.mapFillDepends(mImplementDepends);
            ImSet<CalcProperty> implementDepends = mImplementDepends.immutable();
            return SetFact.add(distribute.property.getUsedChanges(propChanges), propChanges.getUsedDataChanges(implementDepends), propChanges.getUsedChanges(implementDepends));
        } else
            return super.calculateUsedDataChanges(propChanges);
    }

    // такая же помошь компилятору как и при getExpr в GroupProperty
    private Where getGroupKeys(PropertyChange<Interface<I>> propertyChange, Result<ImRevMap<I, KeyExpr>> mapKeys, Result<ImMap<I, Expr>> mapValueKeys) {
        ImMap<CalcPropertyInterfaceImplement<I>, Expr> changeValues = propertyChange.getMapExprs().mapKeys(new GetValue<CalcPropertyInterfaceImplement<I>, Interface<I>>() {
            public CalcPropertyInterfaceImplement<I> getMapValue(Interface<I> value) {
                return value.implement;
            }});

        ImRevMap<I, KeyExpr> innerKeys = KeyExpr.getMapKeys(innerInterfaces);

        Where valueWhere = Where.TRUE;
        ImValueMap<I,Expr> mvMapValueKeys = innerKeys.mapItValues();// есть совместная обработка
        for(int i=0,size=innerKeys.size();i<size;i++) {
            Expr expr = changeValues.get(innerKeys.getKey(i));
            if(expr!=null) {
                mvMapValueKeys.mapValue(i, expr);
                valueWhere = valueWhere.and(innerKeys.getValue(i).compare(expr, Compare.EQUALS));
            } else
                mvMapValueKeys.mapValue(i, innerKeys.getValue(i));
        }

        mapKeys.set(innerKeys);
        mapValueKeys.set(mvMapValueKeys.immutableValue());
        return valueWhere;
    }

    @Override
    public ImSet<DataProperty> getChangeProps() {
        if(distribute!=null)
            return groupProperty.mapChangeProps();
        return super.getChangeProps();
    }

    @Override
    protected boolean noIncrement() {
        return false;
    }

    @Override
    protected DataChanges calculateDataChanges(PropertyChange<Interface<I>> propertyChange, WhereBuilder changedWhere, PropertyChanges propChanges) {
        if(distribute != null) {
            // создаем распределяющее свойство от этого, moidfier который меняет это свойство на PropertyChange, получаем значение распределяющего и условие на изменение
            // зацепит лишние changed'ы как и в MaxChangeExpr и иже с ними но пока забьем

            Result<ImRevMap<I, KeyExpr>> mapKeys = new Result<>(); Result<ImMap<I, Expr>> mapValueKeys = new Result<>();
            Where valueWhere = getGroupKeys(propertyChange, mapKeys, mapValueKeys);

            PropertyChanges mapChanges = new PropertyChanges(nullImplement.property, propertyChange.mapChange(nullImplement.mapping));

            Where nullWhere = propertyChange.getWhere(getGroupImplements(mapValueKeys.result, propChanges)).and(groupProperty.mapExpr(mapValueKeys.result, propChanges).getWhere()); // where чтобы за null'ить
            GroupType type = GroupType.ASSERTSINGLE_CHANGE();
            if(!nullWhere.isFalse())
                mapChanges = groupProperty.mapJoinDataChanges(mapKeys.result, CaseExpr.NULL, nullWhere.and(valueWhere), type, null, propChanges).add(mapChanges); // все одинаковые

            Expr distributeExpr = distribute.mapExpr(mapValueKeys.result, mapChanges.add(propChanges));
            DataChanges dataChanges = groupProperty.mapJoinDataChanges(mapKeys.result, distributeExpr, distributeExpr.getWhere().or(nullWhere).and(valueWhere), type, null, propChanges);
            if(changedWhere!=null) {
                if (Settings.get().isCalculateGroupDataChanged())
                    getExpr(propertyChange.getMapExprs(), dataChanges.add(propChanges), changedWhere);
                else
                    changedWhere.add(propertyChange.where);
            }
            return dataChanges;
        } else
            return super.calculateDataChanges(propertyChange, changedWhere, propChanges);
    }

    @Override
    public GroupType getGroupType() {
        return GroupType.SUM;
    }
}
