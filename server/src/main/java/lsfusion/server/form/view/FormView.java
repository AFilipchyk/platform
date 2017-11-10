package lsfusion.server.form.view;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.mutable.MOrderExclMap;
import lsfusion.base.identity.DefaultIDGenerator;
import lsfusion.base.identity.IDGenerator;
import lsfusion.base.identity.IdentityObject;
import lsfusion.interop.FontInfo;
import lsfusion.interop.PropertyEditType;
import lsfusion.interop.form.layout.AbstractForm;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.type.Type;
import lsfusion.server.form.entity.*;
import lsfusion.server.form.entity.filter.RegularFilterEntity;
import lsfusion.server.form.entity.filter.RegularFilterGroupEntity;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.linear.LP;
import lsfusion.server.logics.mutables.NFFact;
import lsfusion.server.logics.mutables.SIDHandler;
import lsfusion.server.logics.mutables.Version;
import lsfusion.server.logics.mutables.interfaces.NFOrderMap;
import lsfusion.server.logics.mutables.interfaces.NFOrderSet;
import lsfusion.server.logics.property.Property;
import lsfusion.server.logics.property.group.AbstractGroup;
import lsfusion.server.logics.property.group.AbstractNode;
import lsfusion.server.serialization.ServerCustomSerializable;
import lsfusion.server.serialization.ServerSerializationPool;

import javax.swing.*;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.synchronizedMap;

public class FormView extends IdentityObject implements ServerCustomSerializable, AbstractForm<ContainerView, ComponentView, LocalizedString> {

    // нужен для того, чтобы генерировать уникальный идентификаторы объектам рисования, для передачи их клиенту
    protected final IDGenerator idGenerator = new DefaultIDGenerator();

    public FormEntity<?> entity;

    public KeyStroke keyStroke = null;

    public LocalizedString caption = LocalizedString.create("");
    public String canonicalName = "";
    public String creationPath = "";

    public Integer overridePageWidth;

    public int autoRefresh = 0;

    // список деревеьев
    public NFOrderSet<TreeGroupView> treeGroups = NFFact.orderSet();
    public Iterable<TreeGroupView> getTreeGroupsIt() {
        return treeGroups.getIt();
    }
    public ImOrderSet<TreeGroupView> getTreeGroupsList() {
        return treeGroups.getOrderSet();
    }
    public Iterable<TreeGroupView> getNFTreeGroupsListIt(Version version) { // предполагается все с одной версией, равной текущей (конструирование FormView)
        return treeGroups.getNFListIt(version);
    }

    // список групп
    public NFOrderSet<GroupObjectView> groupObjects = NFFact.orderSet();
    public Iterable<GroupObjectView> getGroupObjectsIt() {
        return groupObjects.getIt();
    }
    public ImOrderSet<GroupObjectView> getGroupObjectsListIt() {
        return groupObjects.getOrderSet();
    }
    public Iterable<GroupObjectView> getNFGroupObjectsIt(Version version) {
        return groupObjects.getNFIt(version); 
    }
    public Iterable<GroupObjectView> getNFGroupObjectsListIt(Version version) { // предполагается все с одной версией, равной текущей (конструирование FormView)
        return groupObjects.getNFListIt(version);
    }

    // список свойств
    public NFOrderSet<PropertyDrawView> properties = NFFact.orderSet();
    public Iterable<PropertyDrawView> getPropertiesIt() {
        return properties.getIt();
    }
    public ImOrderSet<PropertyDrawView> getPropertiesList() {
        return properties.getOrderSet();
    }
    public Iterable<PropertyDrawView> getNFPropertiesIt(Version version) { // предполагается все с одной версией, равной текущей (конструирование FormView)
        return properties.getNFIt(version);
    }
    public Iterable<PropertyDrawView> getNFPropertiesListIt(Version version) { // предполагается все с одной версией, равной текущей (конструирование FormView)
        return properties.getNFListIt(version);
    }

    // список фильтров
    public NFOrderSet<RegularFilterGroupView> regularFilters = NFFact.orderSet();
    public Iterable<RegularFilterGroupView> getRegularFiltersIt() {
        return regularFilters.getIt();
    }
    public ImOrderSet<RegularFilterGroupView> getRegularFiltersList() {
        return regularFilters.getOrderSet();
    }
    public Iterable<RegularFilterGroupView> getNFRegularFiltersListIt(Version version) { // предполагается все с одной версией, равной текущей (конструирование FormView)
        return regularFilters.getNFListIt(version);
    }

