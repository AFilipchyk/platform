package lsfusion.server.lifecycle;

import lsfusion.server.context.NewThreadExecutionStack;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.context.TopExecutionStack;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.session.DataSession;

import java.sql.SQLException;

public abstract class EventServer extends LifecycleAdapter {

    public EventServer() {
    }

    public EventServer(int order) {
        super(order);
    }

    public abstract String getEventName();

    private final NewThreadExecutionStack stack = new TopExecutionStack(getEventName());
    public NewThreadExecutionStack getTopStack() {
        return stack;
    }

    public abstract LogicsInstance getLogicsInstance();

    protected DataSession createSession() throws SQLException {
        return getLogicsInstance().getDbManager().createSession();
    }
}
