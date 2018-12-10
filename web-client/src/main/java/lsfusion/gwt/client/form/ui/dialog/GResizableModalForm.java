package lsfusion.gwt.client.form.ui.dialog;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.Window;
import lsfusion.gwt.client.base.Dimension;
import lsfusion.gwt.shared.view.GKeyStroke;
import lsfusion.gwt.shared.result.NumberResult;
import lsfusion.gwt.client.ErrorHandlingCallback;
import lsfusion.gwt.client.form.FormsController;
import lsfusion.gwt.client.form.ui.GFormController;
import lsfusion.gwt.shared.view.GForm;
import lsfusion.gwt.client.grid.EditEvent;
import lsfusion.gwt.client.grid.NativeEditEvent;

import static java.lang.Math.min;

public class GResizableModalForm extends GResizableModalWindow {

    private final EditEvent initFilterEvent;
    private final GFormController form;

    public GResizableModalForm(FormsController formsController, GForm gForm, boolean isDialog, EditEvent initFilterEvent, final WindowHiddenHandler hiddenHandler) {
        super(gForm.caption, gForm.getTooltip(), hiddenHandler);
        this.initFilterEvent = initFilterEvent;

        form = new GFormController(formsController, gForm, true, isDialog) {
            @Override
            public void hideForm() {
                super.hideForm();
                GResizableModalForm.this.hide();
                dropCurrentForm();
            }

            @Override
            protected void onInitialFormChangesReceived() {
                super.onInitialFormChangesReceived();
                Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
                    @Override
                    public void execute() {
                        initialFormChangesReceived();
                    }
                });
            }
        };

        setContentWidget(form);

        //сразу добавляем в DOM, чтобы можно было посчитать естественную ширину элементов
        attach();
    }

    public GFormController getForm() {
        return form;
    }

    @Override
    protected void onAttach() {
        super.onAttach();
        form.modalFormAttached();
    }

    @Override
    protected void onLoad() {
        if (initialOnLoad) {
            Dimension size = form.getMaxPreferredSize();
            if (size.width > 0) {
                int wndWidth = Window.getClientWidth();
                size.width = min(size.width + 20, wndWidth - 20);
                form.setWidth(size.width + "px");
            }
            if (size.height > 0) {
                int wndHeight = Window.getClientHeight();
                size.height = min(size.height, wndHeight - 100);
                form.setHeight(size.height + "px");
            }
        }
        super.onLoad();
    }

    private void initialFormChangesReceived() {
        if (initFilterEvent != null && initFilterEvent instanceof NativeEditEvent) {
            NativeEvent event = ((NativeEditEvent) initFilterEvent).getNativeEvent();
            if (GKeyStroke.isPossibleStartFilteringEvent(event) && !GKeyStroke.isSpaceKeyEvent(event)) {
                form.getInitialFilterProperty(new ErrorHandlingCallback<NumberResult>() {
                    @Override
                    public void success(NumberResult result) {
                        Integer initialFilterPropertyID = (Integer) result.value;

                        if (initialFilterPropertyID != null) {
                            form.quickFilter(initFilterEvent, initialFilterPropertyID);
                        }
                    }
                });
            }
        }
    }

    public static GResizableModalForm showForm(FormsController formsController, GForm form, boolean isDialog, EditEvent initFilterEvent, final WindowHiddenHandler hiddenHandler) {
        GResizableModalForm modalForm = new GResizableModalForm(formsController, form, isDialog, initFilterEvent, hiddenHandler);
        modalForm.justCenter();
        return modalForm;
    }
}
