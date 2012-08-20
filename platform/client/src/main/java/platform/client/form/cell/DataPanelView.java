package platform.client.form.cell;

import platform.client.Main;
import platform.client.SwingUtils;
import platform.client.form.ClientFormController;
import platform.client.form.ClientFormLayout;
import platform.client.form.dispatch.SimpleChangePropertyDispatcher;
import platform.client.logics.ClientGroupObjectValue;
import platform.client.logics.ClientPropertyDraw;
import platform.interop.ClassViewType;
import platform.interop.event.ValueEvent;
import platform.interop.event.ValueEventListener;

import javax.swing.*;
import java.awt.*;

import static platform.base.BaseUtils.nullEquals;

public class DataPanelView extends JPanel implements PanelView {
    private final JLabel label;

    private final DataPanelViewTable table;

    private final ClientPropertyDraw property;
    private final ClientGroupObjectValue columnKey;

    private ValueEventListener valueEventListener;
    private final ClientFormController form;

    private final SimpleChangePropertyDispatcher simpleDispatcher;

    public DataPanelView(final ClientFormController iform, final ClientPropertyDraw iproperty, ClientGroupObjectValue icolumnKey) {
        setOpaque(false);

        form = iform;
        property = iproperty;
        columnKey = icolumnKey;
        simpleDispatcher = form.getSimpleChangePropertyDispatcher();

        setLayout(new BoxLayout(this, (property.panelLabelAbove ? BoxLayout.Y_AXIS : BoxLayout.X_AXIS)));

        label = new JLabel();
        label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        label.setText(property.getEditCaption());
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        property.design.designHeader(label);

        //игнорируем key.readOnly, чтобы разрешить редактирование,
        //readOnly будет проверяться на уровне сервера и обрезаться возвратом пустого changeType
        table = new DataPanelViewTable(iform, this, columnKey, property);

        setToolTip(property.getCaption());

        if (property.showTableFirst) {
            add(table);
            add(label);
        } else {
            add(label);
            add(table);
        }

        if (property.eventSID != null) {
            valueEventListener = new ValueEventListener() {
                @Override
                public void actionPerfomed(final ValueEvent event) {
                    // может вызываться не из EventDispatchingThread
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            ClientFormLayout focusLayout = SwingUtils.getClientFormlayout(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
                            ClientFormLayout curLayout = SwingUtils.getClientFormlayout(table);
                            if ((curLayout != null) && (curLayout.equals(focusLayout))) {
                                forceChangeValue(event.getValue(), false);
                            }
                        }
                    });
                }
            };
            Main.eventBus.addListener(valueEventListener, property.eventSID);
        }
    }

    @Override
    public int hashCode() {
        return property.getID() * 31 + columnKey.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DataPanelView && ((DataPanelView) o).property.equals(property) && ((DataPanelView) o).columnKey.equals(columnKey);
    }

    protected void forceChangeValue(Object value, boolean aggValue) {
        if (form.commitCurrentEditing()) {
            simpleDispatcher.changeProperty(value, table.getProperty(), columnKey);
        }
    }

    @Override
    public boolean requestFocusInWindow() {
        return table.requestFocusInWindow();
    }

    @Override
    public void setFocusable(boolean focusable) {
        table.setFocusable(focusable);
        table.setCellSelectionEnabled(focusable);
    }

    public JComponent getComponent() {
        return this;
    }

    public void setValue(Object ivalue) {
        table.setValue(ivalue);
    }

    public void setBackgroundColor(Color background) {
        if (nullEquals(table.getBackgroundColor(), background)) {
            return;
        }

        table.setBackgroundColor(background);

        revalidate();
        repaint();
    }

    public void setForegroundColor(Color foreground) {
        if (nullEquals(table.getForegroundColor(), foreground)) {
            return;
        }

        table.setForegroundColor(foreground);

        revalidate();
        repaint();
    }

    @SuppressWarnings("deprecation")
    public void forceEdit() {
        if (!table.isEditing()) {
            table.editCellAt(0, 0, null);
            Component editorComponent = table.getEditorComponent();
            if (editorComponent instanceof JComponent) {
                ((JComponent) editorComponent).setNextFocusableComponent(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
            }
        }
    }

    public void setCaption(String caption) {
        label.setText(caption);
    }

    @Override
    public String toString() {
        return property.toString();
    }

    public void setToolTip(String caption) {
        label.setToolTipText(property.getTooltipText(caption));
    }

    public void changeViewType(ClassViewType type) {
        //пока тут ничего не делаем
    }

    public Icon getIcon() {
        throw new RuntimeException("not supported");
    }

    public void setIcon(Icon icon) {
        throw new RuntimeException("not supported");
    }
}
