package lsfusion.server.logics.property;

import com.google.common.base.Throwables;
import jasperapi.ReportGenerator;
import lsfusion.base.FunctionSet;
import lsfusion.base.Pair;
import lsfusion.base.Processor;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.interop.ModalityType;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.form.ReportGenerationData;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.*;
import lsfusion.server.classes.sets.ResolveClassSet;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.context.SameThreadExecutionStack;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.QueryEnvironment;
import lsfusion.server.data.SQLCallable;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.type.ObjectType;
import lsfusion.server.data.type.Type;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.form.entity.PropertyDrawEntity;
import lsfusion.server.form.entity.filter.FilterEntity;
import lsfusion.server.form.instance.*;
import lsfusion.server.form.instance.listener.CustomClassListener;
import lsfusion.server.logics.*;
import lsfusion.server.logics.SecurityManager;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.linear.LP;
import lsfusion.server.logics.property.actions.FormEnvironment;
import lsfusion.server.remote.InteractiveFormReportManager;
import lsfusion.server.session.*;
import net.sf.jasperreports.engine.JRAbstractExporter;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRPdfExporter;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ScheduledExecutorService;

public class ExecutionContext<P extends PropertyInterface> implements UserInteraction, SessionCreator {
    private ImMap<P, ? extends ObjectValue> keys;
    private Stack<UpdateCurrentClasses> updateClasses;
    public void pushUpdate(UpdateCurrentClasses push) {
        if(updateClasses == null)
            updateClasses = new Stack<>();
        updateClasses.push(push);
    }
    public void popUpdate() {
        updateClasses.pop();
    }

    public final ExecutionStack stack;

    private class ContextStack extends SameThreadExecutionStack {

        public ContextStack(ExecutionStack upStack) {
            super(upStack);
        }

        @Override
        protected DataSession getSession() {
            return env.getSession();
        }

        public ImMap<String, String> getAllParamsWithClassesInStack() {
            ImMap<String, String> result = MapFact.EMPTY();

            if(paramsToFQN != null) {
                result = paramsToFQN.addExcl(result);
            }

            if(newDebugStack)
                return result;

            return result.addExcl(super.getAllParamsWithClassesInStack());
        }

        public ImMap<String, ObjectValue> getAllParamsWithValuesInStack() {
            ImMap<String, ObjectValue> result = MapFact.EMPTY();

            if(paramsToInterfaces != null) {
                result = paramsToInterfaces.mapValues(new GetValue<ObjectValue, P>() {
                    public ObjectValue getMapValue(P value) {
                        return getKeyValue(value);
                    }
                }).addExcl(result);
            }

            if(newDebugStack)
                return result;

            return result.addExcl(super.getAllParamsWithValuesInStack());
        }

        public ImSet<Pair<LP, List<ResolveClassSet>>> getAllLocalsInStack() {
            ImSet<Pair<LP, List<ResolveClassSet>>> result = SetFact.EMPTY();

            if(locals != null)
                result = result.addExcl(locals);

            if(newDebugStack)
                return result;

            return result.addExcl(super.getAllLocalsInStack());
        }

        public boolean hasNewDebugStack() {
            if(newDebugStack)
                return true;
            return super.hasNewDebugStack();
        }

        public Processor<ImMap<String, ObjectValue>> getWatcher() {
            if(watcher != null)
                return watcher;
            return super.getWatcher();
        }

        public void updateOnApply(DataSession session) throws SQLException, SQLHandledException {
            final ImMap<P, ? extends ObjectValue> prevKeys = keys;
            keys = session.updateCurrentClasses(keys);
            session.addRollbackInfo(new Runnable() {
                public void run() {
                    keys = prevKeys;
                }});

            if(updateClasses!=null) {
                for(UpdateCurrentClasses update : updateClasses)
                    update.updateOnApply(session);
            }
            super.updateOnApply(session);
        }
    }

