package lsfusion.server.logics;

import com.google.common.base.Throwables;
import lsfusion.base.BaseUtils;
import lsfusion.base.SystemUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.ServerLoggers;
import lsfusion.server.Settings;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.IntegerClass;
import lsfusion.server.classes.LongClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.KeyField;
import lsfusion.server.data.PropertyField;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.expr.query.GroupType;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.GroupObjectEntity;
import lsfusion.server.form.entity.PropertyDrawEntity;
import lsfusion.server.form.navigator.NavigatorAction;
import lsfusion.server.form.navigator.NavigatorElement;
import lsfusion.server.integration.*;
import lsfusion.server.lifecycle.LifecycleEvent;
import lsfusion.server.lifecycle.LogicsManager;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.linear.LP;
import lsfusion.server.logics.property.*;
import lsfusion.server.logics.property.group.AbstractGroup;
import lsfusion.server.logics.property.group.AbstractNode;
import lsfusion.server.logics.table.ImplementTable;
import lsfusion.server.logics.tasks.PublicTask;
import lsfusion.server.logics.tasks.TaskRunner;
import lsfusion.server.session.DataSession;
import lsfusion.server.session.PropertyChange;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.sql.SQLException;
import java.util.*;

import static java.util.Arrays.asList;
import static lsfusion.base.SystemUtils.getRevision;

public class ReflectionManager extends LogicsManager implements InitializingBean {

    public static final Logger startLogger = ServerLoggers.startLogger;

    private BusinessLogics<?> businessLogics;

    private DBManager dbManager;

    private PublicTask initTask;

    public void setBusinessLogics(BusinessLogics<?> businessLogics) {
        this.businessLogics = businessLogics;
    }

    public void setDbManager(DBManager dbManager) {
        this.dbManager = dbManager;
    }

    public void setInitTask(PublicTask initTask) {
        this.initTask = initTask;
    }

    private BaseLogicsModule<?> LM;
    private ReflectionLogicsModule reflectionLM;
    private SystemEventsLogicsModule systemEventsLM;
    private TimeLogicsModule timeLM;

    public ReflectionManager() {
        super(REFLECTION_ORDER);
    }

    public void afterPropertiesSet() throws Exception {
        Assert.notNull(initTask, "initTask must be specified");
    }

    @Override
    protected void onInit(LifecycleEvent event) {
        this.LM = businessLogics.LM;
        this.reflectionLM = businessLogics.reflectionLM;
        this.systemEventsLM = businessLogics.systemEventsLM;
        this.timeLM = businessLogics.timeLM;
    }
    
    @Override
    protected void onStarted(LifecycleEvent event) {
        try {
            new TaskRunner(businessLogics).runTask(initTask, startLogger);
        } catch (Exception e) {
            throw new RuntimeException("Error starting ReflectionManager: ", e);
        }
    }

    public Integer getServerComputer() {
        return dbManager.getComputer(SystemUtils.getLocalHostName(), getStack());
    }
    
    private DataSession createSession() throws SQLException {
        return dbManager.createSession();
    }

    private DataSession createSyncSession() throws SQLException {
        DataSession session = createSession();
        if(Settings.get().isStartServerAnyWay())
            session.setNoCancelInTransaction(true);
        return session;
    }

    public boolean isSourceHashChanged() {
        return dbManager.sourceHashChanged;
    }

    public void synchronizeNavigatorElements() {
        synchronizeNavigatorElements(reflectionLM.form, FormEntity.class, false, reflectionLM.isForm);
        synchronizeNavigatorElements(reflectionLM.navigatorAction, NavigatorAction.class, true, reflectionLM.isNavigatorAction);
        synchronizeNavigatorElements(reflectionLM.navigatorElement, NavigatorElement.class, true, reflectionLM.isNavigatorElement);
        
        // todo [dale]: Изменится в дальнейшем, нужно для перехода на новую логику
        updateFormsCanonicalNames();
    }

    private void updateFormsCanonicalNames() {
        try (DataSession session = createSyncSession()) {
            LP prop = reflectionLM.formCanonicalName;
            KeyExpr key = new KeyExpr("key");
            PropertyChange change = new PropertyChange(MapFact.singletonRev(prop.listInterfaces.single(), key), reflectionLM.canonicalNameNavigatorElement.getExpr(key), key.isClass(reflectionLM.form));
            session.changeProperty((DataProperty) prop.property, change);
            session.apply(businessLogics, getStack());
        } catch (SQLException | SQLHandledException  e) {
            throw new RuntimeException(e);
        }
    }
    
