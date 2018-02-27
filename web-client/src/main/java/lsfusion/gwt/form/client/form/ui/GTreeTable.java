package lsfusion.gwt.form.client.form.ui;

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Node;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Event;
import lsfusion.gwt.base.client.GwtClientUtils;
import lsfusion.gwt.base.client.jsni.NativeHashMap;
import lsfusion.gwt.base.shared.GwtSharedUtils;
import lsfusion.gwt.cellview.client.Column;
import lsfusion.gwt.cellview.client.DataGrid;
import lsfusion.gwt.cellview.client.HeaderPanel;
import lsfusion.gwt.cellview.client.KeyboardRowChangedEvent;
import lsfusion.gwt.cellview.client.cell.Cell;
import lsfusion.gwt.cellview.client.cell.CellPreviewEvent;
import lsfusion.gwt.form.shared.view.*;
import lsfusion.gwt.form.shared.view.changes.GGroupObjectValue;
import lsfusion.gwt.form.shared.view.grid.EditEvent;
import lsfusion.gwt.form.shared.view.grid.GridEditableCell;

import java.util.*;

import static java.util.Collections.singleton;

public class GTreeTable extends GGridPropertyTable<GTreeGridRecord> {
    private boolean dataUpdated;
    private boolean columnsUpdated;

    private GGroupObjectValue pathToSet;

    private ArrayList<GTreeGridRecord> currentRecords;

    private GTreeTableTree tree;

    private List<String> createdFields = new ArrayList<>();

    private GTreeGridRecord selectedRecord;

    private Set<GTreeTableNode> expandedNodes;

    private TreeTableKeyboardSelectionHandler keyboardSelectionHandler;

    private GTreeGroupController treeGroupController;
    
    private boolean autoSize;

    private int hierarchicalWidth;
    private Column hierarchicalColumn;

    public GTreeTable(GFormController iformController, GForm iform, GTreeGroupController treeGroupController, GTreeGroup treeGroup, boolean autoSize) {
        super(iformController, treeGroupController.getFont());

        this.treeGroupController = treeGroupController;
        this.autoSize = autoSize;

        tree = new GTreeTableTree(iform);

        Column<GTreeGridRecord, Object> column = new Column<GTreeGridRecord, Object>(new GTreeGridControlCell(this)) {
            @Override
            public Object getValue(GTreeGridRecord object) {
                return object.getAttribute("treeColumn");
            }
        };
        GGridPropertyTableHeader header = new GGridPropertyTableHeader(this, "Дерево");
        header.setHeaderHeight(HeaderPanel.DEFAULT_HEADER_HEIGHT);
        createdFields.add("treeColumn");
        headers.add(header);
        addColumn(column, header);

        hierarchicalColumn = column;
        hierarchicalWidth = treeGroup.calculateSize();

        keyboardSelectionHandler = new TreeTableKeyboardSelectionHandler(this);
        setKeyboardSelectionHandler(keyboardSelectionHandler);

        addKeyboardRowChangedHandler(new KeyboardRowChangedEvent.Handler() {
            @Override
            public void onKeyboardRowChanged(KeyboardRowChangedEvent event) {
                final GTreeGridRecord kbSelectedRecord = getKeyboardSelectedRowValue();
                if (kbSelectedRecord != null && !kbSelectedRecord.equals(selectedRecord)) {
                    setCurrentRecord(kbSelectedRecord);
                    form.changeGroupObjectLater(kbSelectedRecord.getGroup(), kbSelectedRecord.getKey());
                }
            }
        });

        sortableHeaderManager = new GGridSortableHeaderManager<GPropertyDraw>(this, true) {
            @Override
            protected void orderChanged(GPropertyDraw columnKey, GOrder modiType) {
                form.changePropertyOrder(columnKey, GGroupObjectValue.EMPTY, modiType);
            }

            @Override
            protected void ordersCleared(GGroupObject groupObject) {
                form.clearPropertyOrders(groupObject);
            }

            @Override
            protected GPropertyDraw getColumnKey(int column) {
                return tree.getColumnProperty(column);
            }
        };

        setFixedHeaderHeight(HeaderPanel.DEFAULT_HEADER_HEIGHT);
    }

