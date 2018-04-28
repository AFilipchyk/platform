package lsfusion.server.logics.property;

import lsfusion.server.classes.ObjectValueClassSet;
import lsfusion.server.data.Table;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.logics.table.ImplementTable;

public interface ObjectClassField extends IsClassField {

    Expr getInconsistentExpr(Expr expr);

    Expr getStoredExpr(Expr expr);

    ObjectValueClassSet getObjectSet();

    ImplementTable getTable();

    ClassDataProperty getProperty();
}
