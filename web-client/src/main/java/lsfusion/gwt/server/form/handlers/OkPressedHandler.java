package lsfusion.gwt.server.form.handlers;

import lsfusion.gwt.server.form.FormServerResponseActionHandler;
import net.customware.gwt.dispatch.server.ExecutionContext;
import lsfusion.gwt.server.MainDispatchServlet;
import lsfusion.http.provider.form.FormSessionObject;
import lsfusion.gwt.shared.actions.form.OkPressed;
import lsfusion.gwt.shared.actions.form.ServerResponseResult;

import java.rmi.RemoteException;

public class OkPressedHandler extends FormServerResponseActionHandler<OkPressed> {
    public OkPressedHandler(MainDispatchServlet servlet) {
        super(servlet);
    }

    @Override
    public ServerResponseResult executeEx(OkPressed action, ExecutionContext context) throws RemoteException {
        FormSessionObject form = getFormSessionObject(action.formSessionID);
        return getServerResponseResult(form, form.remoteForm.okPressed(action.requestIndex, defaultLastReceivedRequestIndex));
    }
}
