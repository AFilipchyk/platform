package platform.client.form.dispatch;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import platform.client.form.ClientFormController;
import platform.client.form.EditPropertyHandler;
import platform.client.logics.ClientGroupObjectValue;
import platform.client.logics.ClientPropertyDraw;
import platform.client.logics.classes.ClientType;
import platform.interop.action.EditNotPerformedClientAction;
import platform.interop.action.RequestUserInputClientAction;
import platform.interop.form.ServerResponse;
import platform.interop.form.UserInputResult;

import java.io.IOException;

import static platform.base.BaseUtils.deserializeObject;
import static platform.client.logics.classes.ClientTypeSerializer.deserialize;

public class EditPropertyDispatcher extends ClientFormActionDispatcher {
    protected final EditPropertyHandler handler;

    private boolean valueRequested = false;
    private boolean editPerformed;

    private ClientGroupObjectValue editColumnKey;
    private ClientPropertyDraw simpleChangeProperty;

    private ClientType readType;
    private Object oldValue;

    public EditPropertyDispatcher(EditPropertyHandler handler) {
        this.handler = handler;
    }

    @Override
    public ClientFormController getFormController() {
        return handler.getForm();
    }

    /**
     * @return true, если на сервере вызван action для редактирования
     */
    public boolean executePropertyEditAction(ClientPropertyDraw property, ClientGroupObjectValue columnKey, String actionSID, Object currentValue) {
        try {
            readType = null;
            simpleChangeProperty = null;
            editColumnKey = null;

            if (actionSID.equals(ServerResponse.CHANGE) && property.changeType != null) {
                editColumnKey = columnKey;
                simpleChangeProperty = property;
                return internalRequestValue(property.changeType, currentValue);
            }

            editPerformed = true;
            ServerResponse response = handler.getForm().executeEditAction(property, columnKey, actionSID);
            return internalDispatchResponse(response);
        } catch (IOException ex) {
            throw Throwables.propagate(ex);
        }
    }

    /**
     * @return true, если на сервере вызван action для редактирования
     */
    private boolean internalDispatchResponse(ServerResponse response) throws IOException {
        assert response != null;

        dispatchResponse(response);
        if (readType != null) {
            if (!internalRequestValue(readType, oldValue)) {
                cancelEdit();
            }
            return true;
        }

        return readType != null || response.resumeInvocation || editPerformed;
    }

    private boolean internalRequestValue(ClientType readType, Object oldValue) throws IOException {
        valueRequested = true;
        return handler.requestValue(readType, oldValue);
    }

    private void internalCommitValue(UserInputResult inputResult) {
        Preconditions.checkState(valueRequested, "value wasn't requested");

        if (simpleChangeProperty != null) {
            if (!inputResult.isCanceled()) {
                try {
                    getFormController().changeProperty(simpleChangeProperty, editColumnKey, inputResult.getValue());
                } catch (IOException e) {
                    throw Throwables.propagate(e);
                }
            }
            return;
        }

        try {
            valueRequested = false;
            continueDispatching(inputResult);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public void commitValue(Object value) {
        internalCommitValue(new UserInputResult(value));
    }

    public void cancelEdit() {
        internalCommitValue(UserInputResult.canceled);
    }

    public Object execute(RequestUserInputClientAction action) {
        try {
            readType = deserialize(action.readType);
            oldValue = deserializeObject(action.oldValue);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        pauseDispatching();

        return null;
    }

    @Override
    public void execute(EditNotPerformedClientAction action) {
        editPerformed = false;
    }
}
