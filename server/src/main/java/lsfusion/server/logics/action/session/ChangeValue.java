package lsfusion.server.logics.action.session;

import lsfusion.server.logics.classes.ConcreteValueClass;

import java.io.DataOutputStream;
import java.io.IOException;

public abstract class ChangeValue {
    public ConcreteValueClass changeClass;

    ChangeValue(ConcreteValueClass iClass) {
        changeClass = iClass;
    }

    abstract byte getTypeID();
    public void serialize(DataOutputStream outStream) throws IOException {
        outStream.writeByte(getTypeID());
        changeClass.serialize(outStream);
    }
}
