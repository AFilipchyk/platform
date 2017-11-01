package lsfusion.server.logics.scripted;

import lsfusion.base.OrderedMap;
import lsfusion.interop.ClassViewType;
import lsfusion.interop.PropertyEditType;
import lsfusion.server.form.entity.ActionPropertyObjectEntity;
import lsfusion.server.form.entity.CalcPropertyObjectEntity;
import lsfusion.server.form.entity.GroupObjectEntity;
import lsfusion.server.form.entity.PropertyDrawEntity;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.ActionProperty;
import lsfusion.server.logics.property.Property;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static lsfusion.base.BaseUtils.isRedundantString;
import static lsfusion.base.BaseUtils.nvl;

public class FormPropertyOptions {
    private PropertyEditType editType;
    private Boolean hintNoUpdate;
    private Boolean hintTable;
    private Boolean optimisticAsync;
    private Columns columns;
    private CalcPropertyObjectEntity showIf;
    private CalcPropertyObjectEntity readOnlyIf;
    private CalcPropertyObjectEntity background;
    private CalcPropertyObjectEntity foreground;
    private CalcPropertyObjectEntity header;
    private CalcPropertyObjectEntity footer;
    private ClassViewType forceViewType;
    private GroupObjectEntity toDraw;
    private OrderedMap<String, LocalizedString> contextMenuBindings;
    private Map<String, ActionPropertyObjectEntity> editActions;
    private String eventId;
    private PropertyDrawEntity neighbourPropertyDraw;
    private PropertyDrawEntity quickFilterPropertyDraw;
    private String neighbourPropertyText;
    private Boolean isRightNeighbour;
    
    private Boolean newSession;
    private Boolean isNested;
    
    public void setNewSession(Boolean newSession) {
        this.newSession = newSession;
    }
    
    public void setNested(Boolean isNested) {
        this.isNested = isNested;
    }
    
    public Boolean isNewSession() {
        return newSession;
    }
    
    public Boolean isNested() {
        return isNested;
    }
    

    public PropertyEditType getEditType() {
        return editType;
    }

    public void setEditType(PropertyEditType editType) {
        this.editType = editType;
    }

    public Columns getColumns() {
        return columns;
    }

    public void setColumns(Columns columns) {
        this.columns = columns;
    }

    public void setColumns(String columnsName, List<GroupObjectEntity> columns) {
        this.columns = new Columns(columnsName, columns);
    }

    public CalcPropertyObjectEntity getShowIf() {
        return showIf;
    }

    public void setShowIf(CalcPropertyObjectEntity showIf) {
        this.showIf = showIf;
    }

    public CalcPropertyObjectEntity getReadOnlyIf() {
        return readOnlyIf;
    }

    public void setReadOnlyIf(CalcPropertyObjectEntity readOnlyIf) {
        this.readOnlyIf = readOnlyIf;
    }

    public CalcPropertyObjectEntity getBackground() {
        return background;
    }

    public void setBackground(CalcPropertyObjectEntity background) {
        this.background = background;
    }

    public CalcPropertyObjectEntity getForeground() {
        return foreground;
    }

    public void setForeground(CalcPropertyObjectEntity foreground) {
        this.foreground = foreground;
    }

    public CalcPropertyObjectEntity getHeader() {
        return header;
    }

    public void setHeader(CalcPropertyObjectEntity header) {
        this.header = header;
    }

    public CalcPropertyObjectEntity getFooter() {
        return footer;
    }

    public void setFooter(CalcPropertyObjectEntity footer) {
        this.footer = footer;
    }

    public void setForceViewType(ClassViewType forceViewType) {
        this.forceViewType = forceViewType;
    }

    public ClassViewType getForceViewType() {
        return forceViewType;
    }

    public void setToDraw(GroupObjectEntity toDraw) {
        this.toDraw = toDraw;
    }

    public GroupObjectEntity getToDraw() {
        return toDraw;
    }

    public Boolean getHintNoUpdate() {
        return hintNoUpdate;
    }

    public void setHintNoUpdate(Boolean hintNoUpdate) {
        this.hintNoUpdate = hintNoUpdate;
    }

    public Boolean getHintTable() {
        return hintTable;
    }

    public void setHintTable(Boolean hintTable) {
        this.hintTable = hintTable;
    }

    public Boolean getOptimisticAsync() {
        return optimisticAsync;
    }

    public void setOptimisticAsync(Boolean optimisticAsync) {
        this.optimisticAsync = optimisticAsync;
    }

    public void addEditAction(String actionSID, ActionPropertyObjectEntity action) {
        if (action != null) {
            if (editActions == null) {
                editActions = new HashMap<>();
            }
            editActions.put(actionSID, action);
        }
    }

