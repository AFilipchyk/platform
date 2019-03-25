package lsfusion.gwt.client.form.filter.user.view;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlowPanel;
import lsfusion.gwt.client.ClientMessages;
import lsfusion.gwt.client.base.GwtClientUtils;
import lsfusion.gwt.client.base.view.ResizableFocusPanel;
import lsfusion.gwt.client.base.view.ResizableVerticalPanel;
import lsfusion.gwt.client.form.filter.user.GPropertyFilter;
import lsfusion.gwt.client.form.filter.user.controller.GFilterController;
import lsfusion.gwt.client.form.object.table.controller.GTableController;
import lsfusion.gwt.client.form.object.table.grid.user.toolbar.view.GToolbarButton;
import lsfusion.gwt.client.form.property.GPropertyDraw;
import lsfusion.gwt.client.form.property.cell.controller.EditEvent;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class GFilterView extends ResizableFocusPanel implements GFilterConditionView.UIHandler {
    private static final ClientMessages messages = ClientMessages.Instance.get();
    private static final String ADD_CONDITION = "filtadd.png";
    private static final String APPLY = "filt.png";

    private ResizableVerticalPanel filterContainer;

    private GToolbarButton applyButton;

    private GFilterController controller;

    private Map<GPropertyFilter, GFilterConditionView> conditionViews = new LinkedHashMap<>();

    public GFilterView(GFilterController iController) {
        controller = iController;

        filterContainer = new ResizableVerticalPanel();
        setWidget(filterContainer);
        addStyleName("noOutline");

        applyButton = new GToolbarButton(APPLY, messages.formQueriesFilterApply()) {
            @Override
            public void addListener() {
                addClickHandler(new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent event) {
                        applyFilter();
                    }
                });    
            }
        };
        applyButton.addStyleName("flowPanelChildLeftAlign");

        GToolbarButton addConditionButton = new GToolbarButton(ADD_CONDITION, messages.formQueriesFilterAddCondition() + " (alt + F2)") {
            @Override
            public void addListener() {
                addClickHandler(new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent event) {
                        controller.addPressed();
                    }
                });    
            }
        };
        addConditionButton.addStyleName("flowPanelChildRightAlign");

        FlowPanel controlPanel = new FlowPanel();
        controlPanel.add(applyButton);
        controlPanel.add(addConditionButton);

        filterContainer.add(controlPanel);

        sinkEvents(Event.ONKEYDOWN);
    }

    @Override
    public void onBrowserEvent(Event event) {
        if (event.getKeyCode() == KeyCodes.KEY_ESCAPE) {
            GwtClientUtils.stopPropagation(event);
            controller.allRemovedPressed();
        } else {
            super.onBrowserEvent(event);
        }
    }

    public void addCondition(GPropertyFilter condition, GTableController logicsSupplier) {
        GFilterConditionView conditionView = new GFilterConditionView(condition, logicsSupplier, this);
        conditionViews.put(condition, conditionView);
        filterContainer.add(conditionView);
        conditionChanged();
        focusOnValue();
    }

    public void removeCondition(GPropertyFilter condition) {
        filterContainer.remove(conditionViews.get(condition));
        conditionViews.remove(condition);
        conditionChanged();
        focusOnValue();
    }

    public void removeAllConditions() {
        for (GPropertyFilter condition : conditionViews.keySet()) {
            filterContainer.remove(conditionViews.get(condition));
        }
        conditionViews.clear();
    }

    @Override
    public void conditionChanged() {
        applyButton.setVisible(true);
        for (GFilterConditionView conditionView : conditionViews.values()) {
            conditionView.setJunctionVisible(Arrays.asList(conditionViews.values().toArray()).indexOf(conditionView) < conditionViews.size() - 1);
        }
    }

    @Override
    public void conditionRemoved(GPropertyFilter condition) {
        controller.removePressed(condition);
    }

    public void queryApplied() {
        applyButton.setVisible(false);
    }

    public void focusOnValue() {
        if (!conditionViews.isEmpty()) {
            // пробегаем по всем ячейкам со значеними, останавливаясь на последней, чтобы сбросить стили выделения в остальных
            for (GFilterConditionView filterView : conditionViews.values()) {
                filterView.focusOnValue();
            }
        }
    }

    public void applyFilter() {
        controller.collapsePressed();
        controller.applyPressed();
    }

    public void startEditing(EditEvent keyEvent, GPropertyDraw propertyDraw) {
        if (conditionViews.size() > 0) {
            GFilterConditionView view = conditionViews.values().iterator().next();
            view.setSelectedPropertyDraw(propertyDraw);
            view.startEditing(keyEvent);
        }
    }
}
