package lsfusion.server.logics.property;

import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.Settings;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.where.WhereBuilder;
import lsfusion.server.data.where.classes.ClassWhere;
import lsfusion.server.physics.admin.drilldown.DrillDownFormEntity;
import lsfusion.server.physics.admin.drilldown.OldDrillDownFormEntity;
import lsfusion.server.logics.LogicsModule;
import lsfusion.server.physics.dev.i18n.LocalizedString;
import lsfusion.server.logics.property.infer.ExClassSet;
import lsfusion.server.logics.property.infer.InferType;
import lsfusion.server.logics.property.infer.Inferred;
import lsfusion.server.logics.action.session.change.PropertyChanges;

public class OldProperty<T extends PropertyInterface> extends SessionCalcProperty<T> {

    public OldProperty(CalcProperty<T> property, PrevScope scope) {
        super(LocalizedString.concat("(" + scope.getSID() + ",в БД) ", property.localizedToString()), property, scope);
        
        drawOptions.inheritDrawOptions(property.drawOptions);
    }

/*    @Override
    protected Collection<Pair<Property<?>, LinkType>> calculateLinks() {
        return BaseUtils.add(super.calculateLinks(), new Pair<Property<?>, LinkType>(property, LinkType.EVENTACTION)); // чтобы лексикографику для applied была
    }*/

    public OldProperty<T> getOldProperty() {
        return this;
    }

    public ChangedProperty<T> getChangedProperty() {
        return property.getChanged(IncrementType.CHANGED, scope);
    }

    protected Expr calculateExpr(ImMap<T, ? extends Expr> joinImplement, CalcType calcType, PropertyChanges propChanges, WhereBuilder changedWhere) {
        if(calcType instanceof CalcClassType) {
            return getVirtualTableExpr(joinImplement, (CalcClassType) calcType);
        }
        return property.getExpr(joinImplement, calcType); // возвращаем старое значение
    }

    @Override
    public ClassWhere<Object> calcClassValueWhere(CalcClassType type) {
        ClassWhere<Object> classValueWhere = property.getClassValueWhere(type);
        if(type == CalcClassType.PREVBASE)
            return classValueWhere.getBase();
        return classValueWhere;
//        return super.getClassValueWhere(type);
    }

    public Inferred<T> calcInferInterfaceClasses(ExClassSet commonValue, InferType inferType) {
        Inferred<T> result = property.inferInterfaceClasses(commonValue, inferType);
        if(inferType == InferType.PREVBASE)
            result = result.getBase(inferType);
        return result;
    }
    public ExClassSet calcInferValueClass(ImMap<T, ExClassSet> inferred, InferType inferType) {
        ExClassSet exClassSet = property.inferValueClass(inferred, inferType);
        if(inferType == InferType.PREVBASE)
            exClassSet = ExClassSet.getBase(exClassSet);
        return exClassSet;
    }

    @Override
    public boolean supportsDrillDown() {
        return property != null;
    }

    @Override
    public boolean drillDownInNewSession() {
        return true;
    }

    @Override
    public DrillDownFormEntity createDrillDownForm(LogicsModule LM, String canonicalName) {
        return new OldDrillDownFormEntity(
                canonicalName, LocalizedString.create("{logics.property.drilldown.form.old}"), this, LM
        );
    }

    @Override
    public void finalizeAroundInit() {
        super.finalizeAroundInit();
        hideOlds(); // для multi-threading'а, возможно правильнее подсчитывать getLinks в ActionProperty, но похоже что разницы никакой, а там небольшая проблема с кжшами (links)
    }

    @Override
    public ImSet<SessionCalcProperty> getSessionCalcDepends(boolean events) {
        if(hideOlds())
            return SetFact.EMPTY();
        return super.getSessionCalcDepends(events);
    }

    private boolean hideOlds() {
        return Settings.get().isUseEventValuePrevHeuristic() && property instanceof AggregateProperty && ((AggregateProperty)property).hasAlotKeys();
    }
}
