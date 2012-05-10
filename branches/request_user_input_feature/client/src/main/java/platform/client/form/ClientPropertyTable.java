package platform.client.form;

import com.google.common.base.Preconditions;
import platform.client.SwingUtils;
import platform.client.form.cell.CellTableInterface;
import platform.client.form.cell.ClientAbstractCellEditor;
import platform.client.form.cell.ClientAbstractCellRenderer;
import platform.client.form.dispatch.EditPropertyDispatcher;
import platform.client.logics.ClientGroupObjectValue;
import platform.client.logics.ClientPropertyDraw;
import platform.client.logics.classes.ClientType;
import platform.interop.KeyStrokes;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.EventObject;

public abstract class ClientPropertyTable extends JTable implements TableTransferHandler.TableInterface, CellTableInterface, EditPropertyHandler {
    private final EditPropertyDispatcher editDispatcher = new EditPropertyDispatcher(this);
    protected final EditBindingMap editBindingMap = new EditBindingMap();
    private final CellTableContextMenuHandler contextMenuHandler = new CellTableContextMenuHandler(this);

    protected EventObject editEvent;
    protected int editRow;
    protected int editCol;
    protected ClientType currentEditType;
    protected Object currentEditValue;
    protected boolean editPerformed;
    protected boolean commitingValue;

    protected ClientPropertyTable(TableModel model) {
        super(model);

        SwingUtils.setupClientTable(this);

        setDefaultRenderer(Object.class, new ClientAbstractCellRenderer());
        setDefaultEditor(Object.class, new ClientAbstractCellEditor(this));

        initializeActionMap();

        contextMenuHandler.install();
    }

    private void initializeActionMap() {
        //  Have the enter key work the same as the tab key
        InputMap im = getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        im.put(KeyStrokes.getEnter(), im.get(KeyStrokes.getTab()));
    }

    public ClientType getCurrentEditType() {
        return currentEditType;
    }

    public Object getCurrentEditValue() {
        return currentEditValue;
    }

    public boolean editCellAt(int row, int column, EventObject e) {
        if (!getForm().commitCurrentEditing()) {
            return false;
        }

        quickLog("formTable.editCellAt: " + e);

        ClientPropertyDraw property = getProperty(row, column);
        ClientGroupObjectValue columnKey = getColumnKey(row, column);

        String actionSID = getEditActionSID(e, property);
        if (actionSID != null) {
            editRow = row;
            editCol = column;
            editEvent = e;
            commitingValue = false;

            //здесь немного запутанная схема...
            //executePropertyEditAction возвращает true, если редактирование произошло на сервере, необязательно с вводом значения...
            //но из этого editCellAt мы должны вернуть true, только если началось редактирование значения
            editPerformed = editDispatcher.executePropertyEditAction(property, columnKey, actionSID);
            return editorComp != null;
        }

        return false;
    }

    private String getEditActionSID(EventObject e, ClientPropertyDraw property) {
        String actionSID = null;
        if (property.editBindingMap != null) {
            actionSID = property.editBindingMap.getAction(e);
        }

        if (actionSID == null) {
            actionSID = editBindingMap.getAction(e);
        }
        return actionSID;
    }

    @Override
    public void setValueAt(Object aValue, int row, int column) {
        Preconditions.checkState(false, "setValueAt shouldn't be called");
    }

    public abstract int getCurrentRow();

    protected boolean trySelectCell(int row, int column, EventObject e) {
        //по умолчанию ничего не делаем
        return true;
    }

    public boolean requestValue(ClientType valueType, Object oldValue) {
        quickLog("formTable.requestValue: " + valueType);

        //пока чтение значения можно вызывать только один раз в одном изменении...
        //если получится безусловно задержать фокус, то это ограничение можно будет убрать
        Preconditions.checkState(!commitingValue, "You can request value only once per edit action.");

        currentEditType = valueType;
        currentEditValue = oldValue;

        if (!super.editCellAt(editRow, editCol, editEvent)) {
            return false;
        }

        prepareTextEditor();

        editorComp.requestFocusInWindow();

        getForm().setCurrentEditingTable(this);

        return true;
    }

    void prepareTextEditor() {
        if (editorComp instanceof JTextComponent) {
            JTextComponent textEditor = (JTextComponent) editorComp;
            textEditor.selectAll();
            if (getProperty(editRow, editCol).clearText) {
                textEditor.setText("");
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public Component prepareEditor(TableCellEditor editor, int row, int column) {
        Component component = super.prepareEditor(editor, row, column);
        if (component instanceof JComponent) {
            JComponent jComponent = (JComponent) component;
            // у нас есть возможность редактировать нефокусную таблицу, и тогда после редактирования фокус теряется,
            // поэтому даём возможность FocusManager'у самому поставить фокус
            if (!isFocusable() && jComponent.getNextFocusableComponent() == this) {
                jComponent.setNextFocusableComponent(null);
                return component;
            }
        }
        return component;
    }

    private void commitValue(Object value) {
        quickLog("formTable.commitValue: " + value);
        commitingValue = true;
        editDispatcher.commitValue(value);
    }

    @Override
    public void editingStopped(ChangeEvent e) {
        TableCellEditor editor = getCellEditor();
        if (editor != null) {
            Object value = editor.getCellEditorValue();
            internalRemoveEditor();
            commitValue(value);
        }
    }

    @Override
    public void editingCanceled(ChangeEvent e) {
        internalRemoveEditor();
        editDispatcher.cancelEdit();
    }

    @SuppressWarnings("deprecation")
    private void internalRemoveEditor() {
        // в removeEditor фокус запрашивается обратно в таблицу,
        // поэтому вместо этого явно ставим его на следующий элемент.
        // в обычных случаях - это и будет таблица, но при редактировании по хоткею - предыдущий компонент

        Component editorComp = getEditorComponent();
        Component nextComp = null;
        if (editorComp instanceof JComponent) {
            nextComp = ((JComponent) editorComp).getNextFocusableComponent();
        }

        super.removeEditor();

        if (nextComp != null) {
            nextComp.requestFocusInWindow();
        }
    }

    @Override
    public void removeEditor() {
        // removeEditor иногда вызывается напрямую, поэтому вызываем cancelCellEditing сами
        TableCellEditor cellEditor = getCellEditor();
        if (cellEditor != null) {
            cellEditor.cancelCellEditing();
        }
    }

    protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        editPerformed = false;
        boolean consumed = super.processKeyBinding(ks, e, condition, pressed);
        return consumed || editPerformed;
    }

    @Override
    public synchronized void addMouseListener(MouseListener listener) {
        //подменяем стандартный MouseListener
        if (listener != null && "javax.swing.plaf.basic.BasicTableUI$Handler".equals(listener.getClass().getName())) {
            listener = new ClientPropertyTableUIHandler(this);
        }
        super.addMouseListener(listener);
    }

    protected void quickLog(String msg) {
//        if (getForm().isDialog()) {
//            return;
//        }
//        System.out.println("-------------------------------------------------");
//        System.out.println(this + ": ");
//        System.out.println("    " + msg);
//        ExceptionUtils.dumpStack();
    }
}
