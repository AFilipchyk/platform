package lsfusion.gwt.client.form.object.table.grid.controller;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import lsfusion.gwt.client.ClientMessages;
import lsfusion.gwt.client.GFormChanges;
import lsfusion.gwt.client.base.GwtClientUtils;
import lsfusion.gwt.client.classes.data.GIntegralType;
import lsfusion.gwt.client.form.controller.GFormController;
import lsfusion.gwt.client.form.design.GComponent;
import lsfusion.gwt.client.form.filter.user.GPropertyFilter;
import lsfusion.gwt.client.form.object.GGroupObject;
import lsfusion.gwt.client.form.object.GGroupObjectValue;
import lsfusion.gwt.client.form.object.panel.controller.GPanelController;
import lsfusion.gwt.client.form.object.table.controller.GAbstractTableController;
import lsfusion.gwt.client.form.object.table.grid.user.design.GGridUserPreferences;
import lsfusion.gwt.client.form.object.table.grid.user.design.GGroupObjectUserPreferences;
import lsfusion.gwt.client.form.object.table.grid.user.design.view.GUserPreferencesButton;
import lsfusion.gwt.client.form.object.table.grid.user.toolbar.view.GCalculateSumButton;
import lsfusion.gwt.client.form.object.table.grid.user.toolbar.view.GCountQuantityButton;
import lsfusion.gwt.client.form.object.table.grid.user.toolbar.view.GToolbarButton;
import lsfusion.gwt.client.form.object.view.GShowTypeView;
import lsfusion.gwt.client.form.order.user.GOrder;
import lsfusion.gwt.client.form.property.*;
import lsfusion.gwt.client.form.property.cell.GEditBindingMap;

import java.util.*;

import static lsfusion.gwt.client.base.GwtClientUtils.isShowing;
import static lsfusion.gwt.client.form.property.GClassViewType.DEFAULT;

public class GGridController extends GAbstractTableController {
    private final ClientMessages messages = ClientMessages.Instance.get();
    public GGroupObject groupObject;

    private GGridTableController grid;
    private GShowTypeView showTypeView;

    private GClassViewType classView = DEFAULT;

    private GCountQuantityButton quantityButton;
    private GCalculateSumButton sumButton;

    public GGridController(GFormController iformController) {
        this(iformController, null, null);
    }

    public GGridController(GFormController iformController, GGroupObject igroupObject, GGridUserPreferences[] userPreferences) {
        super(iformController, igroupObject == null ? null : igroupObject.toolbar);
        groupObject = igroupObject;

        if (groupObject != null) {
            grid = new GGridTableController(groupObject.grid, formController, this, userPreferences);
            grid.addToLayout(getFormLayout());

            showTypeView = new GShowTypeView(formController, groupObject);
            showTypeView.addToLayout(getFormLayout());
            showTypeView.setBanClassViews(groupObject.banClassView);

            configureToolbar();
        }
    }
    
    public GGridTableController getGrid() {
        return grid;
    }

