package lsfusion.server.form.entity;

import lsfusion.interop.PropertyEditType;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.form.view.DefaultFormView;
import lsfusion.server.form.view.FormView;
import lsfusion.server.logics.BaseLogicsModule;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.mutables.Version;

public class EditFormEntity<T extends BusinessLogics<T>> extends BaseClassFormEntity<T> {

    public EditFormEntity(BaseLogicsModule<T> LM, CustomClass cls) {
        super(LM, cls, null, cls.caption);

        object.groupTo.setPanelClassView();

        finalizeInit(LM.getVersion());
    }

    @Override
    public FormView createDefaultRichDesign(Version version) {
        DefaultFormView design = (DefaultFormView) super.createDefaultRichDesign(version);

        design.getDropButton().removeFromParent(version);

        return design;
    }

}
