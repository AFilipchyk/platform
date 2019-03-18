package lsfusion.server.logics.property;

import lsfusion.base.BaseUtils;
import lsfusion.base.lambda.set.SFunctionSet;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImCol;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.server.logics.action.ExecutionContext;
import lsfusion.server.logics.action.ExecutionEnvironment;
import lsfusion.server.logics.action.session.change.DataChanges;
import lsfusion.server.logics.action.session.change.PropertyChange;
import lsfusion.server.logics.action.session.change.PropertyChanges;
import lsfusion.server.logics.action.session.change.modifier.Modifier;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.sets.AndClassSet;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.query.GroupType;
import lsfusion.server.data.where.Where;
import lsfusion.server.data.where.WhereBuilder;
import lsfusion.server.data.where.classes.ClassWhere;
import lsfusion.server.logics.form.struct.property.CalcPropertyObjectEntity;
import lsfusion.server.logics.form.struct.object.ObjectEntity;
import lsfusion.server.logics.form.interactive.instance.property.CalcPropertyObjectInstance;
import lsfusion.server.logics.form.interactive.instance.property.PropertyObjectInterfaceInstance;
import lsfusion.server.data.DataObject;
import lsfusion.server.data.ObjectValue;
import lsfusion.server.logics.property.cases.CalcCase;
import lsfusion.server.logics.property.cases.CaseUnionProperty;
import lsfusion.server.logics.property.cases.graph.Graph;
import lsfusion.server.logics.property.data.DataProperty;
import lsfusion.server.logics.property.derived.DerivedProperty;
import lsfusion.server.logics.property.infer.*;
import lsfusion.server.session.*;

import java.sql.SQLException;

public class CalcPropertyMapImplement<P extends PropertyInterface, T extends PropertyInterface> extends CalcPropertyRevImplement<P, T> implements CalcPropertyInterfaceImplement<T> {

    public CalcPropertyMapImplement(CalcProperty<P> property) {
        super(property, MapFact.<P, T>EMPTYREV());
    }
    
    public CalcPropertyMapImplement(CalcProperty<P> property, ImRevMap<P, T> mapping) {
        super(property, mapping);
    }

    public DataChanges mapJoinDataChanges(PropertyChange<T> change, GroupType type, WhereBuilder changedWhere, PropertyChanges propChanges) {
        ImMap<T, Expr> mapExprs = change.getMapExprs();
        if(mapExprs.size() == mapping.size()) // optimization
            return property.getDataChanges(change.mapChange(mapping), propChanges, changedWhere);
        return property.getJoinDataChanges(mapping.join(mapExprs), change.expr, change.where, type, propChanges, changedWhere);
    }

    public CalcPropertyMapImplement<P, T> mapOld(PrevScope event) {
        return new CalcPropertyMapImplement<>(property.getOld(event), mapping);
    }

    public CalcPropertyMapImplement<P, T> mapChanged(IncrementType type, PrevScope scope) {
        return new CalcPropertyMapImplement<>(property.getChanged(type, scope), mapping);
    }

    public CalcPropertyValueImplement<P> mapValues(ImMap<T, DataObject> mapValues) {
        return new CalcPropertyValueImplement<>(property, mapping.join(mapValues));
    }

    public CalcPropertyValueImplement<P> mapObjectValues(ImMap<T, ? extends ObjectValue> mapValues) {
        ImMap<P, ? extends ObjectValue> mapped = mapping.join(mapValues);
        ImMap<P, DataObject> mappedData = DataObject.filterDataObjects(mapped);
        if(mappedData.size() < mapped.size())
            return null;
        return new CalcPropertyValueImplement<>(property, mappedData);
    }

    public void change(ImMap<T, DataObject> keys, ExecutionEnvironment env, Object value) throws SQLException, SQLHandledException {
        change(keys, env, env.getSession().getObjectValue(property.getValueClass(ClassType.editValuePolicy), value));
    }

    public <K extends PropertyInterface> CalcPropertyMapImplement<P, K> map(ImRevMap<T, K> remap) {
        return new CalcPropertyMapImplement<>(property, mapping.join(remap));
    }