    protected NFOrderMap<PropertyDrawView,Boolean> defaultOrders = NFFact.orderMap();
    public ImOrderMap<PropertyDrawView, Boolean> getDefaultOrders() {
        return defaultOrders.getListMap();
    }

    public ContainerView mainContainer;

    protected PropertyDrawView printButton;
    protected PropertyDrawView editButton;
    protected PropertyDrawView xlsButton;
    protected PropertyDrawView dropButton;
    protected PropertyDrawView refreshButton;
    protected PropertyDrawView applyButton;
    protected PropertyDrawView cancelButton;
    protected PropertyDrawView okButton;
    protected PropertyDrawView closeButton;

    protected transient Map<TreeGroupEntity, TreeGroupView> mtreeGroups = synchronizedMap(new HashMap<TreeGroupEntity, TreeGroupView>());
    public TreeGroupView get(TreeGroupEntity treeGroup) { return mtreeGroups.get(treeGroup); }

    protected transient Map<GroupObjectEntity, GroupObjectView> mgroupObjects = synchronizedMap(new HashMap<GroupObjectEntity, GroupObjectView>());
    public GroupObjectView get(GroupObjectEntity groupObject) { return mgroupObjects.get(groupObject); }

    protected transient Map<ObjectEntity, ObjectView> mobjects = synchronizedMap(new HashMap<ObjectEntity, ObjectView>());
    public ObjectView get(ObjectEntity object) { return mobjects.get(object); }

    protected transient Map<PropertyDrawEntity, PropertyDrawView> mproperties = synchronizedMap(new HashMap<PropertyDrawEntity, PropertyDrawView>());
    public PropertyDrawView get(PropertyDrawEntity property) { return mproperties.get(property); }

    protected transient Map<RegularFilterGroupEntity, RegularFilterGroupView> mfilters = synchronizedMap(new HashMap<RegularFilterGroupEntity, RegularFilterGroupView>());
    public RegularFilterGroupView get(RegularFilterGroupEntity filterGroup) { return mfilters.get(filterGroup); }

    public ComponentView findById(int id) {
        return mainContainer.findById(id);
    }

    public FormView() {
    }

    public FormView(FormEntity<?> entity, Version version) {
        super(0);

        idGenerator.idRegister(0);
        
        this.entity = entity;

        mainContainer = new ContainerView(idGenerator.idShift(), true);
        setComponentSID(mainContainer, getMainContainerSID(), version);

        for (GroupObjectEntity group : entity.getNFGroupsListIt(version)) {
            addGroupObjectBase(group, version);
        }

        for (TreeGroupEntity treeGroup : entity.getNFTreeGroupsListIt(version)) {
            addTreeGroupBase(treeGroup, version);
        }

        for (PropertyDrawEntity property : entity.getNFPropertyDrawsListIt(version)) {
            PropertyDrawView view = addPropertyDrawBase(property, version);
            view.caption = property.initCaption;
        }

        for (RegularFilterGroupEntity filterGroup : entity.getNFRegularFilterGroupsListIt(version)) {
            addRegularFilterGroupBase(filterGroup, version);
        }

        initButtons(version);
    }

    public void addDefaultOrder(PropertyDrawEntity property, boolean ascending, Version version) {
        defaultOrders.add(get(property), ascending, version);
    }

    private void addPropertyDrawView(PropertyDrawView property) {
        mproperties.put(property.entity, property);
    }

    private PropertyDrawView addPropertyDrawBase(PropertyDrawEntity property, Version version) {
        PropertyDrawView propertyView = new PropertyDrawView(property);
        properties.add(propertyView, version);
        addPropertyDrawView(propertyView);

        //походу инициализируем порядки по умолчанию
        Boolean ascending = entity.getNFDefaultOrder(property, version);
        if (ascending != null) {
            defaultOrders.add(propertyView, ascending, version);
        }

        return propertyView;
    }

    public PropertyDrawView addPropertyDraw(PropertyDrawEntity property, Version version) {
        return addPropertyDrawBase(property, version);
    }

