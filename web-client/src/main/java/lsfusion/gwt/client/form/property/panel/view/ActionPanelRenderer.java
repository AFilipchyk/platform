package lsfusion.gwt.client.form.property.panel.view;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Widget;
import lsfusion.gwt.client.base.view.ImageButton;
import lsfusion.gwt.shared.form.event.GKeyStroke;
import lsfusion.gwt.shared.GwtSharedUtils;
import lsfusion.gwt.client.form.property.cell.controller.dispatch.GEditPropertyDispatcher;
import lsfusion.gwt.client.form.property.cell.controller.GEditPropertyHandler;
import lsfusion.gwt.client.form.controller.GFormController;
import lsfusion.gwt.client.form.property.table.view.GPropertyContextMenuPopup;
import lsfusion.gwt.client.base.TooltipManager;
import lsfusion.gwt.shared.form.property.cell.GEditBindingMap;
import lsfusion.gwt.shared.form.property.GPropertyDraw;
import lsfusion.gwt.client.base.ImageDescription;
import lsfusion.gwt.shared.form.object.GGroupObjectValue;
import lsfusion.gwt.shared.classes.GType;
import lsfusion.gwt.client.form.property.cell.controller.EditManager;
import lsfusion.gwt.client.form.property.cell.controller.NativeEditEvent;
import lsfusion.gwt.client.form.property.cell.classes.controller.DialogBasedGridCellEditor;
import lsfusion.gwt.client.form.property.cell.controller.GridCellEditor;
import lsfusion.gwt.client.form.property.cell.classes.controller.PopupBasedGridCellEditor;

import static lsfusion.gwt.client.base.GwtClientUtils.isShowing;
import static lsfusion.gwt.client.base.GwtClientUtils.stopPropagation;
import static lsfusion.gwt.shared.GwtSharedUtils.nullEquals;
import static lsfusion.gwt.client.form.controller.HotkeyManager.Binding;
import static lsfusion.gwt.shared.form.property.cell.GEditBindingMap.getPropertyKeyPressActionSID;

public class ActionPanelRenderer implements PanelRenderer, GEditPropertyHandler {

    private final GFormController form;
    private final GEditPropertyDispatcher editDispatcher;
    private final EditManager editManager = new ActionEditManager();
    private final GPropertyContextMenuPopup contextMenuPopup = new GPropertyContextMenuPopup();
    private final GPropertyDraw property;
    private String tooltip;

    private final GGroupObjectValue columnKey;
    private final ActionButton button;

    private boolean isValueTrue = true;
    private boolean readOnly = false;
    private boolean enabled = true;
    private EventTarget focusTargetAfterEdit;

    private Object background;
    private Object foreground;

    public ActionPanelRenderer(final GFormController iform, final GPropertyDraw iproperty, GGroupObjectValue icolumnKey) {
        this.form = iform;
        this.property = iproperty;
        this.columnKey = icolumnKey;
        this.editDispatcher = new GEditPropertyDispatcher(form, this);

        button = new ActionButton(property.getEditCaption(), property.icon);
        button.addStyleName("actionPanelRenderer");

        setTooltip(property.caption);

        TooltipManager.registerWidget(button, new TooltipManager.TooltipHelper() {
            @Override
            public String getTooltip() {
                return property.getTooltipText(tooltip);
            }

            @Override
            public boolean stillShowTooltip() {
                return button.isAttached() && button.isVisible();
            }
        });

        int baseHeight = property.getValueHeight(null);
        if (baseHeight > -1) {
            button.setHeight(baseHeight + "px");
        }
        if (property.font != null) {
            property.font.apply(button.getLabel().getElement().getStyle());
        }

        button.getElement().setPropertyObject("groupObject", property.groupObject);

        button.setFocusable(property.focusable);

        finishLayoutSetup();

        initUIHandlers(iform);
    }

    private void finishLayoutSetup() {
        if (property.isVerticallyStretched()) {
            button.getElement().getStyle().clearHeight();
        }
        if (property.isHorizontallyStretched()) {
            button.getElement().getStyle().clearWidth();
        }
    }

