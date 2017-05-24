package lsfusion.server.logics.scripted;

import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.linear.LAP;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ClassType;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.property.actions.SystemExplicitActionProperty;
import lsfusion.server.logics.property.actions.flow.ChangeFlowType;
import lsfusion.server.session.DataSession;
import org.antlr.runtime.RecognitionException;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;

/**
 * User: DAle
 * Date: 15.11.12
 * Time: 17:11
 */

public class EvalActionProperty<P extends PropertyInterface> extends SystemExplicitActionProperty {
    private final LCP<P> source;
    private final ImMap<P, ClassPropertyInterface> mapSource;

    public EvalActionProperty(LocalizedString caption, LCP<P> source) {
        super(caption, source.getInterfaceClasses(ClassType.aroundPolicy));
        mapSource = source.listInterfaces.mapSet(getOrderInterfaces());
        this.source = source;
    }

    private String getScript(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        ImMap<P, ? extends ObjectValue> sourceToData = mapSource.join(context.getKeys());
        return (String) source.read(context, source.listInterfaces.mapOrder(sourceToData).toArray(new ObjectValue[interfaces.size()]));
    }

    @Override
    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        String script = getScript(context);

        try {
            ScriptingLogicsModule module = EvalUtils.evaluate(context.getBL(), script);

            String runName = module.getName() + ".run[]";
            LAP<?> runAction = module.findAction(runName);
            if (runAction != null) {
                runAction.execute(context);
            }
        } catch (EvalUtils.EvaluationException | RecognitionException e) {
            context.delayUserInteraction(new MessageClientAction(getMessage(e), "Parse error"));
            throw new RuntimeException(e);
        } catch (Throwable e) {
            context.delayUserInteraction(new MessageClientAction(getMessage(e), "Execution error"));
            throw new RuntimeException(e);
        }
    }
    @Override
    public boolean hasFlow(ChangeFlowType type) {
        return true;
    }

    private String getMessage(Throwable e) {
        return e.getMessage() == null ? String.valueOf(e) : e.getMessage();
    }
}
