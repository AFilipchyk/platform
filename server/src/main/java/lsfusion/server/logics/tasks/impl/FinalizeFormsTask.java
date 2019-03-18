package lsfusion.server.logics.tasks.impl;

import lsfusion.server.logics.form.struct.FormEntity;
import lsfusion.server.logics.tasks.GroupFormsTask;

public class FinalizeFormsTask extends GroupFormsTask {

    protected void runTask(FormEntity form) {
        form.finalizeAroundInit();
    }

    public String getCaption() {
        return "Finalizing forms";
    }
}
