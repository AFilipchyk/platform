package lsfusion.server.logics.property;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImCol;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.base.col.interfaces.mutable.add.MAddSet;
import lsfusion.base.identity.IdentityObject;
import lsfusion.server.base.caches.LazyInit;
import lsfusion.server.logics.action.ExecutionContext;
import lsfusion.server.logics.action.session.changed.OldProperty;
import lsfusion.server.logics.classes.sets.AndClassSet;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.PullExpr;
import lsfusion.server.data.expr.query.GroupType;
import lsfusion.server.data.where.Where;
import lsfusion.server.data.where.WhereBuilder;
import lsfusion.server.data.where.classes.ClassWhere;
import lsfusion.server.data.ObjectValue;
import lsfusion.server.logics.property.cases.CalcCase;
import lsfusion.server.logics.property.cases.graph.Graph;
import lsfusion.server.logics.property.data.DataProperty;
import lsfusion.server.logics.property.infer.ExClassSet;
import lsfusion.server.logics.property.infer.InferType;
import lsfusion.server.logics.property.infer.Inferred;
import lsfusion.server.logics.action.session.change.DataChanges;
import lsfusion.server.logics.action.session.change.modifier.Modifier;
import lsfusion.server.logics.action.session.change.PropertyChange;
import lsfusion.server.logics.action.session.change.PropertyChanges;

import java.sql.SQLException;

public class PropertyInterface<P extends PropertyInterface<P>> extends IdentityObject implements CalcPropertyInterfaceImplement<P>, Comparable<P> {

    public PropertyInterface() {
        this(-1);
    }

    public PropertyInterface(int ID) {
        super(ID, "PropInt" + ID);
    }

    public static <T, P extends PropertyInterface> ImRevMap<T, P> getIdentityMap(ImMap<T, CalcPropertyInterfaceImplement<P>> mapping) {
        MAddSet<PropertyInterface> checked = SetFact.mAddSet();
        for(CalcPropertyInterfaceImplement<P> propImplement : mapping.valueIt())
            if(!(propImplement instanceof PropertyInterface && !checked.add((PropertyInterface) propImplement)))
                return null;
        return BaseUtils.immutableCast(mapping.toRevExclMap());
    }

    public String toString() {
        return "I/"+ID;
    }

    public Expr mapExpr(ImMap<P, ? extends Expr> joinImplement, PropertyChanges changes, WhereBuilder changedWhere) {
        return mapExpr(joinImplement);
    }

    public Expr mapExpr(ImMap<P, ? extends Expr> joinImplement, CalcType calcType, PropertyChanges changes, WhereBuilder changedWhere) {
        return mapExpr(joinImplement);
    }

    public Expr mapExpr(ImMap<P, ? extends Expr> joinImplement, PropertyChanges propChanges) {
        return mapExpr(joinImplement);
    }

    public Expr mapExpr(ImMap<P, ? extends Expr> joinImplement, Modifier modifier) {
        return mapExpr(joinImplement);
    }

    public Expr mapExpr(ImMap<P, ? extends Expr> joinImplement) {
        return joinImplement.get((P) this);
    }

    public Object read(ExecutionContext context, ImMap<P, ? extends ObjectValue> interfaceValues) throws SQLException {
        return interfaceValues.get((P) this).getValue();
    }

    public ObjectValue readClasses(ExecutionContext context, ImMap<P, ? extends ObjectValue> interfaceValues) throws SQLException {
        return interfaceValues.get((P) this);
    }

    public void mapFillDepends(MSet<CalcProperty> depends) {
    }

    public ImSet<OldProperty> mapOldDepends() {
        return SetFact.EMPTY();
    }

    public int compareTo(P o) {
        return ID-o.ID;
    }

    // для того чтобы "попробовать" изменения (на самом деле для кэша)
    @LazyInit
    public Expr getChangeExpr() {
        if(changeExpr==null)
            changeExpr = new PullExpr(ID);
        return changeExpr;
    }

    public Expr changeExpr;

    public DataChanges mapJoinDataChanges(ImMap<P, ? extends Expr> mapKeys, Expr expr, Where where, GroupType type, WhereBuilder changedWhere, PropertyChanges propChanges) {
        return DataChanges.EMPTY;
    }

    public void fill(MSet<P> interfaces, MSet<CalcPropertyMapImplement<?, P>> properties) {
        interfaces.add((P) this);
    }

    public ImCol<P> getInterfaces() {
        return SetFact.singleton((P) this);
    }

    public <K extends PropertyInterface> CalcPropertyInterfaceImplement<K> map(ImRevMap<P, K> remap) {
        return remap.get((P)this);
    }

    public ActionPropertyMapImplement<?, P> mapEditAction(String editActionSID, CalcProperty filterProperty) {
        return null;
    }

    public Inferred<P> mapInferInterfaceClasses(ExClassSet commonValue, InferType inferType) {
        return new Inferred<>((P) this, commonValue);
    }
    public ExClassSet mapInferValueClass(ImMap<P, ExClassSet> inferred, InferType inferType) {
        return inferred.get((P)this);
    }

    public AndClassSet mapValueClassSet(ClassWhere<P> interfaceClasses) {
        return interfaceClasses.getCommonClass((P)this);
    }

    public ImSet<DataProperty> mapChangeProps() {
        return SetFact.EMPTY();
    }

    public boolean mapHasAlotKeys() {
        return true;
    }

    public int mapEstComplexity() {
        return 0;
    }

    public boolean mapIsComplex() {
        return false;
    }

    public long mapComplexity() {
        return 0;
    }

    public DataChanges mapJoinDataChanges(PropertyChange<P> change, GroupType type, WhereBuilder changedWhere, PropertyChanges propChanges) {
        return DataChanges.EMPTY;
    }

    public Graph<CalcCase<P>> mapAbstractGraph() {
        return null;
    }

    public boolean equalsMap(PropertyInterfaceImplement<P> object) {
        return equals(object);
    }

    public int hashMap() {
        return hashCode();
    }

    public boolean mapIsFull(ImSet<P> interfaces) {
        return false;
    }
}
