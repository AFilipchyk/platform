package lsfusion.client.form.grid;

import com.google.common.base.Throwables;
import com.sun.java.swing.plaf.windows.WindowsTableHeaderUI;
import lsfusion.base.Pair;
import lsfusion.client.Main;
import lsfusion.client.SwingUtils;
import lsfusion.client.form.*;
import lsfusion.client.form.grid.preferences.GridUserPreferences;
import lsfusion.client.form.layout.ClientFormLayout;
import lsfusion.client.form.renderer.LabelPropertyRenderer;
import lsfusion.client.form.sort.MultiLineHeaderRenderer;
import lsfusion.client.form.sort.TableSortableHeaderManager;
import lsfusion.client.logics.ClientForm;
import lsfusion.client.logics.ClientGroupObject;
import lsfusion.client.logics.ClientGroupObjectValue;
import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.client.logics.classes.ClientLogicalClass;
import lsfusion.client.logics.classes.ClientStringClass;
import lsfusion.interop.FontInfo;
import lsfusion.interop.KeyStrokes;
import lsfusion.interop.Order;
import lsfusion.interop.Scroll;
import lsfusion.interop.form.GroupObjectUserPreferences;
import lsfusion.interop.form.ServerResponse;
import sun.swing.UIAction;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Format;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.List;

import static java.lang.Math.max;
import static java.lang.String.valueOf;
import static lsfusion.base.BaseUtils.*;
import static lsfusion.client.ClientResourceBundle.getString;
import static lsfusion.client.form.ClientFormController.PasteData;

public class GridTable extends ClientPropertyTable {

    public static final String GOTO_LAST_ACTION = "gotoLastRow";
    public static final String GOTO_FIRST_ACTION = "gotoFirstRow";

    public static final int DEFAULT_HEADER_HEIGHT = 34;
    public static int DEFAULT_PREFERRED_WIDTH = 130;
    public static int DEFAULT_PREFERRED_HEIGHT = 70;
    public static Dimension DEFAULT_PREFERRED_SIZE = new Dimension(DEFAULT_PREFERRED_WIDTH, DEFAULT_PREFERRED_HEIGHT - DEFAULT_HEADER_HEIGHT);
    public static int DEFAULT_MAX_PREFERRED_HEIGHT = 140;
    public static Dimension DEFAULT_MAX_PREFERRED_SIZE = new Dimension(DEFAULT_PREFERRED_WIDTH, DEFAULT_MAX_PREFERRED_HEIGHT - DEFAULT_HEADER_HEIGHT);

    private static final long QUICK_SEARCH_MAX_DELAY = 2000;
    private String lastQuickSearchPrefix = "";
    private long lastQuickSearchTime = 0;
    private EventObject lastQuickSearchEvent;

    private final List<ClientPropertyDraw> properties = new ArrayList<>();

    private List<ClientGroupObjectValue> rowKeys = new ArrayList<>();
    private Map<ClientPropertyDraw, List<ClientGroupObjectValue>> columnKeys = new HashMap<>();
    private Map<ClientPropertyDraw, Map<ClientGroupObjectValue, Object>> captions = new HashMap<>();
    private Map<ClientPropertyDraw, Map<ClientGroupObjectValue, Object>> showIfs = new HashMap<>();
    private Map<ClientPropertyDraw, Map<ClientGroupObjectValue, Object>> values = new HashMap<>();
    private Map<ClientPropertyDraw, Map<ClientGroupObjectValue, Object>> readOnlyValues = new HashMap<>();
    private Map<ClientGroupObjectValue, Object> rowBackground = new HashMap<>();
    private Map<ClientGroupObjectValue, Object> rowForeground = new HashMap<>();
    private Map<ClientPropertyDraw, Map<ClientGroupObjectValue, Object>> cellBackgroundValues = new HashMap<>();
    private Map<ClientPropertyDraw, Map<ClientGroupObjectValue, Object>> cellForegroundValues = new HashMap<>();

    private ClientGroupObjectValue currentObject;

    private final GridTableModel model;

    private Action moveToNextCellAction = null;

    //нужно для отключения поиска свободных мест при навигации по таблице
    private boolean hasFocusableCells;

    private boolean isInternalNavigating = false;

    private boolean isLayouting;

    private final GridController gridController;
    private final GroupObjectController groupController;

    private boolean tabVertical = false;

    private int viewMoveInterval = 0;

    //private boolean calledChangeGroupObject = false;
    protected int oldRowScrollTop;
    private int scrollToIndex = -1;
    private int selectIndex = -1;
    private boolean scrollToOldObject = true; // не скроллить при ctrl+home/ctrl+end (синхронный запрос)

    private ClientGroupObject groupObject;
    private TableSortableHeaderManager<Pair<ClientPropertyDraw, ClientGroupObjectValue>> sortableHeaderManager;

    //для вдавливаемости кнопок
    private int pressedCellRow = -1;
    private int pressedCellColumn = -1;
    private int previousSelectedRow = 0;

    private int pageSize = 50;

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    private GridSelectionController selectionController = new GridSelectionController(this);
    private KeyController keyController = new KeyController(this);

    private GridUserPreferences generalGridPreferences;
    private GridUserPreferences userGridPreferences;
    private GridUserPreferences currentGridPreferences;

    private boolean skipScrollingAdjusments = false;

    private WeakReference<TableCellRenderer> defaultHeaderRendererRef;
    private TableCellRenderer wrapperHeaderRenderer;

    // Хак. См. BasicTableUI.Actions метод actionPerformed(). Проблема при изменении ряда сразу после редактирования и
    // одновременном изменении ключей. Между вычислением значения leadRow и изменением selection'а на это значение
    // вызывается stopCellEditing(), который приводит к обновлению таблицы и текущего ряда. Таким образом на момент
    // вызова changeSelection() значение leadRow оказывается устаревшим. Подменяем его для избежания прыжков.
    private ThreadLocal<UIAction> threadLocalUIAction = new ThreadLocal<>();
    private ThreadLocal<Boolean> threadLocalIsStopCellEditing = new ThreadLocal<>();