    private final ObjectValue pushedUserInput;
    private final DataObject pushedAddObject; // чисто для асинхронного добавления объектов

    private final ExecutionEnvironment env;

    private final ScheduledExecutorService executorService;

    private final FormEnvironment<P> form;

    // debug info 
    private ImRevMap<String, P> paramsToInterfaces;
    private ImMap<String, String> paramsToFQN;
    private ImSet<Pair<LP, List<ResolveClassSet>>> locals;
    private boolean newDebugStack;
    private Processor<ImMap<String, ObjectValue>> watcher;
    
//    public String actionName;
//    public int showStack() {
//        int level = 0;
//        if(stack != null) {
//            if(newDebugStack)
//                System.out.println("new debug");
//            level = stack.showStack();
//        }
//        if(actionName != null)
//            System.out.println((level++) + actionName + (newDebugStack ? " NEWSTACK" : ""));
//        return level;
//    } 
//
    public ExecutionContext(ImMap<P, ? extends ObjectValue> keys, ExecutionEnvironment env, ExecutionStack stack) {
        this(keys, null, null, env, null, null, stack);
    }

    public ExecutionContext(ImMap<P, ? extends ObjectValue> keys, ObjectValue pushedUserInput, DataObject pushedAddObject, ExecutionEnvironment env, ScheduledExecutorService executorService, FormEnvironment<P> form, ExecutionStack stack) {
        this.keys = keys;
        this.pushedUserInput = pushedUserInput;
        this.pushedAddObject = pushedAddObject;
        this.env = env;
        this.executorService = executorService;
        this.form = form;
        this.stack = new ContextStack(stack);
    }
    
    public ExecutionContext<P> override() { // для дебаггера
        return new ExecutionContext<>(keys, pushedUserInput, pushedAddObject, env, executorService, form, stack);
    }

    public void setParamsToInterfaces(ImRevMap<String, P> paramsToInterfaces) {
        this.paramsToInterfaces = paramsToInterfaces;
    }

    public void setLocals(ImSet<Pair<LP, List<ResolveClassSet>>> locals) {
        this.locals = locals;
    }
    
    public void setNewDebugStack(boolean newDebugStack) {
        this.newDebugStack = newDebugStack;        
    }

    public boolean isPrevEventScope() { // если не в объявлении действия и не в локальном событии
        return getSession().isInSessionEvent() && !stack.hasNewDebugStack();
    }

    public void setWatcher(Processor<ImMap<String, ObjectValue>> watcher) {
        this.watcher = watcher;
    }

    public ScheduledExecutorService getExecutorService() {
        return executorService;
    }

    public ImRevMap<String, P> getParamsToInterfaces() {
        return paramsToInterfaces;
    }

    public ImMap<String, String> getAllParamsWithClassesInStack() {
        ImMap<String, String> result = MapFact.EMPTY();
        
        if(paramsToFQN != null) {
            result = paramsToFQN.addExcl(result); // потому 
        }

        if(!newDebugStack && stack != null)
            result = result.addExcl(stack.getAllParamsWithClassesInStack());

        return result;
    }

    public ImMap<String, ObjectValue> getAllParamsWithValuesInStack() {
        ImMap<String, ObjectValue> result = MapFact.EMPTY();

        if(paramsToInterfaces != null) {
            result = paramsToInterfaces.mapValues(new GetValue<ObjectValue, P>() {
                public ObjectValue getMapValue(P value) {
                    return getKeyValue(value);
                }
            }).addExcl(result);
        }

        if(!newDebugStack && stack != null)
            result = result.addExcl(stack.getAllParamsWithValuesInStack());

        return result;
    }

    public ImSet<Pair<LP, List<ResolveClassSet>>> getAllLocalsInStack() {
        ImSet<Pair<LP, List<ResolveClassSet>>> result = SetFact.EMPTY();
        
        if(locals != null)
            result = result.addExcl(locals);

        if(!newDebugStack && stack != null)
            result = result.addExcl(stack.getAllLocalsInStack());
        
        return result;
    }
    