    public void movePropertyDrawTo(PropertyDrawEntity property, PropertyDrawEntity newNeighbour, boolean isRightNeighbour, Version version) {
        PropertyDrawView propertyView = mproperties.get(property);
        PropertyDrawView neighbourView = mproperties.get(newNeighbour);
        assert propertyView != null && neighbourView != null;

        properties.move(propertyView, neighbourView, isRightNeighbour, version);
    }

    private void addGroupObjectView(GroupObjectView groupObjectView, Version version) {
        mgroupObjects.put(groupObjectView.entity, groupObjectView);

        boolean isInTree = groupObjectView.entity.isInTree();

        if(!isInTree) { // правильнее вообще не создавать компоненты, но для этого потребуется более сложный рефакторинг, поэтому пока просто сделаем так чтобы к ним нельзя было обратиться
            setComponentSID(groupObjectView.getGrid(), getGridSID(groupObjectView), version);
            setComponentSID(groupObjectView.getShowType(), getShowTypeSID(groupObjectView), version);
            setComponentSID(groupObjectView.getToolbar(), getToolbarSID(groupObjectView), version);
            setComponentSID(groupObjectView.getFilter(), getFilterSID(groupObjectView), version);
            setComponentSID(groupObjectView.getCalculations(), getCalculationsSID(groupObjectView), version);
        }

        for (ObjectView object : groupObjectView) {
            mobjects.put(object.entity, object);
            if(!isInTree)
                setComponentSID(object.classChooser, getClassChooserSID(object.entity), version);
        }
    }
    
    public GroupObjectView addGroupObjectBase(GroupObjectEntity groupObject, GroupObjectEntity neighbour, Boolean isRightNeighbour, Version version) {
        GroupObjectView groupObjectView = new GroupObjectView(idGenerator, groupObject);
        if (neighbour != null) {
            groupObjects.addIfNotExistsToThenLast(groupObjectView, get(neighbour), isRightNeighbour != null && isRightNeighbour, version);
        } else {
            groupObjects.add(groupObjectView, version);
        }
        addGroupObjectView(groupObjectView, version);
        return groupObjectView;    
    }

    private GroupObjectView addGroupObjectBase(GroupObjectEntity groupObject, Version version) {
        return addGroupObjectBase(groupObject, null, null, version);
    }

    public GroupObjectView addGroupObject(GroupObjectEntity groupObject, GroupObjectEntity neighbour, Boolean isRightNeighbour, Version version) {
        return addGroupObjectBase(groupObject, neighbour, isRightNeighbour, version);
    }

    public TreeGroupView addTreeGroup(TreeGroupEntity treeGroup, Version version) {
        return addTreeGroupBase(treeGroup, version);
    }

    private void addTreeGroupView(TreeGroupView treeGroupView, Version version) {
        mtreeGroups.put(treeGroupView.entity, treeGroupView);
        setComponentSID(treeGroupView, getGridSID(treeGroupView), version);
        setComponentSID(treeGroupView.getToolbar(), getToolbarSID(treeGroupView), version);
        setComponentSID(treeGroupView.getFilter(), getFilterSID(treeGroupView), version);
    }

    private TreeGroupView addTreeGroupBase(TreeGroupEntity treeGroup, Version version) {
        TreeGroupView treeGroupView = new TreeGroupView(this, treeGroup, version);
        treeGroups.add(treeGroupView, version);
        addTreeGroupView(treeGroupView, version);
        return treeGroupView;
    }

    private void addRegularFilterGroupView(RegularFilterGroupView filterGroupView, Version version) {
        mfilters.put(filterGroupView.entity, filterGroupView);
        setComponentSID(filterGroupView, getRegularFilterGroupSID(filterGroupView.entity), version);
    }

    private RegularFilterGroupView addRegularFilterGroupBase(RegularFilterGroupEntity filterGroup, Version version) {
        RegularFilterGroupView filterGroupView = new RegularFilterGroupView(filterGroup, version);
        regularFilters.add(filterGroupView, version);
        addRegularFilterGroupView(filterGroupView, version);
        return filterGroupView;
    }

    public RegularFilterGroupView addRegularFilterGroup(RegularFilterGroupEntity filterGroupEntity, Version version) {
        return addRegularFilterGroupBase(filterGroupEntity, version);
    }
    
