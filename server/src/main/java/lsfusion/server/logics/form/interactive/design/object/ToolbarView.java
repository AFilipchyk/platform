package lsfusion.server.logics.form.interactive.design.object;

import lsfusion.server.logics.form.interactive.design.ComponentView;
import lsfusion.server.logics.form.interactive.controller.remote.serialization.ServerSerializationPool;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ToolbarView extends ComponentView {
    public boolean visible = true;

    public boolean showGroupChange = true;
    public boolean showCountRows = true;
    public boolean showCalculateSum = true;
    public boolean showGroupReport = true;
    public boolean showPrint = true;
    public boolean showXls = true;
    public boolean showSettings = true;

    public ToolbarView() {

    }

    public ToolbarView(int ID) {
        super(ID);
    }

    @Override
    public void customSerialize(ServerSerializationPool pool, DataOutputStream outStream) throws IOException {
        super.customSerialize(pool, outStream);

        outStream.writeBoolean(visible);

        outStream.writeBoolean(showGroupChange);
        outStream.writeBoolean(showCountRows);
        outStream.writeBoolean(showCalculateSum);
        outStream.writeBoolean(showGroupReport);
        outStream.writeBoolean(showPrint);
        outStream.writeBoolean(showXls);
        outStream.writeBoolean(showSettings);
    }

    @Override
    public void customDeserialize(ServerSerializationPool pool, DataInputStream inStream) throws IOException {
        super.customDeserialize(pool, inStream);

        visible = inStream.readBoolean();

        showGroupChange = inStream.readBoolean();
        showCountRows = inStream.readBoolean();
        showCalculateSum = inStream.readBoolean();
        showGroupReport = inStream.readBoolean();
        showPrint = inStream.readBoolean();
        showXls = inStream.readBoolean();
        showSettings = inStream.readBoolean();
    }
}
