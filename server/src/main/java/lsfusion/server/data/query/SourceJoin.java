package lsfusion.server.data.query;

import lsfusion.base.col.interfaces.mutable.MMap;
import lsfusion.server.caches.OuterContext;
import lsfusion.server.data.translator.ExprTranslator;
import lsfusion.server.data.where.Where;

public interface SourceJoin<T extends SourceJoin<T>> extends OuterContext<T>, AndContext<T> {

    T translateExpr(ExprTranslator translator);

    //    void fillJoins(List<? extends JoinSelect> Joins);
    void fillJoinWheres(MMap<JoinData, Where> joins, Where andWhere);

    boolean hasUnionExpr();

    boolean needMaterialize();

}
