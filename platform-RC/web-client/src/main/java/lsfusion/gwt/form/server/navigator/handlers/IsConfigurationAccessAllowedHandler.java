package lsfusion.gwt.form.server.navigator.handlers;

import lsfusion.gwt.base.server.LogicsAwareDispatchServlet;
import lsfusion.gwt.base.server.dispatch.NavigatorActionHandler;
import lsfusion.gwt.base.shared.actions.BooleanResult;
import lsfusion.gwt.form.server.form.handlers.LoggableActionHandler;
import lsfusion.gwt.form.shared.actions.navigator.IsConfigurationAccessAllowedAction;
import lsfusion.interop.RemoteLogicsInterface;
import net.customware.gwt.dispatch.server.ExecutionContext;
import net.customware.gwt.dispatch.shared.DispatchException;

import java.io.IOException;

public class IsConfigurationAccessAllowedHandler extends LoggableActionHandler<IsConfigurationAccessAllowedAction, BooleanResult, RemoteLogicsInterface> implements NavigatorActionHandler {
    public IsConfigurationAccessAllowedHandler(LogicsAwareDispatchServlet<RemoteLogicsInterface> servlet) {
        super(servlet);
    }

    @Override
    public BooleanResult executeEx(IsConfigurationAccessAllowedAction action, ExecutionContext context) throws DispatchException, IOException {
        return new BooleanResult(servlet.getNavigator().isConfigurationAccessAllowed());
    }
}
