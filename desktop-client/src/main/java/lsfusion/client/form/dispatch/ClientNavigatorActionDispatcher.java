package lsfusion.client.form.dispatch;

import lsfusion.client.form.DispatcherListener;
import lsfusion.client.navigator.ClientNavigator;
import lsfusion.interop.form.ServerResponse;

import java.io.IOException;
import java.rmi.RemoteException;

public class ClientNavigatorActionDispatcher extends SwingClientActionDispatcher {
    private final ClientNavigator clientNavigator;

    public ClientNavigatorActionDispatcher(ClientNavigator clientNavigator) {
        super(new DispatcherListener() {
            @Override
            public void dispatchingEnded() {}

            @Override
            public void dispatchingPostponedEnded(DispatcherInterface realDispatcher) {}
        });
        this.clientNavigator = clientNavigator;
    }

    @Override
    public ServerResponse continueServerInvocation(long requestIndex, int continueIndex, Object[] actionResults) throws RemoteException {
        return clientNavigator.remoteNavigator.continueNavigatorAction(actionResults);
    }

    @Override
    protected ServerResponse throwInServerInvocation(long requestIndex, int continueIndex, Throwable t) throws IOException {
        return clientNavigator.remoteNavigator.throwInNavigatorAction(t);
    }
}
