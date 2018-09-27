package lsfusion.gwt.form.client.form.ui;

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.core.client.Duration;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.google.gwt.event.dom.client.ScrollHandler;
import com.google.gwt.user.client.Event;
import lsfusion.gwt.base.client.jsni.Function;
import lsfusion.gwt.base.client.jsni.NativeHashMap;
import lsfusion.gwt.base.client.ui.DialogBoxHelper;
import lsfusion.gwt.base.client.ui.GKeyStroke;
import lsfusion.gwt.base.shared.GwtSharedUtils;
import lsfusion.gwt.cellview.client.Column;
import lsfusion.gwt.cellview.client.DataGrid;
import lsfusion.gwt.cellview.client.KeyboardRowChangedEvent;
import lsfusion.gwt.cellview.client.cell.Cell;
import lsfusion.gwt.cellview.client.cell.CellPreviewEvent;
import lsfusion.gwt.form.client.ErrorHandlingCallback;
import lsfusion.gwt.form.client.HotkeyManager;
import lsfusion.gwt.form.client.MainFrameMessages;
import lsfusion.gwt.form.client.form.ui.toolbar.preferences.GGridUserPreferences;
import lsfusion.gwt.form.shared.actions.form.ServerResponseResult;
import lsfusion.gwt.form.shared.view.*;
import lsfusion.gwt.form.shared.view.changes.GGroupObjectValue;
import lsfusion.gwt.form.shared.view.changes.GGroupObjectValueBuilder;
import lsfusion.gwt.form.shared.view.classes.GObjectType;
import lsfusion.gwt.form.shared.view.grid.EditEvent;
import lsfusion.gwt.form.shared.view.grid.GridEditableCell;
import lsfusion.gwt.form.shared.view.grid.editor.TextBasedGridCellEditor;

import java.util.*;

import static java.lang.Boolean.TRUE;
import static java.lang.Math.min;
import static java.lang.String.valueOf;
import static java.util.Collections.singleton;
import static lsfusion.gwt.base.client.GwtClientUtils.isShowing;
import static lsfusion.gwt.base.shared.GwtSharedUtils.*;

public class GGridTable extends GGridPropertyTable<GridDataRecord> {
    private static final MainFrameMessages messages = MainFrameMessages.Instance.get();
    private static final double QUICK_SEARCH_MAX_DELAY = 2000;

    private ArrayList<GPropertyDraw> columnProperties = new ArrayList<>();
    private ArrayList<GGroupObjectValue> columnKeysList = new ArrayList<>();

    private NativeHashMap<GPropertyDraw, NativeHashMap<GGroupObjectValue, GridColumn>> columnsMap = new NativeHashMap<>();

    private ArrayList<GPropertyDraw> properties = new ArrayList<>();

    private ArrayList<GGroupObjectValue> rowKeys = new ArrayList<>();

    private NativeHashMap<GPropertyDraw, NativeHashMap<GGroupObjectValue, Object>> values = new NativeHashMap<>();
    protected Map<GPropertyDraw, Map<GGroupObjectValue, Object>> showIfs = new HashMap<>();
    private Map<GPropertyDraw, Map<GGroupObjectValue, Object>> readOnlyValues = new HashMap<>();

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private NativeHashMap<GPropertyDraw, Boolean> updatedProperties = new NativeHashMap<>();
    private NativeHashMap<GPropertyDraw, List<GGroupObjectValue>> columnKeys = new NativeHashMap<>();

    private boolean rowsUpdated = false;
    private boolean columnsUpdated = false;
    private boolean captionsUpdated = false;
    private boolean dataUpdated = false;

    private final ArrayList<GridDataRecord> currentRecords = new ArrayList<>();
    private GGroupObjectValue currentKey;
    private GGroupObject groupObject;
    private GGridController gridController;

    private GridTableKeyboardSelectionHandler keyboardSelectionHandler;

    private GGroupObjectController groupObjectController;
    
    private GGridUserPreferences generalGridPreferences;
    private GGridUserPreferences userGridPreferences;
    private GGridUserPreferences currentGridPreferences;

    private int nextColumnID = 0;

    private int pageSize = 50;

    private String lastQuickSearchPrefix = "";

    private double lastQuickSearchTime = 0;

    private boolean autoSize;

    @Override
    protected boolean isAutoSize() {
        return autoSize;
    }

