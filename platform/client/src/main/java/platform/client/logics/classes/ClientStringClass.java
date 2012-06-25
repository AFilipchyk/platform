package platform.client.logics.classes;

import platform.base.BaseUtils;
import platform.client.ClientResourceBundle;
import platform.client.form.ClientFormController;
import platform.client.form.PropertyEditorComponent;
import platform.client.form.PropertyRendererComponent;
import platform.client.form.editor.StringPropertyEditor;
import platform.client.form.renderer.StringPropertyRenderer;
import platform.client.logics.ClientPropertyDraw;
import platform.gwt.view.classes.GStringType;
import platform.gwt.view.classes.GType;
import platform.interop.Compare;
import platform.interop.Data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.Format;
import java.text.ParseException;

import static platform.interop.Compare.START_WITH;

public class ClientStringClass extends ClientDataClass {

    public final int length;

    protected String sID;

    @Override
    public String getSID() {
        return sID;
    }

    @Override
    public String getCode() {
        return "StringClass.get(" + length + ")";
    }

    public ClientStringClass(DataInputStream inStream) throws IOException {
        super(inStream);

        length = inStream.readInt();
        sID = "StringClass_" + length;
    }

    public ClientStringClass(int length) {
        this.length = length;
    }

    public final static ClientTypeClass type = new ClientTypeClass() {
        public byte getTypeId() {
            return Data.STRING;
        }

        public ClientStringClass getDefaultClass(ClientObjectClass baseClass) {
            return getDefaultType();
        }

        public ClientStringClass getDefaultType() {
            return new ClientStringClass(50);
        }

        @Override
        public String toString() {
            return ClientResourceBundle.getString("logics.classes.string");
        }
    };
    public ClientTypeClass getTypeClass() {
        return type;
    }

    @Override
    public void serialize(DataOutputStream outStream) throws IOException {
        super.serialize(outStream);

        outStream.writeInt(length);
    }

    @Override
    public String getMinimumMask() {
        return BaseUtils.replicate('0', length / 5);
    }

    public String getPreferredMask() {
        return BaseUtils.replicate('0', length);
    }

    public Format getDefaultFormat() {
        return null;
    }

    public PropertyRendererComponent getRendererComponent(ClientPropertyDraw property) {
        return new StringPropertyRenderer(property);
    }

    @Override
    public PropertyEditorComponent getValueEditorComponent(ClientFormController form, ClientPropertyDraw property, Object value) {
        return new StringPropertyEditor(property, value, length, false);
    }

    public PropertyEditorComponent getDataClassEditorComponent(Object value, ClientPropertyDraw property) {
        return new StringPropertyEditor(property, value, length, true);
    }

    public Object parseString(String s) throws ParseException {
        return s;
    }

    @Override
    public String formatString(Object obj) {
        return obj.toString();
    }

    @Override
    public String toString() {
        return ClientResourceBundle.getString("logics.classes.string")+"(" + length + ")";
    }

    @Override
    public Compare[] getFilterCompares() {
        return Compare.values();
    }

    @Override
    public Compare getDefaultCompare() {
        return START_WITH;
    }

    private GStringType gwtType;
    @Override
    public GType getGwtType() {
        if (gwtType == null) {
            gwtType = new GStringType(length);
        }
        return gwtType;
    }
}
