package lsfusion.client.logics;

import lsfusion.base.BaseUtils;
import lsfusion.base.Pair;
import lsfusion.base.context.ApplicationContext;
import lsfusion.client.Main;
import lsfusion.client.SwingUtils;
import lsfusion.client.form.*;
import lsfusion.client.form.cell.PanelView;
import lsfusion.client.logics.classes.*;
import lsfusion.client.serialization.ClientIdentitySerializable;
import lsfusion.client.serialization.ClientSerializationPool;
import lsfusion.interop.Compare;
import lsfusion.interop.PropertyEditType;
import lsfusion.interop.form.ColorPreferences;
import lsfusion.interop.form.PropertyReadType;
import lsfusion.interop.form.screen.ExternalScreenConstraints;

import javax.swing.*;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static lsfusion.base.BaseUtils.*;
import static lsfusion.client.ClientResourceBundle.getString;

@SuppressWarnings({"UnusedDeclaration"})
public class ClientPropertyDraw extends ClientComponent implements ClientPropertyReader, ClientIdentitySerializable {

    public CaptionReader captionReader = new CaptionReader();
    public ShowIfReader showIfReader = new ShowIfReader();
    public BackgroundReader backgroundReader = new BackgroundReader();
    public ForegroundReader foregroundReader = new ForegroundReader();
    public FooterReader footerReader = new FooterReader();
    public ReadOnlyReader readOnlyReader = new ReadOnlyReader();

    public ClientPropertyDraw quickFilterProperty;

    // символьный идентификатор, нужен для обращению к свойствам в печатных формах
    public ClientType baseType;
    public ClientClass returnClass;

    // асинхронные интерфейсы
    public ClientType changeType;
    public ClientType changeWYSType;
    public Pair<ClientObject, Boolean> addRemove;
    public boolean askConfirm;
    public String askConfirmMessage;

    public boolean hasEditObjectAction;
    public boolean hasChangeAction;

    public String[] interfacesCaptions;
    public ClientClass[] interfacesTypes;

    public String caption;
    public String regexp;
    public String regexpMessage;
    public Long maxValue;
    public boolean echoSymbols;
    public boolean noSort;
    public Compare defaultCompare;

    public KeyStroke editKey;
    public boolean showEditKey;

    public boolean drawAsync; // рисовать асинхронность на этой кнопке

    public Boolean focusable;
    public PropertyEditType editType = PropertyEditType.EDITABLE;

    public boolean panelCaptionAbove;

    public ClientExternalScreen externalScreen;
    public ExternalScreenConstraints externalScreenConstraints;

    public int minimumCharWidth;
    public int maximumCharWidth;
    public int preferredCharWidth;

    public Dimension minimumValueSize;
    public Dimension maximumValueSize;
    public Dimension preferredValueSize;
    public int preferredValueHeight;

    public transient EditBindingMap editBindingMap;
    private transient PropertyRenderer renderer;

    protected Format format;
    private Format defaultFormat;

    public boolean checkEquals;

    protected String namespace;
    protected String sID;

    public String toolTip;

    public ClientGroupObject groupObject;
    public String columnsName;
    public List<ClientGroupObject> columnGroupObjects = new ArrayList<>();

    public boolean autoHide;
    public boolean panelCaptionAfter;
    public boolean clearText;
    public String tableName;
    public String eventID;
    public boolean editOnSingleClick;
    public boolean hide;

    public String creationScript;
    public String creationPath;
    public String formPath;
    
    public ColorPreferences colorPreferences;
    
    public boolean notNull;

    public ClientPropertyDraw() {
    }

    public ClientPropertyDraw(int ID, ApplicationContext context) {
        super(ID, context);
    }

    public KeyStroke getEditKey() {
        return editKey;
    }

    public void setEditKey(KeyStroke key) {
        this.editKey = key;
        updateDependency(this, "editKey");
    }

    public boolean getShowEditKey() {
        return showEditKey;
    }

    public void setShowEditKey(boolean showKey) {
        showEditKey = showKey;
        updateDependency(this, "showEditKey");
    }