    public GGridTable(GFormController iform, GGroupObjectController igroupController, GGridController gridController, GGridUserPreferences[] iuserPreferences, boolean autoSize) {
        super(iform, null, igroupController.groupObject.grid.headerHeight);

        this.groupObjectController = igroupController;
        this.groupObject = igroupController.groupObject;
        this.gridController = gridController;

        this.autoSize = autoSize;

        generalGridPreferences = iuserPreferences != null && iuserPreferences[0] != null ? iuserPreferences[0] : new GGridUserPreferences(groupObject);
        userGridPreferences = iuserPreferences != null && iuserPreferences[1] != null ? iuserPreferences[1] : new GGridUserPreferences(groupObject);
        resetCurrentPreferences(true);

        if (currentGridPreferences.font != null) {
            font = currentGridPreferences.font;
        }
        if (font == null) {
            font = gridController.getFont();
        }

        keyboardSelectionHandler =  new GridTableKeyboardSelectionHandler(this);
        setKeyboardSelectionHandler(keyboardSelectionHandler);
        editBindingMap.setKeyAction(new GKeyStroke(GKeyStroke.KEY_F12), GEditBindingMap.GROUP_CHANGE);

        addKeyboardRowChangedHandler(new KeyboardRowChangedEvent.Handler() {
            @Override
            public void onKeyboardRowChanged(KeyboardRowChangedEvent event) {
                final GridDataRecord selectedRecord = getKeyboardSelectedRowValue();
                if (selectedRecord != null && !selectedRecord.getKey().equals(currentKey)) {
                    if (cellEditor != null && cellEditor instanceof TextBasedGridCellEditor) {
                        ((TextBasedGridCellEditor) cellEditor).validateAndCommit(editCellParent, true);                    
                    }
                    
                    setCurrentKey(selectedRecord.getKey());

                    form.changeGroupObjectLater(groupObject, selectedRecord.getKey());
                }
            }
        });

        sortableHeaderManager = new GGridSortableHeaderManager<Map<GPropertyDraw, GGroupObjectValue>>(this, false) {
            @Override
            protected void orderChanged(Map<GPropertyDraw, GGroupObjectValue> columnKey, GOrder modiType) {
                form.changePropertyOrder(columnKey.keySet().iterator().next(), columnKey.values().iterator().next(), modiType);
            }

            @Override
            protected void ordersCleared(GGroupObject groupObject) {
                form.clearPropertyOrders(groupObject);
            }

            @Override
            protected Map<GPropertyDraw, GGroupObjectValue> getColumnKey(int column) {
                HashMap<GPropertyDraw, GGroupObjectValue> key = new HashMap<>();
                key.put(columnProperties.get(column), columnKeysList.get(column));
                return key;
            }
        };

        getTableDataScroller().addScrollHandler(new ScrollHandler() {
            @Override
            public void onScroll(ScrollEvent event) {
                int selectedRow = getKeyboardSelectedRow();
                GridDataRecord selectedRecord = getKeyboardSelectedRowValue();
                if (selectedRecord != null) {
                    int scrollHeight = getTableDataScroller().getClientHeight();
                    int scrollTop = getTableDataScroller().getVerticalScrollPosition();
                    
                    TableRowElement rowElement = getChildElement(selectedRow);
                    int rowTop = rowElement.getOffsetTop();
                    int rowBottom = rowTop + rowElement.getClientHeight();

                    int newRow = -1;
                    if (rowBottom > scrollTop + scrollHeight + 1) { // поправка на 1 пиксель при скроллировании вверх - компенсация погрешности, возникающей при setScrollTop() / getScrollTop()
                        newRow = getLastSeenRow(scrollTop + scrollHeight, selectedRow);
                    }
                    if (rowTop < scrollTop) {
                        newRow = getFirstSeenRow(scrollTop, selectedRow);
                    }
                    if (newRow != -1) {
                        setKeyboardSelectedRow(newRow, false);
                    }
                }
            }
        });

        getElement().setPropertyObject("groupObject", groupObject);
        gridController.setForceHidden(true);
    }

    protected int getFirstSeenRow(int tableTop, int start) {
        for (int i = start; i < getRowCount(); i++) {
            TableRowElement rowElement = getChildElement(i);
            int rowTop = rowElement.getOffsetTop();
            if (rowTop >= tableTop) {
                return i;
            }
        }
        return 0;
    }
    
    protected int getLastSeenRow(int tableBottom, int start) {
        for (int i = start; i >= 0; i--) {
            TableRowElement rowElement = getChildElement(i);
            int rowBottom = rowElement.getOffsetTop() + rowElement.getClientHeight();
            if (rowBottom <= tableBottom) {
                return i;
            }
        }
        return 0;
    }
    
    public void update() {
        update(false);
    }

    public void update(boolean modifyGroupObject) {
        storeScrollPosition();

        updatedColumnsImpl();

        updateCaptionsImpl();

        updateRowsImpl(modifyGroupObject);
        
        if (modifyGroupObject) {
            // обновим данные в колонках. при асинхронном удалении ряда можем не получить подтверждения от сервера - придётся вернуть строку
            for (GPropertyDraw property : properties) {
                updatedProperties.put(property, TRUE);
            }
            dataUpdated = true;
        }

        updateDataImpl();
    }

    private void updateRowsImpl(boolean modifyGroupObject) {
        if (rowsUpdated) {
            int currentSize = currentRecords.size();
            int newSize = rowKeys.size();

            if (currentSize > newSize) {
                if (modifyGroupObject) {
                    ArrayList<GridDataRecord> oldRecords = new ArrayList<>(currentRecords);
                    for (GridDataRecord record : oldRecords) {
                        if (!rowKeys.contains(record.getKey())) {
                            currentRecords.remove(record);
                        }
                    }
                } else {
                    for (int i = currentSize - 1; i >= newSize; --i) {
                        currentRecords.remove(i);
                    }
                }
            } else if (currentSize < newSize) {
                for (int i = currentSize; i < newSize; ++i) {
                    GGroupObjectValue rowKey = rowKeys.get(i);

                    GridDataRecord record = new GridDataRecord(i, rowKey);
                    record.setRowBackground(rowBackgroundValues.get(rowKey));
                    record.setRowForeground(rowForegroundValues.get(rowKey));

                    currentRecords.add(record);
                }
            }

            for (int i = 0; i < min(newSize, currentSize); ++i) {
                GGroupObjectValue rowKey = rowKeys.get(i);

                GridDataRecord record = currentRecords.get(i);
                record.reinit(rowKey, rowBackgroundValues.get(rowKey), rowForegroundValues.get(rowKey));
            }

            if (currentSize != newSize) {
                setRowData(currentRecords);
            } else {
                redraw();
            }

            if (currentKey != null && rowKeys.contains(currentKey)) {
                setKeyboardSelectedRow(rowKeys.indexOf(currentKey), false);
            }

            rowsUpdated = false;
        }
    }

