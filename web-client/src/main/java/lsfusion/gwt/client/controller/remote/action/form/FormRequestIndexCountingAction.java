package lsfusion.gwt.client.controller.remote.action.form;

import net.customware.gwt.dispatch.shared.Result;

public class FormRequestIndexCountingAction<R extends Result> extends FormAction<R> {
    public int requestIndex;

    public FormRequestIndexCountingAction() {
    }

    @Override
    public String toString() {
        return super.toString() + " [request#: " + requestIndex + "]";
    }
}