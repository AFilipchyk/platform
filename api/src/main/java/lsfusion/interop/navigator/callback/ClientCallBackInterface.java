package lsfusion.interop.navigator.callback;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ClientCallBackInterface extends Remote {
    List<LifecycleMessage> pullMessages() throws RemoteException;
}
