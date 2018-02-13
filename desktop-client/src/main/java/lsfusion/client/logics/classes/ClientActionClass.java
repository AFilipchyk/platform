package lsfusion.client.logics.classes;

import lsfusion.client.ClientResourceBundle;
import lsfusion.client.form.ClientFormController;
import lsfusion.client.form.PropertyEditor;
import lsfusion.client.form.PropertyRenderer;
import lsfusion.client.form.cell.ActionPanelView;
import lsfusion.client.form.cell.PanelView;
import lsfusion.client.form.editor.ActionPropertyEditor;
import lsfusion.client.form.renderer.ActionPropertyRenderer;
import lsfusion.client.logics.ClientGroupObjectValue;
import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.interop.Data;

import java.text.Format;
import java.text.ParseException;

public class ClientActionClass extends ClientDataClass implements ClientTypeClass {
    public final static ClientActionClass instance = new ClientActionClass();

    private ClientActionClass() {
    }

    public byte getTypeId() {
        return Data.ACTION;
    }

    @Override
    public String getDefaultWidthString(ClientPropertyDraw propertyDraw) {
        return "1234567";
    }

    public PropertyRenderer getRendererComponent(ClientPropertyDraw property) {
        return new ActionPropertyRenderer(property);
    }

    public PanelView getPanelView(ClientPropertyDraw key, ClientGroupObjectValue columnKey, ClientFormController form) {
        return new ActionPanelView(key, columnKey, form);
    }

    protected PropertyEditor getDataClassEditorComponent(Object value, ClientPropertyDraw property) {
        return new ActionPropertyEditor(property);
    }

    public Object parseString(String s) throws ParseException {
        throw new ParseException(ClientResourceBundle.getString("logics.classes.actionclass.doesnt.support.convertation.from.string"), 0);
    }

    @Override
    public String formatString(Object obj) throws ParseException {
        throw new ParseException(ClientResourceBundle.getString("logics.classes.actionclass.doesnt.support.convertation.from.string"), 0);
    }

    @Override
    public String getConfirmMessage() {
        return ClientResourceBundle.getString("logics.classes.do.you.really.want.to.take.action");
    }

    @Override
    public String toString() {
        return ClientResourceBundle.getString("logics.classes.actionclass");
    }
}
