package lsfusion.client.form.classes.controller;

import lsfusion.client.form.classes.view.ClassChooserView;
import lsfusion.client.form.controller.ClientFormController;
import lsfusion.client.form.design.view.ClientFormLayout;
import lsfusion.client.form.object.ClientObject;
import lsfusion.client.classes.ClientObjectClass;

import java.io.IOException;

public class ClassChooserController {

    // компонент для отображения
    private ClassChooserView classChooserView;

    // данные по объекту, класс которого обрабатывается
    private final ClientObject object;

    // объект, при помощи которого будет происходить общение с внешним миром
    private final ClientFormController form;

    public ClassChooserController(ClientObject iobject, ClientFormController iform) throws IOException {
        this.object = iobject;
        this.form = iform;
    }

    public ClassChooserView getClassChooserView() {
        return classChooserView;
    }

    public void addView(ClientFormLayout formLayout) {
        // нужно для того, что если объект типа дата, то для него не будет возможностей добавлять объекты
        if (object.baseClass instanceof ClientObjectClass) {
            classChooserView = new ClassChooserView(form, object);
            formLayout.add(object.classChooser, classChooserView);
        }
    }

    public void setVisible(boolean visible) {
        if (classChooserView != null) {
            classChooserView.setVisible(object.classChooser.visible && visible);
        }
    }

}