package platform.server.logics.property.actions;

import platform.interop.ClassViewType;
import platform.server.classes.BaseClass;
import platform.server.classes.ValueClass;
import platform.server.form.instance.*;
import platform.server.logics.ObjectValue;
import platform.server.logics.property.ExecutionContext;
import platform.server.logics.property.CalcProperty;

import java.sql.SQLException;

public class ChangeObjectActionProperty extends CustomActionProperty {

    private final CalcProperty filterProperty;

    public ChangeObjectActionProperty(CalcProperty filterProperty, ValueClass baseClass) {
        super("CO_"+filterProperty, baseClass);
        this.filterProperty = filterProperty;
    }

    @Override
    public void execute(ExecutionContext context) throws SQLException {

        final FormInstance<?> formInstance = context.getFormInstance();
        PropertyObjectInterfaceInstance singleObjectInstance = context.getSingleObjectInstance();

        if(singleObjectInstance instanceof ObjectInstance) {
            ObjectInstance objectInstance = (ObjectInstance) singleObjectInstance;
            if(objectInstance.groupTo.curClassView == ClassViewType.PANEL) { // в grid'е диалог не имеет смысла
                final Object oldValue = objectInstance.getObjectValue().getValue();
                ObjectValue changeValue = null;
                if(objectInstance instanceof CustomObjectInstance) {
                    final CustomObjectInstance customObjectInstance = (CustomObjectInstance) objectInstance;
                    changeValue = context.requestUserObject(new ExecutionContext.RequestDialog() {
                        public DialogInstance createDialog() throws SQLException {
                            return formInstance.createChangeObjectDialog(customObjectInstance.getBaseClass(), oldValue, customObjectInstance.groupTo, filterProperty);
                        }
                    });
                } else
                    changeValue = context.requestUserData(((DataObjectInstance) objectInstance).getBaseClass(), oldValue);
                if(changeValue!=null)
                    formInstance.changeObject(objectInstance, changeValue, context.getActions());
            }
        }
    }
}
