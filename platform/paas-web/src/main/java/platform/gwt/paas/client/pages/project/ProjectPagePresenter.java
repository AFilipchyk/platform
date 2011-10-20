package platform.gwt.paas.client.pages.project;

import com.google.gwt.event.shared.EventBus;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.gwtplatform.dispatch.shared.Action;
import com.gwtplatform.dispatch.shared.DispatchAsync;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.Place;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealRootContentEvent;
import com.smartgwt.client.util.SC;
import com.smartgwt.client.widgets.grid.ListGridRecord;
import paas.api.gwt.shared.dto.ConfigurationDTO;
import paas.api.gwt.shared.dto.ModuleDTO;
import platform.gwt.paas.client.NameTokens;
import platform.gwt.paas.client.PaasPlaceManager;
import platform.gwt.paas.client.common.ErrorHandlingCallback;
import platform.gwt.paas.client.data.BasicRecord;
import platform.gwt.paas.client.data.ConfigurationRecord;
import platform.gwt.paas.client.data.ModuleRecord;
import platform.gwt.paas.client.pages.project.add.AddNewModuleDialog;
import platform.gwt.paas.client.pages.project.add.AddNewModuleUIHandlers;
import platform.gwt.paas.client.pages.project.config.ConfigurationDialog;
import platform.gwt.paas.client.pages.project.config.ConfigurationUIHandlers;
import platform.gwt.paas.shared.actions.*;

import static platform.gwt.paas.client.Parameters.PROJECT_ID_PARAM;

public class ProjectPagePresenter extends Presenter<ProjectPagePresenter.MyView, ProjectPagePresenter.MyProxy> implements ProjectPageUIHandlers {
    @Inject
    private PaasPlaceManager placeManager;
    @Inject
    private DispatchAsync dispatcher;
    private int currentProject = -1;

    @ProxyCodeSplit
    @NameToken(NameTokens.projectPage)
    public interface MyProxy extends Proxy<ProjectPagePresenter>, Place {
    }

    public interface MyView extends View, HasUiHandlers<ProjectPageUIHandlers> {
        ModulesPane getModulesPane();

        void setModules(ModuleDTO[] modules);

        void cleanView();

        void setConfigurations(ConfigurationDTO[] configurations);

        void showLoading();

        void hideLoading();
    }

    @Inject
    public ProjectPagePresenter(EventBus eventBus, MyView view, MyProxy proxy) {
        super(eventBus, view, proxy);

        getView().setUiHandlers(this);
    }

    @Override
    public void moduleRecordSelected(ModuleRecord record) {
        getView().getModulesPane().openModuleTab(record);
    }


    @Override
    public void addNewModuleButtonClicked() {
        AddNewModuleDialog.showDialog(currentProject, new AddNewModuleUIHandlers() {
            @Override
            public void onCreateNewModule(String moduleName) {
                dispatcher.execute(new AddNewModuleAction(currentProject, moduleName), new GetModulesCallback());
            }

            @Override
            public void onSelectModules(ListGridRecord[] modules) {
                dispatcher.execute(new AddModulesAction(currentProject, BasicRecord.getIDs(modules)), new GetModulesCallback());
            }
        });
    }

    @Override
    public void saveAllButtonClicked() {
        dispatcher.execute(getView().getModulesPane().prepareSaveModuleAction(), new ErrorHandlingCallback<VoidResult>() {
            @Override
            public void success(VoidResult result) {
                SC.say("Saved successfully!");
            }
        });
    }

    @Override
    public void configurationButtonClicked() {
        ConfigurationDialog.showDialog(currentProject, new ConfigurationUIHandlers() {
        });
    }

    @Override
    public void refreshButtonClicked(boolean refreshContent) {
        updateModuleList();
        fillConfigurations();
        if (refreshContent) {
            getView().getModulesPane().refreshModules();
        }
    }

    @Override
    public void removeRecordClicked(ModuleRecord record) {
        dispatcher.execute(new RemoveModuleFromProjectAction(currentProject, record.getId()), new GetModulesCallback());
    }

    @Override
    public void startConfiguration(ConfigurationRecord record) {
        executeGetConfigAction(new StartConfigurationAction(record.toDTO()));
    }

    @Override
    public void stopConfiguration(ConfigurationRecord record) {
        executeGetConfigAction(new StopConfigurationAction(record.getId()));
    }

    @Override
    public void restartConfiguration(ConfigurationRecord record) {
        executeGetConfigAction(new RestartConfigurationAction(record.toDTO()));
    }

    private void fillConfigurations() {
        executeGetConfigAction(new GetConfigurationsAction(currentProject));
    }

    @Override
    public void downloadJnlp(ConfigurationRecord record) {
        Window.open(record.getJnlp(), "", "");
    }

    private void showLoading() {
        getView().showLoading();
    }

    private void hideLoading() {
        getView().hideLoading();
    }

    private void executeGetConfigAction(Action<GetConfigurationsResult> action) {
        showLoading();
        dispatcher.execute(action, new GetConfigurationsCallback());
    }

    @Override
    protected void onReveal() {
        super.onReveal();

        int projectId = placeManager.getCurrentIntParameter(PROJECT_ID_PARAM, currentProject);
        if (projectId != -1) {
            if (projectId != currentProject) {
                getView().cleanView();
                currentProject = projectId;
            }

            updateModuleList();
            fillConfigurations();
        }
    }

    private void updateModuleList() {
        dispatcher.execute(new GetModulesAction(currentProject), new GetModulesCallback() {
            @Override
            public void failure(Throwable caught) {
                super.failure(caught);
                placeManager.revealProjectListPage();
            }
        });
    }

    @Override
    protected void revealInParent() {
        RevealRootContentEvent.fire(this, this);
    }

    private class GetModulesCallback extends ErrorHandlingCallback<GetModulesResult> {
        @Override
        public void success(GetModulesResult result) {
            getView().setModules(result.modules);
        }
    }

    private class GetConfigurationsCallback extends ErrorHandlingCallback<GetConfigurationsResult> {
        @Override
        public void preProcess() {
            hideLoading();
        }

        @Override
        public void success(GetConfigurationsResult result) {
            getView().setConfigurations(result.configurations);
        }
    }
}