    public RegularFilterView addRegularFilter(RegularFilterGroupEntity filterGroup, RegularFilterEntity filter, Version version) {
        RegularFilterGroupView filterGroupView = get(filterGroup);
        return filterGroupView.addFilter(filter, version);
    }

    public void fillComponentMaps() {
        for (GroupObjectView group : getGroupObjectsIt()) {
            addGroupObjectView(group, Version.DESCRIPTOR);
        }

        for (TreeGroupView treeGroup : getTreeGroupsIt()) {
            addTreeGroupView(treeGroup, Version.DESCRIPTOR);
        }

        for (PropertyDrawView property : getPropertiesIt()) {
            addPropertyDrawView(property);
        }

        for (RegularFilterGroupView filterGroup : getRegularFiltersIt()) {
            addRegularFilterGroupView(filterGroup, Version.DESCRIPTOR);
        }

        initButtons(Version.DESCRIPTOR);
    }

    private void initButtons(Version version) {
//        printButton = setupFormButton(entity.printActionPropertyDraw, "print", version);
        editButton = setupFormButton(entity.editActionPropertyDraw, "edit", version);
//        xlsButton = setupFormButton(entity.xlsActionPropertyDraw, "xls", version);
        refreshButton = setupFormButton(entity.refreshActionPropertyDraw, "refresh", version);
        applyButton = setupFormButton(entity.applyActionPropertyDraw, "apply", version);
        cancelButton = setupFormButton(entity.cancelActionPropertyDraw, "cancel", version);
        okButton = setupFormButton(entity.okActionPropertyDraw, "ok", version);
        closeButton = setupFormButton(entity.closeActionPropertyDraw, "close", version);
        dropButton = setupFormButton(entity.dropActionPropertyDraw, "drop", version);
    }

    public ContainerView createContainer(Version version) {
        return createContainer(null, version);
    }

    public ContainerView createContainer(LocalizedString caption, Version version) {
        return createContainer(caption, null, null, version);
    }

    public ContainerView createContainer(LocalizedString caption, LocalizedString description, String sID, Version version) {
        ContainerView container = new ContainerView(idGenerator.idShift());
        
        // Не используем здесь setCaption и setDescription из-за того, что они принимают на вход String.
        // Изменить тип, принимаемый set методами не можем, потому что этот интерфейс используется и на клиенте, где
        // LocalizedString отсутствует.
        container.caption = caption;
        container.description = description;
        
        container.setSID(sID);
        if (sID != null) {
            addComponentToMapping(container, version);
        }
        return container;
    }

    private final SIDHandler<ComponentView> componentSIDHandler = new SIDHandler<ComponentView>() {
        public boolean checkUnique() {
            return false;
        }

        protected String getSID(ComponentView component) {
            return component.getSID();
        }
    };
    
    public void addComponentToMapping(ComponentView container, Version version) {
        componentSIDHandler.store(container, version);
    }

    public void removeContainerFromMapping(ContainerView container, Version version) {
        componentSIDHandler.remove(container, version);
    }

    public ComponentView getComponentBySID(String sid, Version version) {
        return componentSIDHandler.find(sid, version);
    }

    public PropertyDrawView getPrintButton() {
        return printButton;
    }

    public PropertyDrawView getEditButton() {
        return editButton;
    }

    public PropertyDrawView getXlsButton() {
        return xlsButton;
    }

    public PropertyDrawView getDropButton() {
        return dropButton;
    }

    public PropertyDrawView getRefreshButton() {
        return refreshButton;
    }

    public PropertyDrawView getApplyButton() {
        return applyButton;
    }

    public PropertyDrawView getCancelButton() {
        return cancelButton;
    }

    public PropertyDrawView getOkButton() {
        return okButton;
    }

    public PropertyDrawView getCloseButton() {
        return closeButton;
    }

    public ContainerView getMainContainer() {
        return mainContainer;
    }

    public GroupObjectView getGroupObject(GroupObjectEntity entity) {
        if (entity == null) {
            return null;
        }
        for (GroupObjectView groupObject : getGroupObjectsIt())
            if (entity.equals(groupObject.entity))
                return groupObject;
        return null;
    }

    public GroupObjectView getNFGroupObject(GroupObjectEntity entity, Version version) {
        if (entity == null) {
            return null;
        }
        for (GroupObjectView groupObject : getNFGroupObjectsIt(version))
            if (entity.equals(groupObject.entity))
                return groupObject;
        return null;
    }