    private void updatedColumnsImpl() {
        if (columnsUpdated) {
            List<GPropertyDraw> orderedVisibleProperties = getOrderedVisibleProperties(properties);

            //разбиваем на группы свойств, которые будут идти чередуясь для каждого ключа из групп в колонках ("шахматка")
            NativeHashMap<String, NativeHashMap<List<GGroupObject>, Integer>> columnGroupsIndices = new NativeHashMap<>();
            List<List<GPropertyDraw>> columnGroups = new ArrayList<>();
            List<List<GGroupObjectValue>> columnGroupsColumnKeys = new ArrayList<>();

            for (GPropertyDraw property : orderedVisibleProperties) {
                if (property.columnsName != null && property.columnGroupObjects != null) {
                    List<GPropertyDraw> columnGroup;

                    Integer groupInd = getFromDoubleMap(columnGroupsIndices, property.columnsName, property.columnGroupObjects);
                    if (groupInd != null) {
                        // уже было свойство с такими же именем и группами в колонках
                        columnGroup = columnGroups.get(groupInd);
                    } else {
                        // новая группа свойств
                        columnGroup = new ArrayList<>();

                        List<GGroupObjectValue> propColumnKeys = columnKeys.get(property);
                        if (propColumnKeys == null) {
                            propColumnKeys = GGroupObjectValue.SINGLE_EMPTY_KEY_LIST;
                        }

                        columnGroupsColumnKeys.add(propColumnKeys);
                        putToDoubleNativeMap(columnGroupsIndices, property.columnsName, property.columnGroupObjects, columnGroups.size());
                        columnGroups.add(columnGroup);
                    }
                    columnGroup.add(property);
                } else {
                    List<GGroupObjectValue> propColumnKeys = columnKeys.get(property);
                    if (propColumnKeys == null) {
                        propColumnKeys = GGroupObjectValue.SINGLE_EMPTY_KEY_LIST;
                    }
                    columnGroupsColumnKeys.add(propColumnKeys);
                    columnGroups.add(Collections.singletonList(property));
                }
            }

            columnProperties.clear();
            columnKeysList.clear();

            for (int i = 0; i < columnGroups.size(); i++) {
                List<GPropertyDraw> columnGroup = columnGroups.get(i);
                List<GGroupObjectValue> columnKeys = columnGroupsColumnKeys.get(i);
                
                if (columnGroup.size() == 1) {
                    //noinspection Duplicates
                    for (GPropertyDraw property : columnGroup) {
                        //showIfs.get() вынесен из двойного цикла
                        Map<GGroupObjectValue, Object> propShowIfs = showIfs.get(property);
                        for (GGroupObjectValue columnKey : columnKeys) {
                            if ((propShowIfs == null || propShowIfs.get(columnKey) != null)) {
                                columnProperties.add(property);
                                columnKeysList.add(columnKey);
                            }
                        }
                    }
                } else {
                    //noinspection Duplicates
                    for (GGroupObjectValue columnKey : columnKeys) {
                        for (GPropertyDraw property : columnGroup) {
                            Map<GGroupObjectValue, Object> propShowIfs = showIfs.get(property);
                            if ((propShowIfs == null || propShowIfs.get(columnKey) != null)) {
                                columnProperties.add(property);
                                columnKeysList.add(columnKey);
                            }
                        }
                    }
                }

            }

            int rowHeight = 0;
            int headerHeight = getHeaderHeight();

            NativeHashMap<GPropertyDraw, NativeHashMap<GGroupObjectValue, GridColumn>> newColumnsMap = new NativeHashMap<>();
            for (int i = 0; i < columnProperties.size(); ++i) {
                GPropertyDraw property = columnProperties.get(i);
                GGroupObjectValue columnKey = columnKeysList.get(i);

                GridColumn column = removeFromColumnsMap(columnsMap, property, columnKey);
                if (column != null) {
                    moveGridColumn(column, i);
                } else {
                    column = insertGridColumn(i);
                    // если колонка появилась через showif без обновления данных
                    if (!updatedProperties.containsKey(property)) {
                        updatedProperties.put(property, TRUE);
                        dataUpdated = true; // если кроме появления этой колонки в гриде ничего не поменялось, всё равно нужно обновить данные и подсветки
                    }
                }
                
                //дублирование логики изменения captions для оптимизации
                String columnCaption;
                Map<GGroupObjectValue, Object> propCaptions = propertyCaptions.get(property);
                columnCaption = getUserCaption(property);
                if(columnCaption == null) {
                    Object propCaption = null;
                    if (propCaptions == null || (propCaption = propCaptions.get(columnKey)) != null) {
                        if (propCaptions != null) {
                            columnCaption = property.getDynamicCaption(propCaption);
                        } else {
                            columnCaption = property.getCaptionOrEmpty();
                        }
                    }
                }

                GGridPropertyTableHeader header = headers.get(i);
                header.setCaption(columnCaption, property.notNull, property.hasChangeAction);
                header.setToolTip(property.getTooltipText(columnCaption));

                header.setHeaderHeight(headerHeight);

                property.setUserPattern(getUserPattern(property));

                putToColumnsMap(newColumnsMap, property, columnKey, column);

                int columnMinimumHeight = property.getValueHeight(font);
                rowHeight = Math.max(rowHeight, columnMinimumHeight);
            }

            setFixedHeaderHeight(headerHeight);
            
            setCellHeight(rowHeight);

            columnsMap.foreachValue(new Function<NativeHashMap<GGroupObjectValue, GridColumn>>() {
                @Override
                public void apply(NativeHashMap<GGroupObjectValue, GridColumn> columnsCollection) {
                    columnsCollection.foreachValue(new Function<GridColumn>() {
                        @Override
                        public void apply(GridColumn column) {
                            removeGridColumn(column);
                        }
                    });
                }
            });

            columnsMap = newColumnsMap;

            updateLayoutWidth(); // после заполнения columnsMap так как используется внутри

            refreshHeaders();

            gridController.setForceHidden(columnProperties.isEmpty());

            columnsUpdated = false;
            captionsUpdated = false;
        }
    }

    protected int getColumnsCount() {
        return columnProperties.size();
    }
    protected GPropertyDraw getColumnPropertyDraw(int i) {
        return columnProperties.get(i);
    }
    protected Column getColumnDraw(int i) {
        return getFromColumnsMap(columnsMap, columnProperties.get(i), columnKeysList.get(i));
    }

