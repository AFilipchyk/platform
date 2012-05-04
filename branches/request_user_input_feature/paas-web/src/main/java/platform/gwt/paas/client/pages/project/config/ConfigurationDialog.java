package platform.gwt.paas.client.pages.project.config;

import com.gwtplatform.dispatch.shared.Action;
import com.gwtplatform.dispatch.shared.DispatchAsync;
import com.smartgwt.client.types.Alignment;
import com.smartgwt.client.types.Overflow;
import com.smartgwt.client.types.TitleOrientation;
import com.smartgwt.client.widgets.Button;
import com.smartgwt.client.widgets.Window;
import com.smartgwt.client.widgets.events.ClickEvent;
import com.smartgwt.client.widgets.events.ClickHandler;
import com.smartgwt.client.widgets.events.CloseClickEvent;
import com.smartgwt.client.widgets.events.CloseClickHandler;
import com.smartgwt.client.widgets.form.DynamicForm;
import com.smartgwt.client.widgets.form.FormItemIfFunction;
import com.smartgwt.client.widgets.form.events.ItemChangedEvent;
import com.smartgwt.client.widgets.form.events.ItemChangedHandler;
import com.smartgwt.client.widgets.form.fields.*;
import com.smartgwt.client.widgets.form.validator.IntegerRangeValidator;
import com.smartgwt.client.widgets.grid.ListGridRecord;
import com.smartgwt.client.widgets.grid.events.SelectionChangedHandler;
import com.smartgwt.client.widgets.grid.events.SelectionEvent;
import com.smartgwt.client.widgets.layout.HLayout;
import com.smartgwt.client.widgets.layout.VLayout;
import com.smartgwt.client.widgets.layout.VStack;
import paas.api.gwt.shared.dto.ConfigurationDTO;
import platform.gwt.paas.client.Paas;
import platform.gwt.paas.client.common.ErrorHandlingCallback;
import platform.gwt.paas.client.data.ConfigurationRecord;
import platform.gwt.paas.client.widgets.SpacerItem;
import platform.gwt.paas.client.widgets.Toolbar;
import platform.gwt.paas.shared.actions.*;

import java.util.LinkedHashMap;

public class ConfigurationDialog extends Window {
    private final DispatchAsync dispatcher = Paas.ginjector.getDispatchAsync();

    private int project;
    @SuppressWarnings({"UnusedDeclaration"})
    private final ConfigurationUIHandlers uiHandlers;

    private ConfigurationListGrid configurationGrid;
    private Button btnClose;
    private Toolbar toolbar;
    private DynamicForm configurationForm;
    private ButtonItem btnApply;
    private ButtonItem btnStop;
    private ButtonItem btnStart;
    private TextItem portItem;
    private TextItem nameItem;

    public ConfigurationDialog(int project, ConfigurationUIHandlers uiHandlers) {
        this.project = project;
        this.uiHandlers = uiHandlers;

        setTitle("Configurations");
        setWidth(680);
        setMinWidth(680);
        setHeight(370);
        setMinHeight(370);
        setShowMinimizeButton(false);
        setShowModalMask(true);
        setIsModal(true);
        setAutoCenter(true);
        setCanDragResize(true);
        setCanDragReposition(true);
        setOverflow(Overflow.HIDDEN);

        createConfigurationToolbar();

        createConfigurationGrid();

        createConfigurationForm();

        createButtons();

        configureLayout();

        bindUIHandlers();

        refreshConfigurations();
    }

    private void configureLayout() {
        HLayout centerPane = new HLayout();
        centerPane.setMembersMargin(5);
        centerPane.addMember(configurationGrid);
        centerPane.addMember(configurationForm);
        configurationGrid.setShowResizeBar(true);

        VStack bottomPane = new VStack();
        bottomPane.setMargin(10);
        bottomPane.setWidth100();
        bottomPane.setAutoHeight();
        bottomPane.addMember(btnClose);

        VLayout topPane = new VLayout();
        topPane.setShowEdges(true);
        topPane.addMember(toolbar);
        topPane.addMember(centerPane);

        VLayout mainPane = new VLayout();
        mainPane.addMember(topPane);
        mainPane.addMember(bottomPane);

        addItem(mainPane);
    }

