package lsfusion.client.form.panel;

import lsfusion.base.Callback;
import lsfusion.client.form.ClientFormController;
import lsfusion.client.form.cell.PanelView;
import lsfusion.client.form.layout.ClientFormLayout;
import lsfusion.client.form.layout.JComponentPanel;
import lsfusion.client.logics.ClientGroupObjectValue;
import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.interop.form.layout.Alignment;
import lsfusion.interop.form.layout.FlexConstraints;
import lsfusion.interop.form.screen.ExternalScreenComponent;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PropertyController {
    private final ClientFormController form;
    private final PanelController panelController;
    private final ClientPropertyDraw property;

    private List<ClientGroupObjectValue> columnKeys;
    private Map<ClientGroupObjectValue, Object> values;
    private Map<ClientGroupObjectValue, Object> captions;
    private Map<ClientGroupObjectValue, Object> showIfs;
    private Map<ClientGroupObjectValue, Object> readOnly;
    private Map<ClientGroupObjectValue, Object> cellBackgroundValues;
    private Map<ClientGroupObjectValue, Object> cellForegroundValues;

    private Map<ClientGroupObjectValue, PanelView> views;

    private JComponentPanel viewsPanel;

    private final ExternalScreenComponent extView;

    public PropertyController(final ClientFormController form, final PanelController panelController, ClientPropertyDraw property) {

        this.form = form;
        this.panelController = panelController;
        this.property = property;

        if (property.editKey != null) {
            form.getLayout().addKeyBinding(property.editKey, property.groupObject, new ClientFormLayout.KeyBinding() {
                @Override
                public boolean keyPressed(KeyEvent e) {
                    return forceEdit();
                }
            });
        }

        extView = property.externalScreen == null ? null : new ExternalScreenComponent();

        viewsPanel = new JComponentPanel(false, Alignment.LEADING);
    }

    public boolean forceEdit() {
        if (views != null && !views.isEmpty()) {
            return views.values().iterator().next().forceEdit();
        }
        return false;
    }

    public boolean requestFocusInWindow() {
        if (views != null && !views.isEmpty()) {
            views.values().iterator().next().getComponent().requestFocusInWindow();
            return true;
        }
        return false;
    }

    public void addView(ClientFormLayout formLayout) {
        formLayout.add(property, viewsPanel);
        if (property.externalScreen != null) {
            property.externalScreen.add(form.getID(), extView, property.externalScreenConstraints);
        }
    }

    public void removeView(ClientFormLayout formLayout) {
        formLayout.remove(property, viewsPanel);
        if (property.externalScreen != null) {
            property.externalScreen.remove(form.getID(), extView);
        }
    }

    public void setVisible(boolean visible) {
        viewsPanel.setVisible(visible);
    }

    public void setPropertyValues(Map<ClientGroupObjectValue, Object> valueMap, boolean update) {
        if (update) {
            values.putAll(valueMap);
        } else {
            values = valueMap;
        }
    }

    public void setPropertyCaptions(Map<ClientGroupObjectValue, Object> captions) {
        this.captions = captions;
    }

    public void setReadOnlyValues(Map<ClientGroupObjectValue, Object> readOnlyValues) {
        this.readOnly = readOnlyValues;
    }

    public void setShowIfs(Map<ClientGroupObjectValue, Object> showIfs) {
        this.showIfs = showIfs;
    }

    public void setColumnKeys(List<ClientGroupObjectValue> columnKeys) {
        this.columnKeys = columnKeys;
    }

    public void setCellBackgroundValues(Map<ClientGroupObjectValue, Object> cellBackgroundValues) {
        this.cellBackgroundValues = cellBackgroundValues;
    }

    public void setCellForegroundValues(Map<ClientGroupObjectValue, Object> cellForegroundValues) {
        this.cellForegroundValues = cellForegroundValues;
    }

    void update(Color rowBackground, Color rowForeground) {
        if (views == null) {
            views = new HashMap<>();
        }

        List<ClientGroupObjectValue> columnKeys = this.columnKeys != null ? this.columnKeys : ClientGroupObjectValue.SINGLE_EMPTY_KEY_LIST;
        for (final ClientGroupObjectValue columnKey : columnKeys) {
            if (showIfs == null || showIfs.get(columnKey) != null) {
                PanelView view = views.get(columnKey);
                if (view == null && !property.hide) {
                    view = property.getPanelView(form, columnKey);
                    view.setReadOnly(property.isReadOnly());
                    views.put(columnKey, view);
                    
                    view.getEditPropertyDispatcher().setUpdateEditValueCallback(new Callback<Object>() {
                        @Override
                        public void done(Object result) {
                            values.put(columnKey, result);
                        }
                    });

                    panelController.addGroupObjectActions(view.getComponent());
                }
            } else {
                PanelView view = views.remove(columnKey);
                if (view != null) {
                    viewsPanel.remove(view.getComponent());
                }
            }
        }

        //вообще надо бы удалять всё, и добавлять заново, чтобы соблюдался порядок,
        //но при этом будет терятся фокус с удалённых компонентов, поэтому пока забиваем на порядок
//        viewsPanel.removeAll();

        for (ClientGroupObjectValue columnKey : columnKeys) {
            PanelView view = views.get(columnKey);
            if (view != null && view.getComponent().getParent() != viewsPanel) {
                viewsPanel.add(view.getComponent(), new FlexConstraints(property.getAlignment(), property.getValueWidth(viewsPanel)));
            }
        }

        for (Map.Entry<ClientGroupObjectValue, PanelView> e : views.entrySet()) {
            updatePanelView(e.getKey(), e.getValue(), rowBackground, rowForeground);
        }

        if (property.drawAsync && !views.isEmpty()) {
            form.setAsyncView(views.get(columnKeys.get(0)));
        }
    }

    private void updatePanelView(ClientGroupObjectValue columnKey, PanelView view, Color rowBackground, Color rowForeground) {
        if (values != null) {
            Object value = values.get(columnKey);

            view.setValue(value);
            if (extView != null) {
                String oldValue = (extView.getValue() == null) ? "" : extView.getValue();
                String newValue = (value == null) ? "" : value.toString();
                if (oldValue.equals(newValue)) {
                    return;
                }
                extView.setValue((value == null) ? "" : value.toString());
                property.externalScreen.invalidate();
            }
        }

        if (readOnly != null) {
            view.setReadOnly(readOnly.get(columnKey) != null);
        }

        Color background = rowBackground;
        if (background == null && cellBackgroundValues != null) {
            background = (Color) cellBackgroundValues.get(columnKey);
        }
        view.setBackgroundColor(background);

        Color foreground = rowForeground;
        if (foreground == null && cellForegroundValues != null) {
            foreground = (Color) cellForegroundValues.get(columnKey);
        }
        view.setForegroundColor(foreground);

        if (captions != null) {
            String caption = property.getDynamicCaption(captions.get(columnKey));
            view.setCaption(caption);
            view.setToolTip(caption);
        }
    }
}