    public void change(ImMap<T, DataObject> keys, ExecutionEnvironment env, ObjectValue objectValue) throws SQLException, SQLHandledException {
        env.change(property, mapValues(keys).getPropertyChange(objectValue.getExpr()));
    }

    public void change(ExecutionEnvironment env, PropertyChange<T> change) throws SQLException, SQLHandledException {
        env.change(property, change.mapChange(mapping));
    }

    public ImMap<T,ValueClass> mapInterfaceClasses(ClassType type) {
        return mapInterfaceClasses(type, null);
    }

    public ImMap<T,ValueClass> mapInterfaceClasses(ClassType type, ExClassSet valueClasses) {
        return mapping.rightCrossJoin(property.getInterfaceClasses(type, valueClasses));
    }

    public ClassWhere<T> mapClassWhere(ClassType type) {
        return new ClassWhere<>(property.getClassWhere(type), mapping);
    }

    public boolean mapIsInInterface(ImMap<T, ? extends AndClassSet> classes, boolean isAny) {
        return property.isInInterface(mapping.join(classes), isAny);
    }

    public ImMap<T, ValueClass> mapGetInterfaceClasses(ClassType classType) {
        return mapping.rightCrossJoin(property.getInterfaceClasses(classType));
    }

    public boolean mapIsNotNull(ImSet<T> interfaces) {
        if(interfaces.isEmpty()) // оптимизация
            return true;

        ImSet<P> checkInterfaces = mapping.filterValues(interfaces).keys();

        // если все собрали интерфейсы
        return checkInterfaces.size() >= interfaces.size() && property.isNotNull(checkInterfaces, AlgType.actionType);
    }

    public boolean mapIsFull(ImSet<T> interfaces) {
        if(interfaces.isEmpty()) // оптимизация
            return true;

        ImSet<P> checkInterfaces = mapping.filterValues(interfaces).keys();

        // если все собрали интерфейсы
        return checkInterfaces.size() >= interfaces.size() && property.isFull(checkInterfaces, AlgType.actionType);
    }

    public Expr mapExpr(ImMap<T, ? extends Expr> joinImplement, Modifier modifier) throws SQLException, SQLHandledException {
        return property.getExpr(mapping.join(joinImplement), modifier);
    }
    public Expr mapExpr(ImMap<T, ? extends Expr> joinImplement, PropertyChanges propChanges) {
        return property.getExpr(mapping.join(joinImplement), propChanges);
    }

    public Expr mapExpr(ImMap<T, ? extends Expr> joinImplement) {
        return property.getExpr(mapping.join(joinImplement));
    }

    public void mapFillDepends(MSet<CalcProperty> depends) {
        depends.add(property);
    }

    public ImSet<OldProperty> mapOldDepends() {
        return property.getOldDepends();
    }

    public Object read(ExecutionContext context, ImMap<T, ? extends ObjectValue> interfaceValues) throws SQLException, SQLHandledException {
        return property.read(context.getSession().sql, mapping.join(interfaceValues), context.getModifier(), context.getQueryEnv());
    }

    public ObjectValue readClasses(ExecutionContext context, ImMap<T, ? extends ObjectValue> interfaceValues) throws SQLException, SQLHandledException {
        return property.readClasses(context.getSession(), mapping.join(interfaceValues), context.getModifier(), context.getQueryEnv());
    }

    @Override
    public boolean mapHasAlotKeys() {
        return property instanceof AggregateProperty && ((AggregateProperty) property).hasAlotKeys();
    }

    @Override
    public int mapEstComplexity() {
        return property.getEstComplexity();
    }

    public ImSet<DataProperty> mapChangeProps() {
        return property.getChangeProps();
    }

    public boolean mapIsComplex() {
        return property.isComplex();
    }
    public long mapComplexity() {
        return property.getComplexity();
    }

    public DataChanges mapJoinDataChanges(ImMap<T, ? extends Expr> mapKeys, Expr expr, Where where, GroupType type, WhereBuilder changedWhere, PropertyChanges propChanges) {
        return property.getJoinDataChanges(mapping.join(mapKeys), expr, where, type, propChanges, changedWhere);
    }

    public void fill(MSet<T> interfaces, MSet<CalcPropertyMapImplement<?, T>> properties) {
        properties.add(this);
    }

    public ImSet<T> getInterfaces() {
        return mapping.valuesSet();
    }