    public boolean containsProperty(GPropertyDraw property) {
        return properties.contains(property);
    }

    public List<GPropertyDraw> getOrderedVisibleProperties(List<GPropertyDraw> propertiesList) {
        List<GPropertyDraw> result = new ArrayList<>();

        for (GPropertyDraw property : propertiesList) {
            if (hasUserPreferences()) {
                Boolean userHide = getUserHide(property);
                if (userHide == null || !userHide) {
                    if (getUserOrder(property) == null) {
                        setUserHide(property, true);
                        setUserOrder(property, Short.MAX_VALUE + propertiesList.indexOf(property));
                    } else {
                        result.add(property);
                    }
                }
            } else if (!property.hide) {
                result.add(property);
            }
        }

        if (hasUserPreferences()) {
            Collections.sort(result, getCurrentPreferences().getUserOrderComparator());
        }
        return result;
    }

    public void updateCaptionsImpl() {
        if (captionsUpdated) {
            for (int i = 0; i < columnProperties.size(); ++i) {
                GPropertyDraw property = columnProperties.get(i);

                String columnCaption;
                Map<GGroupObjectValue, Object> propCaptions = propertyCaptions.get(property);
                if (propCaptions != null) {
                    columnCaption = property.getDynamicCaption(propCaptions.get(columnKeysList.get(i)));
                } else {
                    columnCaption = property.getCaptionOrEmpty();
                }

                headers.get(i).setCaption(columnCaption, property.notNull, property.hasChangeAction);
            }
            refreshHeaders();
            captionsUpdated = false;
        }
    }

    public void columnsPreferencesChanged() {
        columnsUpdated = true;
        dataUpdated = true;

        updatedColumnsImpl();
        updateDataImpl();
//
//        final ArrayList<GFontWidthString> fonts = new ArrayList<>();
//        for(GPropertyDraw property : properties)
//            property.getValueWidth(font, new GWidthStringProcessor() {
//                public void addWidthString(GFontWidthString fontWidthString) {
//                    fonts.add(fontWidthString);
//                }
//            });
//        GFontMetrics.calculateFontMetrics(fonts, new GFontMetrics.MetricsCallback() {
//            @Override
//            public Widget metricsCalculated() {
//                updatedColumnsImpl();
//                updateDataImpl();
//                return null;
//            }
//        });
    }
    
    public int getHeaderHeight() {
        Integer headerHeight = currentGridPreferences.headerHeight;
        if (headerHeight == null || headerHeight < 0) {
            headerHeight = gridController.getHeaderHeight();
        }
        return headerHeight;
    }
    
    public GFont getDesignFont() {
        return gridController.getFont();
    }

    public static void putToColumnsMap(NativeHashMap<GPropertyDraw, NativeHashMap<GGroupObjectValue, GridColumn>> columnsMap, GPropertyDraw row, GGroupObjectValue column, GridColumn value) {
        NativeHashMap<GGroupObjectValue, GridColumn> rowMap = columnsMap.get(row);
        if (rowMap == null) {
            columnsMap.put(row, rowMap = new NativeHashMap<>());
        }
        rowMap.put(column, value);
    }

    public static GridColumn getFromColumnsMap(NativeHashMap<GPropertyDraw, NativeHashMap<GGroupObjectValue, GridColumn>> columnsMap, GPropertyDraw row, GGroupObjectValue column) {
        NativeHashMap<GGroupObjectValue, GridColumn> rowMap = columnsMap.get(row);
        if (rowMap != null) {
            return rowMap.get(column);
        }
        return null;
    }

    public static GridColumn removeFromColumnsMap(NativeHashMap<GPropertyDraw, NativeHashMap<GGroupObjectValue, GridColumn>> columnsMap, GPropertyDraw row, GGroupObjectValue column) {
        GridColumn result = null;
        NativeHashMap<GGroupObjectValue, GridColumn> rowMap = columnsMap.get(row);
        if (rowMap != null) {
            result = rowMap.remove(column);
        }
        return result;
    }

    private void updateDataImpl() {
        if (dataUpdated) {
            final Set<Column> updatedColumns = new HashSet<>();
            for (final GridDataRecord record : currentRecords) {
                final GGroupObjectValue rowKey = record.getKey();
                updatedProperties.foreachKey(new Function<GPropertyDraw>() {
                    @Override
                    public void apply(GPropertyDraw property) {
                        List<GGroupObjectValue> propColumnKeys = columnKeys.get(property);
                        if (propColumnKeys == null) {
                            propColumnKeys = GGroupObjectValue.SINGLE_EMPTY_KEY_LIST;
                        }
                        NativeHashMap<GGroupObjectValue, Object> propValues = values.get(property);
                        Map<GGroupObjectValue, Object> propReadOnly = readOnlyValues.get(property);
                        Map<GGroupObjectValue, Object> propertyBackgrounds = cellBackgroundValues.get(property);
                        Map<GGroupObjectValue, Object> propertyForegrounds = cellForegroundValues.get(property);
                        for (GGroupObjectValue columnKey : propColumnKeys) {
                            NativeHashMap<GGroupObjectValue, GridColumn> propertyColumns = columnsMap.get(property);
                            GridColumn column = propertyColumns == null ? null : propertyColumns.get(columnKey);
                            // column == null, когда свойство скрыто через showif
                            if (column != null) {
                                updatedColumns.add(column);

                                GGroupObjectValue fullKey = columnKey.isEmpty() ? rowKey : new GGroupObjectValueBuilder(rowKey, columnKey).toGroupObjectValue();

                                Object value = propValues.get(fullKey);
                                Object readOnly = propReadOnly == null ? null : propReadOnly.get(fullKey);
                                Object background = propertyBackgrounds == null ? null : propertyBackgrounds.get(fullKey);
                                Object foreground = propertyForegrounds == null ? null : propertyForegrounds.get(fullKey);

                                record.setValue(column.columnID, value);
                                record.setReadOnly(column.columnID, readOnly);
                                record.setBackground(column.columnID, background == null ? property.background : background);
                                record.setForeground(column.columnID, foreground == null ? property.foreground : foreground);
                            }
                        }
                    }
                });
            }

            redrawColumns(updatedColumns);

            updatedProperties.clear();
            dataUpdated = false;
        }
    }

