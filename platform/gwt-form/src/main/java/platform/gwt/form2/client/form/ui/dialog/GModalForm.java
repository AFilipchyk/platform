package platform.gwt.form2.client.form.ui.dialog;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.ResizeLayoutPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import platform.gwt.form2.client.form.ui.GFormController;
import platform.gwt.form2.shared.view.GForm;

public class GModalForm extends GModalWindow {

    protected final ResizeLayoutPanel mainPane;

    public GModalForm(GForm form, final WindowHiddenHandler hiddenHandler) {
        super(form.caption, hiddenHandler);

        GFormController editorForm = new GFormController(form, true) {
            @Override
            public void hideForm() {
                GModalForm.this.hide();
            }
        };

        int width = Math.min(Window.getClientWidth() - 20, editorForm.getPreferredWidth() == -1 ? 800 : editorForm.getPreferredWidth());
        int height = Math.min(Window.getClientHeight() - 100, editorForm.getPreferredHeight() == -1 ? 600 : editorForm.getPreferredHeight());

        mainPane = new ResizeLayoutPanel();
        mainPane.setPixelSize(width, height);
        mainPane.setWidget(new ScrollPanel(editorForm));

        setWidget(mainPane);
    }

    public static GModalForm showForm(GForm form, WindowHiddenHandler hiddenHandler) {
        GModalForm modalForm = new GModalForm(form, hiddenHandler);
        modalForm.center();
        return modalForm;
    }
}
