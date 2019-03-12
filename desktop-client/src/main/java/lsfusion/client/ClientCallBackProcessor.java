package lsfusion.client;

import lsfusion.interop.navigator.callback.LifecycleMessage;
import lsfusion.interop.navigator.callback.PushMessage;

import javax.swing.*;
import java.util.List;

public class ClientCallBackProcessor {

    public ClientCallBackProcessor() {
    }

    public void processMessages(List<LifecycleMessage> messages) {
        if (messages != null) {
            for (LifecycleMessage message : messages) {
                processMessage(message);
            }
        }
    }

    private void processMessage(final LifecycleMessage message) {
        if(message instanceof PushMessage) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    Main.executeNotificationAction(((PushMessage)message).idNotification);
                }
            });
        }
    }
}