    public Processor<ImMap<String, ObjectValue>> getWatcher() {
        if(watcher != null)
            return watcher;
        if(stack != null)
            return stack.getWatcher();
        return null;
    }

    public void setParamsToFQN(ImMap<String, String> paramsToFQN) {
        this.paramsToFQN = paramsToFQN;
    }

    public ExecutionEnvironment getEnv() {
        return env;
    }

    public ImMap<P, ? extends ObjectValue> getKeys() {
        return keys;
    }

    public ImMap<P, DataObject> getDataKeys() { // предполагается что вызывается из действий у которых !allowNulls
        return DataObject.assertDataObjects(getKeys());
    }

    public ObjectValue getKeyValue(P key) {
        return getKeys().get(key);
    }

    public DataObject getDataKeyValue(P key) {
        return getDataKeys().get(key);
    }

    public Object getKeyObject(P key) {
        return getKeyValue(key).getValue();
    }

    public ObjectValue getSingleKeyValue() {
        return getKeys().singleValue();
    }

    public DataObject getSingleDataKeyValue() {
        return getDataKeys().singleValue();
    }

    public Object getSingleKeyObject() {
        return getSingleKeyValue().getValue();
    }

    public int getKeyCount() {
        return getKeys().size();
    }

    public DataSession getSession() {
        return env.getSession();
    }

    public void delayUserInterfaction(ClientAction action) {
        ThreadLocalContext.delayUserInteraction(action);
    }

    public ExecutionEnvironment getSessionEventFormEnv() {
        return getFormInstance(true, false);
    }
    
    // подразумевают вызов только из Top Action'ов (FORM.ADDOBJ, form*, DefaultChange)
    public FormInstance getFormFlowInstance() {
        return getFormInstance(true, true);
    }
    
    // использование формы, чисто для того чтобы передать дальше 
    public FormInstance getFormAspectInstance() {
        return getFormInstance(false, false);
    }

    public FormInstance<?> getFormInstance(boolean sameSession, boolean assertExists) {
        FormInstance formInstance = form != null ? form.getInstance() : null;
        FormInstance formExecEnv = env.getFormInstance();
        
        if(formExecEnv != null) { // пока дублирующие механизмы, в будущем надо рефакторить
            // formInstance == null так как в события не всегда formEnv проталкивается
            ServerLoggers.assertLog(formInstance == null || formExecEnv == formInstance, "FORMS SHOULD BE EQUAL");
            return formExecEnv;
        }
        
        if(formInstance != null) {
            if (formInstance.getSession() == env) {
                ServerLoggers.assertLog(false, "FORM EXECUTION ENVIRONMENT DROPPED");
            } else {
                if (sameSession)
                    formInstance = null;
            }
        }
        
        if(assertExists && formInstance == null)
            ServerLoggers.assertLog(false, "FORM ALWAYS SHOULD EXIST");
        return formInstance;
    }

    //todo: закэшировать, если скорость доступа к ThreadLocal станет критичной
    //todo: сейчас по идее не актуально, т.к. большая часть времени в ActionProperty уходит на основную работу
    public LogicsInstance getLogicsInstance() {
        return ThreadLocalContext.getLogicsInstance();
    }
    
    private CustomClassListener getClassListener() {
        return ThreadLocalContext.getClassListener();
    }

    public BusinessLogics<?> getBL() {
        return getLogicsInstance().getBusinessLogics();
    }

    public DBManager getDbManager() {
        return getLogicsInstance().getDbManager();
    }

    public NavigatorsManager getNavigatorsManager() {
        return getLogicsInstance().getNavigatorsManager();
    }

    public RestartManager getRestartManager() {
        return getLogicsInstance().getRestartManager();
    }

    public SecurityManager getSecurityManager() {
        return getLogicsInstance().getSecurityManager();
    }

    public RMIManager getRmiManager() {
        return getLogicsInstance().getRmiManager();
    }