    private void createConfigurationToolbar() {
        toolbar = new Toolbar();
        toolbar.addToolStripButton("Add", "add.png", "Add configuration", new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                addNewConfiguration();
            }
        });

        toolbar.addToolStripButton("Remove", "remove.png", "Remove configuration", new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                removeSelectedConfiguration();
            }
        });

        toolbar.addToolStripButton("Refresh", "refresh.png", "Refresh configurations list", new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                refreshConfigurations();
            }
        });
        toolbar.addFill();
        toolbar.addLoadingIndicator();
        toolbar.addSpacer(10);
    }

    private void createConfigurationGrid() {
        configurationGrid = new ConfigurationListGrid();
        configurationGrid.setWidth(250);
    }

    private void createConfigurationForm() {
        TextItem statusItem = new TextItem(ConfigurationRecord.STATUS_FIELD, "Status");
        statusItem.setAttribute("readOnly", true);
        statusItem.setColSpan(5);
        statusItem.setWidth("*");
        statusItem.setValueMap(new LinkedHashMap() {{
            put("started", "Started");
            put("stopped", "Stopped");
        }});

        nameItem = new TextItem(ConfigurationRecord.NAME_FIELD, "Name");
        nameItem.setRequired(true);
        nameItem.setColSpan(5);
        nameItem.setWidth("*");

        portItem = new TextItem(ConfigurationRecord.PORT_FIELD, "Port");
        portItem.setRequired(true);
        portItem.setColSpan(5);
        portItem.setWidth("*");
        IntegerRangeValidator portValidator = new IntegerRangeValidator();
        portValidator.setMax(65535);
        portValidator.setMin(1024);
        portItem.setValidators(portValidator);

        RowSpacerItem filler = new RowSpacerItem();
        filler.setHeight("*");

        LinkItem jnlpItem = new LinkItem(ConfigurationRecord.JNLP_FIELD);
        jnlpItem.setShowTitle(false);
        jnlpItem.setColSpan(5);
        jnlpItem.setWidth("*");
        jnlpItem.setTextAlign(Alignment.RIGHT);
        jnlpItem.setLinkTitle("Download jnlp to connect");
        jnlpItem.setShowIfCondition(new FormItemIfFunction() {
            @Override
            public boolean execute(FormItem item, Object value, DynamicForm form) {
                return form.getValue("port") != null;
            }
        });

        btnStart = new ButtonItem("startField", "Start");
        btnStart.setStartRow(false);
        btnStart.setEndRow(false);
        btnStart.setAlign(Alignment.CENTER);
        btnStart.setWidth("100");
        btnStart.setColSpan(1);
        btnStart.setIcon("icons/start.png");
        btnStart.addClickHandler(new com.smartgwt.client.widgets.form.fields.events.ClickHandler() {
            @Override
            public void onClick(com.smartgwt.client.widgets.form.fields.events.ClickEvent event) {
                startPressed();
            }
        });

        btnStop = new ButtonItem("stopField", "Stop");
        btnStop.setStartRow(false);
        btnStop.setEndRow(false);
        btnStop.setAlign(Alignment.CENTER);
        btnStop.setWidth("100");
        btnStop.setIcon("icons/stop.png");
        btnStop.addClickHandler(new com.smartgwt.client.widgets.form.fields.events.ClickHandler() {
            @Override
            public void onClick(com.smartgwt.client.widgets.form.fields.events.ClickEvent event) {
                stopPressed();
            }
        });

        btnApply = new ButtonItem("applyField", "Apply");
        btnApply.setStartRow(false);
        btnApply.setEndRow(false);
        btnApply.setAlign(Alignment.CENTER);
        btnApply.setWidth("100");
        btnApply.setIcon("icons/apply.png");
        btnApply.addClickHandler(new com.smartgwt.client.widgets.form.fields.events.ClickHandler() {
            @Override
            public void onClick(com.smartgwt.client.widgets.form.fields.events.ClickEvent event) {
                applyPressed();
            }
        });

        configurationForm = new DynamicForm();
        configurationForm.setMargin(5);
        configurationForm.setWidth100();
        configurationForm.setTitleOrientation(TitleOrientation.TOP);
        configurationForm.setNumCols(5);
        configurationForm.setColWidths("*", "110", "110", "110", "*");
        configurationForm.setFields(statusItem,
                                    nameItem,
                                    portItem,
                                    new SpacerItem(), btnStart, btnStop, btnApply, new SpacerItem(),
                                    filler,
                                    jnlpItem
        );
        configurationForm.hide();
    }

    private void createButtons() {
        btnClose = new Button("Close");
        btnClose.setLayoutAlign(Alignment.RIGHT);
    }

    private void bindUIHandlers() {
        addCloseClickHandler(new CloseClickHandler() {
            public void onCloseClick(CloseClickEvent event) {
                destroy();
            }
        });

        btnClose.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                destroy();
            }
        });

        configurationGrid.addSelectionChangedHandler(new SelectionChangedHandler() {
            @Override
            public void onSelectionChanged(SelectionEvent event) {
                recordSelected();
            }
        });

        configurationForm.addItemChangedHandler(new ItemChangedHandler() {
            @Override
            public void onItemChanged(ItemChangedEvent event) {
                btnApply.enable();
            }
        });
    }

    private void addNewConfiguration() {
        executeDispatcherAction(new AddNewConfigurationAction(project));
    }

    private void removeSelectedConfiguration() {
        ConfigurationRecord record = (ConfigurationRecord) configurationGrid.getSelectedRecord();
        if (record != null) {
            executeDispatcherAction(new RemoveConfigurationAction(project, record.getId()));
        }
    }

    private void refreshConfigurations() {
        executeDispatcherAction(new GetConfigurationsAction(project));
    }

    private void startPressed() {
        ConfigurationRecord record = (ConfigurationRecord) configurationGrid.getSelectedRecord();
        if (record != null && configurationForm.validate()) {
            btnStart.disable();
            executeDispatcherAction(new StartConfigurationAction(populateCurrentRecord().toDTO()));
        }
    }

    private void stopPressed() {
        ConfigurationRecord record = (ConfigurationRecord) configurationGrid.getSelectedRecord();
        if (record != null) {
            btnStop.disable();
            executeDispatcherAction(new StopConfigurationAction(record.getId()));
        }
    }

    private void applyPressed() {
        if (configurationForm.validate()) {
            populateCurrentRecord();

            executeDispatcherAction(new UpdateConfigurationAction(populateCurrentRecord().toDTO()));
        }
    }

    private ConfigurationRecord populateCurrentRecord() {
        ConfigurationRecord record = (ConfigurationRecord) configurationGrid.getSelectedRecord();
        record.setName(nameItem.getValueAsString());
        record.setPort(getPortValue());
        return record;
    }

    private Integer getPortValue() {
        String portStr = portItem.getValueAsString();
        try {
            return Integer.parseInt(portStr);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private void recordSelected() {
        ConfigurationRecord selected = (ConfigurationRecord) configurationGrid.getSelectedRecord();
        if (selected != null) {
            configurationForm.show();
            configurationForm.editRecord(selected);
            String status = selected.getStatus();
            btnStop.setDisabled("stopped".equals(status));
            btnStart.setDisabled("started".equals(status));
            portItem.setDisabled("started".equals(status));
            nameItem.setDisabled("started".equals(status));
            btnApply.disable();
        } else {
            configurationForm.hide();
        }
    }

    private void setConfigurations(ConfigurationDTO[] configurations) {
        configurationGrid.setDataFromDTOs(configurations);
    }

    private void showLoading() {
        toolbar.showLoading();
    }

    private void hideLoading() {
        toolbar.hideLoading();
    }

    private void executeDispatcherAction(Action<GetConfigurationsResult> action) {
        showLoading();
        dispatcher.execute(action, new GetConfigurationsCallback());
    }

    public static ConfigurationDialog showDialog(int project, ConfigurationUIHandlers uiHandlers) {
        ConfigurationDialog wl = new ConfigurationDialog(project, uiHandlers);
        wl.show();
        return wl;
    }

    private class GetConfigurationsCallback extends ErrorHandlingCallback<GetConfigurationsResult> {
        @Override
        public void preProcess() {
            hideLoading();
        }

        @Override
        public void success(GetConfigurationsResult result) {
            setConfigurations(result.configurations);
        }

        @Override
        public void failure(Throwable caught) {
            super.failure(caught);
            ListGridRecord selected = configurationGrid.getSelectedRecord();
            if (selected != null) {
                configurationGrid.deselectRecord(selected);
                configurationGrid.selectSingleRecord(selected);
            }
        }
    }
}
