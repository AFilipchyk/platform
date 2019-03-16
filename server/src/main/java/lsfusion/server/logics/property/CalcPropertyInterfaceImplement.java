package lsfusion.server.logics.property;

import lsfusion.base.col.interfaces.immutable.ImCol;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.server.logics.classes.sets.AndClassSet;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.query.GroupType;
import lsfusion.server.data.where.Where;
import lsfusion.server.data.where.WhereBuilder;
import lsfusion.server.data.where.classes.ClassWhere;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.cases.CalcCase;
import lsfusion.server.logics.property.cases.graph.Graph;
import lsfusion.server.logics.property.infer.ExClassSet;
import lsfusion.server.logics.property.infer.InferType;
import lsfusion.server.logics.property.infer.Inferred;
import lsfusion.server.session.DataChanges;
import lsfusion.server.session.Modifier;
import lsfusion.server.session.PropertyChange;
import lsfusion.server.session.PropertyChanges;

import java.sql.SQLException;

public interface CalcPropertyInterfaceImplement<P extends PropertyInterface> extends PropertyInterfaceImplement<P> {

    <T extends PropertyInterface> CalcPropertyInterfaceImplement<T> map(ImRevMap<P, T> map);

    Expr mapExpr(ImMap<P, ? extends Expr> joinImplement, CalcType calcType, PropertyChanges changes, WhereBuilder changedWhere);
    Expr mapExpr(ImMap<P, ? extends Expr> joinImplement, PropertyChanges changes, WhereBuilder changedWhere);
    Expr mapExpr(ImMap<P, ? extends Expr> joinImplement, PropertyChanges propChanges);
    Expr mapExpr(ImMap<P, ? extends Expr> joinImplement, Modifier modifier) throws SQLException, SQLHandledException;
    Expr mapExpr(ImMap<P, ? extends Expr> joinImplement);

    void mapFillDepends(MSet<CalcProperty> depends);
    ImSet<OldProperty> mapOldDepends();

    Object read(ExecutionContext context, ImMap<P, ? extends ObjectValue> interfaceValues) throws SQLException, SQLHandledException;
    ObjectValue readClasses(ExecutionContext context, ImMap<P, ? extends ObjectValue> interfaceValues) throws SQLException, SQLHandledException;

    ActionPropertyMapImplement<?, P> mapEditAction(String editActionSID, CalcProperty filterProperty);

    boolean mapHasAlotKeys();
    int mapEstComplexity();
    
    ImSet<DataProperty> mapChangeProps();
    boolean mapIsComplex();
    long mapComplexity();
    DataChanges mapJoinDataChanges(ImMap<P, ? extends Expr> mapKeys, Expr expr, Where where, GroupType type, WhereBuilder changedWhere, PropertyChanges propChanges);
    DataChanges mapJoinDataChanges(PropertyChange<P> change, GroupType type, WhereBuilder changedWhere, PropertyChanges propChanges);

    void fill(MSet<P> interfaces, MSet<CalcPropertyMapImplement<?, P>> properties);
    ImCol<P> getInterfaces();

    Inferred<P> mapInferInterfaceClasses(ExClassSet commonValue, InferType inferType);
    ExClassSet mapInferValueClass(ImMap<P, ExClassSet> inferred, InferType inferType);

    boolean mapIsFull(ImSet<P> interfaces);

    AndClassSet mapValueClassSet(ClassWhere<P> interfaceClasses);

    Graph<CalcCase<P>> mapAbstractGraph();
}