    @Override
    protected boolean isAutoSize() {
        return autoSize;
    }

    public void removeProperty(GGroupObject group, GPropertyDraw property) {
        dataUpdated = true;
        int index = tree.removeProperty(group, property);
        if (index > 0) {
            createdFields.remove(index);
            headers.remove(index);
            removeColumn(index);

            columnsMap.remove(property);
            columnsUpdated = true;
        }
    }

    private NativeHashMap<GPropertyDraw, Column> columnsMap = new NativeHashMap<>();

    public void addProperty(GGroupObject group, GPropertyDraw property) {
        dataUpdated = true;

        int index = tree.addProperty(group, property);

        if (index > -1) {
            if (!createdFields.contains(property.sID)) {
                Column<GTreeGridRecord, Object> gridColumn = createGridColumn(property);
                String propertyCaption = property.getCaptionOrEmpty();
                GGridPropertyTableHeader header = new GGridPropertyTableHeader(this, propertyCaption, property.getTooltipText(propertyCaption));
                header.setHeaderHeight(HeaderPanel.DEFAULT_HEADER_HEIGHT);

                headers.add(index, header);
                insertColumn(index, gridColumn, header);
                createdFields.add(index, property.sID);

                columnsMap.put(property, gridColumn);
                columnsUpdated = true;
            }
        }
    }

    private Column<GTreeGridRecord, Object> createGridColumn(final GPropertyDraw property) {
        return new Column<GTreeGridRecord, Object>(new GridEditableCell(this, true)) {
            @Override
            public Object getValue(GTreeGridRecord record) {
                int column = tree.columnProperties.indexOf(property);
                return tree.getValue(record.getGroup(), column, record.getKey());
            }
        };
    }

    public void setKeys(GGroupObject group, ArrayList<GGroupObjectValue> keys, ArrayList<GGroupObjectValue> parents, HashMap<GGroupObjectValue, Boolean> expandable) {
        tree.setKeys(group, keys, parents, expandable);
        dataUpdated = true;
        needToRestoreScrollPosition = true;
    }

    public void updatePropertyValues(GPropertyDraw property, Map<GGroupObjectValue, Object> propValues, boolean updateKeys) {
        if (propValues != null) {
            dataUpdated = true;
            tree.setPropertyValues(property, propValues, updateKeys);
        }
    }

    public void updateReadOnlyValues(GPropertyDraw property, Map<GGroupObjectValue, Object> readOnlyValues) {
        if (readOnlyValues != null) {
            tree.setReadOnlyValues(property, readOnlyValues);
        }
    }

    @Override
    public void updateCellBackgroundValues(GPropertyDraw propertyDraw, Map<GGroupObjectValue, Object> values) {
        super.updateCellBackgroundValues(propertyDraw, values);
        dataUpdated = true;
    }

    @Override
    public void updateCellForegroundValues(GPropertyDraw propertyDraw, Map<GGroupObjectValue, Object> values) {
        super.updateCellForegroundValues(propertyDraw, values);
        dataUpdated = true;
    }

    @Override
    public void updateRowBackgroundValues(Map<GGroupObjectValue, Object> values) {
        super.updateRowBackgroundValues(values);
        dataUpdated = true;
    }

    @Override
    public void updateRowForegroundValues(Map<GGroupObjectValue, Object> values) {
        super.updateRowForegroundValues(values);
        dataUpdated = true;
    }

    private Integer hierarchicalUserWidth = null;
    private final NativeHashMap<GPropertyDraw, Integer> userWidths = new NativeHashMap<>();
    @Override
    protected void setUserWidth(GPropertyDraw property, Integer value) {
        userWidths.put(property, value);
    }

    @Override
    protected Integer getUserWidth(GPropertyDraw property) {
        return userWidths.get(property);
    }

    @Override
    protected void setUserWidth(int i, int width) {
        if(i==0) {
            hierarchicalUserWidth = width;
            return;
        }
        super.setUserWidth(i - 1, width);
    }

    @Override
    protected Integer getUserWidth(int i) {
        if(i==0)
            return hierarchicalUserWidth;
        return super.getUserWidth(i - 1);
    }

    @Override
    protected int getColumnsCount() {
        return 1 + tree.columnProperties.size();
    }

