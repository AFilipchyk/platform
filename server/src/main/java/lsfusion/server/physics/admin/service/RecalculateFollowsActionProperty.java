package lsfusion.server.physics.admin.service;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.ServiceLogicsModule;
import lsfusion.server.physics.dev.i18n.LocalizedString;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.logics.action.session.SessionCreator;

import java.sql.SQLException;

import static lsfusion.server.base.context.ThreadLocalContext.localize;

public class RecalculateFollowsActionProperty extends ScriptingActionProperty {
    public RecalculateFollowsActionProperty(ServiceLogicsModule LM) {
        super(LM);
    }
    @Override
    public void executeCustom(final ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        ServiceDBActionProperty.runData(context, new RunServiceData() {
            public void run(SessionCreator session, boolean isolatedTransaction) throws SQLException, SQLHandledException {
                String result = context.getBL().recalculateFollows(session, isolatedTransaction, context.stack);
                if(result != null)
                    context.delayUserInterfaction(new MessageClientAction(result, localize("{logics.recalculation.follows}")));
            }
        });

        context.delayUserInterfaction(new MessageClientAction(localize(LocalizedString.createFormatted("{logics.recalculation.completed}", localize("{logics.recalculation.follows}"))), localize("{logics.recalculation.follows}")));
    }
}