    private void synchronizeNavigatorElements(ConcreteCustomClass elementCustomClass, Class<? extends NavigatorElement> filterJavaClass, boolean exactJavaClass, LCP deleteLP) {

        startLogger.info("synchronizeNavigatorElements collecting data started");
        ImportField nameField = new ImportField(reflectionLM.navigatorElementCanonicalNameClass);
        ImportField captionField = new ImportField(reflectionLM.navigatorElementCaptionClass);

        ImportKey<?> keyNavigatorElement = new ImportKey(elementCustomClass, reflectionLM.navigatorElementCanonicalName.getMapping(nameField));

        List<List<Object>> elementsData = new ArrayList<>();
        for (NavigatorElement element : businessLogics.getNavigatorElements()) {
            if (element.needsToBeSynchronized() && (exactJavaClass ? filterJavaClass == element.getClass() : filterJavaClass.isInstance(element))) {
                elementsData.add(asList((Object) element.getCanonicalName(), element.caption.getSourceString()));
            }
        }
        elementsData.add(asList((Object) "noParentGroup", "Без родительской группы"));

        startLogger.info("synchronizeNavigatorElements integration service started");
        List<ImportProperty<?>> propsNavigatorElement = new ArrayList<>();
        propsNavigatorElement.add(new ImportProperty(nameField, reflectionLM.canonicalNameNavigatorElement.getMapping(keyNavigatorElement)));
        propsNavigatorElement.add(new ImportProperty(captionField, reflectionLM.captionNavigatorElement.getMapping(keyNavigatorElement)));

        List<ImportDelete> deletes = Collections.singletonList(
                new ImportDelete(keyNavigatorElement, deleteLP.getMapping(keyNavigatorElement), false)
        );
        ImportTable table = new ImportTable(asList(nameField, captionField), elementsData);

        try {
            try (DataSession session = createSyncSession()) {
                session.pushVolatileStats("RM_NE");
                IntegrationService service = new IntegrationService(session, table, Collections.singletonList(keyNavigatorElement), propsNavigatorElement, deletes);
                service.synchronize(true, false);
                session.popVolatileStats();
                session.apply(businessLogics, getStack());
                startLogger.info("synchronizeNavigatorElements finished");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void synchronizeParents() {

        startLogger.info("synchronizeParents collecting data started");
        ImportField nameField = new ImportField(reflectionLM.navigatorElementCanonicalNameClass);
        ImportField parentNameField = new ImportField(reflectionLM.navigatorElementCanonicalNameClass);
        ImportField numberField = new ImportField(reflectionLM.numberNavigatorElement);

        List<List<Object>> dataParents = getRelations(LM.root, getElementsWithParent(LM.root));

        startLogger.info("synchronizeParents integration service started");
        ImportKey<?> keyElement = new ImportKey(reflectionLM.navigatorElement, reflectionLM.navigatorElementCanonicalName.getMapping(nameField));
        ImportKey<?> keyParent = new ImportKey(reflectionLM.navigatorElement, reflectionLM.navigatorElementCanonicalName.getMapping(parentNameField));
        List<ImportProperty<?>> propsParent = new ArrayList<>();
        propsParent.add(new ImportProperty(parentNameField, reflectionLM.parentNavigatorElement.getMapping(keyElement), LM.object(reflectionLM.navigatorElement).getMapping(keyParent)));
        propsParent.add(new ImportProperty(numberField, reflectionLM.numberNavigatorElement.getMapping(keyElement), GroupType.MIN));
        ImportTable table = new ImportTable(asList(nameField, parentNameField, numberField), dataParents);
        try {
            try (DataSession session = createSyncSession()) {
                session.pushVolatileStats("RM_PT");
                IntegrationService service = new IntegrationService(session, table, asList(keyElement, keyParent), propsParent);
                service.synchronize(true, false);
                session.popVolatileStats();
                session.apply(businessLogics, getStack());
                startLogger.info("synchronizeParents finished");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Set<String> getElementsWithParent(NavigatorElement element) {
        Set<String> parentInfo = new HashSet<>();
        ImSet<NavigatorElement> children = (ImSet<NavigatorElement>) element.getChildren();
        parentInfo.add(element.getCanonicalName());
        for (NavigatorElement child : children) 
            if(child.needsToBeSynchronized()) {
                parentInfo.add(child.getCanonicalName());
                parentInfo.addAll(getElementsWithParent(child));
            }
        return parentInfo;
    }

    private List<List<Object>> getRelations(NavigatorElement element, Set<String> elementsWithParent) {
        List<List<Object>> parentInfo = new ArrayList<>();
        ImOrderSet<NavigatorElement> children = (ImOrderSet<NavigatorElement>) element.getChildrenList();
        int counter = 1;
        for (NavigatorElement child : children) 
            if(child.needsToBeSynchronized()) {
                parentInfo.add(BaseUtils.toList((Object) child.getCanonicalName(), element.getCanonicalName(), counter++));
                parentInfo.addAll(getRelations(child));
            }
        counter = 1;
        for(NavigatorElement navigatorElement : businessLogics.getNavigatorElements()) {
            if(navigatorElement.needsToBeSynchronized() && !elementsWithParent.contains(navigatorElement.getCanonicalName()))
                parentInfo.add(BaseUtils.toList((Object) navigatorElement.getCanonicalName(), "noParentGroup", counter++));
        }
        return parentInfo;
    }

    private List<List<Object>> getRelations(NavigatorElement element) {
        List<List<Object>> parentInfo = new ArrayList<>();
        ImOrderSet<NavigatorElement> children = (ImOrderSet<NavigatorElement>) element.getChildrenList();
        int counter = 1;
        for (NavigatorElement child : children)
            if(child.needsToBeSynchronized()) {
                parentInfo.add(BaseUtils.toList((Object) child.getCanonicalName(), element.getCanonicalName(), counter++));
                parentInfo.addAll(getRelations(child));
            }
        return parentInfo;
    }

    private void migratePropertyDraws() {
        startLogger.info("migratePropertyDraws collecting data started");
        Map<String, String> nameChanges = dbManager.getPropertyDrawNamesChanges();
        
        ImportField oldPropertyDrawSIDField = new ImportField(reflectionLM.propertyDrawSIDClass);
        ImportField oldFormCanonicalNameField = new ImportField(reflectionLM.navigatorElementCanonicalNameClass);
        ImportField newPropertyDrawSIDField = new ImportField(reflectionLM.propertyDrawSIDClass);
        ImportField newFormCanonicalNameField = new ImportField(reflectionLM.navigatorElementCanonicalNameClass);

        ImportKey<?> keyForm = new ImportKey(reflectionLM.form, reflectionLM.navigatorElementCanonicalName.getMapping(newFormCanonicalNameField));
        ImportKey<?> keyProperty = new ImportKey(reflectionLM.propertyDraw, reflectionLM.propertyDrawSIDNavigatorElementNamePropertyDraw.getMapping(oldFormCanonicalNameField, oldPropertyDrawSIDField));

        try {
            List<List<Object>> data = new ArrayList<>();
            for (String oldName : nameChanges.keySet()) {
                String newName = nameChanges.get(oldName);
                String oldFormName = oldName.substring(0, oldName.lastIndexOf('.'));
                String newFormName = newName.substring(0, newName.lastIndexOf('.'));
                data.add(Arrays.<Object>asList(oldName.substring(oldFormName.length() + 1), oldFormName, newName.substring(newFormName.length() + 1), newFormName));
            }

            startLogger.info("migratePropertyDraws integration service started");
            List<ImportProperty<?>> properties = new ArrayList<>();
            properties.add(new ImportProperty(newPropertyDrawSIDField, reflectionLM.sidPropertyDraw.getMapping(keyProperty)));
            properties.add(new ImportProperty(newFormCanonicalNameField, reflectionLM.formPropertyDraw.getMapping(keyProperty), LM.object(reflectionLM.navigatorElement).getMapping(keyForm)));

            ImportTable table = new ImportTable(asList(oldPropertyDrawSIDField, oldFormCanonicalNameField, newPropertyDrawSIDField, newFormCanonicalNameField), data);

            try (DataSession session = createSyncSession()) {
                IntegrationService service = new IntegrationService(session, table, asList(keyForm, keyProperty), properties);
                service.synchronize(false, false);
                session.apply(businessLogics, getStack());
                startLogger.info("migratePropertyDraws finished");
            }
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }
    
    public void synchronizePropertyDraws() {
        startLogger.info("synchronizePropertyDraws collecting data started");
        migratePropertyDraws();
        
        List<List<Object>> dataPropertyDraws = new ArrayList<>();
        for (FormEntity formElement : businessLogics.getFormEntities()) {
            String canonicalName = formElement.getCanonicalName();
            if (canonicalName != null && formElement.needsToBeSynchronized()) {
                ImList<PropertyDrawEntity> propertyDraws = formElement.getPropertyDrawsList();
                for (PropertyDrawEntity drawEntity : propertyDraws) {
                    GroupObjectEntity groupObjectEntity = drawEntity.getToDraw(formElement);
                    dataPropertyDraws.add(asList(drawEntity.propertyObject.toString(), drawEntity.getSID(), (Object) canonicalName, groupObjectEntity == null ? null : groupObjectEntity.getSID()));
                }
            }
        }

        startLogger.info("synchronizePropertyDraws integration service started");
        ImportField captionPropertyDrawField = new ImportField(reflectionLM.propertyCaptionValueClass);
        ImportField sidPropertyDrawField = new ImportField(reflectionLM.propertySIDValueClass);
        ImportField nameNavigatorElementField = new ImportField(reflectionLM.navigatorElementCanonicalNameClass);
        ImportField sidGroupObjectField = new ImportField(reflectionLM.propertySIDValueClass);

        ImportKey<?> keyForm = new ImportKey(reflectionLM.form, reflectionLM.navigatorElementCanonicalName.getMapping(nameNavigatorElementField));
        ImportKey<?> keyPropertyDraw = new ImportKey(reflectionLM.propertyDraw, reflectionLM.propertyDrawSIDNavigatorElementNamePropertyDraw.getMapping(nameNavigatorElementField, sidPropertyDrawField));
        ImportKey<?> keyGroupObject = new ImportKey(reflectionLM.groupObject, reflectionLM.groupObjectSIDNavigatorElementNameGroupObject.getMapping(sidGroupObjectField, nameNavigatorElementField));

        List<ImportProperty<?>> propsPropertyDraw = new ArrayList<>();
        propsPropertyDraw.add(new ImportProperty(captionPropertyDrawField, reflectionLM.captionPropertyDraw.getMapping(keyPropertyDraw)));
        propsPropertyDraw.add(new ImportProperty(sidPropertyDrawField, reflectionLM.sidPropertyDraw.getMapping(keyPropertyDraw)));
        propsPropertyDraw.add(new ImportProperty(nameNavigatorElementField, reflectionLM.formPropertyDraw.getMapping(keyPropertyDraw), LM.object(reflectionLM.navigatorElement).getMapping(keyForm)));
        propsPropertyDraw.add(new ImportProperty(sidGroupObjectField, reflectionLM.groupObjectPropertyDraw.getMapping(keyPropertyDraw), LM.object(reflectionLM.groupObject).getMapping(keyGroupObject)));


        List<ImportDelete> deletes = new ArrayList<>();
        deletes.add(new ImportDelete(keyPropertyDraw, LM.is(reflectionLM.propertyDraw).getMapping(keyPropertyDraw), false));

        ImportTable table = new ImportTable(asList(captionPropertyDrawField, sidPropertyDrawField, nameNavigatorElementField, sidGroupObjectField), dataPropertyDraws);

        try {
            try (DataSession session = createSyncSession()) {
                session.pushVolatileStats("RM_PD");
                IntegrationService service = new IntegrationService(session, table, asList(keyForm, keyPropertyDraw, keyGroupObject), propsPropertyDraw, deletes);
                service.synchronize(true, false);
                session.popVolatileStats();
                session.apply(businessLogics, getStack());
                startLogger.info("synchronizePropertyDraws finished");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void synchronizeGroupObjects() {
        startLogger.info("synchronizeGroupObjects collecting data started");
        List<List<Object>> dataGroupObjectList = new ArrayList<>();
        for (FormEntity<?> formElement : businessLogics.getFormEntities()) {
            String formCanonicalName = formElement.getCanonicalName();
            if (formCanonicalName != null && formElement.needsToBeSynchronized()) { //formSID - sidGroupObject
                for (PropertyDrawEntity property : formElement.getPropertyDrawsList()) {
                    GroupObjectEntity groupObjectEntity = property.getToDraw(formElement);
                    if (groupObjectEntity != null) {
                        dataGroupObjectList.add(
                                Arrays.asList((Object) formCanonicalName, groupObjectEntity.getSID()));
                    }
                }
            }
        }

        startLogger.info("synchronizeGroupObjects integration service started");
        ImportField canonicalNameNavigatorElementField = new ImportField(reflectionLM.navigatorElementCanonicalNameClass);
        ImportField sidGroupObjectField = new ImportField(reflectionLM.propertySIDValueClass);

        ImportKey<?> keyForm = new ImportKey(reflectionLM.form, reflectionLM.navigatorElementCanonicalName.getMapping(canonicalNameNavigatorElementField));
        ImportKey<?> keyGroupObject = new ImportKey(reflectionLM.groupObject, reflectionLM.groupObjectSIDNavigatorElementNameGroupObject.getMapping(sidGroupObjectField, canonicalNameNavigatorElementField));

        List<ImportProperty<?>> propsGroupObject = new ArrayList<>();
        propsGroupObject.add(new ImportProperty(sidGroupObjectField, reflectionLM.sidGroupObject.getMapping(keyGroupObject)));
        propsGroupObject.add(new ImportProperty(canonicalNameNavigatorElementField, reflectionLM.navigatorElementGroupObject.getMapping(keyGroupObject), LM.object(reflectionLM.navigatorElement).getMapping(keyForm)));

        List<ImportDelete> deletes = new ArrayList<>();
        deletes.add(new ImportDelete(keyGroupObject, LM.is(reflectionLM.groupObject).getMapping(keyGroupObject), false));

        ImportTable table = new ImportTable(asList(canonicalNameNavigatorElementField, sidGroupObjectField), dataGroupObjectList);

        try {
            try(DataSession session = createSyncSession()) {
                session.pushVolatileStats("RM_GO");
                IntegrationService service = new IntegrationService(session, table, asList(keyForm, keyGroupObject), propsGroupObject, deletes);
                service.synchronize(true, false);
                session.popVolatileStats();
                session.apply(businessLogics, getStack());
                startLogger.info("synchronizeGroupObjects finished");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean needsToBeSynchronized(Property property) {
        return property.isNamed() && (property instanceof ActionProperty || !((CalcProperty)property).isEmpty(AlgType.syncType));
    }

    public void synchronizePropertyEntities() {

        startLogger.info("synchronizePropertyEntities collecting data started");
        ImportField canonicalNamePropertyField = new ImportField(reflectionLM.propertyCanonicalNameValueClass);
        ImportField dbNamePropertyField = new ImportField(reflectionLM.propertySIDValueClass);
        ImportField captionPropertyField = new ImportField(reflectionLM.propertyCaptionValueClass);
        ImportField loggablePropertyField = new ImportField(reflectionLM.propertyLoggableValueClass);
        ImportField storedPropertyField = new ImportField(reflectionLM.propertyStoredValueClass);
        ImportField isSetNotNullPropertyField = new ImportField(reflectionLM.propertyIsSetNotNullValueClass);
        ImportField returnPropertyField = new ImportField(reflectionLM.propertyClassValueClass);
        ImportField classPropertyField = new ImportField(reflectionLM.propertyClassValueClass);
        ImportField complexityPropertyField = new ImportField(LongClass.instance);
        ImportField tableSIDPropertyField = new ImportField(reflectionLM.propertyTableValueClass);
        ImportField annotationPropertyField = new ImportField(reflectionLM.propertyTableValueClass);
        ImportField statsPropertyField = new ImportField(IntegerClass.instance);

        ImportKey<?> keyProperty = new ImportKey(reflectionLM.property, reflectionLM.propertyCanonicalName.getMapping(canonicalNamePropertyField));

        try {
            List<List<Object>> dataProperty = new ArrayList<>();
            for (Property property : businessLogics.getOrderProperties()) {
                if (needsToBeSynchronized(property)) {
                    String returnClass = "";
                    String classProperty = "";
                    String tableSID = "";
                    Long complexityProperty = null;

                    try {
                        classProperty = property.getClass().getSimpleName();
                        
                        if(property instanceof CalcProperty) {
                            CalcProperty calcProperty = (CalcProperty)property;
                            complexityProperty = calcProperty.getComplexity();
                            if (calcProperty.mapTable != null) {
                                tableSID = calcProperty.mapTable.table.getName();
                            } else {
                                tableSID = "";
                            }
                        }
                        
                        returnClass = property.getValueClass(ClassType.syncPolicy).getSID();
                    } catch (NullPointerException | ArrayIndexOutOfBoundsException ignored) {
                    }
                    
                    dataProperty.add(asList(property.getCanonicalName(),(Object) property.getDBName(), property.caption.getSourceString(), property instanceof CalcProperty && ((CalcProperty)property).isLoggable() ? true : null,
                            property instanceof CalcProperty && ((CalcProperty) property).isStored() ? true : null,
                            property instanceof CalcProperty && ((CalcProperty) property).reflectionNotNull ? true : null,
                            returnClass, classProperty, complexityProperty, tableSID, property.annotation, (Settings.get().isDisableSyncStatProps() ? (Integer)Stat.DEFAULT.getCount() : businessLogics.getStatsProperty(property))));
                }
            }

            startLogger.info("synchronizePropertyEntities integration service started");
            List<ImportProperty<?>> properties = new ArrayList<>();
            properties.add(new ImportProperty(canonicalNamePropertyField, reflectionLM.canonicalNameProperty.getMapping(keyProperty)));
            properties.add(new ImportProperty(dbNamePropertyField, reflectionLM.dbNameProperty.getMapping(keyProperty)));
            properties.add(new ImportProperty(captionPropertyField, reflectionLM.captionProperty.getMapping(keyProperty)));
            properties.add(new ImportProperty(loggablePropertyField, reflectionLM.loggableProperty.getMapping(keyProperty)));
            properties.add(new ImportProperty(storedPropertyField, reflectionLM.storedProperty.getMapping(keyProperty)));
            properties.add(new ImportProperty(isSetNotNullPropertyField, reflectionLM.isSetNotNullProperty.getMapping(keyProperty)));
            properties.add(new ImportProperty(returnPropertyField, reflectionLM.returnProperty.getMapping(keyProperty)));
            properties.add(new ImportProperty(classPropertyField, reflectionLM.classProperty.getMapping(keyProperty)));
            properties.add(new ImportProperty(complexityPropertyField, reflectionLM.complexityProperty.getMapping(keyProperty)));
            properties.add(new ImportProperty(tableSIDPropertyField, reflectionLM.tableSIDProperty.getMapping(keyProperty)));
            properties.add(new ImportProperty(annotationPropertyField, reflectionLM.annotationProperty.getMapping(keyProperty)));
            properties.add(new ImportProperty(statsPropertyField, reflectionLM.statsProperty.getMapping(keyProperty)));

            List<ImportDelete> deletes = new ArrayList<>();
            deletes.add(new ImportDelete(keyProperty, LM.is(reflectionLM.property).getMapping(keyProperty), false));

            ImportTable table = new ImportTable(asList(canonicalNamePropertyField, dbNamePropertyField, captionPropertyField, loggablePropertyField,
                    storedPropertyField, isSetNotNullPropertyField, returnPropertyField,
                    classPropertyField, complexityPropertyField, tableSIDPropertyField, annotationPropertyField, statsPropertyField), dataProperty);

            try (DataSession session = createSyncSession()) {
                session.pushVolatileStats("RM_PE");
                IntegrationService service = new IntegrationService(session, table, Collections.singletonList(keyProperty), properties, deletes);
                service.synchronize(true, false);
                session.popVolatileStats();
                session.apply(businessLogics, getStack());
                startLogger.info("synchronizePropertyEntities finished");
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public void synchronizePropertyParents() {
        startLogger.info("synchronizePropertyParents collecting data started");

        ImportField canonicalNamePropertyField = new ImportField(reflectionLM.propertyCanonicalNameValueClass);
        ImportField numberPropertyField = new ImportField(reflectionLM.numberProperty);
        ImportField parentSidField = new ImportField(reflectionLM.navigatorElementSIDClass);

        List<List<Object>> dataParent = new ArrayList<>();
        for (Property property : businessLogics.getOrderProperties()) {
            if (needsToBeSynchronized(property))
                dataParent.add(asList(property.getCanonicalName(), (Object) property.getParent().getSID(), getNumberInListOfChildren(property)));
        }

        startLogger.info("synchronizePropertyParents integration service started");
        ImportKey<?> keyProperty = new ImportKey(reflectionLM.property, reflectionLM.propertyCanonicalName.getMapping(canonicalNamePropertyField));
        ImportKey<?> keyParent = new ImportKey(reflectionLM.propertyGroup, reflectionLM.propertyGroupSID.getMapping(parentSidField));
        List<ImportProperty<?>> properties = new ArrayList<>();

        properties.add(new ImportProperty(parentSidField, reflectionLM.parentProperty.getMapping(keyProperty), LM.object(reflectionLM.propertyGroup).getMapping(keyParent)));
        properties.add(new ImportProperty(numberPropertyField, reflectionLM.numberProperty.getMapping(keyProperty)));
        ImportTable table = new ImportTable(asList(canonicalNamePropertyField, parentSidField, numberPropertyField), dataParent);

        try {
            try (DataSession session = createSyncSession()) {
                session.pushVolatileStats("RM_PP");
                IntegrationService service = new IntegrationService(session, table, asList(keyProperty, keyParent), properties);
                service.synchronize(true, false);
                session.popVolatileStats();
                session.apply(businessLogics, getStack());
                startLogger.info("synchronizePropertyParents finished");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void synchronizeGroupProperties() {

        startLogger.info("synchronizeGroupProperties collecting data started");
        ImportField sidField = new ImportField(reflectionLM.navigatorElementSIDClass);
        ImportField captionField = new ImportField(reflectionLM.navigatorElementCaptionClass);
        ImportField numberField = new ImportField(reflectionLM.numberPropertyGroup);

        ImportKey<?> key = new ImportKey(reflectionLM.propertyGroup, reflectionLM.propertyGroupSID.getMapping(sidField));

        List<List<Object>> data = new ArrayList<>();

        for (AbstractGroup group : businessLogics.getParentGroups()) {
            data.add(asList(group.getSID(), (Object) group.caption.getSourceString()));
        }

        startLogger.info("synchronizeGroupProperties integration service started");
        List<ImportProperty<?>> props = new ArrayList<>();
        props.add(new ImportProperty(sidField, reflectionLM.SIDPropertyGroup.getMapping(key)));
        props.add(new ImportProperty(captionField, reflectionLM.captionPropertyGroup.getMapping(key)));

        List<ImportDelete> deletes = new ArrayList<>();
        deletes.add(new ImportDelete(key, LM.is(reflectionLM.propertyGroup).getMapping(key), false));

        ImportTable table = new ImportTable(asList(sidField, captionField), data);

        List<List<Object>> data2 = new ArrayList<>();

        for (AbstractGroup group : businessLogics.getParentGroups()) {
            if (group.getParent() != null) {
                data2.add(asList(group.getSID(), (Object) group.getParent().getSID(), getNumberInListOfChildren(group)));
            }
        }

        ImportField parentSidField = new ImportField(reflectionLM.navigatorElementSIDClass);
        ImportKey<?> key2 = new ImportKey(reflectionLM.propertyGroup, reflectionLM.propertyGroupSID.getMapping(parentSidField));
        List<ImportProperty<?>> props2 = new ArrayList<>();
        props2.add(new ImportProperty(parentSidField, reflectionLM.parentPropertyGroup.getMapping(key), LM.object(reflectionLM.propertyGroup).getMapping(key2)));
        props2.add(new ImportProperty(numberField, reflectionLM.numberPropertyGroup.getMapping(key)));
        ImportTable table2 = new ImportTable(asList(sidField, parentSidField, numberField), data2);

        try {
            try (DataSession session = createSyncSession()) {
                session.pushVolatileStats("RM_GP");
                IntegrationService service = new IntegrationService(session, table, Collections.singletonList(key), props, deletes);
                service.synchronize(true, false);
                service = new IntegrationService(session, table2, asList(key, key2), props2);
                service.synchronize(true, false);
                session.popVolatileStats();
                session.apply(businessLogics, getStack());
                startLogger.info("synchronizeGroupProperties finished");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private Integer getNumberInListOfChildren(AbstractNode abstractNode) {
        AbstractGroup nodeParent = abstractNode.getParent();
        int counter = 0;
        for (AbstractNode node : nodeParent.getChildrenIt()) {
            if(abstractNode instanceof Property && counter > 20)  // оптимизация
                return nodeParent.getIndexedPropChildren().get((Property) abstractNode);
            counter++;
            if (abstractNode instanceof Property) {
                if (node instanceof Property)
                    if (node == abstractNode) {
                        return counter;
                    }
            } else {
                if (node instanceof AbstractGroup)
                    if (((AbstractGroup) node).getSID().equals(((AbstractGroup) abstractNode).getSID())) {
                        return counter;
                    }
            }
        }
        return 0;
    }

    public void synchronizeTables() {

        startLogger.info("synchronizeTables collecting data started");
        ImportField tableSidField = new ImportField(reflectionLM.sidTable);
        ImportField tableKeySidField = new ImportField(reflectionLM.sidTableKey);
        ImportField tableKeyNameField = new ImportField(reflectionLM.nameTableKey);
        ImportField tableKeyClassField = new ImportField(reflectionLM.classTableKey);
        ImportField tableKeyClassSIDField = new ImportField(reflectionLM.classSIDTableKey);
        ImportField tableColumnSidField = new ImportField(reflectionLM.sidTableColumn);
        ImportField tableColumnLongSIDField = new ImportField(reflectionLM.longSIDTableColumn); 

        ImportKey<?> tableKey = new ImportKey(reflectionLM.table, reflectionLM.tableSID.getMapping(tableSidField));
        ImportKey<?> tableKeyKey = new ImportKey(reflectionLM.tableKey, reflectionLM.tableKeySID.getMapping(tableKeySidField));
        ImportKey<?> tableColumnKey = new ImportKey(reflectionLM.tableColumn, reflectionLM.tableColumnLongSID.getMapping(tableColumnLongSIDField));

        List<List<Object>> data = new ArrayList<>();
        List<List<Object>> dataKeys = new ArrayList<>();
        List<List<Object>> dataProps = new ArrayList<>();
        for (ImplementTable dataTable : LM.tableFactory.getImplementTables()) {
            Object tableName = dataTable.getName();
            data.add(Collections.singletonList(tableName));
            ImMap<KeyField, ValueClass> classes = dataTable.getClasses().getCommonParent(dataTable.getTableKeys());
            for (KeyField key : dataTable.keys) {
                dataKeys.add(asList(tableName, key.getName(), tableName + "." + key.getName(), classes.get(key).getCaption().getSourceString(), classes.get(key).getSID()));
            }
            for (PropertyField property : dataTable.properties) {
                dataProps.add(asList(tableName, property.getName(), tableName + "." + property.getName()));
            }
        }

        startLogger.info("synchronizeTables integration service started");
        List<ImportProperty<?>> properties = new ArrayList<>();
        properties.add(new ImportProperty(tableSidField, reflectionLM.sidTable.getMapping(tableKey)));

        List<ImportProperty<?>> propertiesKeys = new ArrayList<>();
        propertiesKeys.add(new ImportProperty(tableKeySidField, reflectionLM.sidTableKey.getMapping(tableKeyKey)));
        propertiesKeys.add(new ImportProperty(tableKeyNameField, reflectionLM.nameTableKey.getMapping(tableKeyKey)));
        propertiesKeys.add(new ImportProperty(tableKeyClassField, reflectionLM.classTableKey.getMapping(tableKeyKey)));
        propertiesKeys.add(new ImportProperty(tableKeyClassSIDField, reflectionLM.classSIDTableKey.getMapping(tableKeyKey)));
        propertiesKeys.add(new ImportProperty(null, reflectionLM.tableTableKey.getMapping(tableKeyKey), reflectionLM.tableSID.getMapping(tableSidField)));

        List<ImportProperty<?>> propertiesColumns = new ArrayList<>();
        propertiesColumns.add(new ImportProperty(null, reflectionLM.tableTableColumn.getMapping(tableColumnKey), reflectionLM.tableSID.getMapping(tableSidField)));
        propertiesColumns.add(new ImportProperty(tableColumnSidField, reflectionLM.sidTableColumn.getMapping(tableColumnKey)));
        propertiesColumns.add(new ImportProperty(tableColumnLongSIDField, reflectionLM.longSIDTableColumn.getMapping(tableColumnKey)));

        List<ImportDelete> delete = new ArrayList<>();
        delete.add(new ImportDelete(tableKey, LM.is(reflectionLM.table).getMapping(tableKey), false));

        List<ImportDelete> deleteKeys = new ArrayList<>();
        deleteKeys.add(new ImportDelete(tableKeyKey, LM.is(reflectionLM.tableKey).getMapping(tableKeyKey), false));

        List<ImportDelete> deleteColumns = new ArrayList<>();
        deleteColumns.add(new ImportDelete(tableColumnKey, LM.is(reflectionLM.tableColumn).getMapping(tableColumnKey), false));

        ImportTable table = new ImportTable(Collections.singletonList(tableSidField), data);
        ImportTable tableKeys = new ImportTable(asList(tableSidField, tableKeyNameField, tableKeySidField, tableKeyClassField, tableKeyClassSIDField), dataKeys);
        ImportTable tableColumns = new ImportTable(asList(tableSidField, tableColumnSidField, tableColumnLongSIDField), dataProps);

        try {
            try(DataSession session = createSyncSession()) {
                session.pushVolatileStats("RM_TE");

                IntegrationService service = new IntegrationService(session, table, Collections.singletonList(tableKey), properties, delete);
                service.synchronize(true, false);

                service = new IntegrationService(session, tableKeys, Collections.singletonList(tableKeyKey), propertiesKeys, deleteKeys);
                service.synchronize(true, false);

                service = new IntegrationService(session, tableColumns, Collections.singletonList(tableColumnKey), propertiesColumns, deleteColumns);
                service.synchronize(true, false);

                session.popVolatileStats();
                session.apply(businessLogics, getStack());
                startLogger.info("synchronizeTables finished");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void resetConnectionStatus() {
        try {
            try (DataSession session = createSession()) {
                PropertyChange statusChanges = new PropertyChange(systemEventsLM.connectionStatus.getDataObject("disconnectedConnection"),
                        systemEventsLM.connectionStatusConnection.property.interfaces.single(),
                        systemEventsLM.connectionStatus.getDataObject("connectedConnection"));
                session.change((CalcProperty) systemEventsLM.connectionStatusConnection.property, statusChanges);
                session.apply(businessLogics, getStack());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void runOnStarted() {
        try {
            try (DataSession session = dbManager.createSession()) {
                LM.onStarted.execute(session, getStack());
                session.apply(businessLogics, getStack());
            }
            if(dbManager.needExtraUpdateStats) {
                try (DataSession session = dbManager.createSession()) {
                    dbManager.updateStats(session.sql);
                    session.apply(businessLogics, getStack());
                }
            }

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
    
    public void logLaunch() {
        try {
            try (DataSession session = createSession()) {
                DataObject newLaunch = session.addObject(systemEventsLM.launch);
                systemEventsLM.computerLaunch.change(getServerComputer(), session, newLaunch);
                systemEventsLM.timeLaunch.change(timeLM.currentDateTime.read(session), session, newLaunch);
                systemEventsLM.revisionLaunch.change(getRevision(), session, newLaunch);
                systemEventsLM.currentLaunch.change(newLaunch.object, session);
                session.apply(businessLogics, getStack());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
