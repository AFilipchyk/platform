package lsfusion.server.logics.action.flow;

import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.*;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.server.caches.IdentityInstanceLazy;
import lsfusion.server.logics.classes.LogicalClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.sets.ResolveClassSet;
import lsfusion.server.logics.ScriptParsingException;
import lsfusion.server.physics.dev.i18n.LocalizedString;
import lsfusion.server.logics.mutables.Version;
import lsfusion.server.logics.property.*;
import lsfusion.server.logics.property.derived.DerivedProperty;

import java.util.List;

public abstract class ListCaseActionProperty extends KeepContextActionProperty {

    private final CalcPropertyMapImplement<UnionProperty.Interface, PropertyInterface> abstractWhere;
    protected boolean isExclusive;
    
    public enum AbstractType { CASE, MULTI, LIST }

    protected boolean checkExclusiveImplementations;
    protected boolean isLast;
    protected final AbstractType type;

    public boolean isAbstract() {
        return abstractWhere != null;
    }

    public AbstractType getAbstractType() {
        return type;
    }


    protected void addWhereOperand(ActionPropertyMapImplement<?, PropertyInterface> action, List<ResolveClassSet> signature, Version version) {
        ((CaseUnionProperty) abstractWhere.property).addOperand(action.mapWhereProperty().map(abstractWhere.mapping.reverse()), signature, version);
    }

    protected void addWhereCase(CalcPropertyInterfaceImplement<PropertyInterface> where, ActionPropertyMapImplement<?, PropertyInterface> action, Version version) {
        ImRevMap<PropertyInterface, UnionProperty.Interface> abstractMap = abstractWhere.mapping.reverse();
        ((CaseUnionProperty) abstractWhere.property).addCase(where.map(abstractMap), action.mapWhereProperty().map(abstractMap), version);
    }

    // immutable реализация
    protected <I extends PropertyInterface> ListCaseActionProperty(LocalizedString caption, boolean isExclusive, ImOrderSet<I> innerInterfaces) {
        super(caption, innerInterfaces.size());

        this.abstractWhere = null;
        this.type = null;
        this.isExclusive = isExclusive;
    }

    // mutable реализация
    public <I extends PropertyInterface> ListCaseActionProperty(LocalizedString caption, boolean checkExclusiveImplementations, boolean checkAllImplementations, boolean isLast, AbstractType type, ImOrderSet<I> innerInterfaces, ImMap<I, ValueClass> mapClasses)  {
        super(caption, innerInterfaces.size());

        this.checkExclusiveImplementations = checkExclusiveImplementations;
        this.isLast = isLast;
        this.type = type;

        CaseUnionProperty.Type caseType = null;
        switch (type) {
            case CASE: caseType = CaseUnionProperty.Type.CASE; break;
            case MULTI: caseType = CaseUnionProperty.Type.MULTI; break;
            case LIST: caseType = CaseUnionProperty.Type.VALUE; break;
        }
        abstractWhere = DerivedProperty.createUnion(checkExclusiveImplementations, checkAllImplementations, isLast, caseType, interfaces, LogicalClass.instance, getMapInterfaces(innerInterfaces).join(mapClasses));
    }

    protected abstract CalcPropertyMapImplement<?, PropertyInterface> calcCaseWhereProperty();

    @IdentityInstanceLazy
    public CalcPropertyMapImplement<?, PropertyInterface> calcWhereProperty() {
        if(isAbstract())
            return abstractWhere;

        return calcCaseWhereProperty();
    }

    protected void finalizeAbstractInit() {
        CaseUnionProperty caseProp = (CaseUnionProperty) abstractWhere.property;
        try {
            caseProp.finalizeInit();
        } catch (CaseUnionProperty.NotFullyImplementedException e) {
            throw new RuntimeException("Action is not fully implemented: " + this +  "\n\tCalculated: " + e.fullClassValueWhere + "\n\tSpecified: " + e.classValueWhere);
        } catch (ScriptParsingException e) {
            throw new ScriptParsingException("error finalizing abstract " + this + ":\n" + e.getMessage());
        }
    }
    
    @Override
    public void finalizeInit() {
        super.finalizeInit();

        if (isAbstract()) {
            finalizeAbstractInit();
        }
    }

    public void markRecursions() {
        assert isAbstract();
        markRecursions(SetFact.<ListCaseActionProperty>EMPTY());
    }

    @Override
    protected void markRecursions(ImSet<ListCaseActionProperty> recursiveActions) {
        super.markRecursions(recursiveActions.addExcl(this)); // // пока исходим из того что рекурсивными могут быть только abstract'ы
    }

    protected abstract ImList<ActionPropertyMapImplement<?, PropertyInterface>> getListActions();

    public ImSet<ActionProperty> getDependActions() {
        return getListActions().mapListValues(new GetValue<ActionProperty, ActionPropertyMapImplement<?, PropertyInterface>>() {
            public ActionProperty getMapValue(ActionPropertyMapImplement<?, PropertyInterface> value) {
                return value.property;
            }
        }).toOrderSet().getSet();
    }
}
