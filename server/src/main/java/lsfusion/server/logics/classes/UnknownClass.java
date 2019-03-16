package lsfusion.server.logics.classes;

import lsfusion.base.mutability.ImmutableObject;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.server.caches.IdentityStrongLazy;
import lsfusion.server.classes.sets.*;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.expr.formula.FormulaClass;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.data.type.ObjectType;
import lsfusion.server.data.type.Type;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.classes.sets.*;
import lsfusion.server.logics.property.ActionProperty;

public class UnknownClass extends ImmutableObject implements FormulaClass, ConcreteObjectClass {

    public String toString() {
        return ThreadLocalContext.localize("{classes.unknown}");
    }

    public final BaseClass baseClass;
    
    public UnknownClass(BaseClass baseClass) {
        this.baseClass = baseClass;
    }

    public void getDiffSet(ConcreteObjectClass diffClass, MSet<CustomClass> mAddClasses, MSet<CustomClass> mRemoveClasses) {
        if(diffClass instanceof CustomClass) // все удаляются
            ((CustomClass)diffClass).fillParents(mRemoveClasses);
    }

    public boolean inSet(AndClassSet set) {
        return ConcreteCustomClass.inSet(this, set);
    }

    public boolean containsAll(AndClassSet node, boolean implicitCast) {
        return node instanceof UnknownClass && equals(node);
    }

    public OrObjectClassSet getOr() {
        return new OrObjectClassSet();
    }

    public ObjectValue getClassObject() {
        return NullValue.instance;
    }

    public Type getType() {
        return ObjectType.instance;
    }

    public Stat getTypeStat(boolean forJoin) {
        return Stat.MAX;
    }

    public ObjectClassSet and(AndClassSet node) {
        return ConcreteCustomClass.and(this,node);
    }

    public AndClassSet or(AndClassSet node) {
        return ConcreteCustomClass.or(this,node);
    }

    public boolean isEmpty() {
        return false;
    }

    public BaseClass getBaseClass() {
        return baseClass;
    }

    public AndClassSet getKeepClass() {
        return this;
    }

    public AndClassSet[] getAnd() {
        return new AndClassSet[]{this};
    }

    @IdentityStrongLazy // для ID
    public ActionProperty getChangeClassAction() {
        return CustomClass.getChangeClassAction(this);
    }

    public ObjectValueClassSet getValueClassSet() {
        return OrObjectClassSet.FALSE;
    }

    public ResolveClassSet toResolve() {
        return ResolveUpClassSet.FALSE;
    }

    @Override
    public String getShortName() {
        return toString();
    }
}
