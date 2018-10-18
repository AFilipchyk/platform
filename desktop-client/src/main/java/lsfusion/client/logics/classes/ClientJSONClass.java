package lsfusion.client.logics.classes;

import lsfusion.client.ClientResourceBundle;
import lsfusion.client.form.PropertyEditor;
import lsfusion.client.form.PropertyRenderer;
import lsfusion.client.form.editor.FilePropertyEditor;
import lsfusion.client.form.renderer.JSONPropertyRenderer;
import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.interop.Data;

public class ClientJSONClass extends ClientStaticFormatFileClass {

    public final static ClientJSONClass instance = new ClientJSONClass(false, false);

    public ClientJSONClass(boolean multiple, boolean storeName) {
        super(multiple, storeName);
    }

    @Override
    public String[] getExtensions() {
        return new String[] {"json"};
    }

    public PropertyRenderer getRendererComponent(ClientPropertyDraw property) {
        return new JSONPropertyRenderer(property);
    }

    @Override
    public String formatString(Object obj) {
        return "JSON";
    }

    public byte getTypeId() {
        return Data.JSON;
    }

    @Override
    public PropertyEditor getDataClassEditorComponent(Object value, ClientPropertyDraw property) {
        return new FilePropertyEditor(multiple, storeName, ClientResourceBundle.getString("logics.classes.json"), getExtensions());
    }

    @Override
    public String toString() {
        return ClientResourceBundle.getString("logics.classes.json.file");
    }
}