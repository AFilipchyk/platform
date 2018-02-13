package lsfusion.client.logics.classes;

import lsfusion.client.ClientResourceBundle;
import lsfusion.client.form.ClientFormController;
import lsfusion.client.form.PropertyEditor;
import lsfusion.client.form.editor.IntegerPropertyEditor;
import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.interop.Data;

import java.text.NumberFormat;
import java.text.ParseException;

public class ClientLongClass extends ClientIntegralClass implements ClientTypeClass {

    public final static ClientLongClass instance = new ClientLongClass();

    public byte getTypeId() {
        return Data.LONG;
    }

    @Override
    protected int getLength() {
        return 10;
    }

    @Override
    public NumberFormat getDefaultFormat() {
        NumberFormat format = super.getDefaultFormat();
        format.setParseIntegerOnly(true);
        return format;
    }

    @Override
    public Object parseString(String s) throws ParseException {
        try {
            Number n = parseWithDefaultFormat(s);
            return n.longValue();
        } catch (NumberFormatException nfe) {
            throw new ParseException(s + ClientResourceBundle.getString("logics.classes.can.not.be.converted.to.double"), 0);
        }
    }

    @Override
    public PropertyEditor getValueEditorComponent(ClientFormController form, ClientPropertyDraw property, Object value) {
        return new IntegerPropertyEditor(value, getEditFormat(property), property.design, Long.class);
    }

    public PropertyEditor getDataClassEditorComponent(Object value, ClientPropertyDraw property) {
        return new IntegerPropertyEditor(value, property.maxValue, getEditFormat(property), property.design, Long.class);
    }

    @Override
    public String toString() {
        return ClientResourceBundle.getString("logics.classes.long");
    }
}