    public DataSession createSession() throws SQLException {
        return getSession().createSession();
//        return getDbManager().createSession();
    }

    public GroupObjectInstance getChangingPropertyToDraw() {
        PropertyDrawInstance drawInstance = form.getChangingDrawInstance();
        if(drawInstance==null)
            return null;
        return drawInstance.toDraw;
    }

    public ImMap<P, PropertyObjectInterfaceInstance> getObjectInstances() {
        return form!=null ? form.getMapObjects() : null;
    }

    public PropertyObjectInterfaceInstance getSingleObjectInstance() {
        ImMap<P, PropertyObjectInterfaceInstance> mapObjects = getObjectInstances();
        return mapObjects != null && mapObjects.size() == 1 ? mapObjects.singleValue() : null;
    }

    public Modifier getModifier() {
        return getEnv().getModifier();
    }

    public DataObject addObject(ConcreteCustomClass cls) throws SQLException, SQLHandledException {
        return getSession().addObject(cls, pushedAddObject);
    }
    
    public DataObject addObjectAutoSet(ConcreteCustomClass cls) throws SQLException, SQLHandledException {
        return getSession().addObjectAutoSet(cls, pushedAddObject, getBL(), getClassListener());
    }

    public <T extends PropertyInterface> SinglePropertyTableUsage<T> addObjects(ConcreteCustomClass cls, PropertySet<T> set) throws SQLException, SQLHandledException {
        return getSession().addObjects(cls, set);
    }

    public DataObject formAddObject(ObjectEntity object, ConcreteCustomClass cls) throws SQLException, SQLHandledException {
        FormInstance<?> form = getFormFlowInstance();
        return form.addFormObject((CustomObjectInstance) form.instanceFactory.getInstance(object), cls, pushedAddObject, stack);
    }

    public void changeClass(PropertyObjectInterfaceInstance objectInstance, DataObject object, ConcreteObjectClass changeClass) throws SQLException, SQLHandledException {
        getEnv().changeClass(objectInstance, object, changeClass);
    }

    public void changeClass(ClassChange change) throws SQLException, SQLHandledException {
        getSession().changeClass(change);
    }

    public boolean checkApply(BusinessLogics BL) throws SQLException, SQLHandledException {
        return getSession().check(BL, getSessionEventFormEnv(), stack, this);
    }

    public boolean checkApply() throws SQLException, SQLHandledException {
        return checkApply(getBL());
    }

    public boolean apply() throws SQLException, SQLHandledException {
        return apply(SetFact.<ActionPropertyValueImplement>EMPTYORDER());
    }

    public boolean apply(ImOrderSet<ActionPropertyValueImplement> applyActions) throws SQLException, SQLHandledException {
        return apply(applyActions, SetFact.<SessionDataProperty>EMPTY());
    }
    
    public boolean apply(ImOrderSet<ActionPropertyValueImplement> applyActions, FunctionSet<SessionDataProperty> keepProperties) throws SQLException, SQLHandledException {
        return getEnv().apply(getBL(), stack, this, applyActions, keepProperties, getSessionEventFormEnv());
    }

    public void cancel(FunctionSet<SessionDataProperty> keep) throws SQLException, SQLHandledException {
        getEnv().cancel(stack, keep);
    }

    public ExecutionContext<P> override(ExecutionEnvironment newEnv) {
        return new ExecutionContext<>(keys, pushedUserInput, pushedAddObject, newEnv, executorService, form, stack);
    }

    public ExecutionContext<P> override(ScheduledExecutorService newExecutorService) {
        return new ExecutionContext<>(keys, pushedUserInput, pushedAddObject, env, newExecutorService, form, stack);
    }

    public ExecutionContext<P> override(ExecutionEnvironment newEnv, ExecutionStack stack) {
        return new ExecutionContext<>(keys, pushedUserInput, pushedAddObject, newEnv, executorService, form, stack);
    }

