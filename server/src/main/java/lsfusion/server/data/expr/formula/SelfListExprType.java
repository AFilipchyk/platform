package lsfusion.server.data.expr.formula;

import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.type.Type;

public class SelfListExprType extends ListExprType {

    public SelfListExprType(ImList<? extends Expr> exprs) {
        super(exprs);
    }

    public Type getType(int i) {
        return exprs.get(i).getSelfType();
    }
}
