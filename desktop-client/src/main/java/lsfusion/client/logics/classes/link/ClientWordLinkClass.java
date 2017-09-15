package lsfusion.client.logics.classes.link;

import lsfusion.client.ClientResourceBundle;
import lsfusion.client.form.PropertyEditor;
import lsfusion.client.form.PropertyRenderer;
import lsfusion.client.form.editor.LinkPropertyEditor;
import lsfusion.client.form.renderer.link.WordLinkPropertyRenderer;
import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.interop.Data;

public class ClientWordLinkClass extends ClientStaticFormatLinkClass {

    public final static ClientWordLinkClass instance = new ClientWordLinkClass(false);

    public ClientWordLinkClass(boolean multiple) {
        super(multiple);
    }

    public PropertyRenderer getRendererComponent(ClientPropertyDraw property) {
        return new WordLinkPropertyRenderer(property);
    }

    public byte getTypeId() {
        return Data.WORDLINK;
    }

    @Override
    public PropertyEditor getDataClassEditorComponent(Object value, ClientPropertyDraw property) {
        return new LinkPropertyEditor(property, value);
    }

    @Override
    public String toString() {
        return ClientResourceBundle.getString("logics.classes.word.link");
    }
}