    private void initUIHandlers(GFormController iform) {
        button.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                click(null);
            }
        });
        button.addDomHandler(new ContextMenuHandler() {
            @Override
            public void onContextMenu(ContextMenuEvent event) {
                contextMenuPopup.show(property, event.getNativeEvent().getClientX(), event.getNativeEvent().getClientY(), new GPropertyContextMenuPopup.ItemSelectionListener() {
                    @Override
                    public void onMenuItemSelected(String actionSID) {
                        onContextMenuItemSelected(actionSID);
                    }
                });
                stopPropagation(event);
            }
        }, ContextMenuEvent.getType());
        
        button.addDomHandler(new KeyDownHandler() {
            @Override
            public void onKeyDown(KeyDownEvent event) {
                if (!form.isEditing()) {
                    final String actionSID = getPropertyKeyPressActionSID(new NativeEditEvent(event.getNativeEvent()), property);
                    if (actionSID != null) {
                        editDispatcher.executePropertyEditAction(property, columnKey, actionSID, null);
                    }
                }
            }
        }, KeyDownEvent.getType());

        if (property.editKey != null) {
            iform.addHotkeyBinding(property.groupObject, property.editKey, new Binding() {
                @Override
                public boolean onKeyPress(NativeEvent event, GKeyStroke key) {
                    return isShowing(button) && enabled && click(event.getEventTarget());
                }
            });
        }
    }

    private void onContextMenuItemSelected(String actionSID) {
        if (!form.isEditing()) {
            editDispatcher.executePropertyEditAction(property, columnKey, actionSID, null);
        }
    }

    private boolean click(EventTarget ifocusTargetAfterEdit) {
        if (!form.isEditing()) {
            focusTargetAfterEdit = ifocusTargetAfterEdit;
            editDispatcher.executePropertyEditAction(property, columnKey, GEditBindingMap.CHANGE, null);
            return true;
        }
        return false;
    }

    @Override
    public void requestValue(GType valueType, Object oldValue) {
        GridCellEditor editor = valueType.createGridCellEditor(editManager, property);
        if (editor instanceof PopupBasedGridCellEditor) {
            ((PopupBasedGridCellEditor) editor).showPopup(null);
        } else if (editor instanceof DialogBasedGridCellEditor) {
            editor.startEditing(null, null, null, null);
        } else {
            editDispatcher.cancelEdit();
        }
    }

    @Override
    public void updateEditValue(Object value) {
    }

    @Override
    public Widget getComponent() {
        return button;
    }

    @Override
    public void setValue(Object value) {
        this.isValueTrue = value != null && (Boolean) value;
        updateEnabled();
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        updateEnabled();
    }

    private void updateEnabled() {
        boolean newEnabled = isValueTrue && !readOnly;
        if (enabled != newEnabled) {
            enabled = newEnabled;
            button.setEnabled(enabled);
            button.setAppImagePath(property.getIconPath(enabled));
        }
    }

    @Override
    public void setCaption(String caption) {
        button.setText(property.getEditCaption(caption));
        setTooltip(caption);
    }

    public void setTooltip(String caption) {
        tooltip = !GwtSharedUtils.isRedundantString(property.toolTip) ? property.toolTip : caption;
    }

    @Override
    public void setDefaultIcon() {
        button.setImage(property.icon);
    }

    @Override
    public void setIcon(String iconPath) {
        button.setModuleImagePath(iconPath);
    }

    @Override
    public void updateCellBackgroundValue(Object value) {
        if (!nullEquals(background, value)) {
            background = value;
            button.getElement().getStyle().setBorderColor(value == null ? null : value.toString());
        }
    }

    @Override
    public void updateCellForegroundValue(Object value) {
        if (!nullEquals(foreground, value)) {
            foreground = value;
            button.getElement().getStyle().setColor(value == null ? null : value.toString());
        }
    }

    @Override
    public void focus() {
        button.setFocus(true);
    }

    @Override
    public void takeFocusAfterEdit() {
        if (focusTargetAfterEdit != null) {
            Element.as(focusTargetAfterEdit).focus();
            focusTargetAfterEdit = null;
        } else {
            focus();
        }
    }

    private class ActionButton extends ImageButton {
        private ActionButton(String caption, ImageDescription imageDescription) {
            super(caption, imageDescription);
            sinkEvents(Event.ONBLUR | Event.ONFOCUS);
        }

        @Override
        public final void onBrowserEvent(Event event) {
            // Verify that the target is still a child of this widget. IE fires focus
            // events even after the element has been removed from the DOM.
            EventTarget eventTarget = event.getEventTarget();
            if (!Element.is(eventTarget) || !getElement().isOrHasChild(Element.as(eventTarget))) {
                return;
            }
            super.onBrowserEvent(event);

            String eventType = event.getType();
            if (BrowserEvents.FOCUS.equals(eventType)) {
                getElement().getStyle().setBorderColor("#4567FF");
            } else if (BrowserEvents.BLUR.equals(eventType)) {
                getElement().getStyle().clearBorderColor();
            }
        }
    }

    private class ActionEditManager implements EditManager {
        @Override
        public void commitEditing(Object value) {
            editDispatcher.commitValue(value);
        }

        @Override
        public void cancelEditing() {
            editDispatcher.cancelEdit();
        }

        @Override
        public void selectNextCellInColumn(boolean down) {
        }
    }
}
