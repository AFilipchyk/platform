package platform.server.data.expr.query;

import platform.base.QuickSet;
import platform.server.caches.IdentityLazy;
import platform.server.data.Value;
import platform.server.data.expr.BaseExpr;
import platform.server.data.expr.KeyExpr;
import platform.server.data.query.stat.KeyStat;
import platform.server.data.query.stat.StatKeys;
import platform.server.data.translator.MapTranslate;
import platform.server.data.where.Where;

import java.util.Map;
import java.util.Set;

public class SubQueryJoin extends QueryJoin<KeyExpr, Where, SubQueryJoin, SubQueryJoin.QueryOuterContext> {

    public SubQueryJoin(QuickSet<KeyExpr> keys, QuickSet<Value> values, Where inner, Map<KeyExpr, BaseExpr> group) {
        super(keys, values, inner, group);
    }

    public static class QueryOuterContext extends QueryJoin.QueryOuterContext<KeyExpr, Where, SubQueryJoin, SubQueryJoin.QueryOuterContext> {
        public QueryOuterContext(SubQueryJoin thisObj) {
            super(thisObj);
        }

        public SubQueryJoin translateThis(MapTranslate translator) {
            return new SubQueryJoin(thisObj, translator);
        }
    }
    protected QueryOuterContext createOuterContext() {
        return new QueryOuterContext(this);
    }

    protected SubQueryJoin createThis(QuickSet<KeyExpr> keys, QuickSet<Value> values, Where query, Map<KeyExpr, BaseExpr> group) {
        return new SubQueryJoin(keys, values, query, group);
    }

    private SubQueryJoin(SubQueryJoin partitionJoin, MapTranslate translator) {
        super(partitionJoin, translator);
    }

    @IdentityLazy
    public StatKeys<KeyExpr> getStatKeys() {
        return query.getStatKeys(keys);
    }

    // кэшить
    public StatKeys<KeyExpr> getStatKeys(KeyStat keyStat) {
        return getStatKeys();
    }

    public Where getWhere() {
        return query;
    }
}