    public boolean isEditableNotNull() {
        return notNull && !isReadOnly();
    }

    public boolean isReadOnly() {
        return editType == PropertyEditType.READONLY;
    }

    public ClientGroupObject getGroupObject() {
        return groupObject;
    }

    public LinkedHashMap<String, String> getContextMenuItems() {
        return editBindingMap == null ? null : editBindingMap.getContextMenuItems();
    }

    public PropertyEditor getValueEditorComponent(ClientFormController form, Object value) {
        return baseType.getValueEditorComponent(form, this, value);
    }

    public PropertyRenderer getRendererComponent() {
        if (renderer == null) {
            renderer = baseType.getRendererComponent(this);
        }
        return renderer;
    }

    public PanelView getPanelView(ClientFormController form, ClientGroupObjectValue columnKey) {
        return baseType.getPanelView(this, columnKey, form);
    }

    public int getBaseValueWidth(JComponent comp) {
        return BaseUtils.min(getMinimumValueWidth(comp), getPreferredValueWidth(comp));
    }

    public int getMinimumValueWidth(JComponent comp) {
        if (minimumValueSize != null && minimumValueSize.width > -1) {
            return minimumValueSize.width;
        }
        return baseType.getMinimumWidth(minimumCharWidth, comp.getFontMetrics(design.getFont(comp)));
    }

    public int getMinimumValueHeight(JComponent comp) {
        if (minimumValueSize != null && minimumValueSize.height > -1) {
            return minimumValueSize.height;
        }
        return getPreferredValueHeight(comp);
    }

    public Dimension getMinimumValueSize(JComponent comp) {
        return new Dimension(getMinimumValueWidth(comp), getMinimumValueHeight(comp));
    }

    public boolean isFlex(JComponent comp) { // table layout fixed - не поддерживает различные preferred и flex, поэтому просто возвращаем все
        return flex > 0 || getMaximumValueWidth(comp) > getBaseValueWidth(comp);
    }

    public int getPreferredValueWidth(JComponent comp) {
        if (preferredValueSize != null && preferredValueSize.width > -1) {
            return preferredValueSize.width;
        }
        return baseType.getPreferredWidth(preferredCharWidth, comp.getFontMetrics(design.getFont(comp)));
    }

    public int getPreferredValueHeight(JComponent comp) {
        if (preferredValueSize != null && preferredValueSize.height > -1) {
            return preferredValueSize.height;
        }
        int height = baseType.getPreferredHeight(comp.getFontMetrics(design.getFont(comp)));
        if (design.getImage() != null) // предпочитаемую высоту берем исходя из размера иконки
            height = Math.max(design.getImage().getIconHeight() + 6, height);
        return height;
    }

    public Dimension getPreferredValueSize(JComponent comp) {
        return new Dimension(getPreferredValueWidth(comp), getPreferredValueHeight(comp));
    }

    public int getMaximumValueWidth(JComponent comp) {
        int result;
        if (maximumValueSize != null && maximumValueSize.width > -1) {
            result = maximumValueSize.width;
        } else {
            result = baseType.getMaximumWidth(maximumCharWidth, comp.getFontMetrics(design.getFont(comp)));
        }
        return max(result, getPreferredValueWidth(comp));
    }

    public int getMaximumValueHeight(JComponent comp) {
        int result;
        if (maximumValueSize != null && maximumValueSize.width > -1) {
            result = maximumValueSize.height;
        } else {
            result = baseType.getMaximumHeight(comp.getFontMetrics(design.getFont(comp)));
        }
        return max(result, getPreferredValueHeight(comp));
    }

    public Dimension getMaximumValueSize(JComponent comp) {
        return new Dimension(getMaximumValueWidth(comp), getMaximumValueHeight(comp));
    }

    public Format getFormat() {
        if (format == null) {
            return baseType.getDefaultFormat();
        } else {
            return mergeFormats(format, baseType.getDefaultFormat());
        }
    }

    public boolean hasMask() {
        return format != null;
    }

