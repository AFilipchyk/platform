package platform.server;

import platform.interop.action.ClientAction;
import platform.server.classes.CustomClass;
import platform.server.classes.DataClass;
import platform.server.form.entity.FormEntity;
import platform.server.form.entity.ObjectEntity;
import platform.server.form.instance.DialogInstance;
import platform.server.form.instance.FormInstance;
import platform.server.form.instance.remote.RemoteDialog;
import platform.server.form.instance.remote.RemoteForm;
import platform.server.logics.BusinessLogics;
import platform.server.logics.DataObject;
import platform.server.logics.ObjectValue;
import platform.server.logics.property.ExecutionContext;
import platform.server.session.DataSession;

import java.sql.SQLException;
import java.util.Map;

public interface Context {
    ThreadLocal<Context> context = new ThreadLocal<Context>();

    BusinessLogics getBL();

    void setActionMessage(String message);
    String getActionMessage();
    void pushActionMessage(String segment);
    String popActionMessage();

    FormInstance createFormInstance(FormEntity formEntity, Map<ObjectEntity, DataObject> mapObjects, DataSession session, boolean newSession, boolean checkOnOk, boolean interactive)  throws SQLException;
    RemoteForm createRemoteForm(FormInstance formInstance);
    RemoteDialog createRemoteDialog(DialogInstance dialogInstance);

    ObjectValue requestUserObject(ExecutionContext.RequestDialog requestDialog) throws SQLException;
    ObjectValue requestUserData(DataClass dataClass, Object oldValue);
    ObjectValue requestUserClass(CustomClass baseClass, CustomClass defaultValue, boolean concrete);

    Object requestUserInteraction(ClientAction action);
    Object[] requestUserInteraction(ClientAction... actions);
}
