package lsfusion.server.logics.tasks.impl;

import lsfusion.server.logics.tasks.SimpleBLTask;
import org.apache.log4j.Logger;

public class GetPropertyListTask extends SimpleBLTask {

    public String getCaption() {
        return "Building property list";
    }

    public void run(Logger logger) {
        getBL().getPropertyList();
    }
}
