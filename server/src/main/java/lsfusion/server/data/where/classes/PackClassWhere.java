package lsfusion.server.data.where.classes;

import lsfusion.base.mutability.TwinImmutableObject;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.MMap;
import lsfusion.server.caches.OuterContext;
import lsfusion.server.caches.hash.HashContext;
import lsfusion.server.data.expr.BaseExpr;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.NullableExprInterface;
import lsfusion.server.data.expr.query.StatType;
import lsfusion.server.data.query.CompileSource;
import lsfusion.server.data.query.JoinData;
import lsfusion.server.data.query.innerjoins.GroupJoinsWheres;
import lsfusion.server.data.query.innerjoins.UpWhere;
import lsfusion.server.data.query.stat.KeyStat;
import lsfusion.server.data.translator.MapTranslate;
import lsfusion.server.data.translator.ExprTranslator;
import lsfusion.server.data.where.DataWhere;
import lsfusion.server.data.where.Where;

// упрощенный Where
public class PackClassWhere extends DataWhere {

    ClassExprWhere packWhere;

    public PackClassWhere(ClassExprWhere packWhere) {
        this.packWhere = packWhere;

        assert !packWhere.isFalse();
        assert !packWhere.isTrue();
    }

    protected ImSet<NullableExprInterface> getExprFollows() {
        return packWhere.getExprFollows();
    }

    public ImSet<OuterContext> calculateOuterDepends() {
        return SetFact.EMPTY();
    }

    protected void fillDataJoinWheres(MMap<JoinData, Where> joins, Where andWhere) {
        throw new RuntimeException("Not supported");
    }

    public int hash(HashContext hashContext) {
        return System.identityHashCode(this);
    }

    public boolean calcTwins(TwinImmutableObject obj) {
        return false;
    }

    public String getSource(CompileSource compile) {
        if(compile instanceof ToString)
            return packWhere.toString();

        throw new RuntimeException("Not supported");
    }

    @Override
    public String toString() {
        return packWhere.toString();
    }

    protected Where translate(MapTranslate translator) {
        throw new RuntimeException("Not supported");
    }
    public Where translate(ExprTranslator translator) {
        throw new RuntimeException("Not supported");
    }

    public <K extends BaseExpr> GroupJoinsWheres groupJoinsWheres(ImSet<K> keepStat, StatType statType, KeyStat keyStat, ImOrderSet<Expr> orderTop, GroupJoinsWheres.Type type) {
        throw new RuntimeException("Not supported");
    }

    @Override
    protected UpWhere getUpWhere() {
        throw new UnsupportedOperationException();
    }

    public ClassExprWhere calculateClassWhere() {
        return packWhere;
    }

    @Override
    public boolean isClassWhere() {
        return true;
    }
}