    private GridColumn insertGridColumn(int index) {
        GridColumn column = new GridColumn();
        GGridPropertyTableHeader header = new GGridPropertyTableHeader(this, gridController.getHeaderHeight());

        headers.add(index, header);

        insertColumn(index, column, header);

        return column;
    }

    private void moveGridColumn(GridColumn column, int newIndex) {
        int oldIndex = getColumnIndex(column);
        if (oldIndex != newIndex) {
            GGridPropertyTableHeader header = headers.remove(oldIndex);
            headers.add(newIndex, header);

            moveColumn(oldIndex, newIndex);
        }
    }

    private void removeGridColumn(GridColumn column) {
        GGridPropertyTableHeader header = (GGridPropertyTableHeader) getHeader(getColumnIndex(column));
        headers.remove(header);
        removeColumn(column);
    }

    public boolean isEmpty() {
        for (GPropertyDraw property : properties) {
            if (!values.get(property).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public GGroupObjectValue getCurrentKey() {
        return currentKey;
    }

    @Override
    public GridPropertyTableKeyboardSelectionHandler getKeyboardSelectionHandler() {
        return keyboardSelectionHandler;
    }

    @Override
    public GAbstractGroupObjectController getGroupController() {
        return groupObjectController;
    }

    public GGroupObject getGroupObject() {
        return groupObject;
    }
    
    public GPropertyDraw getCurrentProperty() {
        GPropertyDraw property = getSelectedProperty();
        if (property == null && getColumnCount() > 0) {
            property = getProperty(0);
        }
        return property;
    }

    public GGroupObjectValue getCurrentColumn() {
        GGroupObjectValue property = getSelectedColumn();
        if (property == null && getColumnCount() > 0) {
            property = getColumnKey(0);
        }
        return property;
    }

    public Object getSelectedValue(GPropertyDraw property, GGroupObjectValue columnKey) {
        GridDataRecord selectedRecord = getKeyboardSelectedRowValue();
        int column = getPropertyIndex(property, columnKey);
        if (selectedRecord != null && column != -1 && column < getColumnCount()) {
            return getColumn(column).getValue(selectedRecord);
        }

        return null;
    }

    public void setCurrentKey(GGroupObjectValue currentKey) {
        Log.debug("Setting current object to: " + currentKey);
        this.currentKey = currentKey;
    }

    public ArrayList<GPropertyDraw> getProperties() {
        return properties;
    }

    public void removeProperty(GPropertyDraw property) {
        if (!properties.contains(property)) {
            return;
        }

        values.remove(property);
        properties.remove(property);

        columnsUpdated = true;
    }

    public void addProperty(final GPropertyDraw property) {
        if (properties.contains(property)) {
            return;
        }

        int newColumnIndex = GwtSharedUtils.relativePosition(property, form.getPropertyDraws(), properties);
        properties.add(newColumnIndex, property);
        
        if (property.editKey != null) {
            form.addHotkeyBinding(property.groupObject, property.editKey, new HotkeyManager.Binding() {
                @Override
                public boolean onKeyPress(NativeEvent event, GKeyStroke key) {
                    if (!form.isEditing() && isShowing(GGridTable.this)) {
                        selectProperty(property); // редактирование сразу по индексу не захотело работать. поэтому сначала выделяем ячейку
                        editCellAt(getKeyboardSelectedRow(), getKeyboardSelectedColumn(), GEditBindingMap.CHANGE);
                        return true;
                    }
                    return false;
                }
            });
        }

        columnsUpdated = true;
    }

    public void setKeys(ArrayList<GGroupObjectValue> keys) {
        this.rowKeys = keys;

        needToRestoreScrollPosition = true;
        rowsUpdated = true;
    }

    public void updatePropertyValues(GPropertyDraw property, Map<GGroupObjectValue, Object> propValues, boolean updateKeys) {
        if (propValues != null) {
            NativeHashMap<GGroupObjectValue, Object> valuesMap = values.get(property);
            if (updateKeys && valuesMap != null) {
                valuesMap.putAll(propValues);
            } else {
                NativeHashMap<GGroupObjectValue, Object> pvalues = new NativeHashMap<>();
                pvalues.putAll(propValues);
                values.put(property, pvalues);
            }
            updatedProperties.put(property, TRUE);

            dataUpdated = true;
        }
    }

    public void updateColumnKeys(GPropertyDraw property, List<GGroupObjectValue> values) {
        List<GGroupObjectValue> oldValues = columnKeys.get(property);
        if (!nullEquals(values, oldValues)) {
            columnKeys.put(property, values);
            columnsUpdated = true;
        }
    }

    @Override
    public void updatePropertyCaptions(GPropertyDraw propertyDraw, Map<GGroupObjectValue, Object> values) {
        super.updatePropertyCaptions(propertyDraw, values);
        captionsUpdated = true;
    }

    public void updateShowIfValues(GPropertyDraw propertyDraw, Map<GGroupObjectValue, Object> values) {
        Map<GGroupObjectValue, Object> oldValues = showIfs.get(propertyDraw);
        if (!nullEquals(oldValues, values)) {
            showIfs.put(propertyDraw, values);
            columnsUpdated = true;
        }
    }

    public void updateReadOnlyValues(GPropertyDraw propertyDraw, Map<GGroupObjectValue, Object> values) {
        readOnlyValues.put(propertyDraw, values);
        updatedProperties.put(propertyDraw, TRUE);
        dataUpdated = true;
    }

    @Override
    public void updateCellBackgroundValues(GPropertyDraw propertyDraw, Map<GGroupObjectValue, Object> values) {
        super.updateCellBackgroundValues(propertyDraw, values);
        updatedProperties.put(propertyDraw, TRUE);
        dataUpdated = true;
    }

    @Override
    public void updateCellForegroundValues(GPropertyDraw propertyDraw, Map<GGroupObjectValue, Object> values) {
        super.updateCellForegroundValues(propertyDraw, values);
        updatedProperties.put(propertyDraw, TRUE);
        dataUpdated = true;
    }

    @Override
    public void updateRowBackgroundValues(Map<GGroupObjectValue, Object> values) {
        super.updateRowBackgroundValues(values);
        rowsUpdated = true;
    }

    @Override
    public void updateRowForegroundValues(Map<GGroupObjectValue, Object> values) {
        super.updateRowForegroundValues(values);
        rowsUpdated = true;
    }

    public GPropertyDraw getProperty(int column) {
        return columnProperties.get(column);
    }

    public GPropertyDraw getProperty(Cell.Context context) {
        return getProperty(context.getColumn());
    }

    @Override
    String getCellBackground(GridDataRecord rowValue, int row, int column) {
        return rowValue.getBackground(((GridColumn) getColumn(column)).columnID);
    }

    @Override
    String getCellForeground(GridDataRecord rowValue, int row, int column) {
        return rowValue.getForeground(((GridColumn) getColumn(column)).columnID);
    }

    public int getPropertyIndex(GPropertyDraw property, GGroupObjectValue columnKey) {
        for (int i = 0; i < columnProperties.size(); ++i) {
            if (property == columnProperties.get(i) && (columnKey == null || columnKey.equals(columnKeysList.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private int getMinPropertyIndex(GPropertyDraw property) {
        for (int i = 0; i < columnProperties.size(); ++i) {
            if (property == columnProperties.get(i)) {
                return i;
            }
        }
        return -1;
    }

    public void modifyGroupObject(GGroupObjectValue rowKey, boolean add, int position) {
        if (add) {
            if (position >= 0) {
                rowKeys.add(position, rowKey);
            } else {
                rowKeys.add(rowKey);
            }
            setCurrentKey(rowKey);
        } else {
            if (currentKey.equals(rowKey) && rowKeys.size() > 0) {
                if (rowKeys.size() == 1) {
                    setCurrentKey(null);
                } else {
                    int index = rowKeys.indexOf(rowKey);
                    index = index == rowKeys.size() - 1 ? index - 1 : index + 1;
                    setCurrentKey(rowKeys.get(index));
                }
            }
            rowKeys.remove(rowKey);
        }
        setKeys(rowKeys);

        update(true);
    }

    public void changeOrder(GPropertyDraw property, GOrder modiType) {
        int ind = getMinPropertyIndex(property);
        HashMap<GPropertyDraw, GGroupObjectValue> key = new HashMap<>();
        key.put(property, ind == -1 ? GGroupObjectValue.EMPTY : columnKeysList.get(ind));
        sortableHeaderManager.changeOrder(key, modiType);
    }

    public GGroupObjectValue getSelectedColumn() {
        return getColumnKey(getCurrentCellContext());
    }

    @Override
    public GGroupObjectValue getColumnKey(Cell.Context context) {
        return columnKeysList.get(context.getColumn());
    }

    public GGroupObjectValue getColumnKey(int column) {
        return columnKeysList.get(column);
    }

    public GGroupObjectValue getCurrentColumnKey() {
        return getColumnKey(getKeyboardSelectedColumn());
    }

    @Override
    public boolean isEditable(Cell.Context context) {
        GPropertyDraw property = getProperty(context);
        if (property != null && !property.isReadOnly() && !(property.baseType instanceof GObjectType)) {
            GridDataRecord rowRecord = (GridDataRecord) context.getRowValue();
            GridColumn column = (GridColumn) getColumn(context.getColumn());
            return column != null && rowRecord != null && !rowRecord.isReadonly(column.columnID);
        }
        return false;
    }

    public Object getValueAt(Cell.Context context) {
        Column column = getColumn(context.getColumn());
        Object rowValue = context.getRowValue();
        if (column == null || rowValue == null) {
            return null;
        }
        return column.getValue(rowValue);
    }
    
    public Object getValueAt(int row, int col) {
        Column column = getColumn(col);
        GridDataRecord rowValue = getRowValue(row);
        if (column == null || rowValue == null) {
            return null;
        }
        return column.getValue(rowValue);
    }

    @Override
    public void pasteData(final List<List<String>> table) {
        final int selectedColumn = getKeyboardSelectedColumn();

        if (selectedColumn == -1 || isEmpty() || table.isEmpty()) {
            return;
        }

        final int tableColumns = table.get(0).size();

        boolean singleC = table.size() == 1 && tableColumns == 1;

        if (!singleC) {
            DialogBoxHelper.showConfirmBox("lsFusion", messages.formGridSureToPasteMultivalue(), false, new DialogBoxHelper.CloseCallback() {
                @Override
                public void closed(DialogBoxHelper.OptionType chosenOption) {
                    if (chosenOption == DialogBoxHelper.OptionType.YES) {
                        int columnsToInsert = Math.min(tableColumns, getColumnCount() - selectedColumn);

                        final ArrayList<GPropertyDraw> propertyList = new ArrayList<>();
                        final ArrayList<GGroupObjectValue> columnKeys = new ArrayList<>();
                        for (int i = 0; i < columnsToInsert; i++) {
                            GPropertyDraw propertyDraw = getProperty(selectedColumn + i);
                            propertyList.add(propertyDraw);
                            columnKeys.add(getColumnKey(selectedColumn + i));
                        }

                        form.pasteExternalTable(propertyList, columnKeys, table, columnsToInsert);
                    }
                }
            });
        } else if (!table.get(0).isEmpty()) {
            GGroupObjectValue fullKey = new GGroupObjectValueBuilder(getCurrentKey(), getColumnKey(selectedColumn)).toGroupObjectValue();
            form.pasteSingleValue(getProperty(selectedColumn), fullKey, table.get(0).get(0));
        }
    }

    @Override
    public void onResize() {
        super.onResize();

        if (isVisible()) {
            int tableHeight = getTableDataScroller().getClientHeight();
            if (tableHeight == 0) {
                return;
            }
            Integer currentPageSize = currentGridPreferences.pageSize;
            TableRowElement rowElement = getChildElement(getKeyboardSelectedRow());
            if (rowElement != null) {
                int rowHeight = rowElement.getClientHeight();
                int newPageSize = currentPageSize != null ? currentPageSize : (tableHeight / rowHeight + 1);
                if (newPageSize != pageSize) {
                    form.changePageSizeAfterUnlock(groupObject, newPageSize);
                    pageSize = newPageSize;
                    setPageIncrement(pageSize - 1);
                }
            }
        }
    }

    @Override
    protected boolean useQuickSearchInsteadOfQuickFilter() {
        return groupObject.grid.quickSearch;
    }

    @Override
    public void quickFilter(EditEvent event, GPropertyDraw filterProperty, GGroupObjectValue columnKey) {
        groupObjectController.quickEditFilter(event, filterProperty, columnKey);
    }

    @Override
    protected void quickSearch(Event editEvent) {
        if (getRowCount() > 0 && getColumnCount() > 0) {
            

            char ch = (char) editEvent.getCharCode();

            double currentTime = Duration.currentTimeMillis();
            lastQuickSearchPrefix = (lastQuickSearchTime + QUICK_SEARCH_MAX_DELAY < currentTime) ? valueOf(ch) : (lastQuickSearchPrefix + ch);

            int searchColumn = 0;
            if (!sortableHeaderManager.getOrderDirections().isEmpty()) {
                for (int i = 0; i < getColumnCount(); ++i) {
                    if (sortableHeaderManager.getSortDirection(i) != null) {
                        searchColumn = i;
                        break;
                    }
                }
            }

            for (int i = 0; i < getRowCount(); ++i) {
                if (isRowWithinBounds(i)) {
                    Object value = getValueAt(i, searchColumn);
                    if (value != null && value.toString().regionMatches(true, 0, lastQuickSearchPrefix, 0, lastQuickSearchPrefix.length())) {
                        setKeyboardSelectedRow(i);
                        break;
                    }
                }
            }

            lastQuickSearchTime = currentTime;
        }
    }

    public void selectProperty(GPropertyDraw propertyDraw) {
        if (propertyDraw == null) {
            return;
        }

        int ind = getMinPropertyIndex(propertyDraw);
        if (ind != -1) {
            setKeyboardSelectedColumn(ind, false);
        }
    }

    public void setValueAt(Cell.Context context, Object value) {
        GridDataRecord rowRecord = (GridDataRecord) context.getRowValue();
        GridColumn column = (GridColumn) getColumn(context.getColumn());

        if (column != null && rowRecord != null) {
            rowRecord.setValue(column.columnID, value);

            setRowValue(context.getIndex(), rowRecord);
            redrawColumns(singleton(column), false);
        }
    }

    public void clearGridOrders(GGroupObject groupObject) {
        sortableHeaderManager.clearOrders(groupObject);
    }

    public Map<Map<GPropertyDraw, GGroupObjectValue>, Boolean> getOrderDirections() {
        return sortableHeaderManager.getOrderDirections();
    }

    public boolean userPreferencesSaved() {
        return userGridPreferences.hasUserPreferences();
    }

    public boolean generalPreferencesSaved() {
        return generalGridPreferences.hasUserPreferences();
    }

    public GGroupObjectUserPreferences getCurrentUserGridPreferences() {
        if (currentGridPreferences.hasUserPreferences()) {
            return currentGridPreferences.convertPreferences();
        }
        return userGridPreferences.convertPreferences();
    }

    public GGroupObjectUserPreferences getGeneralGridPreferences() {
        return generalGridPreferences.convertPreferences();
    }

    public void resetCurrentPreferences(boolean initial) {
        currentGridPreferences = new GGridUserPreferences(userGridPreferences.hasUserPreferences() ? userGridPreferences : generalGridPreferences);
        
        if (!initial) {
            gridController.clearGridOrders(groupObject);
            if (!currentGridPreferences.hasUserPreferences()) {
                groupObjectController.applyDefaultOrders();
            } else {
                groupObjectController.applyUserOrders();
            }
        }
    }

    private void doResetPreferences(final boolean forAllUsers, final boolean completeReset, final ErrorHandlingCallback<ServerResponseResult> callback) {
        GGridUserPreferences prefs;
        if (forAllUsers) {
            prefs = completeReset ? null : userGridPreferences;
        } else {
            // assert !completeReset;
            prefs = generalGridPreferences;
        }
        
        form.saveUserPreferences(currentGridPreferences, forAllUsers, completeReset, getHiddenProps(prefs), new ErrorHandlingCallback<ServerResponseResult>() {
            @Override
            public void failure(Throwable caught) {
                resetCurrentPreferences(false);
                callback.failure(caught);
            }

            @Override
            public void success(ServerResponseResult result) {
                if (forAllUsers) {
                    generalGridPreferences.resetPreferences();
                    if (completeReset) {
                        userGridPreferences.resetPreferences();
                    }
                } else {
                    userGridPreferences.resetPreferences();
                }
                resetCurrentPreferences(false);
                callback.success(result);
            }
        });
    }
    
    public void resetPreferences(boolean forAll, boolean complete, final ErrorHandlingCallback<ServerResponseResult> callback) {
        currentGridPreferences.resetPreferences();

        if (forAll) {
            doResetPreferences(true, complete, callback);
        } else if (!properties.isEmpty()) {
            doResetPreferences(false, false, callback);
        }
    }

    public void saveCurrentPreferences(final boolean forAllUsers, final ErrorHandlingCallback<ServerResponseResult> callback) {
        currentGridPreferences.setHasUserPreferences(true);

        if (!getProperties().isEmpty()) {
            GGridUserPreferences prefs;
            if (forAllUsers) {
                prefs = userGridPreferences.hasUserPreferences() ? userGridPreferences : currentGridPreferences;
            } else {
                prefs = currentGridPreferences;
            }

            form.saveUserPreferences(currentGridPreferences, forAllUsers, false, getHiddenProps(prefs), new ErrorHandlingCallback<ServerResponseResult>() {
                @Override
                public void success(ServerResponseResult result) {
                    if (forAllUsers) {
                        generalGridPreferences = new GGridUserPreferences(currentGridPreferences);
                        resetCurrentPreferences(false);
                    } else {
                        userGridPreferences = new GGridUserPreferences(currentGridPreferences);
                    }
                    callback.success(result);
                }

                @Override
                public void failure(Throwable caught) {
                    resetCurrentPreferences(false);
                    callback.failure(caught);
                }
            });
        }
    }

    private String[] getHiddenProps(final GGridUserPreferences preferences) {
        List<String> result = new ArrayList<>();
        if (preferences != null && preferences.hasUserPreferences()) {
            for (GPropertyDraw propertyDraw : preferences.getColumnUserPreferences().keySet()) {
                Boolean userHide = preferences.getColumnPreferences(propertyDraw).userHide;
                if (userHide != null && userHide) {
                    result.add(propertyDraw.propertyFormName);
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }
    
    public void refreshUPHiddenProps(String[] propSids) {
        assert groupObject != null; // при null нету таблицы, а значит и настроек
        form.refreshUPHiddenProps(groupObject.getSID(), propSids);
    }

    public GGridUserPreferences getCurrentPreferences() {
        return currentGridPreferences;
    }

    public boolean hasUserPreferences() {
        return currentGridPreferences.hasUserPreferences();
    }

    public void setHasUserPreferences(boolean hasUserPreferences) {
        currentGridPreferences.setHasUserPreferences(hasUserPreferences);
    }

    public GFont getUserFont() {
        return currentGridPreferences.font;
    }

    public Boolean getUserHide(GPropertyDraw property) {
        return currentGridPreferences.getUserHide(property);
    }

    public String getUserCaption(GPropertyDraw property) {
        return currentGridPreferences.getUserCaption(property);
    }

    public String getUserPattern(GPropertyDraw property) {
        return currentGridPreferences.getUserPattern(property);
    }
    
    public Integer getUserWidth(GPropertyDraw property) {
        return currentGridPreferences.getUserWidth(property);
    }

    public Integer getUserOrder(GPropertyDraw property) {
        return currentGridPreferences.getUserOrder(property);
    }

    public Integer getUserSort(GPropertyDraw property) {
        return currentGridPreferences.getUserSort(property);
    }

    public Boolean getUserAscendingSort(GPropertyDraw property) {
        return currentGridPreferences.getUserAscendingSort(property);
    }

    public void setUserPageSize(Integer pageSize) {
        currentGridPreferences.pageSize = pageSize;
    }

    public void setUserHeaderHeight(Integer headerHeight) {
        currentGridPreferences.headerHeight = headerHeight;
    }
    
    public void setUserFont(GFont userFont) {
        currentGridPreferences.font = userFont;
    }

    public void setUserHide(GPropertyDraw property, Boolean userHide) {
        currentGridPreferences.setUserHide(property, userHide);
    }

    public void setColumnSettings(GPropertyDraw property, String caption, String pattern, Integer order, Boolean hide) {
        currentGridPreferences.setColumnSettings(property, caption, pattern, order, hide);
    }
    
    public void setUserWidth(GPropertyDraw property, Integer userWidth) {
        currentGridPreferences.setUserWidth(property, userWidth);
    }

    public void setUserOrder(GPropertyDraw property, Integer userOrder) {
        currentGridPreferences.setUserOrder(property, userOrder);
    }

    public void setUserSort(GPropertyDraw property, Integer userSort) {
        currentGridPreferences.setUserSort(property, userSort);
    }

    public void setUserAscendingSort(GPropertyDraw property, Boolean userAscendingSort) {
        currentGridPreferences.setUserAscendingSort(property, userAscendingSort);
    }

    public Comparator<GPropertyDraw> getUserSortComparator() {
        return getCurrentPreferences().getUserSortComparator();
    }

    private class GridColumn extends Column<GridDataRecord, Object> {
        private int columnID;

        public GridColumn() {
            super(new GridEditableCell(GGridTable.this));
            this.columnID = nextColumnID++;
        }

        @Override
        public Object getValue(GridDataRecord record) {
            return record.getValue(columnID);
        }
    }

    public class GridTableKeyboardSelectionHandler extends GridPropertyTableKeyboardSelectionHandler<GridDataRecord> {
        public GridTableKeyboardSelectionHandler(DataGrid<GridDataRecord> table) {
            super(table);
        }

        @Override
        public boolean handleKeyEvent(CellPreviewEvent<GridDataRecord> event) {
            NativeEvent nativeEvent = event.getNativeEvent();

            assert BrowserEvents.KEYDOWN.equals(nativeEvent.getType());

            int keyCode = nativeEvent.getKeyCode();
            boolean ctrlPressed = nativeEvent.getCtrlKey();
            if (keyCode == KeyCodes.KEY_HOME && ctrlPressed) {
                form.scrollToEnd(groupObject, false);
                return true;
            } else if (keyCode == KeyCodes.KEY_END && ctrlPressed) {
                form.scrollToEnd(groupObject, true);
                return true;
            }

            return super.handleKeyEvent(event);
        }
    }
}
