package lsfusion.gwt.shared.view.classes;

import lsfusion.gwt.client.form.MainFrameMessages;

public class GWordType extends GFileType {
    @Override
    public String toString() {
        return MainFrameMessages.Instance.get().typeWordFileCaption();
    }
}
