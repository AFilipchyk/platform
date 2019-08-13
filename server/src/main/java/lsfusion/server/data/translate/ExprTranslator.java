package lsfusion.server.data.translate;

import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.base.mutability.TwinImmutableObject;
import lsfusion.server.data.SourceJoin;
import lsfusion.server.data.expr.Expr;

public abstract class ExprTranslator extends TwinImmutableObject {

    private GetValue<SourceJoin, SourceJoin> trans;
    private <V extends SourceJoin> GetValue<V, V> TRANS() {
        if(trans==null) {
            trans = value -> value.translateExpr(ExprTranslator.this);
        }
        return (GetValue<V, V>)trans;
    }

    public <T extends SourceJoin<T>> T translate(T expr) {
        return null;
    }

    public <K> ImMap<K, Expr> translate(ImMap<K, ? extends Expr> map) {
        return ((ImMap<K, Expr>)map).mapValues(this.<Expr>TRANS());
    }

    public <K> ImOrderMap<Expr, K> translate(ImOrderMap<? extends Expr, K> map) {
        return ((ImOrderMap<Expr, K>)map).mapMergeOrderKeys(this.<Expr>TRANS());
    }

    public ImList<Expr> translate(ImList<? extends Expr> list) {
        return ((ImList<Expr>)list).mapListValues(this.<Expr>TRANS());
    }

    public ImSet<Expr> translate(ImSet<? extends Expr> set) {
        return ((ImSet<Expr>)set).mapSetValues(this.<Expr>TRANS());
    }

}
