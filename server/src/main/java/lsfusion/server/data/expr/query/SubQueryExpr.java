package lsfusion.server.data.expr.query;

import lsfusion.base.BaseUtils;
import lsfusion.base.Result;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetKeyValue;
import lsfusion.base.lambda.set.SFunctionSet;
import lsfusion.server.base.caches.IdentityInstanceLazy;
import lsfusion.server.data.expr.*;
import lsfusion.server.data.expr.inner.InnerExpr;
import lsfusion.server.data.expr.join.query.SubQueryJoin;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.expr.value.ValueExpr;
import lsfusion.server.data.expr.where.pull.ExprPullWheres;
import lsfusion.server.data.query.compile.CompileSource;
import lsfusion.server.data.translator.ExprTranslator;
import lsfusion.server.data.translator.MapTranslate;
import lsfusion.server.data.translator.PartialKeyExprTranslator;
import lsfusion.server.data.type.Type;
import lsfusion.server.data.where.Where;

public class SubQueryExpr extends QueryExpr<KeyExpr, Expr, SubQueryJoin, SubQueryExpr, SubQueryExpr.QueryInnerContext> {

    public SubQueryExpr(Expr query, ImMap<KeyExpr, BaseExpr> group) {
        super(query, group);
    }

    public static class QueryInnerContext extends QueryExpr.QueryInnerContext<KeyExpr, Expr, SubQueryJoin, SubQueryExpr, QueryInnerContext> {
        public QueryInnerContext(SubQueryExpr thisObj) {
            super(thisObj);
        }

        public Type getType() {
            return thisObj.query.getType(thisObj.query.getWhere());
        }

        protected Expr getMainExpr() {
            return thisObj.query;
        }

        protected Where getFullWhere() {
            return thisObj.query.getWhere();
        }

        protected boolean isSelect() {
            return true;
        }
    }
    protected QueryInnerContext createInnerContext() {
        return new QueryInnerContext(this);
    }

    protected SubQueryExpr createThis(Expr query, ImMap<KeyExpr, BaseExpr> group) {
        return new SubQueryExpr(query, group);
    }

    @IdentityInstanceLazy
    public SubQueryJoin getInnerJoin() {
        return new SubQueryJoin(getInner().getQueryKeys(), getInner().getInnerValues(), getInner().getInnerFollows(), getInner().getFullWhere(), group);
    }

    public SubQueryExpr(SubQueryExpr expr, MapTranslate translator) {
        super(expr, translator);
    }

    protected InnerExpr translate(MapTranslate translator) {
        return new SubQueryExpr(this, translator);
    }

    public class NotNull extends QueryExpr.NotNull {
    }

    public NotNull calculateNotNullWhere() {
        return new NotNull();
    }

    public Expr translate(ExprTranslator translator) {
        return create(query, translator.translate(group));
    }

    public String getSource(CompileSource compile, boolean needValue) {
        return compile.getSource(this, needValue);
    }

    @Override
    public Expr packFollowFalse(Where falseWhere) {
        ImMap<KeyExpr, Expr> packedGroup = packPushFollowFalse(group, falseWhere);
        Expr packedQuery = query.pack();
        if(!(BaseUtils.hashEquals(packedQuery, query) && BaseUtils.hashEquals(packedGroup,group)))
            return create(packedQuery, packedGroup);
        else
            return this;
    }

    public static Expr create(Expr expr) {
        return create(expr, BaseUtils.<ImMap<KeyExpr, BaseExpr>>immutableCast(expr.getOuterKeys().toMap()), null);
    }

    public static Where create(Where where) {
        return create(ValueExpr.get(where)).getWhere();
    }

    public static Expr create(final Expr expr, final ImMap<KeyExpr, ? extends Expr> group, final PullExpr noPull) {
        ImMap<KeyExpr, KeyExpr> pullKeys = BaseUtils.<ImSet<KeyExpr>>immutableCast(getOuterKeys(expr)).filterFn(new SFunctionSet<KeyExpr>() {
            public boolean contains(KeyExpr key) {
                return key instanceof PullExpr && !group.containsKey(key) && !key.equals(noPull);
            }}).toMap();
        return create(expr, MapFact.addExcl(group, pullKeys));
    }

    public static Expr create(final Expr expr, ImMap<KeyExpr, ? extends Expr> group) {
        return new ExprPullWheres<KeyExpr>() {
            protected Expr proceedBase(ImMap<KeyExpr, BaseExpr> map) {
                return createBase(expr, map);
            }
        }.proceed(group);
    }

    public static Expr createBase(Expr expr, ImMap<KeyExpr, BaseExpr> group) {
        Result<ImMap<KeyExpr, BaseExpr>> restGroup = new Result<>();
        ImMap<KeyExpr, BaseExpr> translate = group.splitKeys(new GetKeyValue<Boolean, KeyExpr, BaseExpr>() {
            public Boolean getMapValue(KeyExpr key, BaseExpr value) {
                return value.isValue();
            }
        }, restGroup);

        if(translate.size()>0) {
            ExprTranslator translator = new PartialKeyExprTranslator(translate, true);
            expr = expr.translateExpr(translator);
        }

        return BaseExpr.create(new SubQueryExpr(expr, restGroup.result));
    }

    @Override
    public String toString() {
        return "SUBQUERY(" + query + "," + group + ")";
    }
}
