package lsfusion.server.physics.admin.service;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DBManager;
import lsfusion.server.logics.ServiceLogicsModule;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;

import java.sql.SQLException;

public class SetReupdateModeActionProperty extends ScriptingActionProperty {

    public SetReupdateModeActionProperty(ServiceLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        Object value = context.getSingleKeyObject();
        DBManager.PROPERTY_REUPDATE = value!=null;
    }

    @Override
    protected boolean allowNulls() {
        return true;
    }

}
