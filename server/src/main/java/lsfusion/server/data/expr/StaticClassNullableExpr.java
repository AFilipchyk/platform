package lsfusion.server.data.expr;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.interfaces.immutable.ImCol;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.MMap;
import lsfusion.server.base.caches.IdentityLazy;
import lsfusion.server.base.caches.OuterContext;
import lsfusion.server.logics.classes.ConcreteClass;
import lsfusion.server.logics.classes.ValueClassSet;
import lsfusion.server.logics.classes.sets.AndClassSet;
import lsfusion.server.data.expr.formula.FormulaExpr;
import lsfusion.server.data.expr.query.PropStat;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.data.expr.query.StatType;
import lsfusion.server.data.query.innerjoins.GroupJoinsWheres;
import lsfusion.server.data.query.stat.KeyStat;
import lsfusion.server.data.type.Type;
import lsfusion.server.data.where.Where;
import lsfusion.server.data.where.classes.ClassExprWhere;
import lsfusion.server.logics.property.classes.ObjectClassField;

public abstract class StaticClassNullableExpr extends NullableExpr implements StaticClassExprInterface {
    
    @IdentityLazy
    public ConcreteClass getStaticClass() {
        return getStaticClass(null);
    }
    
    public abstract ConcreteClass getStaticClass(KeyType keyType);

    public Type getType(KeyType keyType) {
        return getStaticClass(keyType).getType();
    }
    public Stat getTypeStat(KeyStat keyStat, boolean forJoin) {
        return getStaticClass().getTypeStat(forJoin);
    }

    @Override
    public ImSet<OuterContext> calculateOuterDepends() {
        return BaseUtils.immutableCast(getParams().toSet());
    }

    public PropStat getStatValue(KeyStat keyStat, StatType type) {
        return FormulaExpr.getStatValue(this, keyStat);
    }

    // множественное наследование StaticClassExpr
    @Override
    public ClassExprWhere getClassWhere(AndClassSet classes) {
        return StaticClassExpr.getClassWhere(this, classes);
    }

    @Override
    public Expr classExpr(ImSet<ObjectClassField> classes, IsClassType type) {
        return StaticClassExpr.classExpr(this, classes, type);
    }

    @Override
    public Where isClass(ValueClassSet set, boolean inconsistent) {
        return StaticClassExpr.isClass(this, set, inconsistent);
    }

    @Override
    public AndClassSet getAndClassSet(ImMap<VariableSingleClassExpr, AndClassSet> and) {
        return StaticClassExpr.getAndClassSet(this, and);
    }

    @Override
    public boolean addAndClassSet(MMap<VariableSingleClassExpr, AndClassSet> and, AndClassSet add) {
        return StaticClassExpr.addAndClassSet(this, add);
    }

    @IdentityLazy
    private Where getCommonWhere() {
        return getNotNullWhere(getBaseJoin().getJoins().values());
    }

    private class NotNull extends NullableExpr.NotNull {

        public <K extends BaseExpr> GroupJoinsWheres groupJoinsWheres(ImSet<K> keepStat, StatType statType, KeyStat keyStat, ImOrderSet<Expr> orderTop, GroupJoinsWheres.Type type) {
            return super.groupJoinsWheres(keepStat, statType, keyStat, orderTop, type).and(getCommonWhere().groupJoinsWheres(keepStat, statType, keyStat, orderTop, type));
        }

        public ClassExprWhere calculateClassWhere() {
            return getCommonWhere().getClassWhere(); // по сути и так BaseExpr.getNotNullClassWhere но для лучшего кэширования идем через ветку getCommonWhere  
        }
    }

    protected abstract ImCol<Expr> getParams();

    protected boolean hasUnionNotNull() { // можно было бы calculateNotNullWhere перегрузить, но хочется getCommonWhere в одном месте оставить
        return true;
    }

    public Where calculateNotNullWhere() { // overrided in FormulaUnionExpr, из-за отсутствия множественного наследования
        if(hasUnionNotNull())
            return new NotNull();
        else
            return getCommonWhere();
    }

}
