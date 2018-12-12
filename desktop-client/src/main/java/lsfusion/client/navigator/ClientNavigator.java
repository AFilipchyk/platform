package lsfusion.client.navigator;

import lsfusion.client.dock.ClientFormDockable;
import lsfusion.client.dock.DockableManager;
import lsfusion.interop.navigator.RemoteNavigatorInterface;
import net.sf.jasperreports.engine.JRException;

import java.io.IOException;
import java.util.Map;

public abstract class ClientNavigator {
    public final RemoteNavigatorInterface remoteNavigator;

    public final ClientNavigatorElement rootElement;
    public final Map<String, ClientNavigatorWindow> windows;

    public ClientNavigator(RemoteNavigatorInterface remoteNavigator, ClientNavigatorElement rootElement, Map<String, ClientNavigatorWindow> windows) {
        this.remoteNavigator = remoteNavigator;
        this.rootElement = rootElement;
        this.windows = windows;
    }

    public abstract void openAction(ClientNavigatorAction action, int modifiers);
}