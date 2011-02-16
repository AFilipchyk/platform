package platform.server.logics;

import platform.base.BaseUtils;
import platform.base.TwinImmutableInterface;
import platform.interop.Compare;
import platform.server.caches.hash.HashValues;
import platform.server.classes.*;
import platform.server.classes.sets.AndClassSet;
import platform.server.data.Value;
import platform.server.data.expr.Expr;
import platform.server.data.expr.SystemValueExpr;
import platform.server.data.expr.ValueExpr;
import platform.server.data.sql.SQLSyntax;
import platform.server.data.translator.MapValuesTranslate;
import platform.server.data.type.Type;
import platform.server.data.type.TypeSerializer;
import platform.server.data.where.Where;
import platform.server.form.entity.ObjectEntity;
import platform.server.form.entity.PropertyObjectInterfaceEntity;
import platform.server.form.instance.GroupObjectInstance;
import platform.server.form.instance.InstanceFactory;
import platform.server.form.instance.ObjectInstance;
import platform.server.form.instance.PropertyObjectInterfaceInstance;
import platform.server.integration.*;
import platform.server.serialization.ServerSerializationPool;
import platform.server.session.SessionChanges;
import platform.server.session.SinglePropertyTableUsage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class DataObject extends ObjectValue<DataObject> implements PropertyObjectInterfaceInstance, PropertyObjectInterfaceEntity, ImportKeyInterface, ImportFieldInterface {

    public Object object;
    public ConcreteClass objectClass;

    @Override
    public String toString() {
        return object + " - " + objectClass;
    }

    public DataObject() {

    }
    
    public DataObject(Object object, ConcreteClass objectClass) {
        this.object = object;

        assert objectClass.getType().read(object).equals(object); // чтобы читалось то что писалось

        this.objectClass = objectClass;
    }

    public DataObject(String string) {
        this(string, StringClass.get(string.length()));
    }

    public DataObject(Double dbl) {
        this(dbl, DoubleClass.instance);
    }

    public DataObject(Boolean bl) {
        this(bl, LogicalClass.instance);
    }

    public DataObject(Integer ig) {
        this(ig, IntegerClass.instance);
    }

    public boolean isString(SQLSyntax syntax) {
        return getType().isSafeString(object);
    }
    public String getString(SQLSyntax syntax) {
        return getType().getString(object, syntax);
    }

    public ValueExpr getExpr() {
        return new ValueExpr(object,objectClass);
    }
    public Expr getSystemExpr() {
        return new SystemValueExpr(object, objectClass);
    }

    public Object getValue() {
        return object;
    }

    public static <K> Map<K,ValueExpr> getMapValueExprs(Map<K,DataObject> map) {
        Map<K,ValueExpr> mapExprs = new HashMap<K,ValueExpr>();
        for(Map.Entry<K,DataObject> keyField : map.entrySet())
            mapExprs.put(keyField.getKey(), keyField.getValue().getExpr());
        return mapExprs;
    }

    public static <K> Map<K,Object> getMapValues(Map<K,DataObject> map) {
        Map<K,Object> mapClasses = new HashMap<K,Object>();
        for(Map.Entry<K,DataObject> keyField : map.entrySet())
            mapClasses.put(keyField.getKey(), keyField.getValue().object);
        return mapClasses;
    }

    public static <K> Map<K,ConcreteClass> getMapClasses(Map<K,DataObject> map) {
        Map<K,ConcreteClass> mapClasses = new HashMap<K,ConcreteClass>();
        for(Map.Entry<K,DataObject> keyField : map.entrySet())
            mapClasses.put(keyField.getKey(), keyField.getValue().objectClass);
        return mapClasses;
    }

    public Where order(Expr expr, boolean desc, Where orderWhere) {
        Where greater = expr.compare(this,Compare.GREATER);
        return (desc?greater.not():greater).or(expr.compare(this,Compare.EQUALS).and(orderWhere));
    }

    public AndClassSet getClassSet(Set<GroupObjectInstance> gridGroups) {
        return objectClass;
    }

    public DataObject getDataObject() {
        return this;
    }

    public ConcreteClass getCurrentClass() {
        return objectClass;
    }

    public GroupObjectInstance getApplyObject() {
        return null;
    }

    public Type getType() {
        return objectClass.getType();
    }

    public PropertyObjectInterfaceInstance getInstance(InstanceFactory instanceFactory) {
        return this;
    }

    public void fillObjects(Set<ObjectEntity> objects) {
    }

    public boolean twins(TwinImmutableInterface o) {
        return getExpr().equals(((DataObject)o).getExpr());
    }

    public int hashValues(HashValues hashValues) {
        return hashValues.hash(getExpr());
    }

    public Set<Value> getValues() {
        return Collections.<Value>singleton(getExpr());
    }

    private DataObject(ValueExpr expr) {
        this(expr.object,expr.objectClass);
    }

    public DataObject translate(MapValuesTranslate mapValues) {
        return new DataObject(mapValues.translate(getExpr()));
    }

    public DataObject refresh(SessionChanges session) throws SQLException {
        return session.getDataObject(object, getType());
    }

    public boolean isNull() {
        return false;
    }

    public void customSerialize(ServerSerializationPool pool, DataOutputStream outStream, String serializationType) throws IOException {
        BaseUtils.serializeObject(outStream, object);

        TypeSerializer.serializeType(outStream, getType());
    }

    public void customDeserialize(ServerSerializationPool pool, DataInputStream inStream) throws IOException {
        object = BaseUtils.deserializeObject(inStream);

        objectClass = pool.context.BL.getDataClass(object, TypeSerializer.deserializeType(inStream));
    }

    public Collection<ObjectInstance> getObjectInstances() {
        return new ArrayList<ObjectInstance>();
    }

    public DataObject getRemappedEntity(ObjectEntity oldObject, ObjectEntity newObject, InstanceFactory instanceFactory) {
        return this;
    }

    public DataObject getDataObject(ImportTable.Row row) {
        return getDataObject();
    }

    public Expr getExpr(Map<ImportField, ? extends Expr> importKeys) {
        return getExpr();
    }

    public Expr getExpr(Map<ImportField, ? extends Expr> importKeys, Map<ImportKey<?>, SinglePropertyTableUsage<?>> addedKeys) {
        return getExpr(importKeys);
    }
}