    public ObjectView getObject(ObjectEntity entity) {
        if (entity == null) {
            return null;
        }
        for (GroupObjectView groupObject : getGroupObjectsIt())
            for(ObjectView object : groupObject)
                if (entity.equals(object.entity))
                    return object;
        return null;
    }

    public TreeGroupView getTreeGroup(TreeGroupEntity entity) {
        if (entity == null) {
            return null;
        }
        for (TreeGroupView treeGroup : getTreeGroupsIt())
            if (entity.equals(treeGroup.entity))
                return treeGroup;
        return null;
    }

    public PropertyDrawView getProperty(PropertyDrawEntity entity) {
        if (entity == null) {
            return null;
        }
        for (PropertyDrawView property : getPropertiesIt()) {
            if (entity.equals(property.entity)) {
                return property;
            }
        }
        return null;
    }

    public PropertyDrawView getNFProperty(PropertyDrawEntity entity, Version version) {
        if (entity == null) {
            return null;
        }
        for (PropertyDrawView property : getNFPropertiesIt(version)) {
            if (entity.equals(property.entity)) {
                return property;
            }
        }
        return null;
    }

    public List<PropertyDrawView> getProperties(AbstractNode group) {

        return getProperties(group, null);
    }

    public List<PropertyDrawView> getProperties(AbstractNode group, GroupObjectEntity groupObject) {

        List<PropertyDrawView> result = new ArrayList<>();

        for (PropertyDrawView property : getPropertiesList()) {
            if ((groupObject==null || groupObject.equals(property.entity.getToDraw(entity))) && group.hasChild(property.entity.propertyObject.property)) {
                result.add(property);
            }
        }

        return result;
    }

    public List<PropertyDrawView> getProperties(Property prop, GroupObjectEntity groupObject) {

        List<PropertyDrawView> result = new ArrayList<>();

        for (PropertyDrawView property : getPropertiesList()) {
            if (groupObject.equals(property.entity.getToDraw(entity)) && prop.equals(property.entity.propertyObject.property)) {
                result.add(property);
            }
        }

        return result;
    }

    public List<PropertyDrawView> getProperties(Property prop) {

        List<PropertyDrawView> result = new ArrayList<>();

        for (PropertyDrawView property : getPropertiesIt()) {
            if (prop.equals(property.entity.propertyObject.property)) {
                result.add(property);
            }
        }

        return result;
    }

    public List<PropertyDrawView> getProperties(GroupObjectEntity groupObject) {

        List<PropertyDrawView> result = new ArrayList<>();

        for (PropertyDrawView property : getPropertiesIt()) {
            if (groupObject.equals(property.entity.getToDraw(entity))) {
                result.add(property);
            }
        }

        return result;
    }

    public void setFont(FontInfo font) {

        for (PropertyDrawView property : getPropertiesIt()) {
            setFont(property, font);
        }
    }

    public void setFont(AbstractGroup group, FontInfo font) {

        for (PropertyDrawView property : getProperties(group)) {
            setFont(property, font);
        }
    }

    public void setFont(AbstractGroup group, FontInfo font, GroupObjectEntity groupObject) {
        
        for (PropertyDrawView property : getProperties(group, groupObject)) {
            setFont(property, font);
        }
    }

    public void setFont(FontInfo font, GroupObjectEntity groupObject) {

        for (PropertyDrawView property : getProperties(groupObject)) {
            setFont(property, font);
        }
    }

    public void setFont(LP property, FontInfo font, GroupObjectEntity groupObject) {
        setFont(property.property, font, groupObject);
    }

    public void setFont(Property property, FontInfo font, GroupObjectEntity groupObject) {

        for (PropertyDrawView propertyView : getProperties(property, groupObject)) {
            setFont(propertyView, font);
        }
    }

    public void setFont(LP property, FontInfo font) {
        setFont(property.property, font);
    }

    public void setFont(Property property, FontInfo font) {

        for (PropertyDrawView propertyView : getProperties(property)) {
            setFont(propertyView, font);
        }
    }

    public void setFont(PropertyDrawView property, FontInfo font) {
        property.design.setFont(font);
    }