    @Override
    protected GPropertyDraw getColumnPropertyDraw(int i) {
        assert i < getColumnsCount() - 1; // уже вычли фиксированную колонку
        return tree.columnProperties.get(i);
    }

    @Override
    protected Column getColumnDraw(int i) {
        if(i==0)
            return hierarchicalColumn;
        return columnsMap.get(getColumnPropertyDraw(i-1));
    }

    @Override
    protected boolean isColumnFlex(int i) {
        if(i == 0)
            return true;
        return super.isColumnFlex(i - 1);
    }

    @Override
    protected int getColumnBaseWidth(int i) {
        if(i==0)
            return hierarchicalWidth;
        return super.getColumnBaseWidth(i - 1);
    }

    public void update() {
        storeScrollPosition();

        if(columnsUpdated) {
            updateLayoutWidth();
            columnsUpdated = false;
        }

        if (dataUpdated) {
            restoreVisualState();

            currentRecords = tree.getUpdatedRecords();
            updatePropertyReaders();
            setRowData(currentRecords);

            keyboardSelectionHandler.dataUpdated();

            redraw();

            dataUpdated = false;
        }

        updateHeader();

        updateCurrentRecord();
    }

    public void updateCurrentRecord() {
        if (pathToSet != null) {
            GGroupObjectValue currentPath = pathToSet;
            pathToSet = null;
            if (currentRecords != null) {
                int i = 0;
                for (GTreeGridRecord record : currentRecords) {
                    if (record.getKey().equals(currentPath)) {
                        setCurrentRecord(record);
                        setKeyboardSelectedRow(i, false);
                        return;
                    }
                    i++;
                }
            }
        }
    }

    @Override
    public void onResize() {
        if (isVisible()) {
            super.onResize();
        }
    }

    protected void updatePropertyReaders() {
        if (currentRecords != null &&
                //раскраска в дереве - редкое явление, поэтому сразу проверяем есть ли она
                (rowBackgroundValues.size() != 0 || rowForegroundValues.size() != 0 || cellBackgroundValues.size() != 0 || cellForegroundValues.size() != 0)) {
            for (GridDataRecord record : currentRecords) {
                GGroupObjectValue key = record.getKey();

                Object rBackground = rowBackgroundValues.get(key);
                Object rForeground = rowForegroundValues.get(key);

                List<GPropertyDraw> columnProperties = getColumnProperties();
                for (int j = 0; j < columnProperties.size(); j++) {
                    GPropertyDraw property = columnProperties.get(j);

                    Object background = rBackground;
                    if (background == null) {
                        Map<GGroupObjectValue, Object> propBackgrounds = cellBackgroundValues.get(property);
                        if (propBackgrounds != null) {
                            background = propBackgrounds.get(key);
                        }
                    }

                    Object foreground = rForeground;
                    if (foreground == null) {
                        Map<GGroupObjectValue, Object> propForegrounds = cellForegroundValues.get(property);
                        if (propForegrounds != null) {
                            foreground = propForegrounds.get(key);
                        }
                    }

                    record.setBackground(j + 1, background == null ? property.background : background);
                    record.setForeground(j + 1, foreground == null ? property.foreground : foreground);
                }
            }
        }
    }

    protected void updateHeader() {
        boolean needsHeaderRefresh = false;
        int rowHeight = 0;
        for (GPropertyDraw property : getColumnProperties()) {
            Map<GGroupObjectValue, Object> captions = propertyCaptions.get(property);
            if (captions != null) {
                String value = GwtSharedUtils.nullTrim(captions.values().iterator().next());
                GGridPropertyTableHeader header = headers.get(getColumnIndex(property));
                header.setCaption(value, false, false);
                header.setToolTip(property.getTooltipText(value));
                needsHeaderRefresh = true;
            }
            rowHeight = Math.max(rowHeight, property.getValueHeight(font));
        }
        setCellHeight(rowHeight);
        if (needsHeaderRefresh) {
            refreshHeaders();
        }
    }

    public void expandNodeByRecord(GTreeGridRecord record) {
        fireExpandNode(tree.getNodeByRecord(record));
    }

