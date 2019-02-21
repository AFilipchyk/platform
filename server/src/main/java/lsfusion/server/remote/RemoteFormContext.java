package lsfusion.server.remote;

import lsfusion.interop.action.ClientAction;
import lsfusion.server.auth.SecurityPolicy;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.form.instance.listener.CustomClassListener;
import lsfusion.server.form.instance.listener.FocusListener;
import lsfusion.server.form.instance.listener.RemoteFormListener;
import lsfusion.server.form.navigator.LogInfo;
import lsfusion.server.logics.LogicsInstance;

import java.util.Locale;

public class RemoteFormContext<F extends FormInstance> extends RemoteUIContext {
    private final RemoteForm<F> form;

    public RemoteFormContext(RemoteForm<F> form) {
        this.form = form;
    }

    @Override
    public LogicsInstance getLogicsInstance() {
        return form.form.logicsInstance;
    }

    public FormInstance getFormInstance() {
        return form.form;
    }

    @Override
    public LogInfo getLogInfo() {
        return form.getLogInfo();
    }

    public void aspectDelayUserInteraction(ClientAction action, String message) {
        form.delayUserInteraction(action);
    }

    @Override
    protected Object[] aspectRequestUserInteraction(ClientAction[] actions, String[] messages) {
        return form.requestUserInteraction(actions);
    }

    @Override
    protected int getExportPort() {
        return form.getExportPort();
    }

    @Override
    protected RemoteFormListener getFormListener() {
        return form.getRemoteFormListener();
    }

    public SecurityPolicy getSecurityPolicy() {
        return form.form.securityPolicy;
    }

    public FocusListener getFocusListener() {
        return form.form.getFocusListener();
    }

    public CustomClassListener getClassListener() {
        return form.form.getClassListener();
    }

    public Long getCurrentComputer() {
        return form.form.session.sql.contextProvider.getCurrentComputer();
    }

    public Long getCurrentUser() {
        return form.form.session.sql.contextProvider.getCurrentUser();
    }

    @Override
    public Long getCurrentUserRole() {
        return form.form.session.user.getCurrentUserRole();
    }

    @Override
    public Locale getLocale() {
        return form.form.getLocale();
    }
}