    public void setCaptionFont(FontInfo captionFont) {
        for (PropertyDrawView property : getPropertiesIt()) {
            setCaptionFont(property, captionFont);
        }
    }

    public void setCaptionFont(FontInfo captionFont, GroupObjectEntity groupObject) {
        for (PropertyDrawView property : getProperties(groupObject)) {
            setCaptionFont(property, captionFont);
        }
    }

    public void setCaptionFont(PropertyDrawView property, FontInfo captionFont) {
        property.design.setCaptionFont(captionFont);
    }

    public void setBackground(AbstractGroup group, Color background, GroupObjectEntity groupObject) {
        for (PropertyDrawView property : getProperties(group, groupObject)) {
            setBackground(property, background);
        }
    }

    public void setBackground(LP prop, Color background) {
        setBackground(prop.property, background);
    }

    public void setBackground(Color background, GroupObjectEntity groupObject, LP... props) {
        for(LP prop : props)
            setBackground(prop.property, groupObject, background);
    }

    public void setBackground(Property prop, GroupObjectEntity groupObject, Color background) {

        for (PropertyDrawView property : getProperties(prop, groupObject)) {
            setBackground(property, background);
        }
    }

    public void setBackground(Property prop, Color background) {

        for (PropertyDrawView property : getProperties(prop)) {
            setBackground(property, background);
        }
    }

    public void setBackground(PropertyDrawView property, Color background) {
        property.design.background = background;
    }

    public void setFocusable(AbstractGroup group, boolean focusable, GroupObjectEntity groupObject) {

        for (PropertyDrawView property : getProperties(group, groupObject)) {
            setFocusable(property, focusable);
        }
    }

    public void setFocusable(LP property, boolean focusable) {
        setFocusable(property.property, focusable);
    }

    public void setFocusable(LP property, boolean focusable, GroupObjectEntity groupObject) {
        setFocusable(property.property, focusable, groupObject);
    }

    public void setFocusable(Property property, boolean focusable) {

        for (PropertyDrawView propertyView : getProperties(property)) {
            setFocusable(propertyView, focusable);
        }
    }

    public void setFocusable(Property property, boolean focusable, GroupObjectEntity groupObject) {

        for (PropertyDrawView propertyView : getProperties(property, groupObject)) {
            setFocusable(propertyView, focusable);
        }
    }

    public void setFocusable(boolean focusable, GroupObjectEntity groupObject) {

        for (PropertyDrawView propertyView : getProperties(groupObject)) {
            setFocusable(propertyView, focusable);
        }
    }

    public void setFocusable(ObjectEntity objectEntity, boolean focusable) {
        for (PropertyDrawView property : getProperties(objectEntity.groupTo)) {
            setFocusable(property, focusable);
        }
    }

    public void setFocusable(PropertyDrawView property, boolean focusable) {
        property.focusable = focusable;
    }

    public void setEditOnSingleClick(Boolean editOnSingleClick, Type type) {

        for (PropertyDrawView propertyView : getPropertiesIt()) {
            if (propertyView.entity.propertyObject.property.getType().equals(type))
                setEditOnSingleClick(propertyView, editOnSingleClick);
        }
    }

    public void setEditOnSingleClick(Boolean editOnSingleClick, GroupObjectEntity groupObject, Type type) {

        for (PropertyDrawView propertyView : getProperties(groupObject)) {
            if (propertyView.entity.propertyObject.property.getType().equals(type))
                setEditOnSingleClick(propertyView, editOnSingleClick);
        }
    }

    public void setEditOnSingleClick(PropertyDrawView property, Boolean editOnSingleClick) {
        property.editOnSingleClick = editOnSingleClick;
    }

    public void setKeyStroke(KeyStroke keyStroke) {
        this.keyStroke = keyStroke;
    }

    public void setCaption(LocalizedString caption) {
        this.caption = caption;
    }

    public void setEnabled(AbstractGroup group, boolean enabled, GroupObjectEntity groupObject) {
        setFocusable(group, enabled, groupObject);
        entity.setEditType(group, PropertyEditType.getReadonlyType(!enabled), groupObject);
    }

    public void setEnabled(LP property, boolean enabled) {
        setFocusable(property, enabled);
        entity.setEditType(property, PropertyEditType.getReadonlyType(!enabled));
    }

