package lsfusion.server.logics.property.classes.data;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.ValueExpr;
import lsfusion.server.data.where.WhereBuilder;
import lsfusion.server.logics.property.implement.CalcPropertyImplement;
import lsfusion.server.logics.property.CalcType;
import lsfusion.server.logics.property.oraction.PropertyInterface;
import lsfusion.server.physics.dev.i18n.LocalizedString;
import lsfusion.server.logics.property.infer.ExClassSet;
import lsfusion.server.logics.property.infer.InferType;
import lsfusion.server.logics.property.infer.Inferred;
import lsfusion.server.logics.action.session.change.PropertyChanges;

public class NotFormulaProperty extends FormulaProperty<PropertyInterface> {

    public NotFormulaProperty() {
        super(LocalizedString.create("Не"), SetFact.singletonOrder(new PropertyInterface(0)));
    }

    public final static NotFormulaProperty instance = new NotFormulaProperty();

    protected Expr calculateExpr(ImMap<PropertyInterface, ? extends Expr> joinImplement, CalcType calcType, PropertyChanges propChanges, WhereBuilder changedWhere) {
        return ValueExpr.get(joinImplement.singleValue().getWhere().not());
    }
    
    public <T> CalcPropertyImplement<PropertyInterface , T> getImplement(T map) {
        return new CalcPropertyImplement<>(this, MapFact.singleton(interfaces.single(), map));
    }

    @Override
    public Inferred<PropertyInterface> calcInferInterfaceClasses(ExClassSet commonValue, InferType inferType) {
        return new Inferred<>(MapFact.<PropertyInterface, ExClassSet>EMPTY());
    }
}