    public GridTable(final GridView igridView, ClientFormController iform, GridUserPreferences[] iuserPreferences) {
        super(new GridTableModel(), iform);

        tableHeader = new GridTableHeader(columnModel) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(columnModel.getTotalColumnWidth(), getHeaderHeight());
            }
        };

        gridController = igridView.getGridController();
        groupController = gridController.getGroupController();
        groupObject = groupController.getGroupObject();

        generalGridPreferences = iuserPreferences != null && iuserPreferences[0] != null ? iuserPreferences[0] : new GridUserPreferences(groupObject);
        userGridPreferences = iuserPreferences != null && iuserPreferences[1] != null ? iuserPreferences[1] : new GridUserPreferences(groupObject);
        resetCurrentPreferences(true);

        FontInfo userFont = getUserFont();
        if (userFont != null) {
            if (userFont.fontSize == 0) {
                setFont(getFont().deriveFont(userFont.getStyle()));
            } else {
                setFont(getFont().deriveFont(userFont.getStyle(), userFont.fontSize));
            }
        } else if (getDesignFont() != null) {
            setFont(groupObject.grid.design.getFont(this));
        }

        setName(groupObject.toString());

        model = getModel();

        setAutoCreateColumnsFromModel(false);
        setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        sortableHeaderManager = new TableSortableHeaderManager<Pair<ClientPropertyDraw, ClientGroupObjectValue>>(this) {
            protected void orderChanged(final Pair<ClientPropertyDraw, ClientGroupObjectValue> columnKey, final Order modiType) {
                RmiQueue.runAction(new Runnable() {
                    @Override
                    public void run() {
                        GridTable.this.orderChanged(columnKey, modiType);
                    }
                });
            }

            @Override
            protected void ordersCleared(final ClientGroupObject groupObject) {
                RmiQueue.runAction(new Runnable() {
                    @Override
                    public void run() {
                        GridTable.this.ordersCleared(groupObject);
                    }
                });

            }

            @Override
            protected Pair<ClientPropertyDraw, ClientGroupObjectValue> getColumnKey(int column) {
                return new Pair<>(model.getColumnProperty(column), model.getColumnKey(column));
            }

            @Override
            protected ClientPropertyDraw getColumnProperty(int column) {
                return model.getColumnProperty(column);
            }

        };

        getTableHeader().setUI(new GridTableHeaderUI());

        tableHeader.addMouseListener(sortableHeaderManager);

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (groupObject != null) {
                    ClientForm.lastActiveGroupObject = groupObject;
                }
            }
        });

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (KeyEvent.getKeyText(e.getKeyCode()).equals("Shift") && getSelectedObject() != null) {
                    if (!keyController.isRecording) {
                        selectionController.recordingStarted(getSelectedColumn());
                    }
                    keyController.startRecording(getSelectedRow());
                }
            }

            public void keyReleased(KeyEvent e) {
                if (KeyEvent.getKeyText(e.getKeyCode()).equals("Shift")) {
                    keyController.stopRecording();
                    selectionController.recordingStopped();
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int column = columnAtPoint(e.getPoint());
                int row = rowAtPoint(e.getPoint());

                if ((column == -1 || row == -1)) {
                    changeSelection(getSelectedRow(), getSelectedColumn(), false, false);
                }

                if (column != -1 && (getSelectedRow() == previousSelectedRow || isEditOnSingleClick(row, column))) {
                    pressedCellColumn = column;
                    pressedCellRow = row;
                    repaint();
                }
                previousSelectedRow = getSelectedRow();

                if (getSelectedColumn() != -1) {
                    if (MouseEvent.getModifiersExText(e.getModifiersEx()).contains("Shift")) {
                        if (!keyController.isRecording) {//пока кривовато работает
                            keyController.startRecording(getSelectedRow());
                            selectionController.recordingStarted(getSelectedColumn());
                        }
                        keyController.completeRecording(getSelectedRow());
                        selectionController.submitShiftSelection(keyController.getValues());
                    } else {
                        selectionController.mousePressed(getSelectedColumn());
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressedCellRow = -1;
                pressedCellColumn = -1;
                previousSelectedRow = getSelectedRow();

                if (!MouseEvent.getModifiersExText(e.getModifiersEx()).contains("Shift")) {
                    selectionController.mouseReleased();
                }

                repaint();
            }
        });

        if (form.isDialog()) {
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    ClientPropertyDraw property = getSelectedProperty();
                    //игнорируем double click по editable boolean
                    boolean ignore = property != null && property.baseType instanceof ClientLogicalClass && !property.isReadOnly();
                    if (e.getClickCount() > 1 && !ignore) {
                        RmiQueue.runAction(new Runnable() {
                            @Override
                            public void run() {
                                form.okPressed();
                            }
                        });
                    }
                }
            });
        }

        //имитируем продвижение фокуса вперёд, если изначально попадаем на нефокусную ячейку
        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                moveToFocusableCellIfNeeded();
            }
        });

        getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (isEditing()) {
                    TableCellEditor cellEditor = getCellEditor();
                    if (cellEditor != null) {
                        cellEditor.stopCellEditing();
                    }
                }

                changeCurrentObjectLater();
                moveToFocusableCellIfNeeded();
            }
        });

        initializeActionMap();
    }

    public int getHeaderHeight() {
        Integer userHeaderHeight = getUserHeaderHeight();
        if (userHeaderHeight != null && userHeaderHeight >= 0) {
            return userHeaderHeight;
        }
        // заданная в дизайне
        int predefinedHeaderHeight = gridController.getGridView().getHeaderHeight();
        if (predefinedHeaderHeight >= 0) {
            return predefinedHeaderHeight;
        }
        return DEFAULT_HEADER_HEIGHT;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return gridController.getAutoSize() ? getPreferredSize() : DEFAULT_PREFERRED_SIZE;
    }

    private boolean isEditOnSingleClick(int row, int col) {
        return getProperty(row, col).editOnSingleClick;
    }

    private void orderChanged(Pair<ClientPropertyDraw, ClientGroupObjectValue> columnKey, Order modiType) {
        try {
            form.changePropertyOrder(columnKey.first, modiType, columnKey.second);
            tableHeader.resizeAndRepaint();
        } catch (IOException e) {
            throw new RuntimeException(getString("errors.error.changing.sorting"), e);
        }

        tableHeader.repaint();
    }

    private void ordersCleared(ClientGroupObject groupObject) {
        try {
            form.clearPropertyOrders(groupObject);
            tableHeader.resizeAndRepaint();
        } catch (IOException e) {
            throw new RuntimeException(getString("errors.error.changing.sorting"), e);
        }

        tableHeader.repaint();
    }

    private void initializeActionMap() {
        editBindingMap.setKeyAction(KeyStrokes.getGroupCorrectionKeyStroke(), ServerResponse.GROUP_CHANGE);

        final Action oldNextAction = getActionMap().get("selectNextColumnCell");
        final Action oldPrevAction = getActionMap().get("selectPreviousColumnCell");
        final Action oldFirstAction = getActionMap().get("selectFirstColumn");
        final Action oldLastAction = getActionMap().get("selectLastColumn");

        final Action nextAction = new GoToNextCellAction(true);
        final Action prevAction = new GoToNextCellAction(false);
        final Action firstAction = new GoToLastCellAction(oldFirstAction, oldNextAction);
        final Action lastAction = new GoToLastCellAction(oldLastAction, oldPrevAction);

        moveToNextCellAction = nextAction;

        ActionMap actionMap = getActionMap();
        // set left and right actions
        actionMap.put("selectNextColumn", nextAction);
        actionMap.put("selectPreviousColumn", prevAction);
        // set tab and shift-tab actions
        actionMap.put("selectNextColumnCell", nextAction);
        actionMap.put("selectPreviousColumnCell", prevAction);
        // set top and end actions
        actionMap.put("selectFirstColumn", firstAction);
        actionMap.put("selectLastColumn", lastAction);

        actionMap.put(GOTO_FIRST_ACTION, new ScrollToEndAction(Scroll.HOME));
        actionMap.put(GOTO_LAST_ACTION, new ScrollToEndAction(Scroll.END));

        //вырезаем default F8 action
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStrokes.getF8(), "none");

        InputMap inputMap = getInputMap();
        inputMap.put(KeyStrokes.getCtrlHome(), GOTO_FIRST_ACTION);
        inputMap.put(KeyStrokes.getCtrlEnd(), GOTO_LAST_ACTION);
    }

    int getID() {
        return groupObject.getID();
    }

    @Override
    public int hashCode() {
        return getID();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GridTable && ((GridTable) o).getID() == this.getID();
    }

    public void updateTable() {
        model.updateColumns(getOrderedVisibleProperties(properties), columnKeys, captions, showIfs);

        model.updateRows(rowKeys, values, readOnlyValues, rowBackground, rowForeground, cellBackgroundValues, cellForegroundValues);

        refreshColumnModel();

        if (viewMoveInterval != 0) {
            selectionController.keysChanged(viewMoveInterval < 0);
        }

        invalidate();
        if (getParent() != null) {
            // нам нужно вызвать validate(), чтобы применить новые размеры
            // но при этом срабатывает scrolling AdjusmentListener, который select'ает неправильный ряд
            // поэтому отключаем эту логику, т.к. мы сами скроллим в adjustSelection
            skipScrollingAdjusments = true;
            getParent().getParent().validate();
            skipScrollingAdjusments = false;
        }

        adjustSelection();

        previousSelectedRow = getCurrentRow();
    }

    public boolean containsProperty(ClientPropertyDraw property) {
        return properties.contains(property);
    }

    public List<ClientPropertyDraw> getOrderedVisibleProperties(List<ClientPropertyDraw> propertiesList) {
        List<ClientPropertyDraw> result = new ArrayList<>();

        for (ClientPropertyDraw property : propertiesList) {
            boolean add = !property.hide;
            if (add && hasUserPreferences()) {
                Boolean userHide = getUserHide(property);
                if (userHide == null || !userHide) {
                    if (getUserOrder(property) == null) {
                        setUserHide(property, true);
                        setUserOrder(property, Short.MAX_VALUE + propertiesList.indexOf(property));
                        add = false;
                    }
                } else {
                    add = false;
                }
            }

            if (add) {
                result.add(property);
            }
        }

        if (hasUserPreferences()) {
            Collections.sort(result, getCurrentPreferences().getUserOrderComparator());
        }
        return result;
    }

    private void changeCurrentObjectLater() {
        final ClientGroupObjectValue selectedObject = getSelectedObject();
        if (!currentObject.equals(selectedObject) && selectedObject != null) {
            setCurrentObject(selectedObject);
            SwingUtils.invokeLaterSingleAction(
                    groupObject.getActionID(),
                    new ActionListener() {
                        public void actionPerformed(ActionEvent ae) {
                            changeCurrentObject(selectedObject);
                        }
                    }, 50);
        }
    }

    private void changeCurrentObject(ClientGroupObjectValue selectedObject) {
        if (currentObject.equals(selectedObject)) {
            try {
                //Коммит закомменчен, поскольку он приводит к неправильной работе SEEK
                //calledChangeGroupObject = true;
                form.changeGroupObject(groupObject, selectedObject);
            } catch (IOException ioe) {
                throw new RuntimeException(getString("errors.error.changing.current.object"), ioe);
            }
        }
    }

    private void refreshColumnModel() {
        TableColumnModel columnModel = getColumnModel();
        int newColumnCount = model.getColumnCount();
        int oldColumnCount = columnModel.getColumnCount();
        if (newColumnCount > oldColumnCount) {
            while (newColumnCount > columnModel.getColumnCount()) {
                addColumn(new GridTableColumn(columnModel.getColumnCount()));
            }
        } else {
            while (newColumnCount < columnModel.getColumnCount()) {
                removeColumn(columnModel.getColumn(columnModel.getColumnCount() - 1));
            }
        }

        int rowHeight = 0;
        hasFocusableCells = false;
        for (int i = 0; i < model.getColumnCount(); ++i) {
            ClientPropertyDraw property = model.getColumnProperty(i);

            GridTableColumn column = getColumn(i);

            column.setHeaderValue(getColumnCaption(i));

            Format format = property.setFormat(getColumnPattern(i));
            PropertyRenderer renderer = property.getRendererComponent();
            if (renderer instanceof LabelPropertyRenderer) {
                ((LabelPropertyRenderer) renderer).setFormat(format);
            }

            rowHeight = max(rowHeight, property.getBaseValueHeight(this));

            hasFocusableCells |= property.focusable == null || property.focusable;

            boolean samePropAsPrevious = i != 0 && property == model.getColumnProperty(i - 1);
            final int index = i;
            if (!samePropAsPrevious && property.editKey != null) {
                form.getLayout().addKeyBinding(property.editKey, property.groupObject, new ClientFormLayout.KeyBinding() {
                    @Override
                    public boolean keyPressed(KeyEvent e) {
                        if (isShowing()) {
                            int leadRow = getSelectionModel().getLeadSelectionIndex();
                            if (leadRow != -1 && !isEditing()) {
                                keyController.stopRecording();
                                editCellAt(leadRow, index);
                            }
                            //даже если редактирование не произошло, всё равно съедаем нажатие клавиши, для единообразия
                            return true;
                        }
                        return false;
                    }
                });
            }
        }

        gridPropertyTable.updateLayoutWidth();

        setFocusable(hasFocusableCells);

        if (model.getColumnCount() != 0) {

            if (getRowHeight() != rowHeight) {
                setRowHeight(rowHeight);
            }

            repaint();
            tableHeader.resizeAndRepaint();
            gridController.setForceHidden(false);
        } else {
            gridController.setForceHidden(true);
        }
    }

    private GridTableColumn getColumn(int index) {
        return (GridTableColumn) columnModel.getColumn(index);
    }

    private String getColumnCaption(int column) {
        ClientPropertyDraw cell = model.getColumnProperty(column);
        String userCaption = getUserCaption(cell);
        return userCaption != null ? userCaption : model.getColumnName(column);
    }

    private String getColumnPattern(int column) {
        return getUserPattern(model.getColumnProperty(column));
    }

    private void adjustSelection() {
        //надо сдвинуть ViewPort - иначе дергаться будет
        final int currentInd = scrollToIndex;
        if (scrollToOldObject && oldRowScrollTop != -1) {
            ClientGroupObjectValue currentKey = getCurrentObject();
            if (currentKey != null && currentInd >= 0 && currentInd < getRowCount()) {
                int newVerticalScrollPosition = max(0, currentInd * getRowHeight() - oldRowScrollTop);

                final Rectangle viewRect = ((JViewport) getParent()).getViewRect();
                viewRect.y = newVerticalScrollPosition;
                ((JViewport) getParent()).setViewPosition(viewRect.getLocation());
            }
            oldRowScrollTop = -1;
        }
        selectRow(selectIndex);
        scrollToIndex = -1;
        selectIndex = -1;
        scrollToOldObject = true;

        if (threadLocalIsStopCellEditing.get() != null && threadLocalUIAction.get() != null) {
            UIAction uiAction = threadLocalUIAction.get();
            int index = getSelectionModel().getLeadSelectionIndex();
            int newLeadRow = index < getRowCount() ? index : -1;

            Field leadRowField;
            try {
                leadRowField = uiAction.getClass().getDeclaredField("leadRow");
                if (leadRowField != null) {
                    leadRowField.setAccessible(true);
                    int oldLeadRow = leadRowField.getInt(uiAction);
                    if (newLeadRow != oldLeadRow) {
                        leadRowField.set(uiAction, newLeadRow);
                    }
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                Throwables.propagate(e);
            }
        }
    }

    private void selectColumn(int columnNumber) {
        selectCell(getCurrentRow(), columnNumber);
    }

    private void selectRow(int rowNumber) {
        selectCell(rowNumber, getColumnModel().getSelectionModel().getLeadSelectionIndex());
    }

    private void selectCell(int row, int col) {
        if (row < 0 || row >= getRowCount()
//            || col < 0 || col >= getColumnCount()
            ) {
            return;
        }

        // scrollRectToVisible обязательно должен идти до setLeadSelectionIndex
        // иначе, если объект за пределами текущего "окна", сработает JViewport.changeListener
        // и он изменит текущий объект на другой (firstRow или lastRow)
        scrollRectToVisible(getCellRect(row, col == -1 ? 0 : col, false));

        if (col == -1) {
            isInternalNavigating = true;
            changeSelection(row, 0, false, false);
            isInternalNavigating = false;
            moveToFocusableCellIfNeeded();
        } else {
            if (row != getSelectedRow() || col != getSelectedColumn()) {
                super.changeSelection(row, col, false, false);
            }
            getSelectionModel().setLeadSelectionIndex(row);
            getColumnModel().getSelectionModel().setLeadSelectionIndex(col);
        }
    }

    protected void centerAndSelectRow(int rowNumber) {
        assert rowNumber >= 0 && rowNumber < getRowCount();

        int rowTop = rowNumber * getRowHeight();

        Rectangle viewRect = ((JViewport) getParent()).getViewRect();

        viewRect.y = max(0, rowTop - max(0, (viewRect.height - getRowHeight()) / 2));

        ((JViewport) getParent()).setViewPosition(viewRect.getLocation());

        selectRow(rowNumber);
    }

    public void setRowKeysAndCurrentObject(List<ClientGroupObjectValue> irowKeys, ClientGroupObjectValue newCurrentObject) {
        //сначала пытаемся спозиционировать старый объект на том же месте
        int oldIndex = rowKeys.indexOf(currentObject);
        int newIndex = irowKeys.indexOf(currentObject);

        if ((!scrollToOldObject || (oldIndex == -1 || newIndex == -1)) && newCurrentObject != null) {
            //если старого объекта не нашли, то позиционируем новый
            oldIndex = rowKeys.indexOf(newCurrentObject);
            newIndex = irowKeys.indexOf(newCurrentObject);
        }

        if (oldIndex != -1 && newIndex != -1) {
            final Rectangle viewRect = ((JViewport) getParent()).getViewRect();
            oldRowScrollTop = getSelectedRow() * getRowHeight() - viewRect.y;
            viewMoveInterval = newIndex - oldIndex;
        }
        scrollToIndex = newIndex;
        //// игнорируем newCurrentObject при вызове changeCurrentObject() для избежания конфликтов (асинхронный запрос)
        selectIndex = /*!calledChangeGroupObject && */newCurrentObject != null ? irowKeys.indexOf(newCurrentObject) : newIndex;

        rowKeys = irowKeys;

        if (newCurrentObject != null) {
            setCurrentObject(newCurrentObject);
        }

        if (newIndex == -1) {
            selectionController.resetSelection();
            updateSelectionInfo();
        }

        //calledChangeGroupObject = false;
    }

    public void modifyGroupObject(ClientGroupObjectValue rowKey, boolean add, int position) {
        if (add) {
            List<ClientGroupObjectValue> irowKeys = new ArrayList<>(rowKeys);
            if (position >= 0 && position <= irowKeys.size()) {
                irowKeys.add(position, rowKey);
            } else {
                irowKeys.add(rowKey);
            }
            setRowKeysAndCurrentObject(irowKeys, rowKey);
        } else {
            setRowKeysAndCurrentObject(removeList(rowKeys, rowKey), currentObject.equals(rowKey) ? getNearObject(singleValue(rowKey), rowKeys) : null);
        }
    }

    private void setCurrentObject(ClientGroupObjectValue value) {
        assert value == null || rowKeys.isEmpty() || rowKeys.contains(value);
        currentObject = value;
    }

    public ClientGroupObjectValue getCurrentObject() {
        return currentObject;
    }

    @Override
    public int getCurrentRow() {
        return rowKeys.indexOf(currentObject);
    }

    public void setTabVertical(boolean tabVertical) {
        this.tabVertical = tabVertical;
    }

    public List<Pair<ClientPropertyDraw, ClientGroupObjectValue>> getVisibleProperties() {
        //возвращает все свойства, за исключеним тех, что формируют группы в колонки без единого значения
        List<Pair<ClientPropertyDraw, ClientGroupObjectValue>> props = new ArrayList<>();
        for (ClientPropertyDraw property : properties) {
            for (ClientGroupObjectValue columnKey : columnKeys.get(property)) {
                if (model.getPropertyIndex(property, columnKey) != -1) {
                    props.add(new Pair<>(property, columnKey));
                }
            }
        }
        return props;
    }

    public List<ClientGroupObjectValue> getRowKeys() {
        return rowKeys;
    }

    @Override
    public void doLayout() {
        gridPropertyTable.doLayout();
    }

    @Override
    public GridTableModel getModel() {
        return (GridTableModel) super.getModel();
    }

    public ClientFormController getForm() {
        return form;
    }

    public ClientPropertyDraw getCurrentProperty() {
        ClientPropertyDraw selectedProperty = getSelectedProperty();
        return selectedProperty != null
                ? selectedProperty
                : model.getColumnCount() > 0
                ? model.getColumnProperty(0)
                : null;
    }

    @Override
    public boolean richTextSelected() {
        ClientPropertyDraw property = getCurrentProperty();
        return property != null && property.baseType instanceof ClientStringClass && ((ClientStringClass) property.baseType).rich;
    }

    public void pasteTable(List<List<String>> table) {
        boolean singleV = selectionController.hasSingleSelection();
        int selectedColumn = getColumnModel().getSelectionModel().getLeadSelectionIndex();
        if (selectedColumn == -1) {
            return;
        }
        int tableColumns = 0;
        if (!table.isEmpty()) {
            tableColumns = table.get(0).size();
        }
        boolean singleC = table.size() == 1 && tableColumns == 1;
        if (!singleV || !singleC) {
            int answer = SwingUtils.showConfirmDialog(form.getLayout(), getString("form.grid.sure.to.paste.multivalue"), "", JOptionPane.QUESTION_MESSAGE, 1, false, 0);
            if (answer == JOptionPane.NO_OPTION) {
                return;
            }
        }

        try {
            if (singleV && !singleC) {
                //т.е. вставляем в одну ячейку, но не одно значение
                int columnsToInsert = Math.min(tableColumns, getColumnCount() - selectedColumn);

                List<ClientPropertyDraw> propertyList = new ArrayList<>();
                List<ClientGroupObjectValue> columnKeys = new ArrayList<>();
                for (int i = 0; i < columnsToInsert; i++) {
                    ClientPropertyDraw propertyDraw = model.getColumnProperty(selectedColumn + i);
                    propertyList.add(propertyDraw);
                    columnKeys.add(model.getColumnKey(selectedColumn + i));
                }

                form.pasteExternalTable(propertyList, columnKeys, table, columnsToInsert);
            } else {
                //вставляем в несколько ячеек, используем только 1е значение
                String sPasteValue = table.get(0).get(0);

                Map<ClientPropertyDraw, PasteData> paste = new HashMap<>();

                for (Map.Entry<Pair<ClientPropertyDraw, ClientGroupObjectValue>, Pair<List<ClientGroupObjectValue>, List<Object>>> e : selectionController.getSelectedCells().entrySet()) {
                    Pair<ClientPropertyDraw, ClientGroupObjectValue> propertyColumn = e.getKey();
                    List<ClientGroupObjectValue> rowKeys = e.getValue().first;
                    List<Object> oldValues = e.getValue().second;

                    ClientPropertyDraw property = propertyColumn.first;
                    ClientGroupObjectValue columnKey = propertyColumn.second;

                    List<ClientGroupObjectValue> keys;
                    if (columnKey.isEmpty()) {
                        keys = rowKeys;
                    } else {
                        keys = new ArrayList<>();
                        for (ClientGroupObjectValue rowKey : rowKeys) {
                            keys.add(rowKey.isEmpty() ? columnKey : new ClientGroupObjectValue(rowKey, columnKey));
                        }
                    }

                    Object newValue = sPasteValue == null ? null : property.parseChangeValueOrNull(sPasteValue);
                    if (property.canUsePasteValueForRendering()) {
                        for (ClientGroupObjectValue key : keys) {
                            Map<ClientGroupObjectValue, Object> propValues = values.get(property);
                            if (propValues.containsKey(key)) {
                                propValues.put(key, newValue);
                            }
                        }
                    }

                    PasteData pasteData = paste.get(property);
                    if (pasteData == null) {
                        pasteData = new PasteData(newValue, keys, oldValues);
                        paste.put(property, pasteData);
                    } else {
                        pasteData.keys.addAll(keys);
                        pasteData.oldValues.addAll(oldValues);
                    }
                }

                form.pasteMulticellValue(paste);
                selectionController.resetSelection();
            }
            updateTable();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private boolean isCellFocusable(int row, int col) {
        //вообще говоря нужно скорректировать индексы к модели, но не актуально,
        //т.к. у нас всё равно не включено перемещение/сокрытие колонок
        return model.isCellFocusable(row, col);
    }

    @Override
    public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
        if (isInternalNavigating || isCellFocusable(rowIndex, columnIndex)) {
            if (!properties.isEmpty() && model.getColumnCount() > 0) {
                if (rowIndex >= getRowCount()) {
                    changeSelection(getRowCount() - 1, columnIndex, toggle, extend);
                    return;
                }
                selectionController.changeSelection(rowIndex, columnIndex, toggle, extend);
            }
            if (getCurrentRow() == rowIndex) {
                toggle = false;
            }
            super.changeSelection(rowIndex, columnIndex, toggle, extend);
            updateSelectionInfo();
            repaint();
        }
    }

    @Override
    protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        boolean result = false;

        try {
            Method getInputMapMethod = JComponent.class.getDeclaredMethod("getInputMap", int.class, boolean.class);
            Object inputMap = null;
            if (getInputMapMethod != null) {
                getInputMapMethod.setAccessible(true);
                inputMap = getInputMapMethod.invoke(this, condition, false);
            }

            Method getActionMapMethod = JComponent.class.getDeclaredMethod("getActionMap", boolean.class);
            Object actionMap = null;
            if (getActionMapMethod != null) {
                getActionMapMethod.setAccessible(true);
                actionMap = getActionMapMethod.invoke(this, false);
            }
            if(inputMap != null && actionMap != null && isEnabled()) {
                Object binding = ((InputMap) inputMap).get(ks);
                Action action = (binding == null) ? null : ((ActionMap) actionMap).get(binding);
                if (action instanceof UIAction) {
                    threadLocalUIAction.set((UIAction) action);
                }
            }

            result = super.processKeyBinding(ks, e, condition, pressed);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ex) {
            Throwables.propagate(ex);
        } finally {
            threadLocalUIAction.set(null);
        }

        return result;
    }

    @Override
    public void editingStopped(ChangeEvent e) {
        TableCellEditor editor = getCellEditor();
        if (editor != null) {
            threadLocalIsStopCellEditing.set(true);
        }
        try {
            super.editingStopped(e);
        } finally {
            threadLocalIsStopCellEditing.set(null);
        }
    }

    private void updateSelectionInfo() {
        int quantity = selectionController.getQuantity();
        if (quantity > 1) {
            int numbers = selectionController.getNumbersQuantity();
            if (numbers > 1) {
                BigDecimal sum = selectionController.getSum();
                groupController.updateSelectionInfo(quantity, format(sum), format(sum.divide(BigDecimal.valueOf(numbers), sum.scale(), RoundingMode.HALF_UP)));
                return;
            }
        }
        groupController.updateSelectionInfo(quantity, null, null);
    }

    private String format(BigDecimal number) {
        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(number.scale());
        return nf.format(number);
    }

    public String getSelectedTable() throws ParseException {
        return selectionController.getSelectedTableString();
    }

    public boolean editCellAt(int row, int column, EventObject editEvent) {
        boolean edited = super.editCellAt(row, column, editEvent);
        if (!editPerformed) {
            assert !edited;

            if (groupObject.grid.quickSearch) {
                quickSearch(editEvent);
            } else {
                quickFilter(editEvent);
            }

            return false;
        }

        return edited;
    }

    private void quickSearch(EventObject editEvent) {
        // здесь делаем дополнительную проверку на то, что мы ещё не обработывали данный ивент,
        // т.к. в JRE7 генерируется дополнительный KeyStoke с тем же ивентом для extended символов (в т.ч. для русских)
        if (editEvent != lastQuickSearchEvent && getRowCount() > 0 && getColumnCount() > 0 && KeyStrokes.isSuitableStartFilteringEvent(editEvent)) {
            assert editEvent instanceof KeyEvent;

            char ch = ((KeyEvent)editEvent).getKeyChar();

            long currentTime = System.currentTimeMillis();
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
                Object value = model.getValueAt(i, searchColumn);
                if (value != null && value.toString().regionMatches(true, 0, lastQuickSearchPrefix, 0, lastQuickSearchPrefix.length())) {
                    centerAndSelectRow(i);
                    break;
                }
            }

            lastQuickSearchEvent = editEvent;
            lastQuickSearchTime = currentTime;
        }
    }

    private void quickFilter(EventObject editEvent) {
        if (KeyStrokes.isSuitableStartFilteringEvent(editEvent)) {
            ClientPropertyDraw currentProperty = getCurrentProperty();
            ClientPropertyDraw filterProperty = currentProperty != null && currentProperty.quickFilterProperty != null
                                                ? currentProperty.quickFilterProperty
                                                : null;
            groupController.quickEditFilter((KeyEvent) editEvent, filterProperty);
        }
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        if (row < getRowCount() && column < getColumnCount()) {
            values.get(getProperty(column)).put(
                    new ClientGroupObjectValue(rowKeys.get(row), model.getColumnKey(column)),
                    value
            );
            super.setValueAt(value, row, column);
        }
    }

    private void moveToFocusableCellIfNeeded() {
        int row = getSelectionModel().getLeadSelectionIndex();
        int col = getColumnModel().getSelectionModel().getLeadSelectionIndex();
        if (!isCellFocusable(row, col)) {
            moveToNextCellAction.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));
        }
    }

    public boolean addProperty(final ClientPropertyDraw property) {
        if (!properties.contains(property)) {
            // конечно кривовато определять порядок по номеру в листе, но потом надо будет сделать по другому
            int ins = relativePosition(property, form.getPropertyDraws(), properties);
            properties.add(ins, property);
            selectionController.addProperty(property);
            return true;
        } else {
            return false;
        }
    }

    public boolean removeProperty(ClientPropertyDraw property) {
        if (properties.contains(property)) {
            selectionController.removeProperty(property, columnKeys.get(property));
            properties.remove(property);
            readOnlyValues.remove(property);
            values.remove(property);
            captions.remove(property);
            showIfs.remove(property);
            columnKeys.remove(property);
            return true;
        }

        return false;
    }

    public void updateColumnCaptions(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> captions) {
        this.captions.put(property, captions);
    }

    public void updateShowIfs(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> showIfs) {
        this.showIfs.put(property, showIfs);
    }

    public void updateReadOnlyValues(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> readOnlyValues) {
        this.readOnlyValues.put(property, readOnlyValues);
    }

    public void updateCellBackgroundValues(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> cellBackgroundValues) {
        this.cellBackgroundValues.put(property, cellBackgroundValues);
    }

    public void updateCellForegroundValues(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> cellForegroundValues) {
        this.cellForegroundValues.put(property, cellForegroundValues);
    }

    public void setColumnValues(ClientPropertyDraw property, Map<ClientGroupObjectValue, Object> values, boolean update) {
        Map<ClientGroupObjectValue, Object> propValues = this.values.get(property);
        if (!update || propValues == null) {
            this.values.put(property, values);
        } else {
            propValues = new HashMap<>(propValues);
            propValues.putAll(values);
            this.values.put(property, propValues);
        }
    }

    public void updateColumnKeys(ClientPropertyDraw property, List<ClientGroupObjectValue> columnKeys) {
        this.columnKeys.put(property, columnKeys);
    }

    public void updateRowBackgroundValues(Map<ClientGroupObjectValue, Object> rowBackground) {
        this.rowBackground = rowBackground;
    }

    public void updateRowForegroundValues(Map<ClientGroupObjectValue, Object> rowForeground) {
        this.rowForeground = rowForeground;
    }

    public Object getSelectedValue(ClientPropertyDraw property, ClientGroupObjectValue columnKey) {
        return getSelectedValue(model.getPropertyIndex(property, columnKey));
    }

    private Object getSelectedValue(int col) {
        int row = getSelectedRow();
        if (row != -1 && row < getRowCount() && col != -1 && col < getColumnCount()) {
            return getValueAt(row, col);
        } else {
            return null;
        }
    }

    public ClientGroupObjectValue getSelectedObject() {
        int rowIndex = getSelectedRow();
        if (rowIndex < 0 || rowIndex >= getRowCount()) {
            return null;
        }
        rowIndex = convertRowIndexToModel(rowIndex);

        try {
            return rowKeys.get(rowIndex);
        } catch (IndexOutOfBoundsException be) {
            be.printStackTrace();
            return null;
        }
    }

    public ClientPropertyDraw getSelectedProperty() {
        int colView = getSelectedColumn();
        if (colView < 0 || colView >= getColumnCount()) {
            return null;
        }

        int colModel = convertColumnIndexToModel(colView);
        if (colModel < 0) {
            return null;
        }

        return model.getColumnProperty(colModel);
    }

    public boolean isPressed(int row, int column) {
        return pressedCellRow == row && pressedCellColumn == column;
    }

    public ClientGroupObject getGroupObject() {
        return groupObject;
    }

    public int getColumnCount() {
        return model.getColumnCount();
    }

    public ClientPropertyDraw getProperty(int row, int col) {
        return model.getColumnProperty(col);
    }

    public ClientPropertyDraw getProperty(int col) {
        return model.getColumnProperty(col);
    }

    public List<ClientPropertyDraw> getProperties() {
        return properties;
    }

    public ClientGroupObjectValue getColumnKey(int row, int col) {
        return model.getColumnKey(col);
    }

    public Pair<ClientPropertyDraw, ClientGroupObjectValue> getColumnProperty(int column) {
        return new Pair<>(getProperty(column), getColumnKey(0, column));
    }

    public boolean isSelected(int row, int column) {
        return selectionController.isCellSelected(getColumnProperty(column), rowKeys.get(row));
    }

    public Color getBackgroundColor(int row, int column) {
        return model.getBackgroundColor(row, column);
    }

    public Color getForegroundColor(int row, int column) {
        return model.getForegroundColor(row, column);
    }

    public void changeGridOrder(ClientPropertyDraw property, Order modiType) throws IOException {
        int ind = getMinPropertyIndex(property);
        sortableHeaderManager.changeOrder(new Pair<>(property, ind == -1 ? ClientGroupObjectValue.EMPTY : model.getColumnKey(ind)), modiType);
    }

    public void clearGridOrders(ClientGroupObject groupObject) throws IOException {
        sortableHeaderManager.clearOrders(groupObject);
    }

    public int getMinPropertyIndex(ClientPropertyDraw property) {
        return model.getPropertyIndex(property, null);
    }

    public GridTableModel getTableModel() {
        return model;
    }

    /*@Override
    protected JTableHeader createDefaultTableHeader() {
        return new GridTableHeader(columnModel);
    }*/

    public void selectProperty(ClientPropertyDraw propertyDraw) {
        if (propertyDraw == null) {
            return;
        }

        int ind = getMinPropertyIndex(propertyDraw);
        if (ind != -1) {
            setColumnSelectionInterval(ind, ind);
        }
    }

    void configureEnclosingScrollPane(final JScrollPane pane) {
        assert pane.getViewport() == getParent();
        if (groupObject.pageSize != 0) {
            pane.getHorizontalScrollBar().addAdjustmentListener(new AdjustmentListener() {
                @Override
                public void adjustmentValueChanged(AdjustmentEvent e) {
                    if (skipScrollingAdjusments) {
                        return;
                    }

                    int currCol = getColumnModel().getSelectionModel().getLeadSelectionIndex();
                    if (currCol != -1 && getColumnCount() > 0) {
                        Pair<Integer, Integer> firstAndLast = getFirstAndLastVisibleColumns(pane);

                        int firstCol = firstAndLast.first;
                        int lastCol = firstAndLast.second;

                        if (lastCol < firstCol) {
                            return;
                        }

                        if (isLayouting) {
                            selectColumn(currCol);
                        } else {
                            if (currCol > lastCol) {
                                selectColumn(lastCol);
                            } else if (currCol < firstCol) {
                                selectColumn(firstCol);
                            }
                        }
                    }
                }
            });

            pane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
                @Override
                public void adjustmentValueChanged(AdjustmentEvent e) {
                    if (skipScrollingAdjusments) {
                        return;
                    }
                    int currRow = getCurrentRow();
                    if (currRow != -1) {
                        Rectangle viewRect = pane.getViewport().getViewRect();
                        int firstRow = rowAtPoint(new Point(0, viewRect.y + getRowHeight() - 1));
                        int lastRow = rowAtPoint(new Point(0, viewRect.y + viewRect.height - getRowHeight() + 1));

                        if (lastRow < firstRow) {
                            return;
                        }

                        if (isLayouting) {
                            selectRow(currRow);
                        } else {
                            if (currRow > lastRow) {
                                keyController.record(false, lastRow);
                                selectRow(lastRow);
                            } else if (currRow < firstRow) {
                                keyController.record(true, firstRow);
                                selectRow(firstRow);
                            }
                        }
                    }
                }
            });

            pane.addComponentListener(new ComponentAdapter() {
                public void componentResized(ComponentEvent ce) {
                    updatePageSizeIfNeeded(true);
                }
            });
        }
    }

    private Pair<Integer, Integer> getFirstAndLastVisibleColumns(JScrollPane pane) {
        Rectangle viewRect = pane.getViewport().getViewRect();

        TableColumnModel columnModel = getColumnModel();
        int cc = getColumnCount();

        int first = -1;
        int last = -1;

        int x = 0;
        for (int column = 0; column < cc; column++) {
            if (first == -1 && x >= viewRect.x && isCellFocusable(0, column)) {
                first = column;
            }
            x += columnModel.getColumn(column).getWidth();
            if (x <= viewRect.x + viewRect.width + 1 && isCellFocusable(0, column)) {
                last = column;
            }
        }

        return new Pair<>(first == -1 ? 0 : first, last == -1 ? 0 : last);
    }

    public void updatePageSizeIfNeeded(boolean checkVisible) {
        Integer currentPageSize = currentGridPreferences.getPageSize();
        int newPageSize = currentPageSize != null ? currentPageSize : (getParent().getHeight() / getRowHeight() + 1);
        if (newPageSize != pageSize && (!checkVisible || (SwingUtils.isRecursivelyVisible(this) && !isRecursivelyBlocked(this)))) {
            try {
                form.changePageSize(groupObject, newPageSize);
                pageSize = newPageSize;
            } catch (IOException e) {
                throw new RuntimeException(getString("errors.error.changing.page.size"), e);
            }
        }
    }

    public boolean isRecursivelyBlocked(Component component) {
        if (component == null) {
            return false;
        }
        if (component instanceof ClientFormLayout) {
            return ((ClientFormLayout) component).isBlocked();
        } else {
            return isRecursivelyBlocked(component.getParent());
        }
    }

    public boolean userPreferencesSaved() {
        return userGridPreferences.hasUserPreferences();
    }

    public boolean generalPreferencesSaved() {
        return generalGridPreferences.hasUserPreferences();
    }

    public GroupObjectUserPreferences getCurrentUserGridPreferences() {
        if (currentGridPreferences.hasUserPreferences()) {
            return currentGridPreferences.convertPreferences();
        }
        return userGridPreferences.convertPreferences();
    }

    public GroupObjectUserPreferences getGeneralGridPreferences() {
        return generalGridPreferences.convertPreferences();
    }

    public void resetCurrentPreferences(boolean initial) {
        currentGridPreferences = new GridUserPreferences(userGridPreferences.hasUserPreferences() ? userGridPreferences : generalGridPreferences);
        if (!initial) {
            try {
                gridController.clearGridOrders(groupObject);
                if (!currentGridPreferences.hasUserPreferences()) {
                    gridController.getGroupController().applyDefaultOrders();
                } else {
                    gridController.getGroupController().applyUserOrders();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void resetPreferences(final boolean forAllUsers, final boolean completeReset, final Runnable onSuccess, final Runnable onFailure) {
        currentGridPreferences.resetPreferences();

        if (!properties.isEmpty()) {
            Runnable successCallback = new Runnable() {
                @Override
                public void run() {
                    if (forAllUsers) {
                        generalGridPreferences.resetPreferences();
                        if (completeReset) {
                            userGridPreferences.resetPreferences();
                        }
                    } else {
                        userGridPreferences.resetPreferences();
                    }

                    RmiQueue.runAction(new Runnable() {
                        @Override
                        public void run() {
                            resetCurrentPreferences(false);

                            onSuccess.run();

                            JOptionPane.showMessageDialog(Main.frame, getString("form.grid.preferences.reset.success"), getString("form.grid.preferences.save"), JOptionPane.INFORMATION_MESSAGE);
                        }
                    });
                }
            };

            Runnable failureCallback = new Runnable() {
                @Override
                public void run() {
                    RmiQueue.runAction(new Runnable() {
                        @Override
                        public void run() {
                            resetCurrentPreferences(false);
                            onFailure.run();
                        }
                    });
                }
            };

            GridUserPreferences prefs;
            if (forAllUsers) {
                prefs = completeReset ? null : userGridPreferences;
            } else {
                // assert !completeReset;
                prefs = generalGridPreferences;
            }

            form.saveUserPreferences(currentGridPreferences, forAllUsers, completeReset, successCallback, failureCallback, getHiddenProps(prefs));
        }
    }

    public void saveCurrentPreferences(final boolean forAllUsers, final Runnable onSuccess, final Runnable onFailure) {
        currentGridPreferences.setHasUserPreferences(true);

        if (!properties.isEmpty()) {
            Runnable successCallback = new Runnable() {
                @Override
                public void run() {
                    RmiQueue.runAction(new Runnable() {
                        @Override
                        public void run() {
                            if (forAllUsers) {
                                generalGridPreferences = new GridUserPreferences(currentGridPreferences);
                                resetCurrentPreferences(false);
                            } else {
                                userGridPreferences = new GridUserPreferences(currentGridPreferences);
                            }

                            onSuccess.run();

                            JOptionPane.showMessageDialog(Main.frame, getString("form.grid.preferences.save.success"), getString("form.grid.preferences.save"), JOptionPane.INFORMATION_MESSAGE);
                        }
                    });

                }
            };

            Runnable failureCallback = new Runnable() {
                @Override
                public void run() {
                    RmiQueue.runAction(new Runnable() {
                        @Override
                        public void run() {
                            resetCurrentPreferences(false);
                            onFailure.run();
                        }
                    });
                }
            };

            GridUserPreferences prefs;
            if (forAllUsers) {
                prefs = userGridPreferences.hasUserPreferences() ? userGridPreferences : currentGridPreferences;
            } else {
                prefs = currentGridPreferences;
            }

            form.saveUserPreferences(currentGridPreferences, forAllUsers, false, successCallback, failureCallback, getHiddenProps(prefs));
        }
    }

    private String[] getHiddenProps(final GridUserPreferences preferences) {
        List<String> result = new ArrayList<>();
        if (preferences != null && preferences.hasUserPreferences()) {
            for (ClientPropertyDraw propertyDraw : preferences.columnUserPreferences.keySet()) {
                Boolean userHide = preferences.columnUserPreferences.get(propertyDraw).userHide;
                if (userHide != null && userHide) {
                    result.add(propertyDraw.getSID());
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }

    public void refreshUPHiddenProps(String[] propSids) {
        assert groupObject != null; // при null нету таблицы, а значит и настроек
        form.refreshUPHiddenProperties(groupObject.getSID(), propSids);
    }

    public GridUserPreferences getCurrentPreferences() {
        return currentGridPreferences;
    }

    public boolean hasUserPreferences() {
        return currentGridPreferences.hasUserPreferences();
    }

    public void setHasUserPreferences(boolean hasUserPreferences) {
        currentGridPreferences.setHasUserPreferences(hasUserPreferences);
    }

    public FontInfo getUserFont() {
        return currentGridPreferences.fontInfo;
    }

    public Integer getUserPageSize() {
        return currentGridPreferences.pageSize;
    }

    public Integer getUserHeaderHeight() {
        return currentGridPreferences.headerHeight;
    }

    public Boolean getUserHide(ClientPropertyDraw property) {
        return currentGridPreferences.getUserHide(property);
    }

    public String getUserCaption(ClientPropertyDraw property) {
        return currentGridPreferences.getUserCaption(property);
    }

    public String getUserPattern(ClientPropertyDraw property) {
        return currentGridPreferences.getUserPattern(property);
    }

    public Integer getUserWidth(ClientPropertyDraw property) {
        return currentGridPreferences.getUserWidth(property);
    }

    public Integer getUserOrder(ClientPropertyDraw property) {
        return currentGridPreferences.getUserOrder(property);
    }

    public Integer getUserSort(ClientPropertyDraw property) {
        return currentGridPreferences.getUserSort(property);
    }

    public Boolean getUserAscendingSort(ClientPropertyDraw property) {
        return currentGridPreferences.getUserAscendingSort(property);
    }

    public void setUserFont(Font userFont) {
        currentGridPreferences.fontInfo = FontInfo.createFrom(userFont);
    }

    public void setUserPageSize(Integer userPageSize) {
        currentGridPreferences.pageSize = userPageSize;
        updatePageSizeIfNeeded(false);
    }

    public void setUserHeaderHeight(Integer userHeaderHeight) {
        currentGridPreferences.headerHeight = userHeaderHeight;
    }

    public void setUserHide(ClientPropertyDraw property, Boolean userHide) {
        currentGridPreferences.setUserHide(property, userHide);
    }

    public void setUserColumnSettings(ClientPropertyDraw property, String userCaption, String userPattern, int userOrder, Boolean userHide) {
        currentGridPreferences.setUserColumnsSettings(property, userCaption, userPattern, userOrder, userHide);
    }

    public void setUserWidth(ClientPropertyDraw property, Integer userWidth) {
        currentGridPreferences.setUserWidth(property, userWidth);
    }

    public void setUserOrder(ClientPropertyDraw property, Integer userOrder) {
        currentGridPreferences.setUserOrder(property, userOrder);
    }

    public void setUserSort(ClientPropertyDraw property, Integer userSort) {
        currentGridPreferences.setUserSort(property, userSort);
    }

    public void setUserAscendingSort(ClientPropertyDraw property, Boolean userAscendingSort) {
        currentGridPreferences.setUserAscendingSort(property, userAscendingSort);
    }

    public Comparator<ClientPropertyDraw> getUserSortComparator() {
        return getCurrentPreferences().getUserSortComparator();
    }

    public void setLayouting(boolean isLayouting) {
        this.isLayouting = isLayouting;
    }

    private class GoToNextCellAction extends AbstractAction {
        private boolean forward;

        public GoToNextCellAction(boolean forward) {
            this.forward = forward;
        }

        private int moveNext(int row, int column, boolean forward) {

            if (forward) {
                if (tabVertical) {
                    row++;
                } else {
                    column++;
                }
            } else {
                if (tabVertical) {
                    row--;
                } else {
                    column--;
                }
            }
            int num;
            if (tabVertical) {
                num = column * getRowCount() + row;
            } else {
                num = row * getColumnCount() + column;
            }
            if (num < 0) {
                num += getColumnCount() * getRowCount();
            }
            if (num >= getColumnCount() * getRowCount()) {
                num = 0;
            }
            return num;
        }

        public void actionPerformed(ActionEvent e) {
            if (!hasFocusableCells || rowKeys.size() == 0) {
                return;
            }

            if (!form.commitCurrentEditing()) {
                return;
            }

            isInternalNavigating = true;

            int initRow = getSelectedRow();
            int initColumn = getSelectedColumn();

            int row = getSelectedRow();
            int column = getSelectedColumn();
            int oRow;
            int oColumn;
            if (row == -1 && column == -1) {
                changeSelection(0, 0, false, false);
            }
            do {
                int next = moveNext(row, column, forward);

                oRow = row;
                oColumn = column;

                if (tabVertical) {
                    column = next / getRowCount();
                    row = next % getRowCount();
                } else {
                    row = next / getColumnCount();
                    column = next % getColumnCount();
                }
                if (((row == 0 && column == 0 && forward) || (row == getRowCount() - 1 && column == getColumnCount() - 1 && (!forward)))
                        && isCellFocusable(initRow, initColumn)) {
                    row = initRow;
                    column = 0;
                    break;
                }
            } while ((oRow != row || oColumn != column) && !isCellFocusable(row, column));

            changeSelection(row, column, false, false);

            isInternalNavigating = false;
        }
    }

    private class GoToLastCellAction extends AbstractAction {
        private Action oldMoveLastAction;
        private Action oldMoveAction;

        public GoToLastCellAction(Action oldMoveLastAction, Action oldMoveAction) {
            this.oldMoveLastAction = oldMoveLastAction;
            this.oldMoveAction = oldMoveAction;
        }

        public void actionPerformed(ActionEvent e) {
            if (!hasFocusableCells || rowKeys.size() == 0) {
                return;
            }
            isInternalNavigating = true;

            oldMoveLastAction.actionPerformed(e);

            int row = getSelectedRow();
            int column = getSelectedColumn();
            int oRow = row + 1;
            int oColumn = column + 1;
            while ((oRow != row || oColumn != column) && !isCellFocusable(row, column)) {
                oldMoveAction.actionPerformed(e);
                oRow = row;
                oColumn = column;

                row = getSelectedRow();
                column = getSelectedColumn();
            }

            isInternalNavigating = false;
        }
    }

    private class ScrollToEndAction extends AbstractAction {
        private final Scroll direction;

        private ScrollToEndAction(Scroll direction) {
            this.direction = direction;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            RmiQueue.runAction(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (groupObject.pageSize != 0) {
                            scrollToOldObject = false;
                            form.changeGroupObject(groupObject, direction);
                        } else if (!rowKeys.isEmpty()) {
                            switch (direction) {
                                case HOME:
                                    selectRow(0);
                                    break;
                                case END:
                                    selectRow(rowKeys.size() - 1);
                                    break;
                            }
                        }
                    } catch (IOException ioe) {
                        throw new RuntimeException(getString("errors.error.moving.to.the.node"), ioe);
                    }
                }
            });
        }
    }

    private class GridTableHeader extends JTableHeader {
        public GridTableHeader(TableColumnModel columnModel) {
            super(columnModel);
            setReorderingAllowed(false);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int index = columnModel.getColumnIndexAtX(e.getPoint().x);
            if (index == -1) {
                return super.getToolTipText(e);
            }
            int modelIndex = columnModel.getColumn(index).getModelIndex();

            return model.getColumnProperty(modelIndex).getTooltipText(getColumnCaption(index));
        }
    }

    private class GridTableHeaderUI extends WindowsTableHeaderUI {
        @Override
        public void installUI(JComponent c) {
            super.installUI(c);

            header.remove(rendererPane);
            rendererPane = new CellRendererPane() {
                @Override
                public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
                    super.paintComponent(g, c, p, x, y, w, h, shouldValidate);

                    int index = columnModel.getColumnIndexAtX(x);
                    if (index == -1) {
                        return;
                    }
                    int modelIndex = columnModel.getColumn(index).getModelIndex();

                    ClientPropertyDraw property = model.getColumnProperty(modelIndex);
                    if (property.notNull) {
                        SwingUtils.paintRightBottomCornerTriangle((Graphics2D) g, 5, Color.RED, x - 2, -3, w, h); // -2/-3 - не залазим на границы
                    } else if (property.hasChangeAction) {
                        SwingUtils.paintRightBottomCornerTriangle((Graphics2D) g, 5, new Color(120, 170, 208), x - 2, -3, w, h);
                    }
                }
            };
            header.add(rendererPane);
        }
    }

    public class GridTableColumn extends TableColumn {
        public GridTableColumn(int index) {
            super(index);
        }

        @Override
        public TableCellRenderer getHeaderRenderer() {
            TableCellRenderer defaultHeaderRenderer = tableHeader.getDefaultRenderer();
            if (defaultHeaderRendererRef == null || defaultHeaderRendererRef.get() != defaultHeaderRenderer) {
                defaultHeaderRendererRef = new WeakReference<>(defaultHeaderRenderer);
                wrapperHeaderRenderer = new MultiLineHeaderRenderer(defaultHeaderRenderer, sortableHeaderManager) {
                    @Override
                    public Component getTableCellRendererComponent(JTable itable, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                        Component comp = super.getTableCellRendererComponent(itable, value, isSelected, hasFocus, row, column);
                        model.getColumnProperty(column).design.designHeader(comp);
                        return comp;
                    }
                };
            }
            return wrapperHeaderRenderer;
        }
    }

    public Map<Pair<ClientPropertyDraw, ClientGroupObjectValue>, Boolean> getOrderDirections() {
        return sortableHeaderManager.getOrderDirections();
    }

    public FontInfo getDesignFont() {
        return groupObject.grid.design.getFont();
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return gridPropertyTable.getScrollableTracksViewportWidth();
    }
    
    public Dimension getMaxPreferredSize(Dimension preferredSize) {
        return gridPropertyTable.getMaxPreferredSize(preferredSize);
    }

    private final GridPropertyTable gridPropertyTable = new GridPropertyTable() {
        public void setUserWidth(ClientPropertyDraw property, Integer value) {
            GridTable.this.setUserWidth(property, value);
        }

        public Integer getUserWidth(ClientPropertyDraw property) {
            return GridTable.this.getUserWidth(property);
        }

        public int getColumnsCount() {
            return model.getColumnCount();
        }

        public ClientPropertyDraw getColumnPropertyDraw(int i) {
            return model.getColumnProperty(i);
        }

        public TableColumn getColumnDraw(int i) {
            return getColumn(i);
        }

        public JTable getTable() {
            return GridTable.this;
        }
    };
}