    public ExecutionContext<P> override(ExecutionStack stack) {
        return new ExecutionContext<>(keys, pushedUserInput, pushedAddObject, env, executorService, form, stack);
    }

    public <T extends PropertyInterface> ExecutionContext<T> override(ImMap<T, ? extends ObjectValue> keys, ImMap<T, ? extends CalcPropertyInterfaceImplement<P>> mapInterfaces) {
        return override(keys, form!=null ? form.mapJoin(mapInterfaces) : null, pushedUserInput);
    }

    public <T extends PropertyInterface> ExecutionContext<T> map(ImRevMap<T, P> mapping) {
        return override(mapping.join(keys), form!=null ? form.map(mapping) : null, pushedUserInput);
    }

    public ExecutionContext<P> override(ImMap<P, ? extends ObjectValue> keys) {
        return override(keys, form, pushedUserInput);
    }

    public <T extends PropertyInterface> ExecutionContext<T> override(ImMap<T, ? extends ObjectValue> keys, FormEnvironment<T> form) {
        return override(keys, form, pushedUserInput);
    }

    public <T extends PropertyInterface> ExecutionContext<T> override(ImMap<T, ? extends ObjectValue> keys, FormEnvironment<T> form, ObjectValue pushedUserInput) {
        return new ExecutionContext<>(keys, pushedUserInput, pushedAddObject, env, executorService, form, stack);
    }

    public QueryEnvironment getQueryEnv() {
        return env.getQueryEnv();
    }

    public void executeSessionEvents() throws SQLException, SQLHandledException {
        getSession().executeSessionEvents(getSessionEventFormEnv(), stack);
    }

    public void delayUserInteraction(ClientAction action) {
        ThreadLocalContext.delayUserInteraction(action);
    }

    private void assertNotUserInteractionInTransaction() {
        ServerLoggers.assertLog(!getSession().isInTransaction() || ThreadLocalContext.canBeProcessed(), "USER INTERACTION IN TRANSACTION");
    }
    public Object requestUserInteraction(ClientAction action) {
        assertNotUserInteractionInTransaction();
        return ThreadLocalContext.requestUserInteraction(action);
    }

    public void requestFormUserInteraction(FormInstance remoteForm, ModalityType modalityType, ExecutionStack stack) throws SQLException, SQLHandledException {
        assertNotUserInteractionInTransaction();
        ThreadLocalContext.requestFormUserInteraction(remoteForm, modalityType, stack);
    }

    public void writeRequested(ObjectValue chosenValue, Type type, LCP targetProp) throws SQLException, SQLHandledException {
        getBL().LM.writeRequested(chosenValue, type, getEnv(), targetProp);
    }

    public <R> R pushRequest(SQLCallable<R> callable) throws SQLException, SQLHandledException {
        return getBL().LM.pushRequest(getEnv(), callable);
    }

    public <R> R pushRequestedValue(ObjectValue pushValue, Type pushType, SQLCallable<R> callable) throws SQLException, SQLHandledException {
        return getBL().LM.pushRequestedValue(pushValue, pushType, getEnv(), callable);
    }
    
    public ObjectValue getPushedUserInput() {
        return pushedUserInput;
    }

    // чтение пользователя
    public ObjectValue requestUserObject(final DialogRequest dialog) throws SQLException, SQLHandledException { // null если canceled
        return requestUser(ObjectType.instance, new SQLCallable<ObjectValue>() {
            public ObjectValue call() throws SQLException, SQLHandledException {
                return ThreadLocalContext.requestUserObject(dialog, stack);
            }
        });
    }

    // cannot use because of backward compatibility 
//    public ObjectValue requestUserData(FileClass dataClass, Object oldValue) {
//        assertNotUserInteractionInTransaction();
//        ObjectValue userInput = pushedUserInput != null ? pushedUserInput : ThreadLocalContext.requestUserData(dataClass, oldValue);
//        setLastUserInput(userInput);
//        return userInput;
//    }


