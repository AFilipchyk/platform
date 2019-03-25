package lsfusion.client.form.filter;

import java.io.DataOutputStream;
import java.io.IOException;

abstract public class ClientFilterValue {

    abstract byte getTypeID();
    public void serialize(DataOutputStream outStream) throws IOException {
        outStream.writeByte(getTypeID());
    }    
}
