package lsfusion.server.logics.property.actions;

import lsfusion.interop.ClassViewType;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.form.instance.*;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.CalcProperty;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;

import java.sql.SQLException;

public class DefaultChangeObjectActionProperty extends SystemExplicitActionProperty {

    private final CalcProperty filterProperty;
    private final ObjectEntity object;
    
    public DefaultChangeObjectActionProperty(CalcProperty filterProperty, ValueClass baseClass, ObjectEntity object) {
        super(LocalizedString.create("CO_" + filterProperty, false), baseClass);
        this.filterProperty = filterProperty;
        this.object = object;
    }

    @Override
    protected boolean isVolatile() {
        return true;
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        final FormInstance<?> formInstance = context.getFormFlowInstance();
        ObjectInstance objectInstance = formInstance.instanceFactory.getInstance(object);
        if (objectInstance.groupTo.curClassView.isPanel()) { // в grid'е диалог не имеет смысла
            final ObjectValue oldValue = objectInstance.getObjectValue();
            ObjectValue changeValue;
            if (objectInstance instanceof CustomObjectInstance) {
                final CustomObjectInstance customObjectInstance = (CustomObjectInstance) objectInstance;
                changeValue = context.requestUserObject(
                        formInstance.createChangeObjectDialogRequest(customObjectInstance.getBaseClass(), oldValue, customObjectInstance.groupTo, filterProperty, context.stack)
                );
            } else {
                changeValue = context.requestUserData(((DataObjectInstance) objectInstance).getBaseClass(), oldValue.getValue());
            }
            if (changeValue != null) {
                formInstance.changeObject(objectInstance, changeValue);
            }
        }
    }
}