    public void setEnabled(LP property, boolean enabled, GroupObjectEntity groupObject) {
        setFocusable(property, enabled, groupObject);
        entity.setEditType(property, PropertyEditType.getReadonlyType(!enabled), groupObject);
    }

    public void setEnabled(Property property, boolean enabled) {
        setFocusable(property, enabled);
        entity.setEditType(property, PropertyEditType.getReadonlyType(!enabled));
    }

    public void setEnabled(Property property, boolean enabled, GroupObjectEntity groupObject) {
        setFocusable(property, enabled, groupObject);
        entity.setEditType(property, PropertyEditType.getReadonlyType(!enabled), groupObject);
    }

    public void setEnabled(boolean enabled, GroupObjectEntity groupObject) {
        setFocusable(enabled, groupObject);
        entity.setEditType(PropertyEditType.getReadonlyType(!enabled), groupObject);
    }

    public void setEnabled(ObjectEntity objectEntity, boolean enabled) {
        setFocusable(objectEntity, enabled);
        entity.setEditType(objectEntity, PropertyEditType.getReadonlyType(!enabled));
    }

    public void setEnabled(PropertyDrawView property, boolean enabled) {
        setFocusable(property, enabled);
        entity.setEditType(property.entity, PropertyEditType.getReadonlyType(!enabled));
    }

    public void setChangeKey(LP property, KeyStroke keyStroke, GroupObjectEntity groupObject) {
        setChangeKey(property.property, keyStroke, groupObject);
    }

    public void setChangeKey(LP property, KeyStroke keyStroke) {
        setChangeKey(property.property, keyStroke);
    }

    public void setChangeKey(Property property, KeyStroke keyStroke, GroupObjectEntity groupObject) {

        for (PropertyDrawView propertyView : getProperties(property, groupObject)) {
            setChangeKey(propertyView, keyStroke);
        }
    }

    public void setChangeKey(Property property, KeyStroke keyStroke) {

        for (PropertyDrawView propertyView : getProperties(property)) {
            setChangeKey(propertyView, keyStroke);
        }
    }

    public void setChangeKey(PropertyDrawView property, KeyStroke keyStroke) {
        property.changeKey = keyStroke;
    }

    public void setPanelCaptionAbove(AbstractGroup group, boolean panelCaptionAbove, GroupObjectEntity groupObject) {

        for (PropertyDrawView property : getProperties(group, groupObject)) {
            setPanelCaptionAbove(property, panelCaptionAbove);
        }
    }

    public void setPanelCaptionAbove(AbstractGroup group, boolean panelCaptionAbove) {

        for (PropertyDrawView property : getProperties(group)) {
            setPanelCaptionAbove(property, panelCaptionAbove);
        }
    }

    public void setPanelCaptionAbove(PropertyDrawView property, boolean panelCaptionAbove) {
        property.panelCaptionAbove = panelCaptionAbove;
    }

    public void setPreferredSize(AbstractGroup group, Dimension size) {
        for (PropertyDrawView property : getProperties(group)) {
            setPreferredSize(property, size);
        }
    }

    private void setPreferredSize(PropertyDrawView property, Dimension size) {
        property.setPreferredSize(new Dimension(size));
    }

    public void setPropertyDrawViewHide(boolean hide, PropertyDrawEntity... properties) {
        for (PropertyDrawEntity property : properties) {
            setPropertyDrawViewHide(property, hide);
        }
    }

    public void setPropertyDrawViewHide(PropertyDrawEntity property, boolean hide) {
        getProperty(property).hide = hide;
    }

    protected void setComponentSID(ComponentView component, String sid, Version version) {
        component.setSID(sid);
        addComponentToMapping(component, version);
    }

    public ContainerView getContainerBySID(String sid, Version version) {
        ComponentView component = getComponentBySID(sid, version);
        if (component != null && !(component instanceof ContainerView)) {
            throw new IllegalStateException(sid + " component has to be container");
        }
        return (ContainerView) component;
    }

    private PropertyDrawView setupFormButton(PropertyDrawEntity function, String type, Version version) {
        PropertyDrawView functionView = getNFProperty(function, version);        
        setComponentSID(functionView, getClientFunctionSID(type), version);
        return functionView;         
    }

    private static String getMainContainerSID() {
        return "main";
    }

