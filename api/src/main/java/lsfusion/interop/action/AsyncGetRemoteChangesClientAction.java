package lsfusion.interop.action;

import java.io.IOException;

public class AsyncGetRemoteChangesClientAction extends ExecuteClientAction {
    public AsyncGetRemoteChangesClientAction() {
    }

    @Override
    public void execute(ClientActionDispatcher dispatcher) {
        dispatcher.execute(this);
    }
}
