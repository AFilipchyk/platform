package lsfusion.interop.action;

import java.io.IOException;

public class ImportFileClientAction implements ClientAction {

    public String fileName;
    public String charsetName;

    public boolean erase = false;

    public ImportFileClientAction(String fileName, String charsetName, boolean erase) {
        this.fileName = fileName;
        this.charsetName = charsetName;
        this.erase = erase;
    }

    @Override
    public Object dispatch(ClientActionDispatcher dispatcher) {
        return dispatcher.execute(this);
    }
}
