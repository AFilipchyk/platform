package lsfusion.gwt.form.server.navigator.handlers;

import lsfusion.gwt.base.server.dispatch.NavigatorActionHandler;
import lsfusion.gwt.form.server.FormDispatchServlet;
import lsfusion.gwt.form.server.FormSessionObject;
import lsfusion.gwt.form.server.form.handlers.ServerResponseActionHandler;
import lsfusion.gwt.form.shared.actions.form.ServerResponseResult;
import lsfusion.gwt.form.shared.actions.navigator.ThrowInNavigatorAction;
import net.customware.gwt.dispatch.server.ExecutionContext;
import net.customware.gwt.dispatch.shared.DispatchException;

import java.io.IOException;

public class ThrowInNavigatorActionHandler extends ServerResponseActionHandler<ThrowInNavigatorAction> implements NavigatorActionHandler {
    public ThrowInNavigatorActionHandler(FormDispatchServlet servlet) {
        super(servlet);
    }

    @Override
    public ServerResponseResult executeEx(ThrowInNavigatorAction action, ExecutionContext context) throws DispatchException, IOException {
        return getServerResponseResult(new FormSessionObject(null, null, action.tabSID), servlet.getNavigator().throwInNavigatorAction(action.throwable));
    }
}
