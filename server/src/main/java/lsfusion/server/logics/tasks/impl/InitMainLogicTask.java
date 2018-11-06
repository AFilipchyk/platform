package lsfusion.server.logics.tasks.impl;

import com.google.common.base.Throwables;
import lsfusion.server.logics.LogicsModule;
import lsfusion.server.logics.tasks.GroupModuleTask;
import org.antlr.runtime.RecognitionException;

import java.io.FileNotFoundException;

public class InitMainLogicTask extends GroupModuleTask {

    protected boolean isGroupLoggable() {
        return true;
    }

    protected long getTaskComplexity(LogicsModule module) {
        return module.getModuleComplexity();
    }

    protected boolean isGraph() {
        return true;
    }

    public String getCaption() {
        return "Initializing main logic";
    }

    protected void runTask(LogicsModule module) throws RecognitionException {
        try {
            module.initMainLogic();
        } catch (FileNotFoundException e) {
            throw Throwables.propagate(e);
        }
    }
}
