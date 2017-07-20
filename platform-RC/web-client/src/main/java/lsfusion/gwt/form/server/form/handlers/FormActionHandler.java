package lsfusion.gwt.form.server.form.handlers;

import lsfusion.gwt.base.server.dispatch.BaseFormBoundActionHandler;
import lsfusion.gwt.form.server.FormDispatchServlet;
import lsfusion.gwt.form.server.FormSessionManager;
import lsfusion.gwt.form.server.FormSessionObject;
import lsfusion.interop.RemoteLogicsInterface;
import net.customware.gwt.dispatch.shared.Action;
import net.customware.gwt.dispatch.shared.Result;

public abstract class FormActionHandler<A extends Action<R>, R extends Result> extends LoggableActionHandler<A, R, RemoteLogicsInterface> implements BaseFormBoundActionHandler {
    public FormActionHandler(FormDispatchServlet servlet) {
        super(servlet);
    }

    public FormSessionManager getFormSessionManager() {
        return ((FormDispatchServlet)servlet).getFormSessionManager();

    }

    /**
    * Ищет форму в сессии с name=formSessionID
    *
    * Если форма не найдена, то выбрасывает RuntimeException
    * @throws RuntimeException
    */
    public FormSessionObject getFormSessionObject(String formSessionID) throws RuntimeException {
        return getFormSessionManager().getFormSessionObject(formSessionID);
    }

    public FormSessionObject getFormSessionObjectOrNull(String formSessionID) throws RuntimeException {
        return getFormSessionManager().getFormSessionObjectOrNull(formSessionID);
    }
}