    public void addContextMenuBinding(String actionSID, LocalizedString caption) {
        if (contextMenuBindings == null) {
            contextMenuBindings = new OrderedMap<>();
        }
        contextMenuBindings.put(actionSID, caption);
    }

    public void addContextMenuEditAction(LocalizedString caption, ActionPropertyObjectEntity action) {
        if (action != null) {
            ActionProperty property = (ActionProperty) action.property;

            addEditAction(property.getSID(), action);
            addContextMenuBinding(property.getSID(), getContextMenuCaption(caption, property));
            property.checkReadOnly = false;
        }
    }

    public static LocalizedString getContextMenuCaption(LocalizedString caption, ActionProperty property) {
        if (caption == null || isRedundantString(caption.getSourceString())) {
            caption = property.caption;
        }
        if (caption == null || isRedundantString(caption.getSourceString())) {
            caption = LocalizedString.create(property.getSID());
        }
        return caption;
    }

    public OrderedMap<String, LocalizedString> getContextMenuBindings() {
        return contextMenuBindings;
    }

    public void setContextMenuBindings(OrderedMap<String, LocalizedString> contextMenuBindings) {
        this.contextMenuBindings = contextMenuBindings;
    }

    public Map<String, ActionPropertyObjectEntity> getEditActions() {
        return editActions;
    }

    public void setEditActions(Map<String, ActionPropertyObjectEntity> editActions) {
        this.editActions = editActions;
    }

    public PropertyDrawEntity getNeighbourPropertyDraw() {
        return neighbourPropertyDraw;
    }

    public String getNeighbourPropertyText() {
        return neighbourPropertyText;
    }

    public void setNeighbourPropertyDraw(PropertyDrawEntity neighbourPropertyDraw, String propText) {
        this.neighbourPropertyDraw = neighbourPropertyDraw;
        this.neighbourPropertyText = propText;
    }

    public PropertyDrawEntity getQuickFilterPropertyDraw() {
        return quickFilterPropertyDraw;
    }

    public void setQuickFilterPropertyDraw(PropertyDrawEntity quickFilterPropertyDraw) {
        this.quickFilterPropertyDraw = quickFilterPropertyDraw;
    }

    public Boolean isRightNeighbour() {
        return isRightNeighbour;
    }

    public void setNeighbourType(Boolean isRightNeighbour) {
        this.isRightNeighbour = isRightNeighbour;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public FormPropertyOptions overrideWith(FormPropertyOptions overrides) {
        FormPropertyOptions merged = new FormPropertyOptions();

        merged.setNewSession(nvl(overrides.isNewSession(), newSession));
        merged.setNested(nvl(overrides.isNested(), isNested));
        merged.setEditType(nvl(overrides.getEditType(), editType));
        merged.setHintNoUpdate(nvl(overrides.getHintNoUpdate(), hintNoUpdate));
        merged.setHintTable(nvl(overrides.getHintTable(), hintTable));
        merged.setOptimisticAsync(nvl(overrides.getOptimisticAsync(), optimisticAsync));
        merged.setColumns(nvl(overrides.getColumns(), columns));
        merged.setShowIf(nvl(overrides.getShowIf(), showIf));
        merged.setReadOnlyIf(nvl(overrides.getReadOnlyIf(), readOnlyIf));
        merged.setBackground(nvl(overrides.getBackground(), background));
        merged.setForeground(nvl(overrides.getForeground(), foreground));
        merged.setHeader(nvl(overrides.getHeader(), header));
        merged.setFooter(nvl(overrides.getFooter(), footer));
        merged.setForceViewType(nvl(overrides.getForceViewType(), forceViewType));
        merged.setToDraw(nvl(overrides.getToDraw(), toDraw));
        merged.setEditActions(nvl(overrides.getEditActions(), editActions));
        merged.setContextMenuBindings(nvl(overrides.getContextMenuBindings(), contextMenuBindings));
        merged.setEventId(nvl(overrides.getEventId(), eventId));
        merged.setNeighbourPropertyDraw(nvl(overrides.getNeighbourPropertyDraw(), neighbourPropertyDraw), nvl(overrides.getNeighbourPropertyText(), neighbourPropertyText));
        merged.setNeighbourType(nvl(overrides.isRightNeighbour(), isRightNeighbour));
        merged.setQuickFilterPropertyDraw(nvl(overrides.getQuickFilterPropertyDraw(), quickFilterPropertyDraw));
        return merged;
    }
    
    public static class Columns {
        public final String columnsName;
        public final List<GroupObjectEntity> columns;

        public Columns(String columnsName, List<GroupObjectEntity> columns) {
            this.columnsName = columnsName;
            this.columns = columns;
        }
    }
}
