package lsfusion.client.form.filter.user;

import lsfusion.base.BaseUtils;
import lsfusion.client.ClientResourceBundle;

import java.io.DataOutputStream;
import java.io.IOException;

public class ClientDataFilterValue extends ClientFilterValue {

    private Object value;

    byte getTypeID() {
        return 0;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void serialize(DataOutputStream outStream) throws IOException {
        super.serialize(outStream);
        BaseUtils.serializeObject(outStream, value);
    }

    public String toString() {
        return ClientResourceBundle.getString("logics.value");
    }
}
