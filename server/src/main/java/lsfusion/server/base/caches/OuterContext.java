package lsfusion.server.base.caches;

import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.base.caches.hash.HashContext;
import lsfusion.server.data.Value;
import lsfusion.server.data.expr.ParamExpr;
import lsfusion.server.data.expr.StaticValueExpr;
import lsfusion.server.data.pack.PackInterface;
import lsfusion.server.data.query.ExprEnumerator;
import lsfusion.server.data.translator.MapTranslate;

public interface OuterContext<T extends OuterContext<T>> extends PackInterface<T>, TranslateContext<T> {

    ImSet<ParamExpr> getOuterKeys();

    ImSet<Value> getOuterValues();

    ImSet<StaticValueExpr> getOuterStaticValues();

    int hashOuter(HashContext hashContext);

    ImSet<OuterContext> getOuterDepends();

    T translateOuter(MapTranslate translator);

    boolean enumerate(ExprEnumerator enumerator);
}
