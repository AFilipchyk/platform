package paas;

import paas.properties.RefreshStatusActionProperty;
import paas.properties.StartConfigurationActionProperty;
import paas.properties.StopConfigurationActionProperty;
import platform.interop.ClassViewType;
import platform.interop.Compare;
import platform.interop.PropertyEditType;
import platform.server.classes.*;
import platform.server.form.entity.FormEntity;
import platform.server.form.entity.ObjectEntity;
import platform.server.form.entity.PropertyDrawEntity;
import platform.server.form.entity.filter.CompareFilterEntity;
import platform.server.form.navigator.NavigatorElement;
import platform.server.form.view.DefaultFormView;
import platform.server.form.view.FormView;
import platform.server.form.view.PropertyDrawView;
import platform.server.logics.BaseLogicsModule;
import platform.server.logics.LogicsModule;
import platform.server.logics.linear.LP;
import platform.server.logics.property.ClassPropertyInterface;
import platform.server.logics.property.group.AbstractGroup;

public class PaasLogicsModule extends LogicsModule {

    public ConcreteCustomClass paasUser;
    public ConcreteCustomClass project;
    public ConcreteCustomClass module;
    public ConcreteCustomClass configuration;
    public ConcreteCustomClass database;
    public StaticCustomClass status;

    public LP loginToUser;

    public LP projectDescription;
    public LP projectOwnerName;
    public LP projectOwnerUserLogin;
    public LP projectOwner;

    public LP moduleInProject;
    public LP moduleSource;
    public LP moduleOrder;
    public LP selectProjectModules;

    public LP configurationProject;
    public LP configurationDatabase;
    public LP configurationDatabaseName;
    public LP configurationPort;
    public LP configurationStatus;
    public LP configurationStatusName;
    public LP configurationStart;
    public LP configurationStop;
    public LP conditionalDelete;

    public LP databaseConfiguration;

    public LP refreshStatus;
    private final PaasBusinessLogics paas;

    public PaasLogicsModule(BaseLogicsModule<PaasBusinessLogics> baseLM, PaasBusinessLogics paas) {
        super("PaasLogicsModule");
        this.paas = paas;
        setBaseLogicsModule(baseLM);
    }

    @Override
    public String getNamePrefix() {
        return null;
    }

    @Override
    public void initClasses() {
        initBaseClassAliases();

//        paasUser = addConcreteClass("paasUser", "Пользователь", baseLM.user, baseLM.emailObject);



        project = addConcreteClass("project", "Проект", baseClass.named);

        module = addConcreteClass("module", "Модуль", baseClass.named);

        configuration = addConcreteClass("configuration", "Конфигурация", baseClass.named);

        database = addConcreteClass("database", "База данных", baseClass.named);

        status = addStaticClass("status", "Статус конфигурации",
                                       new String[]{"stopped", "started",  "busyPort"},
                                       new String[]{"Остановлен", "Работает", "Порт занят"});
    }

    @Override
    public void initTables() {
    }

    @Override
    public void initGroups() {
        initBaseGroupAliases();
    }

    @Override
    public void initProperties() {
        projectDescription = addDProp(baseGroup, "projectDescription", "Описание", StringClass.get(300), project);
        projectOwner = addDProp("projectOwner", "Владелец", baseLM.customUser, project);
        projectOwnerName = addJProp(baseGroup, "projectOwnerName", "Владелец", baseLM.name, projectOwner, 1);
        projectOwnerUserLogin = addJProp("projectOwnerUserName", "Владелец", baseLM.userLogin, projectOwner, 1);

        moduleInProject = addDProp(baseGroup, "moduleInProject", "Модуль в проекте", LogicalClass.instance, project, module);
        moduleSource = addDProp(baseGroup, "moduleSource", "Исходный код модуля", TextClass.instance, module);
        moduleOrder = addDProp(baseGroup, "moduleOrder", "Порядок загрузки модуля", IntegerClass.instance, project, module);

        selectProjectModules = addSelectFromListAction(null, "Выбрать модули", moduleInProject, module, project);

        configurationProject = addDProp(baseGroup, "configurationProject", "Проект", project, configuration);
        configurationDatabase = addDProp("configurationDatabase", "База данных", database, configuration);
        configurationDatabaseName = addJProp(baseGroup, "configurationDatabaseName", "Имя базы данных", baseLM.name, configurationDatabase, 1);
        configurationPort = addDProp(baseGroup, "configurationPort", "Порт для запуска", IntegerClass.instance, configuration);
        configurationStatus = addDProp("configurationStatus", "Статус", status, configuration);
        configurationStatusName = addJProp(baseGroup, "configurationStatusName", "Статус", baseLM.name, configurationStatus, 1);

        refreshStatus = addJProp("Обновить", baseLM.and1, addRefreshStatusProperty(), 1, baseLM.vtrue);

        configurationStart = addJProp("Запустить", baseLM.and1,
                                      addStartConfigurationProperty(), 1,
                                      addJProp(baseLM.diff2, configurationStatus, 1, addCProp(status, "started")), 1);
        configurationStop = addJProp("Остановить", baseLM.and1,
                                      addStopConfigurationProperty(), 1,
                                      addJProp(baseLM.diff2, configurationStatus, 1, addCProp(status, "stopped")), 1);
        conditionalDelete = addJProp(baseLM.delete.property.caption, baseLM.and1,
                                      baseLM.delete, 1,
                                      addJProp(baseLM.diff2, configurationStatus, 1, addCProp(status, "started")), 1);

        databaseConfiguration = addAGProp("databaseConfiguration", "Конфигурация", configurationDatabase);

        initNavigators();
    }

