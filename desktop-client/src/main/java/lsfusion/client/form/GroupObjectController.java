package lsfusion.client.form;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.heavy.OrderedMap;
import lsfusion.client.ClientResourceBundle;
import lsfusion.client.form.user.queries.CalculationsView;
import lsfusion.client.form.object.grid.GridController;
import lsfusion.client.form.user.preferences.GridUserPreferences;
import lsfusion.client.form.layout.ClientFormLayout;
import lsfusion.client.form.object.panel.PanelController;
import lsfusion.client.form.object.panel.PropertyController;
import lsfusion.client.form.user.queries.FilterController;
import lsfusion.client.form.property.classes.renderer.link.ImageLinkPropertyRenderer;
import lsfusion.client.form.user.ShowTypeController;
import lsfusion.client.logics.*;
import lsfusion.interop.form.property.ClassViewType;
import lsfusion.interop.form.event.KeyStrokes;
import lsfusion.interop.form.user.Order;
import lsfusion.interop.form.user.GroupObjectUserPreferences;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static lsfusion.client.ClientResourceBundle.getString;

public class GroupObjectController extends AbstractGroupObjectController {
    private final ClientGroupObject groupObject;

    public GridController grid;

    protected CalculationsView calculationsView;
    public ShowTypeController showType;

    private final Map<ClientObject, ObjectController> objects = new HashMap<>();

    public ClassViewType classView = ClassViewType.DEFAULT;

    public GroupObjectController(LogicsSupplier ilogicsSupplier, ClientFormController iform, ClientFormLayout formLayout) throws IOException {
        this(null, ilogicsSupplier, iform, formLayout, null);
    }

    public GroupObjectController(ClientGroupObject igroupObject, LogicsSupplier ilogicsSupplier, ClientFormController iform, final ClientFormLayout formLayout, GridUserPreferences[] userPreferences) throws IOException {
        super(iform, ilogicsSupplier, formLayout, igroupObject == null ? null : igroupObject.toolbar);
        groupObject = igroupObject;

        panel = new PanelController(form, formLayout) {
            protected void addGroupObjectActions(final JComponent comp) {
                GroupObjectController.this.addGroupObjectActions(comp);
                if(filter != null) {
                    filter.getView().addActionsToPanelInputMap(comp);
                }
            }
        };

        if (groupObject != null) {
            calculationsView = new CalculationsView();
            formLayout.add(groupObject.calculations, calculationsView);
            
            if (groupObject.filter.visible) {
                filter = new FilterController(this, groupObject.filter) {
                    protected void remoteApplyQuery() {
                        RmiQueue.runAction(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    form.changeFilter(groupObject, getConditions());
                                    grid.table.requestFocusInWindow();
                                } catch (IOException e) {
                                    throw new RuntimeException(ClientResourceBundle.getString("errors.error.applying.filter"), e);
                                }
                            }
                        });
                    }
                };

