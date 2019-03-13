package lsfusion.server.logics.property;

import lsfusion.base.BaseUtils;
import lsfusion.base.Pair;
import lsfusion.base.col.ListFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImCol;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.MCol;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.server.ServerLoggers;
import lsfusion.server.Settings;
import lsfusion.server.logics.action.session.*;
import lsfusion.server.logics.classes.CustomClass;
import lsfusion.server.logics.classes.LogicalClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.expr.ValueExpr;
import lsfusion.server.data.where.Where;
import lsfusion.server.data.where.WhereBuilder;
import lsfusion.server.data.where.classes.ClassWhere;
import lsfusion.server.logics.form.struct.drilldown.ChangedDrillDownFormEntity;
import lsfusion.server.logics.form.struct.drilldown.DrillDownFormEntity;
import lsfusion.server.logics.ApplyCalcEvent;
import lsfusion.server.logics.ApplyRemoveClassesEvent;
import lsfusion.server.logics.LogicsModule;
import lsfusion.server.physics.dev.i18n.LocalizedString;
import lsfusion.server.logics.property.actions.ChangeEvent;
import lsfusion.server.logics.property.infer.ExClassSet;
import lsfusion.server.logics.property.infer.InferType;
import lsfusion.server.logics.property.infer.Inferred;
import lsfusion.server.session.*;

import java.sql.SQLException;

public class ChangedProperty<T extends PropertyInterface> extends SessionCalcProperty<T> {

    private final IncrementType type;

    public static class Interface extends PropertyInterface<Interface> {
        Interface(int ID) {
            super(ID);
        }
    }

    public ChangedProperty(CalcProperty<T> property, IncrementType type, PrevScope scope) {
        super(LocalizedString.concat("(" + type + ") ", property.localizedToString()), property, scope);
        this.type = type;

        property.getOld(scope);// чтобы зарегить old
    }

    public OldProperty<T> getOldProperty() {
        return property.getOld(scope);
    }

    public ChangedProperty<T> getChangedProperty() {
        return this;
    }

    @Override
    protected void fillDepends(MSet<CalcProperty> depends, boolean events) {
        depends.add(property);
        depends.add(property.getOld(scope));
    }


    @Override
    public ImSet<CalcProperty> calculateUsedChanges(StructChanges propChanges) {
        Boolean setOrDropped = getSetOrDropped();
        if(setOrDropped != null && !Settings.get().isDisableSetDroppedOptimization()) {
            if (isFakeChange(propChanges, setOrDropped))
                return SetFact.EMPTY();
        }
        return super.calculateUsedChanges(propChanges);
    }

    // должно быть синхронизировано с аналогичным методом в StructChanges
    private boolean isFakeChange(StructChanges propChanges, Boolean setOrDropped) {
        return isSingleFakeChange(propChanges, property, setOrDropped) && isSingleFakeChange(propChanges, property.getOld(scope), !setOrDropped);
    }

    private static boolean isSingleFakeChange(StructChanges propChanges, CalcProperty<?> property, boolean setOrDropped) {
        ImSet<CalcProperty> usedChanges = property.getUsedChanges(propChanges);
        if(usedChanges.isEmpty()) // нет изменений
            return true;

        ChangeType type = propChanges.getUsedChange(property);

        if(usedChanges.size() > 1 || !BaseUtils.hashEquals(usedChanges.single(), property)) { // есть изменения, кроме считанного usedChange
            ServerLoggers.assertLog(type == null || !type.isFinal(), "SHOULD NOT BE");
            return false;
        }

        assert BaseUtils.hashEquals(usedChanges.single(), property);
        ServerLoggers.assertLog(type != null, "SHOULD NOT BE");

        Boolean changeSetOrDropped = type.getSetOrDropped();
        return changeSetOrDropped != null && !changeSetOrDropped.equals(setOrDropped); // если SET, а изменение на NULL, или DROPPED и наоборот, считаем что изменение не используется так как все равно всегда NULL будет
    }

    protected Expr calculateExpr(ImMap<T, ? extends Expr> joinImplement, CalcType calcType, PropertyChanges propChanges, WhereBuilder changedWhere) {
        if(calcType.isExpr() && propChanges.isEmpty()) // оптимизация для событий
            return Expr.NULL;
        
        WhereBuilder changedIncrementWhere = new WhereBuilder();
        property.getIncrementExpr(joinImplement, changedIncrementWhere, calcType, propChanges, type, scope);
        if(changedWhere!=null) changedWhere.add(changedIncrementWhere.toWhere());
        return ValueExpr.get(changedIncrementWhere.toWhere());
    }

