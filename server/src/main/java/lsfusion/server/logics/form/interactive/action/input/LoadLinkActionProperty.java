package lsfusion.server.logics.form.interactive.action.input;

import lsfusion.interop.action.LoadLinkClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.BaseLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.ScriptingAction;

import java.sql.SQLException;

public class LoadLinkActionProperty extends ScriptingAction {

    public LoadLinkActionProperty(BaseLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        context.getBL().LM.baseLM.networkPath.change((String)context.requestUserInteraction(new LoadLinkClientAction()), context);
    }
}