    public boolean isRequest() throws SQLException, SQLHandledException {
        assertNotUserInteractionInTransaction();
        return getBL().LM.isRequest(getEnv());
    }

    public ObjectValue requestUser(Type type, SQLCallable<ObjectValue> request) throws SQLException, SQLHandledException {
        assertNotUserInteractionInTransaction();
        return getBL().LM.getRequestedValue(type, getEnv(), request);
    }

    public ObjectValue requestUserData(final DataClass dataClass, final Object oldValue) {
        try { // временно для обратной совместимости
            return requestUser(dataClass, new SQLCallable<ObjectValue>() {
                public ObjectValue call() throws SQLException, SQLHandledException {
                    return ThreadLocalContext.requestUserData(dataClass, oldValue);
                }
            });
        } catch (SQLException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    public ObjectValue inputUserData(DataClass dataClass, Object oldValue) {
        assertNotUserInteractionInTransaction();
        return ThreadLocalContext.requestUserData(dataClass, oldValue);
    }

    public ObjectValue requestUserClass(final CustomClass baseClass, final CustomClass defaultValue, final boolean concrete) throws SQLException, SQLHandledException {
        return requestUser(ObjectType.instance, new SQLCallable<ObjectValue>() {
            public ObjectValue call() {
                return ThreadLocalContext.requestUserClass(baseClass, defaultValue, concrete);
            }
        });
    }

    public FormInstance createFormInstance(FormEntity formEntity) throws SQLException, SQLHandledException {
        return createFormInstance(formEntity, MapFact.<ObjectEntity, DataObject>EMPTY(), getSession());
    }

    public FormInstance createFormInstance(FormEntity formEntity, ImMap<ObjectEntity, ? extends ObjectValue> mapObjects, DataSession session) throws SQLException, SQLHandledException {
        return createFormInstance(formEntity, mapObjects, session, false, false, false, false, false, false, null, null, null, false);
    }

    public FormInstance createFormInstance(FormEntity formEntity, ImMap<ObjectEntity, ? extends ObjectValue> mapObjects) throws SQLException, SQLHandledException {
        return createFormInstance(formEntity, mapObjects, getSession());
    }

    public FormInstance createFormInstance(FormEntity formEntity, ImMap<ObjectEntity, ? extends ObjectValue> mapObjects, DataSession session, boolean isModal, boolean isAdd, Boolean manageSession, boolean checkOnOk, boolean showDrop, boolean interactive, ImSet<FilterEntity> contextFilters, PropertyDrawEntity initFilterProperty, ImSet<PullChangeProperty> pullProps, boolean readonly) throws SQLException, SQLHandledException {
        return ThreadLocalContext.createFormInstance(formEntity, mapObjects, stack, session, isModal, isAdd, manageSession, checkOnOk, showDrop, interactive, contextFilters, initFilterProperty, pullProps, readonly);
    }

    public File generateFileFromForm(BusinessLogics BL, FormEntity formEntity, ObjectEntity objectEntity, DataObject dataObject) throws SQLException, SQLHandledException {

        FormInstance remoteForm = createFormInstance(formEntity, MapFact.singleton(objectEntity, dataObject));
        try {
            ReportGenerationData generationData = new InteractiveFormReportManager(remoteForm).getReportData(false);
            ReportGenerator report = new ReportGenerator(generationData);
            JasperPrint print = report.createReport(false, new HashMap());
            File tempFile = File.createTempFile("lsfReport", ".pdf");

            JRAbstractExporter exporter = new JRPdfExporter();
            exporter.setParameter(JRExporterParameter.OUTPUT_FILE_NAME, tempFile.getAbsolutePath());
            exporter.setParameter(JRExporterParameter.JASPER_PRINT, print);
            exporter.exportReport();

            return tempFile;

        } catch (ClassNotFoundException | IOException | JRException e) {
            throw new RuntimeException(e);
        }
    }

}
