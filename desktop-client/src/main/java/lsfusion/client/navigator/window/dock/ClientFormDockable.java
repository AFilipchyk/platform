package lsfusion.client.navigator.window.dock;

import lsfusion.client.Main;
import lsfusion.client.MainFrame;
import lsfusion.client.form.ClientFormController;
import lsfusion.client.base.RmiQueue;
import lsfusion.client.navigator.ClientNavigator;
import lsfusion.interop.form.RemoteFormInterface;

import java.io.IOException;

public class ClientFormDockable extends ClientDockable {

    private ClientFormController clientForm;

    public ClientFormDockable(ClientNavigator navigator, String canonicalName, String formSID, RemoteFormInterface remoteForm, DockableManager dockableManager, final MainFrame.FormCloseListener closeListener, byte[] firstChanges) throws IOException {
        super(canonicalName, dockableManager);

        clientForm = new ClientFormController(canonicalName, formSID, remoteForm, firstChanges, navigator) {
            @Override
            public void hideForm() {
                if (control() != null) {
                    setVisible(false);
                }
                
                if (closeListener != null) {
                    closeListener.formClosed();
                }
                super.hideForm();
            }

            @Override
            public void blockView() {
                clientForm.getLayout().setBlocked(true);
                ClientFormDockable.this.blockView();
            }

            @Override
            public void unblockView() {
                clientForm.getLayout().setBlocked(false);
                ClientFormDockable.this.unblockView();
            }

            @Override
            public void setBlockingForm(ClientFormDockable blockingForm) {
                ClientFormDockable.this.setBlockingForm(blockingForm);
            }
        };

        setContent(clientForm.getCaption(), clientForm.getTooltip(), clientForm.getLayout());
    }

    @Override
    public void onClosing() {
        RmiQueue.runAction(new Runnable() {
            @Override
            public void run() {
                clientForm.closePressed();
            }
        });
    }

    @Override
    public void onClosed() {
        super.onClosed();

        // удаляем ссылку на clientForm, поскольку ClientFormDockable совершенно не собирается быть собранным сборщиком мусора,
        // поскольку на него хранят ссылку внутренние объекты DockingFrames
        clientForm.closed();
        clientForm = null;

        // на всякий случай
        System.gc();
    }

    @Override
    public void onShowingChanged(boolean oldShowing, boolean newShowing) {
        if (clientForm != null) {
            clientForm.setSelected(newShowing);
        }
    }

    @Override
    public void onOpened() {
        if (clientForm != null)
            Main.setCurrentForm(clientForm);
    }

}