    private void configureToolbar() {
        addFilterButton();
        if (filter != null && grid != null) {
            addToToolbar(GwtClientUtils.createHorizontalStrut(5));
        }

        if (groupObject.toolbar.showGroupChange) {
            addToToolbar(new GToolbarButton("groupchange.png", messages.formGridGroupGroupChange() + " (F12)") {
                @Override
                public void addListener() {
                    addClickHandler(new ClickHandler() {
                        @Override
                        public void onClick(ClickEvent event) {
                            grid.getTable().editCurrentCell(GEditBindingMap.GROUP_CHANGE);
                        }
                    });
                }
            });
        }

        if (groupObject.toolbar.showCountQuantity) {
            quantityButton = new GCountQuantityButton() {
                public void addListener() {
                    addClickHandler(new ClickHandler() {
                        @Override
                        public void onClick(ClickEvent event) {
                            formController.countRecords(groupObject);
                        }
                    });
                }
            };
            addToToolbar(quantityButton);
        }

        if (groupObject.toolbar.showCalculateSum) {
            sumButton = new GCalculateSumButton() {
                @Override
                public void addListener() {
                    addClickHandler(new ClickHandler() {
                        @Override
                        public void onClick(ClickEvent event) {
                            GPropertyDraw property = getSelectedProperty();
                            if (property.baseType instanceof GIntegralType) {
                                formController.calculateSum(groupObject, getSelectedProperty(), grid.getTable().getCurrentColumnKey());
                            } else {
                                showSum(null, property);
                            }
                        }
                    });
                }
            };
            addToToolbar(sumButton);
        }

        if (groupObject.toolbar.showPrintGroup || groupObject.toolbar.showPrintGroupXls) {
            addToToolbar(GwtClientUtils.createHorizontalStrut(5));
        }

        if (groupObject.toolbar.showPrintGroup) {
            addToToolbar(new GToolbarButton("reportbw.png", messages.formGridPrintGrid()) {
                @Override
                public void addListener() {
                    addClickHandler(new ClickHandler() {
                        @Override
                        public void onClick(ClickEvent event) {
                            formController.runGroupReport(groupObject.ID, false);
                        }
                    });
                }
            });
        }

        if (groupObject.toolbar.showPrintGroupXls) {
            addToToolbar(new GToolbarButton("excelbw.png", messages.formGridExportToXlsx()) {
                @Override
                public void addListener() {
                    addClickHandler(new ClickHandler() {
                        @Override
                        public void onClick(ClickEvent event) {
                            formController.runGroupReport(groupObject.ID, true);
                        }
                    });
                }
            });
        }

        if (groupObject.toolbar.showGridSettings) {
            addToToolbar(GwtClientUtils.createHorizontalStrut(5));
            addToToolbar(new GUserPreferencesButton(grid.getTable(), this, formController.hasCanonicalName()));
        }
    }

    public void showRecordQuantity(int quantity) {
        quantityButton.showPopup(quantity);
    }

    public void showSum(Number sum, GPropertyDraw property) {
        sumButton.showPopup(sum, property);
    }

    public void processFormChanges(GFormChanges fc, HashMap<GGroupObject, List<GGroupObjectValue>> currentGridObjects) {
        for (GPropertyDraw property : fc.dropProperties) {
            if (property.groupObject == groupObject) {
                removeProperty(property);
            }
        }

        GClassViewType classViewValue = fc.classViews.get(groupObject);
        if (classViewValue != null) {
            setClassView(classViewValue);
        }

        for (GPropertyReader propertyReader : fc.properties.keySet()) {
            if (propertyReader instanceof GPropertyDraw) {
                GPropertyDraw property = (GPropertyDraw) propertyReader;
                if (property.groupObject == groupObject && !fc.updateProperties.contains(property)) {
                    addProperty(property, fc.panelProperties.contains(property));

                    if (property.columnGroupObjects != null) {
                        LinkedHashMap<GGroupObject, List<GGroupObjectValue>> groupColumnKeys = new LinkedHashMap<>();
                        for (GGroupObject columnGroupObject : property.columnGroupObjects) {
                            List<GGroupObjectValue> columnGroupKeys = currentGridObjects.get(columnGroupObject);
                            if (columnGroupKeys != null) {
                                groupColumnKeys.put(columnGroupObject, columnGroupKeys);
                            }
                        }

                        updateDrawColumnKeys(property, GGroupObject.mergeGroupValues(groupColumnKeys));
                    }
                }
            }
        }

        if (classView.isGrid()) {
            ArrayList<GGroupObjectValue> keys = fc.gridObjects.get(groupObject);
            if (keys != null && grid != null) {
                grid.getTable().setKeys(keys);
            }

            GGroupObjectValue currentKey = fc.objects.get(groupObject);
            if (currentKey != null && grid != null) {
                grid.getTable().setCurrentKey(currentKey);
            }
        }

        for (Map.Entry<GPropertyReader, HashMap<GGroupObjectValue, Object>> readProperty : fc.properties.entrySet()) {
            GPropertyReader propertyReader = readProperty.getKey();
            if (formController.getGroupObject(propertyReader.getGroupObjectID()) == groupObject) {
                propertyReader.update(this, readProperty.getValue(), propertyReader instanceof GPropertyDraw && fc.updateProperties.contains(propertyReader));
            }
        }

        update();
    }

    private void updateDrawColumnKeys(GPropertyDraw property, List<GGroupObjectValue> columnKeys) {
        if (panel.containsProperty(property)) {
            panel.updateColumnKeys(property, columnKeys);
        } else if (grid != null) {
            grid.updateColumnKeys(property, columnKeys);
        }
    }

