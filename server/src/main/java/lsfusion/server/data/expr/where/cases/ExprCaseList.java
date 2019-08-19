package lsfusion.server.data.expr.where.cases;

import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.data.caches.hash.HashContext;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.translate.MapTranslate;

import java.util.function.Function;

public class ExprCaseList extends CaseList<Expr, Expr, ExprCase> {

    public ExprCaseList(ImList<ExprCase> exprCases) {
        super(exprCases);
    }

    public ExprCaseList(ImSet<ExprCase> exprCases) {
        super(exprCases);
    }

    public int hashOuter(HashContext hashContext) {
        int hash = exclusive ? 1 : 0;
        for(ExprCase exprCase : this) {
            if(!exclusive)
                hash = 31 * hash;
            hash = hash + exprCase.hashOuter(hashContext);
        }
        return hash;
    }

    public long getComplexity(boolean outer) {
        long complexity = 0;
        for(ExprCase exprCase : this)
            complexity += exprCase.getComplexity(outer);
        return complexity;
    }

    public ExprCaseList translateOuter(final MapTranslate translate) {
        Function<ExprCase, ExprCase> transCase = exprCase -> new ExprCase(exprCase.where.translateOuter(translate), exprCase.data.translateOuter(translate));

        if(exclusive)
            return new ExprCaseList(((ImSet<ExprCase>)list).mapSetValues(transCase));
        else
            return new ExprCaseList(((ImList<ExprCase>)list).mapListValues(transCase));
    }

    public <K> MapCaseList<K> mapValues(Function<ExprCase, MapCase<K>> mapValue) {
        if(exclusive)
            return new MapCaseList<>(((ImSet<ExprCase>) list).mapSetValues(mapValue));
        else
            return new MapCaseList<>(((ImList<ExprCase>) list).mapListValues(mapValue));
    }
}
