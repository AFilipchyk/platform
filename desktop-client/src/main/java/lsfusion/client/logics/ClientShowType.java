package lsfusion.client.logics;

import lsfusion.client.ClientResourceBundle;
import lsfusion.client.serialization.ClientSerializationPool;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ClientShowType extends ClientComponent {

    public ClientShowType() {
    }

    public ClientGroupObject groupObject;

    @Override
    public void customSerialize(ClientSerializationPool pool, DataOutputStream outStream) throws IOException {
        super.customSerialize(pool, outStream);

        pool.serializeObject(outStream, groupObject);
    }

    @Override
    public void customDeserialize(ClientSerializationPool pool, DataInputStream inStream) throws IOException {
        super.customDeserialize(pool, inStream);

        groupObject = pool.deserializeObject(inStream);
    }

    @Override
    public String getCaption() {
        return ClientResourceBundle.getString("logics.view");
    }

    @Override
    public String toString() {
        return getCaption() + " (" + groupObject.toString() + ")" + "[sid:" + getSID() + "]";
    }
}
