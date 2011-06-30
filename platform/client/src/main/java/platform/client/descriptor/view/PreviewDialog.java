package platform.client.descriptor.view;

import platform.client.ClientResourceBundle;
import platform.client.descriptor.FormDescriptor;
import platform.client.form.ClientFormController;
import platform.client.navigator.ClientNavigator;
import platform.interop.form.RemoteFormInterface;

import javax.swing.*;
import java.awt.*;

public class PreviewDialog extends JDialog {
    private final FormDescriptor form;
    private ClientNavigator navigator;

    public PreviewDialog(ClientNavigator iNavigator, FormDescriptor iForm) {
        super(null, Dialog.ModalityType.MODELESS);

        form = iForm;
        navigator = iNavigator;

        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        setLayout(new BorderLayout());
        try {
            RemoteFormInterface remoteForm = navigator.remoteNavigator.createForm(form.serialize());

            ClientFormController controller = new ClientFormController(remoteForm, navigator);
            controller.getComponent().setFocusTraversalPolicyProvider(true);

            add(controller.getComponent(), BorderLayout.CENTER);
        } catch (Exception e) {
            throw new RuntimeException(ClientResourceBundle.getString("descriptor.view.can.not.create.form"), e);
        }
    }
}