    private void fireExpandNode(GTreeTableNode node) {
        if (node != null) {
            saveVisualState();
            expandedNodes.add(node);
            form.expandGroupObject(node.getGroup(), node.getKey());
        }
    }

    public void collapseNodeByRecord(GTreeGridRecord record) {
        fireCollapseNode(tree.getNodeByRecord(record));
    }

    private void fireCollapseNode(GTreeTableNode node) {
        if (node != null) {
            saveVisualState();
            expandedNodes.remove(node);
            form.collapseGroupObject(node.getGroup(), node.getKey());
        }
    }

    public void saveVisualState() {
        expandedNodes = new HashSet<>();
        expandedNodes.addAll(getExpandedChildren(tree.root));
    }

    private List<GTreeTableNode> getExpandedChildren(GTreeTableNode node) {
        List<GTreeTableNode> exChildren = new ArrayList<>();
        for (GTreeTableNode child : node.getChildren()) {
            if (child.isOpen()) {
                exChildren.add(child);
                exChildren.addAll(getExpandedChildren(child));
            }
        }
        return exChildren;
    }

    public void restoreVisualState() {
        for (GTreeTableNode node : tree.root.getChildren()) {
            expandNode(node);
        }
    }

    private void expandNode(GTreeTableNode node) {
        if (expandedNodes != null && expandedNodes.contains(node) && !tree.hasOnlyExpandningNodeAsChild(node)) {
            node.setOpen(true);
            for (GTreeTableNode child : node.getChildren()) {
                expandNode(child);
            }
        } else {
            node.setOpen(false);
        }
    }

    private void setCurrentRecord(GTreeGridRecord record) {
        Log.debug("Setting current record to: " + record);
        this.selectedRecord = record;
    }

    public void setCurrentPath(GGroupObjectValue currentPath) {
        Log.debug("Setting current path to: " + currentPath);
        this.pathToSet = currentPath;
    }

    public GTreeGridRecord getSelectedRecord() {
        return selectedRecord;
    }

    public GGroupObjectValue getCurrentKey() {
        return selectedRecord == null ? GGroupObjectValue.EMPTY : selectedRecord.getKey();
    }

    @Override
    public GridPropertyTableKeyboardSelectionHandler getKeyboardSelectionHandler() {
        return keyboardSelectionHandler;
    }

    @Override
    public GAbstractGroupObjectController getGroupController() {
        return treeGroupController;
    }

    public GPropertyDraw getCurrentProperty() {
        GPropertyDraw property = getSelectedProperty();
        if (property == null && getColumnCount() > 1) {
            property = tree.getColumnProperty(1);
        }
        return property;
    }

    public Object getSelectedValue(GPropertyDraw property) {
        GTreeGridRecord record = getSelectedRecord();
        return record == null ? null : tree.getValue(record.getGroup(), getColumnIndex(property) - 1, record.getKey());
    }

    public List<GPropertyDraw> getColumnProperties() {
        return tree.columnProperties;
    }

    public int getColumnIndex(GPropertyDraw property) {
        return getColumnProperties().indexOf(property) + 1;
    }

    @Override
    String getCellBackground(GridDataRecord rowValue, int row, int column) {
        return rowValue.getBackground(column);
    }

    @Override
    String getCellForeground(GridDataRecord rowValue, int row, int column) {
        return rowValue.getForeground(column);
    }

    @Override
    public GPropertyDraw getProperty(Cell.Context context) {
        GTreeGridRecord rowValue = (GTreeGridRecord) context.getRowValue();
        return rowValue == null ? null : tree.getProperty(rowValue.getGroup(), context.getColumn() - 1);
    }

    @Override
    public GGroupObjectValue getColumnKey(Cell.Context context) {
        return currentRecords.get(context.getIndex()).getKey();
    }

    @Override
    public boolean isEditable(Cell.Context context) {
        GTreeGridRecord record = (GTreeGridRecord) context.getRowValue();
        return record != null && tree.isEditable(record.getGroup(), context.getColumn() - 1, record.getKey());
    }

    @Override
    public Object getValueAt(Cell.Context context) {
        GTreeGridRecord record = (GTreeGridRecord) context.getRowValue();
        return record == null ? null : tree.getValue(record.getGroup(), context.getColumn() - 1, record.getKey());
    }