    public String getFormatPattern() {
        if (format != null) {
            if (format instanceof DecimalFormat)
                return ((DecimalFormat) format).toPattern();
            else if (format instanceof SimpleDateFormat)
                return ((SimpleDateFormat) format).toPattern();
        }
        return null;
    }

    public Format setFormat(String pattern) {
        if (pattern != null && !pattern.isEmpty()) {
            if (baseType instanceof ClientIntegralClass) {
                format = mergeFormats(new DecimalFormat(pattern), baseType.getDefaultFormat());
            } else if (baseType instanceof ClientDateClass) {
                format = new SimpleDateFormat(pattern);
            }
        } else {
            format = defaultFormat;
        }
        return format;
    }

    private Format mergeFormats(Format format, Format defaultFormat) {
        if(baseType instanceof ClientIntegralClass) {
            ((DecimalFormat) format).setParseIntegerOnly(((NumberFormat)defaultFormat).isParseIntegerOnly());
            ((DecimalFormat) format).setMaximumIntegerDigits(((NumberFormat)defaultFormat).getMaximumIntegerDigits());
            ((DecimalFormat) format).setGroupingUsed(((NumberFormat)defaultFormat).isGroupingUsed());
        }
        return format;
    }

    public String getEditCaption(String caption) {
        if (caption == null) {
            caption = this.caption;
        }

        return showEditKey && editKey != null
               ? caption + " (" + SwingUtils.getKeyStrokeCaption(editKey) + ")"
               : caption;
    }

