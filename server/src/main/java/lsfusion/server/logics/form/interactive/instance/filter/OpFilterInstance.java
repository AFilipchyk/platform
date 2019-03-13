package lsfusion.server.logics.form.interactive.instance.filter;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.form.interactive.change.ChangedData;
import lsfusion.server.logics.form.interactive.change.ReallyChanged;
import lsfusion.server.logics.form.interactive.instance.FormInstance;
import lsfusion.server.logics.form.interactive.instance.object.CustomObjectInstance;
import lsfusion.server.logics.form.interactive.instance.object.GroupObjectInstance;
import lsfusion.server.logics.form.interactive.instance.object.ObjectInstance;
import lsfusion.server.logics.property.CalcProperty;
import lsfusion.server.logics.property.CalcPropertyValueImplement;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.action.session.ExecutionEnvironment;
import lsfusion.server.logics.action.session.Modifier;

import java.io.DataInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

public abstract class OpFilterInstance extends FilterInstance {

    public FilterInstance op1;
    public FilterInstance op2;

    public OpFilterInstance(FilterInstance op1, FilterInstance op2) {
        this.op1 = op1;
        this.op2 = op2;
    }

    protected OpFilterInstance(DataInputStream inStream, FormInstance form) throws IOException, SQLException, SQLHandledException {
        super(inStream, form);
        op1 = deserialize(inStream, form);
        op2 = deserialize(inStream, form);
    }

    public boolean classUpdated(ImSet<GroupObjectInstance> gridGroups) {
        return op1.classUpdated(gridGroups) || op2.classUpdated(gridGroups);
    }

    public boolean objectUpdated(ImSet<GroupObjectInstance> gridGroups) {
        return op1.objectUpdated(gridGroups) || op2.objectUpdated(gridGroups);
    }

    public boolean dataUpdated(ChangedData changedProps, ReallyChanged reallyChanged, Modifier modifier, boolean hidden, ImSet<GroupObjectInstance> groupObjects) throws SQLException, SQLHandledException {
        return op1.dataUpdated(changedProps, reallyChanged, modifier, hidden, groupObjects) || op2.dataUpdated(changedProps, reallyChanged, modifier, hidden, groupObjects);
    }

    public void fillProperties(MSet<CalcProperty> properties) {
        op1.fillProperties(properties);
        op2.fillProperties(properties);
    }

    public GroupObjectInstance getApplyObject() {
        GroupObjectInstance apply1 = op1.getApplyObject();
        GroupObjectInstance apply2 = op2.getApplyObject();
        if(apply2==null || (apply1!=null && apply1.order>apply2.order))
            return apply1;
        else
            return apply2;
    }

    @Override
    public void resolveAdd(ExecutionEnvironment env, CustomObjectInstance object, DataObject addObject, ExecutionStack stack) throws SQLException, SQLHandledException {
        op1.resolveAdd(env, object, addObject, stack);
        op2.resolveAdd(env, object, addObject, stack);
    }

    @Override
    public <X extends PropertyInterface> Set<CalcPropertyValueImplement<?>> getResolveChangeProperties(CalcProperty<X> toChange) {
        return BaseUtils.mergeSet(op1.getResolveChangeProperties(toChange), op2.getResolveChangeProperties(toChange));
    }

    protected void fillObjects(MSet<ObjectInstance> objects) {
        op1.fillObjects(objects);
        op2.fillObjects(objects);
    }
}