    @Override
    public void pasteData(List<List<String>> table) {
        if (!table.isEmpty() && !table.get(0).isEmpty()) {
            GPropertyDraw property = getSelectedProperty();
            GGroupObjectValue columnKey = getCurrentKey();
            if (property != null) {
                form.pasteSingleValue(property, columnKey, table.get(0).get(0));
            }
        }
    }

    @Override
    protected void onBrowserEvent2(Event event) {
        super.onBrowserEvent2(event);

        if (event.getTypeInt() == Event.ONDBLCLICK) {
            if (treeGroupController.isExpandOnClick() && !isEditable(getCurrentCellContext()) && getTableBodyElement().isOrHasChild(Node.as(event.getEventTarget()))) {
                GTreeTableNode node = tree.getNodeByRecord(getSelectedRecord());
                if (node.isExpandable()) {
                    GwtClientUtils.stopPropagation(event);
                    if (!node.isOpen()) {
                        fireExpandNode(node);
                    } else {
                        fireCollapseNode(node);
                    }
                }
            }
        }
    }

    @Override
    public void quickFilter(EditEvent event, GPropertyDraw filterProperty, GGroupObjectValue columnKey) {
        treeGroupController.quickEditFilter(event, filterProperty, columnKey);
    }

    @Override
    public void setValueAt(Cell.Context context, Object value) {
        int row = context.getIndex();
        int column = context.getColumn();

        GTreeGridRecord rowRecord = (GTreeGridRecord) context.getRowValue();

        if (rowRecord != null) {
            GPropertyDraw property = getProperty(context);
            if (property != null) {
                rowRecord.setAttribute(property.sID, value);
                tree.putValue(property, rowRecord.getKey(), value);
            }
        }

        setRowData(row, Arrays.asList(rowRecord));
        redrawColumns(singleton(getColumn(column)), false);
    }

    public void changeOrder(GPropertyDraw property, GOrder modiType) {
        int propertyIndex = tree.getPropertyColumnIndex(property);
        if (propertyIndex > 0) {
            sortableHeaderManager.changeOrder(property, modiType);
        } else {
            //меняем напрямую для верхних groupObjects
            form.changePropertyOrder(property, GGroupObjectValue.EMPTY, modiType);
        }
    }

    public boolean keyboardNodeChangeState(boolean open) {
        GTreeTableNode node = tree.getNodeByRecord(selectedRecord);
        if (node == null || !node.isExpandable()) {
            return false;
        }
        if (open) {
            if (!node.isOpen()) {
                keyboardSelectionHandler.nodeTryingToExpand = node;
                fireExpandNode(node);
                return true;
            }
        } else if (node.isOpen()) {
            fireCollapseNode(node);
            return true;
        }
        return false;
    }

    public void clearOrders(GGroupObject groupObject) {
        sortableHeaderManager.clearOrders(groupObject);
    }

    public class TreeTableKeyboardSelectionHandler extends GridPropertyTableKeyboardSelectionHandler<GTreeGridRecord> {
        public GTreeTableNode nodeTryingToExpand = null;

        public TreeTableKeyboardSelectionHandler(DataGrid<GTreeGridRecord> table) {
            super(table);
        }

        public void dataUpdated() {
            if (nodeTryingToExpand != null) {
                if (!nodeTryingToExpand.isOpen()) {
                    nextColumn(true);
                }
                nodeTryingToExpand = null;
            }
        }

        @Override
        public boolean handleKeyEvent(CellPreviewEvent<GTreeGridRecord> event) {
            NativeEvent nativeEvent = event.getNativeEvent();

            assert BrowserEvents.KEYDOWN.equals(nativeEvent.getType());

            int keyCode = nativeEvent.getKeyCode();
            if (!nativeEvent.getCtrlKey() && getKeyboardSelectedColumn() == 0) {
                if (keyCode == KeyCodes.KEY_RIGHT) {
                    if (keyboardNodeChangeState(true)) {
                        return true;
                    }
                } else if (keyCode == KeyCodes.KEY_LEFT) {
                    if (keyboardNodeChangeState(false)) {
                        return true;
                    }
                }
            }

            return super.handleKeyEvent(event);
        }
    }
}
