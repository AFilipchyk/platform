package lsfusion.server.form.entity.filter;

import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.interop.Compare;
import lsfusion.server.classes.DataClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.where.Where;
import lsfusion.server.form.entity.CalcPropertyObjectEntity;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.form.entity.OrderEntity;
import lsfusion.server.form.instance.InstanceFactory;
import lsfusion.server.form.instance.filter.FilterInstance;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.serialization.ServerSerializationPool;
import lsfusion.server.session.Modifier;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

public class CompareFilterEntity<P extends PropertyInterface> extends PropertyFilterEntity<P> {

    public Compare compare;

    public OrderEntity<?> value;

    // нельзя удалять - используется при сериализации
    public CompareFilterEntity() {
    }

    public CompareFilterEntity(CalcPropertyObjectEntity<P> property, Compare compare, OrderEntity<?> value) {
        this(property, compare, value, true);
    }

    public CompareFilterEntity(CalcPropertyObjectEntity<P> property, Compare compare, Object value) {
        this(property, compare, new DataObject(value, (DataClass) property.property.getType()));
    }

    public CompareFilterEntity(CalcPropertyObjectEntity<P> property, Compare compare, OrderEntity<?> value, boolean resolveAdd) {
        super(property, resolveAdd);
        this.value = value;
        this.compare = compare;
    }

    public FilterInstance getInstance(InstanceFactory instanceFactory) {
         return instanceFactory.getInstance(this);
    }

    @Override
    protected void fillObjects(Set<ObjectEntity> objects) {
        super.fillObjects(objects);
        value.fillObjects(objects);
    }

    @Override
    public FilterEntity getRemappedFilter(ObjectEntity oldObject, ObjectEntity newObject, InstanceFactory instanceFactory) {
        return new CompareFilterEntity<>(property.getRemappedEntity(oldObject, newObject, instanceFactory), compare, value.getRemappedEntity(oldObject, newObject, instanceFactory), resolveAdd);
    }

    @Override
    public Where getWhere(ImMap<ObjectEntity, ? extends Expr> mapKeys, ImMap<ObjectEntity, ObjectValue> mapObjects, Modifier modifier) throws SQLException, SQLHandledException {
        return property.getExpr(mapKeys, modifier, mapObjects).compare(value.getExpr(mapKeys, modifier, mapObjects), compare);
    }
}