    @Override
    public void updatePropertyDrawValues(GPropertyDraw reader, Map<GGroupObjectValue, Object> values, boolean updateKeys) {
        GPropertyDraw property = formController.getProperty(reader.ID);
        if (panel.containsProperty(property)) {
            panel.updatePropertyValues(property, values, updateKeys);
        } else if (grid != null) {
            grid.getTable().updatePropertyValues(property, values, updateKeys);
        }
    }

    @Override
    public void updateBackgroundValues(GBackgroundReader reader, Map<GGroupObjectValue, Object> values) {
        GPropertyDraw property = formController.getProperty(reader.readerID);
        if (panel.containsProperty(property)) {
            panel.updateCellBackgroundValues(property, values);
        } else if (grid != null) {
            grid.updateCellBackgroundValues(property, values);
        }
    }

    @Override
    public void updateForegroundValues(GForegroundReader reader, Map<GGroupObjectValue, Object> values) {
        GPropertyDraw property = formController.getProperty(reader.readerID);
        if (panel.containsProperty(property)) {
            panel.updateCellForegroundValues(property, values);
        } else if (grid != null) {
            grid.updateCellForegroundValues(property, values);
        }
    }

    @Override
    public void updateCaptionValues(GCaptionReader reader, Map<GGroupObjectValue, Object> values) {
        GPropertyDraw property = formController.getProperty(reader.readerID);
        if (panel.containsProperty(property)) {
            panel.updatePropertyCaptions(property, values);
        } else if (grid != null) {
            grid.updatePropertyCaptions(property, values);
        }
    }

    @Override
    public void updateShowIfValues(GShowIfReader reader, Map<GGroupObjectValue, Object> values) {
        GPropertyDraw property = formController.getProperty(reader.readerID);
        if (panel.containsProperty(property)) {
            panel.updateShowIfValues(property, values);
        } else if (grid != null) {
            grid.updateShowIfValues(property, values);
        }
    }

    @Override
    public void updateReadOnlyValues(GReadOnlyReader reader, Map<GGroupObjectValue, Object> values) {
        GPropertyDraw property = formController.getProperty(reader.readerID);
        if (panel.containsProperty(property)) {
            panel.updateReadOnlyValues(property, values);
        } else if (grid != null) {
            grid.updateReadOnlyValues(property, values);
        }
    }

    @Override
    public void updateRowBackgroundValues(Map<GGroupObjectValue, Object> values) {
        if (isInGridClassView()) {
            grid.updateRowBackgroundValues(values);
        } else {
            if (values != null && !values.isEmpty()) {
                panel.updateRowBackgroundValue(values.values().iterator().next());
            }
        }
    }

    @Override
    public void updateRowForegroundValues(Map<GGroupObjectValue, Object> values) {
        if (isInGridClassView()) {
            grid.updateRowForegroundValues(values);
        } else {
            if (values != null && !values.isEmpty()) {
                panel.updateRowForegroundValue(values.values().iterator().next());
            }
        }
    }

    public void setClassView(GClassViewType newClassView) {
        if (newClassView != null && newClassView != classView) {
            classView = newClassView;
        }
    }

    private void removeProperty(GPropertyDraw property) {
        panel.removeProperty(property);
        if (grid != null) {
            grid.getTable().removeProperty(property);
        }
    }

    private void addProperty(GPropertyDraw property, boolean toPanel) {
        if (toPanel) {
            addPanelProperty(property);
        } else {
            addGridProperty(property);
        }
    }

    private void addGridProperty(GPropertyDraw property) {
        if (grid != null) {
            grid.getTable().addProperty(property);
        }
        panel.removeProperty(property);
    }

    private void addPanelProperty(GPropertyDraw property) {
        if (grid != null) {
            grid.getTable().removeProperty(property);
        }
        panel.addProperty(property);
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

            formController.setFiltersVisible(groupObject, grid.isVisible());

            showTypeView.update(classView);
        }