    public LP addRefreshStatusProperty() {
        return addProperty(baseLM.baseGroup, new LP<ClassPropertyInterface>(new RefreshStatusActionProperty(paas, baseLM.genSID(), "")));
    }

    public LP addStartConfigurationProperty() {
        return addProperty(baseLM.baseGroup, new LP<ClassPropertyInterface>(new StartConfigurationActionProperty(paas, baseLM.genSID(), "")));
    }

    public LP addStopConfigurationProperty() {
        return addProperty(baseLM.baseGroup, new LP<ClassPropertyInterface>(new StopConfigurationActionProperty(paas, baseLM.genSID(), "")));
    }

    @Override
    public void initIndexes() {
    }

    private void initNavigators() {
        FormEntity modulesForm = new ModuleFormEntity(baseLM.baseElement, "modulesForm", "Модули");
        addFormEntity(modulesForm);

        FormEntity projectsForm = new ProjectFormEntity(baseLM.baseElement, "projectsForm", "Проекты");
        addFormEntity(projectsForm);
    }

    private class ModuleFormEntity extends FormEntity {

        private ObjectEntity objModule;

        public ModuleFormEntity(NavigatorElement parent, String sID, String caption) {
            super(parent, sID, caption);

            objModule = addSingleGroupObject(module, "Модуль", baseLM.name, moduleSource);
            getPropertyDraw(moduleSource).forceViewType = ClassViewType.PANEL;
            addObjectActions(this, objModule);
        }

        @Override
        public FormView createDefaultRichDesign() {
            DefaultFormView design = (DefaultFormView) super.createDefaultRichDesign();

            design.get(objModule.groupTo).grid.constraints.fillVertical = 0.5;
            design.getPanelContainer(objModule.groupTo).constraints.fillVertical = 1.5;

            PropertyDrawView moduleSourceProperty = design.get(getPropertyDraw(moduleSource));
            moduleSourceProperty.panelLabelAbove = true;
            moduleSourceProperty.constraints.fillHorizontal = 1;
            moduleSourceProperty.constraints.fillVertical = 1;

            return design;
        }
    }

    private class ProjectFormEntity extends FormEntity {

        private ObjectEntity objProject;
        private ObjectEntity objConfiguration;
        private ObjectEntity objModule;

        public ProjectFormEntity(NavigatorElement parent, String sID, String caption) {
            super(parent, sID, caption);

            objProject = addSingleGroupObject(project, "Проект", baseLM.name, projectOwnerName);
            objProject.groupTo.setSingleClassView(ClassViewType.PANEL);
            setEditType(baseLM.name, PropertyEditType.READONLY, objProject.groupTo);
            addObjectActions(this, objProject);

            objModule = addSingleGroupObject(module, "Модуль");
            addPropertyDraw(objProject, objModule, moduleOrder);
            addPropertyDraw(objModule, baseLM.name, moduleSource);
            addObjectActions(this, objModule);
            addPropertyDraw(selectProjectModules, objModule.groupTo, objProject).forceViewType = ClassViewType.PANEL;

            getPropertyDraw(moduleSource).forceViewType = ClassViewType.PANEL;

            addFixedFilter(new CompareFilterEntity(addPropertyObject(moduleInProject, objProject, objModule), Compare.EQUALS, true));

            objConfiguration = addSingleGroupObject(configuration, "Конфигурация", baseLM.name, configurationDatabaseName, configurationPort, configurationStatusName, configurationStart, configurationStop);
            addObjectActions(this, objConfiguration);
            removePropertyDraw(getPropertyDraw(baseLM.delete, objConfiguration));
            addPropertyDraw(conditionalDelete, objConfiguration);

            PropertyDrawEntity refreshProperty = addPropertyDraw(refreshStatus, objProject);
            refreshProperty.setToDraw(objConfiguration.groupTo);
            refreshProperty.forceViewType = ClassViewType.PANEL;

            addFixedFilter(new CompareFilterEntity(addPropertyObject(configurationProject, objConfiguration), Compare.EQUALS, objProject));
        }

        @Override
        public FormView createDefaultRichDesign() {
            DefaultFormView design = (DefaultFormView) super.createDefaultRichDesign();

            design.get(objModule.groupTo).grid.constraints.fillVertical = 0.5;
            design.getPanelContainer(objModule.groupTo).constraints.fillVertical = 1.5;

            PropertyDrawView moduleSourceProperty = design.get(getPropertyDraw(moduleSource));
            moduleSourceProperty.panelLabelAbove = true;
            moduleSourceProperty.constraints.fillHorizontal = 1;
            moduleSourceProperty.constraints.fillVertical = 1;

            return design;
        }
    }

    @Override
    protected <T extends LP<?>> T addProperty(AbstractGroup group, T lp) {
        return super.addProperty(group, lp);
    }
}