    // для resolve'а следствий в частности
    public PropertyChange<T> getFullChange(Modifier modifier) throws SQLException, SQLHandledException {
        ImRevMap<T, KeyExpr> mapKeys = getMapKeys();
        Expr expr = property.getExpr(mapKeys, modifier);
        Where where;
        switch(type) {
            case SET:
                where = expr.getWhere();
                break;
            case DROP:
                where = expr.getWhere().not().and(property.getClassProperty().mapExpr(mapKeys, modifier).getWhere());
                break;
            default:
                return null;
        }
        return new PropertyChange<>(mapKeys, ValueExpr.get(where), Where.TRUE);
    }

    @Override
    protected ImCol<Pair<Property<?>, LinkType>> calculateLinks(boolean events) {
        if(property instanceof IsClassProperty) {
            assert events;  // так как при ONLY_DATA IsClassProperty вообще не может попасть в список
            // специально не вызываем super чтобы развернуть связь и смотреть на реальные DROPPED / SET
            ImCol<Pair<Property<?>, LinkType>> result = getActionChangeProps(); // только у Data и IsClassProperty

            ValueClass interfaceClass = ((IsClassProperty) property).getInterfaceClass();
            if(interfaceClass instanceof CustomClass) {
                CustomClass customClass = (CustomClass) interfaceClass;
                MCol<Pair<Property<?>, LinkType>> mParentLinks = ListFact.mCol();
                for(CustomClass parent : customClass.getParentsIt()) // добавляем очень слабую связь чтобы удаления классов по возможности группами шли
                    if(!parent.disableSingleApply())
                        mParentLinks.add(new Pair<Property<?>, LinkType>(parent.getProperty().getChanged(IncrementType.DROP, ChangeEvent.scope), LinkType.REMOVEDCLASSES));
                result = result.mergeCol(mParentLinks.immutableCol());
            }
            return result;
        } else
            return super.calculateLinks(events);
    }
    
    public ImSet<CalcProperty> getSingleApplyDroppedIsClassProps() {
        assert isSingleApplyDroppedIsClassProp();
        return ((IsClassProperty) property).getSingleApplyDroppedIsClassProps();
    }

    public boolean isSingleApplyDroppedIsClassProp() {
        return type == IncrementType.DROP && property instanceof IsClassProperty && ((IsClassProperty) property).getInterfaceClass() instanceof CustomClass && scope == ChangeEvent.scope;
    }
    
    @Override
    public ApplyCalcEvent getApplyEvent() {
        if (isSingleApplyDroppedIsClassProp()) {
            if(event == null)
                event = new ApplyRemoveClassesEvent(this);
            return (ApplyRemoveClassesEvent)event;
        }
        return null;
    }


    @Override
    public ClassWhere<Object> calcClassValueWhere(CalcClassType calcType) {
        ClassWhere<Object> result = new ClassWhere<Object>("value", LogicalClass.instance).and(BaseUtils.<ClassWhere<Object>>immutableCast(property.getClassWhere(calcType)));
        if(calcType == CalcClassType.PREVBASE && !type.isNotNullNew())
            result = result.getBase();
        return result; // assert что full
    }

    public Inferred<T> calcInferInterfaceClasses(ExClassSet commonValue, InferType inferType) {
        Inferred<T> result = property.inferInterfaceClasses(ExClassSet.notNull(commonValue), inferType);
        if(inferType == InferType.PREVBASE && !type.isNotNullNew())
            result = result.getBase(inferType);
        return result;
    }
    public ExClassSet calcInferValueClass(ImMap<T, ExClassSet> inferred, InferType inferType) {
        return ExClassSet.logical;
    }

    @Override
    public boolean supportsDrillDown() {
        return property != null;
    }

    @Override
    public DrillDownFormEntity createDrillDownForm(LogicsModule LM, String canonicalName) {
        return new ChangedDrillDownFormEntity(
                canonicalName, LocalizedString.create("{logics.property.drilldown.form.data}"), this, LM
        );
    }

    public Boolean getSetOrDropped() {
        return type.getSetOrDropped();
    }

    @Override
    public String getChangeExtSID() {
        if((type == IncrementType.SET || type == IncrementType.DROP) && property instanceof IsClassProperty)
            return "IS" + ((IsClassProperty)property).interfaces.single().interfaceClass.getSID();
        return super.getChangeExtSID();
    }
}