    @Override
    public ActionPropertyMapImplement<?, T> mapEditAction(String editActionSID, CalcProperty filterProperty) {
        ActionPropertyMapImplement<?, P> editAction = property.getEditAction(editActionSID, filterProperty);
        return editAction == null ? null : editAction.map(mapping);
    }

    public Inferred<T> mapInferInterfaceClasses(ExClassSet commonValue, InferType inferType) {
        return property.inferInterfaceClasses(commonValue, inferType).map(mapping);
    }
    public ExClassSet mapInferValueClass(ImMap<T, ExClassSet> inferred, InferType inferType) {
        return property.inferValueClass(mapping.join(inferred), inferType);
    }

    public AndClassSet mapValueClassSet(ClassWhere<T> interfaceClasses) {
        return property.getValueClassSet();
    }

    public CalcPropertyObjectInstance<P> mapObjects(ImMap<T, ? extends PropertyObjectInterfaceInstance> mapObjects) {
        return new CalcPropertyObjectInstance<>(property, mapping.join(mapObjects));
    }

    public CalcPropertyObjectEntity<P> mapEntityObjects(ImRevMap<T, ObjectEntity> mapObjects) {
        return new CalcPropertyObjectEntity<>(property, mapping.join(mapObjects));
    }

    public <I extends PropertyInterface> void mapCheckExclusiveness(String caseInfo, CalcPropertyMapImplement<I, T> implement, String implementCaption, String abstractInfo) {
        property.checkExclusiveness(caseInfo, implement.property, implementCaption, implement.mapping.rightCrossValuesRev(mapping), abstractInfo);
    }

    public ActionPropertyMapImplement<?, T> getSetNotNullAction(boolean notNull) {
        ActionPropertyMapImplement<?, P> action = property.getSetNotNullAction(notNull);
        if(action!=null)
            return action.map(mapping);
        return null;
    }
    
    public static <T extends PropertyInterface> ImCol<CalcPropertyMapImplement<?, T>> filter(ImCol<CalcPropertyInterfaceImplement<T>> col) {
        return BaseUtils.immutableCast(col.filterCol(new SFunctionSet<CalcPropertyInterfaceImplement<T>>() {
            public boolean contains(CalcPropertyInterfaceImplement<T> element) {
                return element instanceof CalcPropertyMapImplement;
            }}));
    }

    public <L> CalcPropertyImplement<P, L> mapImplement(ImMap<T, L> mapImplement) {
        return new CalcPropertyImplement<>(property, mapping.join(mapImplement));
    }

    public <L> CalcPropertyRevImplement<P, L> mapRevImplement(ImRevMap<T, L> mapImplement) {
        return new CalcPropertyRevImplement<>(property, mapping.join(mapImplement));
    }

    public CalcPropertyMapImplement<?, T> mapClassProperty() {
        return property.getClassProperty().mapPropertyImplement(mapping);
    }

    // временно
    public CalcPropertyMapImplement<?, T> cloneProp() {
        return DerivedProperty.createJoin(new CalcPropertyImplement<>(property, BaseUtils.<ImMap<P, CalcPropertyInterfaceImplement<T>>>immutableCast(mapping)));
    }

    public Graph<CalcCase<T>> mapAbstractGraph() {
        if(property instanceof CaseUnionProperty) {
            Graph<CalcCase<UnionProperty.Interface>> absGraph = ((CaseUnionProperty) property).abstractGraph;
            if(absGraph != null)
                return absGraph.map(new GetValue<CalcCase<T>, CalcCase<UnionProperty.Interface>>() {
                    public CalcCase<T> getMapValue(CalcCase<UnionProperty.Interface> value) {
                        return value.map((ImRevMap<UnionProperty.Interface, T>) mapping);
                    }
                });
        }
        return null;
    }
    
    public boolean equalsMap(PropertyInterfaceImplement<T> object) {
        if(!(object instanceof CalcPropertyMapImplement))
            return false;

        CalcPropertyMapImplement<?, T> mapProp = (CalcPropertyMapImplement<?, T>) object;
        return property.equals(mapProp.property) && mapping.equals(mapProp.mapping);
    }

    public int hashMap() {
        return 31 * property.hashCode() + mapping.hashCode();
    }
}