        panel.update();
        panel.setVisible(!classView.isHidden());
    }

    public void beforeHidingGrid() {
        if (grid != null) {
            grid.beforeHiding();
        }
    }

    public void afterShowingGrid() {
        if (grid != null) {
            grid.afterShowing();
        }
    }

    public void restoreScrollPosition() {
        if (grid != null) {
            grid.restoreScrollPosition();
        }
    }

    public boolean isInGridClassView() {
        return classView.isGrid();
    }

    public boolean isGridEmpty() {
        return grid == null || grid.getTable().isEmpty();
    }

    public GGroupObjectValue getCurrentKey() {
        GGroupObjectValue result = null;
        if (grid != null) {
            result = grid.getTable().getCurrentKey();
        }
        return result == null ? GGroupObjectValue.EMPTY : result;
    }

    @Override
    public void changeOrder(GPropertyDraw property, GOrder modiType) {
        grid.changeOrder(property, modiType);
    }

    @Override
    public void clearOrders(GGroupObject groupObject) {
        if (grid != null) {
            grid.clearGridOrders(groupObject);
        }
    }

    public LinkedHashMap<GPropertyDraw, Boolean> getUserOrders() {
        boolean hasUserPreferences = getGrid() != null && getGrid().getTable().hasUserPreferences();
        if (hasUserPreferences) {
            LinkedHashMap<GPropertyDraw, Boolean> userOrders = new LinkedHashMap<>();
            List<GPropertyDraw> propertyDrawList = getGroupObjectProperties();
            Collections.sort(propertyDrawList, getGrid().getTable().getUserSortComparator());
            for (GPropertyDraw property : propertyDrawList) {
                if (getGrid().getTable().getUserSort(property) != null && getGrid().getTable().getUserAscendingSort(property) != null) {
                    userOrders.put(property, getGrid().getTable().getUserAscendingSort(property));
                }
            }
            return userOrders;
        }
        return null;
    }
    
    public void applyUserOrders() {
        formController.applyOrders(getUserOrders(), this);    
    }

    public void applyDefaultOrders() {
        formController.applyOrders(formController.getDefaultOrders(groupObject), this);
    }

    public GGroupObjectUserPreferences getUserGridPreferences() {
        return grid.getTable().getCurrentUserGridPreferences();
    }

    public GGroupObjectUserPreferences getGeneralGridPreferences() {
        return grid.getTable().getGeneralGridPreferences();
    }

    @Override
    public GGroupObject getSelectedGroupObject() {
        return groupObject;
    }

    @Override
    public List<GPropertyDraw> getGroupObjectProperties() {
        ArrayList<GPropertyDraw> properties = new ArrayList<>();
        for (GPropertyDraw property : formController.getPropertyDraws()) {
            if (groupObject.equals(property.groupObject)) {
                properties.add(property);
            }
        }
        return properties;
    }
    
    public boolean isPropertyInGrid(GPropertyDraw property) {
        return grid != null && grid.containsProperty(property);
    }
    
    public boolean isPropertyInPanel(GPropertyDraw property) {
        return panel.containsProperty(property);
    }
    
    @Override
    public GPropertyDraw getSelectedProperty() {
        return grid.getCurrentProperty();
    }
    @Override
    public GGroupObjectValue getSelectedColumn() {
        return grid.getCurrentColumn();
    }

    @Override
    public Object getSelectedValue(GPropertyDraw property, GGroupObjectValue columnKey) {
        return grid.getSelectedValue(property, columnKey);
    }

    public void modifyGroupObject(GGroupObjectValue key, boolean add, int position) {
        assert classView.isGrid();

        grid.modifyGridObject(key, add, position);
    }

    public boolean focusFirstWidget() {
        if (grid != null && !grid.getTable().isEmpty() && isShowing(grid.getTable())) {
            grid.getTable().setFocus(true);
            return true;
        }

        return panel.focusFirstWidget();
    }

    @Override
    public GComponent getGridComponent() {
        return groupObject != null ? groupObject.grid : null;
    }

    @Override
    protected boolean showFilter() {
        return groupObject != null && groupObject.filter.visible;
    }

    @Override
    protected void changeFilter(List<GPropertyFilter> conditions) {
        formController.changeFilter(groupObject, conditions);
    }

    @Override
    public void setFilterVisible(boolean visible) {
        if (isInGridClassView()) {
            super.setFilterVisible(visible);
        }
    }
    
    public void reattachFilter() {
        if (filter != null) {
            filter.reattachDialog();
        }    
    }

    public void selectProperty(GPropertyDraw propertyDraw) {
        grid.selectProperty(propertyDraw);
    }

    public void focusProperty(GPropertyDraw propertyDraw) {
        GPanelController.GPropertyController propertyController = panel.getPropertyController(propertyDraw);
        if (propertyController != null) {
            propertyController.focusFirstWidget();
        } else {
            grid.selectProperty(propertyDraw);
            grid.getTable().setFocus(true);
        }
    }
}
