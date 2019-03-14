package lsfusion.server.physics.dev.integration.internal.to;

import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetIndex;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.server.logics.classes.DataClass;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.formula.CustomFormulaSyntax;
import lsfusion.server.data.expr.formula.FormulaExpr;
import lsfusion.server.data.where.WhereBuilder;
import lsfusion.server.logics.property.CalcType;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.property.classes.data.ValueFormulaProperty;
import lsfusion.server.logics.property.classes.data.FormulaImplProperty;
import lsfusion.server.physics.dev.i18n.LocalizedString;
import lsfusion.server.logics.property.infer.ExClassSet;
import lsfusion.server.logics.property.infer.InferType;
import lsfusion.server.logics.action.session.change.PropertyChanges;

public class StringFormulaProperty extends ValueFormulaProperty<StringFormulaProperty.Interface> {

    private final CustomFormulaSyntax formula;
    private final boolean hasNotNull;
    
    public static String getParamName(String prmID) {
        return "prm" + prmID;
    }

    public static class Interface extends PropertyInterface {

        private String getString() {
            return getParamName(String.valueOf(ID+1));
        }

        public Interface(int ID) {
            super(ID);
        }
    }

    static ImOrderSet<Interface> getInterfaces(int paramCount) {
        return SetFact.toOrderExclSet(paramCount, new GetIndex<Interface>() {
            public Interface getMapValue(int i) {
                return new Interface(i);
            }});
    }

    public Interface findInterface(String string) {
        for(Interface propertyInterface : interfaces)
            if(propertyInterface.getString().equals(string))
                return propertyInterface;
        throw new RuntimeException("not found");
    }

    public StringFormulaProperty(DataClass valueClass, CustomFormulaSyntax formula, int paramCount, boolean hasNotNull) {
        super(LocalizedString.create(formula.getDefaultSyntax()),getInterfaces(paramCount),valueClass);
        this.formula = formula;
        this.hasNotNull = hasNotNull;

        finalizeInit();
    }

    public Expr calculateExpr(final ImMap<Interface, ? extends Expr> joinImplement, CalcType calcType, PropertyChanges propChanges, WhereBuilder changedWhere) {

        ImMap<String, Expr> params = interfaces.mapKeyValues(new GetValue<String, Interface>() {
            public String getMapValue(Interface value) {
                return value.getString();
            }}, new GetValue<Expr, Interface>() {
            public Expr getMapValue(Interface value) {
                return joinImplement.get(value);
            }});

        return FormulaExpr.createCustomFormula(formula, value, params, hasNotNull);
    }

    @Override
    public lsfusion.server.logics.property.infer.ExClassSet calcInferValueClass(ImMap<Interface, ExClassSet> inferred, InferType inferType) {
        return FormulaImplProperty.inferValueClass(getOrderInterfaces(), FormulaExpr.createCustomFormulaImpl(formula, value, hasNotNull, getOrderInterfaces().mapOrderSetValues(new GetValue<String, Interface>() {
            public String getMapValue(Interface value) {
                return value.getString();
            }})), inferred);
    }
}