                filter.addView(formLayout);
            }

            for (ClientObject object : groupObject.objects) {
                objects.put(object, new ObjectController(object, form));
                objects.get(object).addView(formLayout);
            }

            // GRID идет как единый неделимый JComponent, поэтому смысла передавать туда FormLayout нет
            grid = new GridController(this, form, userPreferences);
            addGroupObjectActions(grid.getGridView());
            if (filter != null) {
                filter.getView().addActionsToInputMap(grid.table);
            }
            grid.addView(formLayout);

            showType = new ShowTypeController(groupObject, this, form);
            showType.addView(formLayout);

            configureToolbar();
        }

        update();
    }

    private void configureToolbar() {
        boolean hasClassChoosers = false;
        for (final ClientObject object : groupObject.grid.groupObject.objects) {
            if (object.classChooser.visible) {
                addToToolbar(getObjectController(object).getToolbarButton());
                hasClassChoosers = true;
            }
        }
        if (hasClassChoosers) {
            addToToolbar(Box.createHorizontalStrut(5));
        }

        if (filter != null) {
            addToToolbar(filter.getToolbarButton());
            addToToolbar(Box.createHorizontalStrut(5));
        }

        if (groupObject.toolbar.showGroupChange) {
            addToToolbar(grid.createGroupChangeButton());
        }

        if (groupObject.toolbar.showCountRows) {
            addToToolbar(grid.createCountQuantityButton());
        }

        if (groupObject.toolbar.showCalculateSum) {
            addToToolbar(grid.createCalculateSumButton());
        }

        if (groupObject.toolbar.showGroupReport) {
            addToToolbar(grid.createGroupingButton());
        }

        addToToolbar(Box.createHorizontalStrut(5));

        if (groupObject.toolbar.showPrint) {
            addToToolbar(grid.createPrintGroupButton());
        }

        if (groupObject.toolbar.showXls) {
            addToToolbar(grid.createPrintGroupXlsButton());
            addToToolbar(Box.createHorizontalStrut(5));
        }

        if (groupObject.toolbar.showSettings) {
            addToToolbar(grid.createGridSettingsButton());
            addToToolbar(Box.createHorizontalStrut(5));
        }
    }

    public void processFormChanges(ClientFormChanges fc,
                                   Map<ClientGroupObject, List<ClientGroupObjectValue>> cachedGridObjects
    ) {

        // Сначала меняем виды объектов
        for (ClientPropertyReader read : fc.properties.keySet()) // интересуют только свойства
        {
            if (read instanceof ClientPropertyDraw) {
                ClientPropertyDraw property = (ClientPropertyDraw) read;
                if (property.groupObject == groupObject && property.shouldBeDrawn(form) && !fc.updateProperties.contains(property)) {
                    ImageLinkPropertyRenderer.clearChache(property);
                    
                    addDrawProperty(property, fc.panelProperties.contains(property));

                    OrderedMap<ClientGroupObject, List<ClientGroupObjectValue>> groupColumnKeys = new OrderedMap<>();
                    for (ClientGroupObject columnGroupObject : property.columnGroupObjects) {
                        if (cachedGridObjects.containsKey(columnGroupObject)) {
                            groupColumnKeys.put(columnGroupObject, cachedGridObjects.get(columnGroupObject));
                        }
                    }

                    updateDrawColumnKeys(property, ClientGroupObject.mergeGroupValues(groupColumnKeys));
                }
            }
        }

        for (ClientPropertyDraw property : fc.dropProperties) {
            if (property.groupObject == groupObject) {
                dropProperty(property);
            }
        }

        ClassViewType newClassView = fc.classViews.get(groupObject);
        if (newClassView != null && classView != newClassView) {
            setClassView(newClassView);
//            requestFocusInWindow();
        }

        // Затем подгружаем новые данные

        if (isGrid()) {
            if (fc.gridObjects.containsKey(groupObject)) {
                setRowKeysAndCurrentObject(fc.gridObjects.get(groupObject), fc.objects.get(groupObject));
            }
        }

        // Затем их свойства
        for (Map.Entry<ClientPropertyReader, Map<ClientGroupObjectValue, Object>> readProperty : fc.properties.entrySet()) {
            ClientPropertyReader propertyRead = readProperty.getKey();
            if (propertyRead.getGroupObject() == groupObject && propertyRead.shouldBeDrawn(form)) {
                propertyRead.update(readProperty.getValue(), fc.updateProperties.contains(propertyRead), this);
            }
        }

        update();
    }

    public void setClassView(ClassViewType classView) {
        this.classView = classView;
    }

    public void addPanelProperty(ClientPropertyDraw property) {
        if (grid != null) {
            grid.removeProperty(property);
        }

        panel.addProperty(property);
    }

    public void addGridProperty(ClientPropertyDraw property) {
        grid.addProperty(property);

        panel.removeProperty(property);
    }

    public void addDrawProperty(ClientPropertyDraw property, boolean toPanel) {
        if (toPanel) {
            addPanelProperty(property);
        } else {
            addGridProperty(property);
        }
    }

    public void dropProperty(ClientPropertyDraw property) {
        if (grid != null) {
            grid.removeProperty(property);
        }

        panel.removeProperty(property);
    }

    public void setRowKeysAndCurrentObject(List<ClientGroupObjectValue> gridObjects, ClientGroupObjectValue newCurrentObject) {
        grid.setRowKeysAndCurrentObject(gridObjects, newCurrentObject);
    }

    public void modifyGroupObject(ClientGroupObjectValue gridObject, boolean add, int position) {
        assert classView.isGrid();

        grid.modifyGridObject(gridObject, add, position); // assert что grid!=null

        grid.update();
    }

    public ClientGroupObjectValue getCurrentObject() {
        return grid != null && grid.getCurrentObject() != null ? grid.getCurrentObject() : ClientGroupObjectValue.EMPTY;
    }
    
    public int getCurrentRow() {
        return grid != null ? grid.table.getCurrentRow() : -1;
    }

    public void updateDrawColumnKeys(ClientPropertyDraw property, List<ClientGroupObjectValue> groupColumnKeys) {
        if (panel.containsProperty(property)) {
            panel.updateColumnKeys(property, groupColumnKeys);
        } else {
            grid.updateColumnKeys(property, groupColumnKeys);
        }
    }

    public void updateDrawPropertyCaptions(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> captions) {
        if (panel.containsProperty(property)) {
            panel.updatePropertyCaptions(property, captions);
        } else {
            grid.updatePropertyCaptions(property, captions);
        }
    }

    public void updateShowIfs(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> showIfs) {
        if (panel.containsProperty(property)) {
            panel.updateShowIfs(property, showIfs);
        } else {
            grid.updateShowIfs(property, showIfs);
        }
    }

    @Override
    public void updateReadOnlyValues(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> values) {
        if (panel.containsProperty(property)) {
            panel.updateReadOnlyValues(property, values);
        } else {
            grid.updateReadOnlyValues(property, values);
        }
    }

    public void updateCellBackgroundValues(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> cellBackgroundValues) {
        if (panel.containsProperty(property)) {
            panel.updateCellBackgroundValues(property, cellBackgroundValues);
        } else {
            grid.updateCellBackgroundValues(property, cellBackgroundValues);
        }
    }

    public void updateCellForegroundValues(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> cellForegroundValues) {
        if (panel.containsProperty(property)) {
            panel.updateCellForegroundValues(property, cellForegroundValues);
        } else {
            grid.updateCellForegroundValues(property, cellForegroundValues);
        }
    }

    public void updateDrawPropertyValues(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> values, boolean updateKeys) {
        if (panel.containsProperty(property)) {
            panel.updatePropertyValues(property, values, updateKeys);
        } else {
            grid.updatePropertyValues(property, values, updateKeys);
        }
    }

    public void updateRowBackgroundValues(Map<ClientGroupObjectValue, Object> rowBackground) {
        if (isGrid()) {
            grid.updateRowBackgroundValues(rowBackground);
        } else {
            panel.updateRowBackgroundValue((Color)BaseUtils.singleValue(rowBackground));
        }
    }

    public boolean isGrid() {
        return classView.isGrid();
    }
    
    public void updateRowForegroundValues(Map<ClientGroupObjectValue, Object> rowForeground) {
        if (isGrid()) {
            grid.updateRowForegroundValues(rowForeground);
        } else {
            panel.updateRowForegroundValue((Color)BaseUtils.singleValue(rowForeground));
        }
    }

    public void changeOrder(ClientPropertyDraw property, Order modiType) throws IOException {
        if (grid != null) {
            grid.changeGridOrder(property, modiType);
        }
    }

    @Override
    public void clearOrders() throws IOException {
        if(grid != null) {
            grid.clearGridOrders(getGroupObject());
        }
    }

    public OrderedMap<ClientPropertyDraw, Boolean> getUserOrders() throws IOException {
        if (grid != null && grid.table.hasUserPreferences()) {
            OrderedMap<ClientPropertyDraw, Boolean> userOrders = new OrderedMap<>();
            List<ClientPropertyDraw> clientPropertyDrawList = getGroupObjectProperties();
            Collections.sort(clientPropertyDrawList, grid.table.getUserSortComparator());
            for (ClientPropertyDraw property : clientPropertyDrawList) {
                if (grid.table.getUserSort(property) != null && grid.table.getUserAscendingSort(property) != null) {
                    userOrders.put(property, grid.table.getUserAscendingSort(property));
                }
            }
            return userOrders;
        }
        return null;
    }

    public void applyUserOrders() throws IOException {
        OrderedMap<ClientPropertyDraw, Boolean> userOrders = getUserOrders();
        assert userOrders != null;
        form.applyOrders(userOrders == null ? new OrderedMap<ClientPropertyDraw, Boolean>() : userOrders, this);
    }
    
    public void applyDefaultOrders() throws IOException {
        form.applyOrders(form.getDefaultOrders(groupObject), this);
    }
    
    public GroupObjectUserPreferences getUserGridPreferences() {
        return grid.table.getCurrentUserGridPreferences();
    }

    public GroupObjectUserPreferences getGeneralGridPreferences() {
        return grid.table.getGeneralGridPreferences();
    }

    // приходится делать именно так, так как логика отображения одного GroupObject может не совпадать с логикой Container-Component
    void addGroupObjectActions(JComponent comp) {
        comp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStrokes.getSwitchClassViewKeyStroke(), "switchClassView");
        comp.getActionMap().put("switchClassView", new AbstractAction() {

            public void actionPerformed(ActionEvent ae) {
                RmiQueue.runAction(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            form.switchClassView(groupObject);
                        } catch (IOException e) {
                            throw new RuntimeException(getString("errors.error.changing.type"), e);
                        }
                    }
                });

            }
        });

        // вот так вот приходится делать, чтобы "узнавать" к какому GroupObject относится этот Component
        comp.putClientProperty("groupObject", groupObject);
    }

    public List<ClientPropertyDraw> getPropertyDraws() {
        return logicsSupplier.getPropertyDraws();
    }

    public ClientGroupObject getGroupObject() {
        return groupObject;
    }

    public ClientGroupObject getSelectedGroupObject() {
        return getGroupObject();
    }

    public ObjectController getObjectController(ClientObject object) {
        return objects.get(object);
    }

    public List<ClientPropertyDraw> getGroupObjectProperties() {
        ArrayList<ClientPropertyDraw> properties = new ArrayList<>();
        for (ClientPropertyDraw property : getPropertyDraws()) {
            if (groupObject.equals(property.groupObject)) {
                properties.add(property);
            }
        }

        return properties;
    }
    
    public boolean isPropertyInGrid(ClientPropertyDraw property) {
        return grid != null && grid.containsProperty(property);
    }
    
    public boolean isPropertyInPanel(ClientPropertyDraw property) {
        return panel.containsProperty(property);
    }

    public ClientPropertyDraw getSelectedProperty() {
        return grid.getCurrentProperty();
    }
    public ClientGroupObjectValue getSelectedColumn() {
        return grid.getCurrentColumn();
    }

    public Object getSelectedValue(ClientPropertyDraw cell, ClientGroupObjectValue columnKey) {
        return grid.getSelectedValue(cell, columnKey);
    }

    public void quickEditFilter(KeyEvent initFilterKeyEvent, ClientPropertyDraw propertyDraw, ClientGroupObjectValue columnKey) {
        if (filter != null) {
            filter.quickEditFilter(initFilterKeyEvent, propertyDraw, columnKey);
        }
    }

    public void selectProperty(ClientPropertyDraw propertyDraw) {
        grid.selectProperty(propertyDraw);
    }

    public void focusProperty(ClientPropertyDraw propertyDraw) {
        PropertyController propertyController = panel.getPropertyController(propertyDraw);
        if (propertyController != null) {
            propertyController.requestFocusInWindow();
        } else {
            grid.selectProperty(propertyDraw);
            grid.requestFocusInWindow();
        }
    }

    public void updateSelectionInfo(int quantity, String sum, String avg) {
        if (calculationsView != null) {
            calculationsView.updateSelectionInfo(quantity, sum, avg);
        }
    }

    private void update() {
        if (groupObject != null) {
            grid.update();

            if (toolbarView != null) {
                toolbarView.setVisible(grid.isVisible());
            }

            if (filter != null) {
                filter.setVisible(grid.isVisible());
            }
            
            if (calculationsView != null) {
                calculationsView.setVisible(grid.isVisible());
            }

            form.setFiltersVisible(groupObject, grid.isVisible());

            for (ClientObject object : groupObject.objects) {
                objects.get(object).setVisible(grid.isVisible());
            }

            showType.update(classView);
        }

        panel.update();
        panel.setVisible(true);
    }
}