    public String getEditCaption() {
        return getEditCaption(caption);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getSID() {
        return sID;
    }

    public Object parseChangeValueOrNull(String s) {
        if (changeWYSType == null) {
            return null;
        }
        try {
            return changeWYSType.parseString(s);
        } catch (ParseException pe) {
            return null;
        }
    }

    public Object parseBaseValue(String s) throws ParseException {
        return baseType.parseString(s);
    }

    public boolean canUsePasteValueForRendering() {
        return changeWYSType != null && baseType.getTypeClass() == changeWYSType.getTypeClass();
    }

    public boolean canUseChangeValueForRendering() {
        return changeType != null && baseType.getTypeClass() == changeType.getTypeClass();
    }

    public String formatString(Object obj) throws ParseException {
      return baseType.formatString(obj);
    }

    public boolean shouldBeDrawn(ClientFormController form) {
        return baseType.shouldBeDrawn(form);
    }

    public byte getType() {
        return PropertyReadType.DRAW;
    }

    public void customSerialize(ClientSerializationPool pool, DataOutputStream outStream, String serializationType) throws IOException {
        super.customSerialize(pool, outStream, serializationType);

        pool.writeString(outStream, caption);
        pool.writeString(outStream, regexp);
        pool.writeString(outStream, regexpMessage);
        pool.writeLong(outStream, maxValue);
        outStream.writeBoolean(echoSymbols);
        outStream.writeBoolean(noSort);
        defaultCompare.serialize(outStream);

        outStream.writeInt(minimumCharWidth);
        outStream.writeInt(maximumCharWidth);
        outStream.writeInt(preferredCharWidth);
        
        pool.writeObject(outStream, minimumValueSize);
        pool.writeObject(outStream, maximumValueSize);
        pool.writeObject(outStream, preferredValueSize);

        pool.writeObject(outStream, editKey);

        outStream.writeBoolean(showEditKey);

        pool.writeObject(outStream, format);
        pool.writeObject(outStream, focusable);

        outStream.writeBoolean(panelCaptionAbove);

        outStream.writeBoolean(externalScreen != null);
        if (externalScreen != null) {
            outStream.writeInt(externalScreen.getID());
        }
        pool.writeObject(outStream, externalScreenConstraints);

        outStream.writeBoolean(panelCaptionAfter);
        outStream.writeBoolean(editOnSingleClick);
        outStream.writeBoolean(hide);

        outStream.writeInt(ID);
    }

    public void customDeserialize(ClientSerializationPool pool, DataInputStream inStream) throws IOException {
        super.customDeserialize(pool, inStream);

        caption = pool.readString(inStream);
        regexp = pool.readString(inStream);
        regexpMessage = pool.readString(inStream);
        maxValue = pool.readLong(inStream);
        echoSymbols = inStream.readBoolean();
        noSort = inStream.readBoolean();
        defaultCompare = Compare.deserialize(inStream);
        minimumCharWidth = inStream.readInt();
        maximumCharWidth = inStream.readInt();
        preferredCharWidth = inStream.readInt();

        minimumValueSize = pool.readObject(inStream);
        maximumValueSize = pool.readObject(inStream);
        preferredValueSize = pool.readObject(inStream);

        editKey = pool.readObject(inStream);
        showEditKey = inStream.readBoolean();
        drawAsync = inStream.readBoolean();

        format = pool.readObject(inStream);
        defaultFormat = format;

        focusable = pool.readObject(inStream);
        editType = PropertyEditType.deserialize(inStream.readByte());

        panelCaptionAbove = inStream.readBoolean();

        if (inStream.readBoolean()) {
            externalScreen = ClientExternalScreen.getScreen(inStream.readInt());
        }

        externalScreenConstraints = pool.readObject(inStream);

        panelCaptionAfter = inStream.readBoolean();
        editOnSingleClick = inStream.readBoolean();
        hide = inStream.readBoolean();

        baseType = ClientTypeSerializer.deserializeClientType(inStream);
        if (inStream.readBoolean()) {
            changeType = ClientTypeSerializer.deserializeClientType(inStream);
        }
        if (inStream.readBoolean()) {
            changeWYSType = ClientTypeSerializer.deserializeClientType(inStream);
        }

        if(inStream.readBoolean()) {
            addRemove = new Pair<>(pool.<ClientObject>deserializeObject(inStream), inStream.readBoolean());
        }

        askConfirm = inStream.readBoolean();
        if(askConfirm)
            askConfirmMessage = pool.readString(inStream);
        
        hasEditObjectAction = inStream.readBoolean();
        hasChangeAction = inStream.readBoolean();

        namespace = pool.readString(inStream);
        sID = pool.readString(inStream);

        toolTip = pool.readString(inStream);

        groupObject = pool.deserializeObject(inStream);

        columnsName = pool.readString(inStream);
        columnGroupObjects = pool.deserializeList(inStream);

        checkEquals = inStream.readBoolean();
        clearText = inStream.readBoolean();

        quickFilterProperty = pool.deserializeObject(inStream);

        tableName = pool.readString(inStream);

        int n = inStream.readInt();
        interfacesCaptions = new String[n];
        interfacesTypes = new ClientClass[n];
        for (int i = 0; i < n; ++i) {
            interfacesCaptions[i] = pool.readString(inStream);
            interfacesTypes[i] = inStream.readBoolean()
                                 ? ClientTypeSerializer.deserializeClientClass(inStream)
                                 : null;
        }

        returnClass = ClientTypeSerializer.deserializeClientClass(inStream);
        eventID = pool.readString(inStream);

        creationScript = pool.readString(inStream);
        creationPath = pool.readString(inStream);
        formPath = pool.readString(inStream);

        String mouseBinding = pool.readString(inStream);
        if (mouseBinding != null) {
            initEditBindingMap();
            editBindingMap.setMouseAction(mouseBinding);
        }

        int keyBindingSize = inStream.readInt();
        if (keyBindingSize > 0) {
            initEditBindingMap();
            for (int i = 0; i < keyBindingSize; ++i) {
                KeyStroke keyStroke = pool.readObject(inStream);
                String actionSID = pool.readString(inStream);
                editBindingMap.setKeyAction(keyStroke, actionSID);
            }
        }

        int contextMenuBindingsSize = inStream.readInt();
        if (contextMenuBindingsSize > 0) {
            initEditBindingMap();
            for (int i = 0; i < contextMenuBindingsSize; ++i) {
                String actionSID = pool.readString(inStream);
                String caption = pool.readString(inStream);
                editBindingMap.setContextMenuAction(actionSID, caption);
            }
        }
        
        notNull = inStream.readBoolean();
    }

    private void initEditBindingMap() {
        if (editBindingMap == null) {
            editBindingMap = new EditBindingMap();
        }
    }

    public void update(Map<ClientGroupObjectValue, Object> readKeys, boolean updateKeys, GroupObjectLogicsSupplier controller) {
        controller.updateDrawPropertyValues(this, readKeys, updateKeys);
    }

    @Override
    public String getCaption() {
        return caption;
    }

    public String getHTMLCaption() {
        return caption == null ? null : escapeHTML(caption);
    }

    public String getDynamicCaption(Object captionValue) {
        return BaseUtils.toCaption(captionValue);
    }

    @Override
    public String toString() {
        if (!isRedundantString(caption))
            return caption + " (" + getID() + ")";

        return getString("logics.undefined.property");
    }

    public static final String TOOL_TIP_FORMAT =
            "<html><b>%1$s</b><br>" +
                    "%2$s";

    public static final String DETAILED_TOOL_TIP_FORMAT =
            "<hr><b>sID:</b> %3$s<br>" +
                    "<b>" + getString("logics.grid") + ":</b> %4$s<br>" +
                    "<b>" + getString("logics.objects") + ":</b> %5$s<br>" +
                    "<b>" + getString("logics.signature") + ":</b> %7$s <i>%3$s</i> (%6$s)<br>" +
                    "<b>" + getString("logics.script") + ":</b> %8$s<br>" +
                    "<b>" + getString("logics.scriptpath") + ":</b> %9$s<br>" +
                    "<b>" + getString("logics.formpath") + ":</b> %10$s" +
                    "</html>";

    public static final String DETAILED_ACTION_TOOL_TIP_FORMAT =
            "<hr><b>sID:</b> %3$s<br>" +
                    "<b>" + getString("logics.objects") + ":</b> %4$s<br>" +
                    "<b>" + getString("logics.scriptpath") + ":</b> %5$s<br>" +
                    "<b>" + getString("logics.formpath") + ":</b> %6$s" +
                    "</html>";

    public static final String EDIT_KEY_TOOL_TIP_FORMAT =
            "<hr><b>" + getString("logics.property.edit.key") + ":</b> %1$s<br>";

    public String getTooltipText(String caption) {
        return getTooltipText(caption, false);
    }

    public String getTooltipText(String caption, boolean action) {
        String propCaption = nullTrim(!isRedundantString(toolTip) ? toolTip : caption);
        String editKeyText = editKey == null ? "" : String.format(EDIT_KEY_TOOL_TIP_FORMAT, SwingUtils.getKeyStrokeCaption(editKey));

        if (!Main.configurationAccessAllowed) {
            return String.format(TOOL_TIP_FORMAT, propCaption, editKeyText);
        } else {
            String sid = getNamespace() + "." + getSID();
            String tableName = this.tableName != null ? this.tableName : "&lt;none&gt;";
            String ifaceObjects = BaseUtils.toString(", ", interfacesCaptions);
            String ifaceClasses = BaseUtils.toString(", ", interfacesTypes);
            String returnClass = this.returnClass.toString();

            String script = creationScript != null ? escapeHTML(creationScript).replace("\n", "<br>") : "";
            String scriptPath = creationPath != null ? creationPath.replace("\n", "<br>") : "";
            String scriptFormPath = formPath != null ? formPath.replace("\n", "<br>") : "";
            return action ?
                    String.format(TOOL_TIP_FORMAT + DETAILED_ACTION_TOOL_TIP_FORMAT,
                    propCaption, editKeyText, sid, ifaceObjects, scriptPath, scriptFormPath) :
                    String.format(TOOL_TIP_FORMAT + DETAILED_TOOL_TIP_FORMAT,
                    propCaption, editKeyText, sid, tableName, ifaceObjects, ifaceClasses, returnClass, script, scriptPath, scriptFormPath);
        }
    }

    private String escapeHTML(String value) {
        return value.replace("<", "&lt;").replace(">", "&gt;");
    }
    
    public boolean isRichTextType() {
        return baseType instanceof ClientStringClass && ((ClientStringClass) baseType).rich;
    }

    public class CaptionReader implements ClientPropertyReader {
        public ClientGroupObject getGroupObject() {
            return ClientPropertyDraw.this.getGroupObject();
        }

        public boolean shouldBeDrawn(ClientFormController form) {
            return ClientPropertyDraw.this.shouldBeDrawn(form);
        }

        public void update(Map<ClientGroupObjectValue, Object> readKeys, boolean updateKeys, GroupObjectLogicsSupplier controller) {
            controller.updateDrawPropertyCaptions(ClientPropertyDraw.this, readKeys);
        }

        public int getID() {
            return ClientPropertyDraw.this.getID();
        }

        public byte getType() {
            return PropertyReadType.CAPTION;
        }
    }

    public class ShowIfReader implements ClientPropertyReader {
        public ClientGroupObject getGroupObject() {
            return ClientPropertyDraw.this.getGroupObject();
        }

        public boolean shouldBeDrawn(ClientFormController form) {
            return ClientPropertyDraw.this.shouldBeDrawn(form);
        }

        public void update(Map<ClientGroupObjectValue, Object> values, boolean updateKeys, GroupObjectLogicsSupplier controller) {
            controller.updateShowIfs(ClientPropertyDraw.this, values);
        }

        public int getID() {
            return ClientPropertyDraw.this.getID();
        }

        public byte getType() {
            return PropertyReadType.SHOWIF;
        }
    }

    public class FooterReader implements ClientPropertyReader {
        public ClientGroupObject getGroupObject() {
            return ClientPropertyDraw.this.getGroupObject();
        }

        public boolean shouldBeDrawn(ClientFormController form) {
            return false;
        }

        public void update(Map<ClientGroupObjectValue, Object> readKeys, boolean updateKeys, GroupObjectLogicsSupplier controller) {
        }

        public int getID() {
            return ClientPropertyDraw.this.getID();
        }

        public byte getType() {
            return PropertyReadType.FOOTER;
        }
    }

    public class ReadOnlyReader implements ClientPropertyReader {
        public ClientGroupObject getGroupObject() {
            return ClientPropertyDraw.this.getGroupObject();
        }

        public boolean shouldBeDrawn(ClientFormController form) {
            return ClientPropertyDraw.this.shouldBeDrawn(form);
        }

        public void update(Map<ClientGroupObjectValue, Object> readKeys, boolean updateKeys, GroupObjectLogicsSupplier controller) {
            controller.updateReadOnlyValues(ClientPropertyDraw.this, readKeys);
        }

        public int getID() {
            return ClientPropertyDraw.this.getID();
        }

        public byte getType() {
            return PropertyReadType.READONLY;
        }
    }

    public class BackgroundReader implements ClientPropertyReader {
        public ClientGroupObject getGroupObject() {
            return ClientPropertyDraw.this.getGroupObject();
        }

        public boolean shouldBeDrawn(ClientFormController form) {
            return ClientPropertyDraw.this.shouldBeDrawn(form);
        }

        public void update(Map<ClientGroupObjectValue, Object> readKeys, boolean updateKeys, GroupObjectLogicsSupplier controller) {
            controller.updateCellBackgroundValues(ClientPropertyDraw.this, readKeys);
        }

        public int getID() {
            return ClientPropertyDraw.this.getID();
        }

        public byte getType() {
            return PropertyReadType.CELL_BACKGROUND;
        }
    }

    public class ForegroundReader implements ClientPropertyReader {
        public ClientGroupObject getGroupObject() {
            return ClientPropertyDraw.this.getGroupObject();
        }

        public boolean shouldBeDrawn(ClientFormController form) {
            return ClientPropertyDraw.this.shouldBeDrawn(form);
        }

        public void update(Map<ClientGroupObjectValue, Object> readKeys, boolean updateKeys, GroupObjectLogicsSupplier controller) {
            controller.updateCellForegroundValues(ClientPropertyDraw.this, readKeys);
        }

        public int getID() {
            return ClientPropertyDraw.this.getID();
        }

        public byte getType() {
            return PropertyReadType.CELL_FOREGROUND;
        }
    }
}
