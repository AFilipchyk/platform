package lsfusion.server.data.caches.hash;

import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.data.expr.key.ParamExpr;
import lsfusion.server.data.translator.MapTranslate;

public interface HashKeys {

    int hash(ParamExpr expr);

    boolean isGlobal();

    HashKeys filterKeys(ImSet<ParamExpr> keys);

    HashKeys reverseTranslate(MapTranslate translator, ImSet<ParamExpr> keys);
}