    private static String getRegularFilterGroupSID(RegularFilterGroupEntity entity) {
        return "filters." + entity.getSID();
    }

    private static String getGridSID(PropertyGroupContainerView entity) {
        return entity.getPropertyGroupContainerSID() + ".grid";
    }

    private static String getToolbarSID(PropertyGroupContainerView entity) {
        return entity.getPropertyGroupContainerSID() + ".toolbar";
    }

    private static String getFilterSID(PropertyGroupContainerView entity) {
        return entity.getPropertyGroupContainerSID() + ".filter";
    }
    
    private static String getCalculationsSID(PropertyGroupContainerView entity) {
        return entity.getPropertyGroupContainerSID() + ".calculations";
    }

    private static String getShowTypeSID(PropertyGroupContainerView entity) {
        return entity.getPropertyGroupContainerSID() + ".showType";
    }

    private static String getClassChooserSID(ObjectEntity entity) {
        return entity.getSID() + ".classChooser";
    }

    private static String getClientFunctionSID(String type) {
        return "functions." + type;
    }

    public void customSerialize(ServerSerializationPool pool, DataOutputStream outStream, String serializationType) throws IOException {
        pool.serializeObject(outStream, mainContainer, serializationType);
        pool.serializeCollection(outStream, getTreeGroupsList(), serializationType);
        pool.serializeCollection(outStream, getGroupObjectsListIt(), serializationType);
        pool.serializeCollection(outStream, getPropertiesList(), serializationType);
        pool.serializeCollection(outStream, getRegularFiltersList());

        ImOrderMap<PropertyDrawView, Boolean> defaultOrders = getDefaultOrders();
        int size = defaultOrders.size();
        outStream.writeInt(size);
        for (int i=0;i<size;i++) {
            pool.serializeObject(outStream, defaultOrders.getKey(i), serializationType);
            outStream.writeBoolean(defaultOrders.getValue(i));
        }

        pool.writeObject(outStream, keyStroke);
        pool.writeString(outStream, ThreadLocalContext.localize(caption));
        pool.writeString(outStream, canonicalName);
        pool.writeString(outStream, creationPath);
        pool.writeInt(outStream, overridePageWidth);
        outStream.writeInt(autoRefresh);
    }

    public void customDeserialize(ServerSerializationPool pool, DataInputStream inStream) throws IOException {
        mainContainer = pool.deserializeObject(inStream);
        treeGroups = NFFact.finalOrderSet(pool.<TreeGroupView>deserializeList(inStream));
        groupObjects = NFFact.finalOrderSet(pool.<GroupObjectView>deserializeList(inStream));
        properties = NFFact.finalOrderSet(pool.<PropertyDrawView>deserializeList(inStream));
        regularFilters = NFFact.finalOrderSet(pool.<RegularFilterGroupView>deserializeList(inStream));

        int orderCount = inStream.readInt();
        MOrderExclMap<PropertyDrawView, Boolean> mDefaultOrders = MapFact.mOrderExclMap(orderCount);
        for (int i = 0; i < orderCount; i++) {
            PropertyDrawView order = pool.deserializeObject(inStream);
            mDefaultOrders.exclAdd(order, inStream.readBoolean());
        }
        defaultOrders = NFFact.finalOrderMap(mDefaultOrders.immutableOrder());

        keyStroke = pool.readObject(inStream);
        caption = LocalizedString.create(pool.readString(inStream));
        canonicalName = pool.readString(inStream);
        creationPath = pool.readString(inStream);
        overridePageWidth = pool.readInt(inStream);
        autoRefresh = inStream.readInt();

        entity = pool.context.entity;

        fillComponentMaps();
    }

    public void finalizeAroundInit() {
        treeGroups.finalizeChanges();
        groupObjects.finalizeChanges();
        
        for(TreeGroupView property : getTreeGroupsIt())
            property.finalizeAroundInit();

        for(GroupObjectView property : getGroupObjectsIt())
            property.finalizeAroundInit();

        for(PropertyDrawView property : getPropertiesIt())
            property.finalizeAroundInit();

        defaultOrders.finalizeChanges();

        for(RegularFilterGroupView regularFilter : getRegularFiltersIt())
            regularFilter.finalizeAroundInit();
                
        mainContainer.finalizeAroundInit();
        componentSIDHandler.finalizeChanges();
    }
}