package lsfusion.server.session;

import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import lsfusion.base.*;
import lsfusion.base.col.ListFact;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.*;
import lsfusion.base.col.interfaces.mutable.*;
import lsfusion.base.col.interfaces.mutable.add.MAddCol;
import lsfusion.base.col.interfaces.mutable.add.MAddSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetExValue;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.base.col.interfaces.mutable.mapvalue.ImValueMap;
import lsfusion.interop.Compare;
import lsfusion.interop.action.ConfirmClientAction;
import lsfusion.interop.action.LogMessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.Settings;
import lsfusion.server.caches.ManualLazy;
import lsfusion.server.classes.*;
import lsfusion.server.classes.sets.AndClassSet;
import lsfusion.server.classes.sets.UpClassSet;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.*;
import lsfusion.server.data.expr.*;
import lsfusion.server.data.expr.formula.StringAggConcatenateFormulaImpl;
import lsfusion.server.data.expr.query.GroupExpr;
import lsfusion.server.data.query.Join;
import lsfusion.server.data.query.Query;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.data.type.*;
import lsfusion.server.data.where.Where;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.instance.ChangedData;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.form.instance.GroupObjectInstance;
import lsfusion.server.form.instance.PropertyObjectInterfaceInstance;
import lsfusion.server.form.instance.listener.CustomClassListener;
import lsfusion.server.form.navigator.*;
import lsfusion.server.logics.*;
import lsfusion.server.logics.debug.ActionPropertyDebugger;
import lsfusion.server.logics.debug.ClassDebugInfo;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.*;
import lsfusion.server.logics.property.actions.SessionEnvEvent;
import lsfusion.server.logics.table.IDTable;
import lsfusion.server.logics.table.ImplementTable;
import lsfusion.server.stack.*;

import javax.swing.*;
import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.*;

import static lsfusion.base.col.SetFact.fromJavaSet;
import static lsfusion.server.context.ThreadLocalContext.localize;

public class DataSession extends ExecutionEnvironment implements SessionChanges, SessionCreator, AutoCloseable {

    public static final SessionDataProperty isDataChanged = new SessionDataProperty(LocalizedString.create("Is data changed"), LogicalClass.instance);

    private boolean isStoredDataChanged;

    private DataSession parentSession;

    private Map<DataProperty, PropertyChangeTableUsage<ClassPropertyInterface>> data = MapFact.mAddRemoveMap();
    private SingleKeyPropertyUsage news = null;

    // оптимизационные вещи
    private Set<CustomClass> add = SetFact.mAddRemoveSet();
    private Set<CustomClass> remove = SetFact.mAddRemoveSet();
    private Set<ConcreteObjectClass> usedOldClasses = SetFact.mAddRemoveSet();
    private Set<ConcreteObjectClass> usedNewClasses = SetFact.mAddRemoveSet();
    private Map<CustomClass, DataObject> singleAdd = MapFact.mAddRemoveMap();
    private Map<CustomClass, DataObject> singleRemove = MapFact.mAddRemoveMap();
    private Map<DataObject, ConcreteObjectClass> newClasses = MapFact.mAddRemoveMap(); // просто lazy кэш для getCurrentClass

    private boolean keepLastAttemptCountMap;
    private String lastAttemptCountMap = null;

    public void setKeepLastAttemptCountMap(boolean keepLastAttemptCountMap) {
        this.keepLastAttemptCountMap = keepLastAttemptCountMap;
    }

    public String getLastAttemptCountMap() {
        return lastAttemptCountMap;
    }

    public static Where isValueClass(Expr expr, CustomClass customClass, Set<ConcreteObjectClass> usedNewClasses) {
        return isValueClass(expr, customClass.getUpSet(), usedNewClasses);
    }

    public static Where isValueClass(Expr expr, ObjectValueClassSet classSet, Set<ConcreteObjectClass> usedNewClasses) {
        Where result = Where.FALSE;
        for(ConcreteObjectClass usedClass : usedNewClasses)
            if(usedClass instanceof ConcreteCustomClass) {
                ConcreteCustomClass customUsedClass = (ConcreteCustomClass) usedClass;
                if(customUsedClass.inSet(classSet)) // если изменяется на класс, у которого
                    result = result.or(expr.compare(customUsedClass.getClassObject(), Compare.EQUALS));
            }
        return result;
    }

    public static Where isValueClass(Expr expr, ObjectValueClassSet classSet) {
        Where result = Where.FALSE;
        for(ConcreteCustomClass customUsedClass : classSet.getSetConcreteChildren())
            result = result.or(expr.compare(customUsedClass.getClassObject().getStaticExpr(), Compare.EQUALS));
        return result;
    }

    public ImSet<CalcProperty> getChangedProps(ImSet<CustomClass> add, ImSet<CustomClass> remove, ImSet<ConcreteObjectClass> old, ImSet<ConcreteObjectClass> newc, ImSet<DataProperty> data) {
        return SetFact.<CalcProperty>addExclSet(getClassChanges(add, remove, old, newc), data);
    }
    public ImSet<CalcProperty> getChangedProps() {
        return getChangedProps(fromJavaSet(add), fromJavaSet(remove), fromJavaSet(usedOldClasses), fromJavaSet(usedNewClasses), fromJavaSet(data.keySet()));
    }

    private class DataModifier extends SessionModifier {

        public DataModifier() {
            super("data");
        }

        public SQLSession getSQL() {
            return sql;
        }

        public OperationOwner getOpOwner() {
            return DataSession.this.getOwner();
        }

        public BaseClass getBaseClass() {
            return baseClass;
        }

        public QueryEnvironment getQueryEnv() {
            return env;
        }

        protected <P extends PropertyInterface> ModifyChange<P> calculateModifyChange(CalcProperty<P> property, PrereadRows<P> preread, FunctionSet<CalcProperty> overrided) {
            PropertyChange<P> propertyChange = getPropertyChange(property);
            if(propertyChange!=null)
                return new ModifyChange<>(propertyChange, false);
            if(!preread.isEmpty())
                return new ModifyChange<>(property.getNoChange(), preread, false);
            return null;
        }

        @Override
        public String out() {
            return super.out() + "\nchanged : " + getChangedProps().mapValues(new GetValue<PropertyChange, CalcProperty>() {
                @Override
                public PropertyChange getMapValue(CalcProperty value) {
                    return getPropertyChange(value);
                }
            });
        }

        public ImSet<CalcProperty> calculateProperties() {
            return getChangedProps();
        }

        public long getMaxCount(CalcProperty recDepends) {
            return DataSession.this.getMaxDataUsed(recDepends);
        }
    }
    private final DataModifier dataModifier = new DataModifier();

    protected <P extends PropertyInterface> PropertyChange<P> getPropertyChange(CalcProperty<P> property) {
        if(property instanceof ObjectClassProperty)
            return (PropertyChange<P>) getObjectClassChange((ObjectClassProperty) property);

        if(property instanceof ClassDataProperty)
            return (PropertyChange<P>) getClassDataChange((ClassDataProperty) property);

        if(property instanceof IsClassProperty)
            return (PropertyChange<P>) getClassChange((IsClassProperty) property);

        if(property instanceof DataProperty)
            return (PropertyChange<P>) getDataChange((DataProperty) property);
        return null;
    }

    private class Transaction {
        private final Set<CustomClass> add;
        private final Set<CustomClass> remove;
        private final Set<ConcreteObjectClass> usedOldClases;
        private final Set<ConcreteObjectClass> usedNewClases;
        private final Map<CustomClass, DataObject> singleAdd;
        private final Map<CustomClass, DataObject> singleRemove;
        private final Map<DataObject, ConcreteObjectClass> newClasses;

        private final SessionData news;
        private final ImMap<DataProperty, SessionData> data;

        private Transaction() {
            assert sessionEventChangedOld.isEmpty(); // в транзакции никаких сессионных event'ов быть не может
//            assert applyModifier.getHintProps().isEmpty(); // равно как и хинт'ов, не факт, потому как транзакция не сразу создается

            add = new HashSet<>(DataSession.this.add);
            remove = new HashSet<>(DataSession.this.remove);
            usedOldClases = new HashSet<>(DataSession.this.usedOldClasses);
            usedNewClases = new HashSet<>(DataSession.this.usedNewClasses);
            singleAdd = new HashMap<>(DataSession.this.singleAdd);
            singleRemove = new HashMap<>(DataSession.this.singleRemove);
            newClasses = new HashMap<>(DataSession.this.newClasses);

            data = SessionTableUsage.saveData(DataSession.this.data);
            if(DataSession.this.news!=null)
                news = DataSession.this.news.saveData();
            else
                news = null;
        }
        
        private void rollData() throws SQLException {
            Map<DataProperty, PropertyChangeTableUsage<ClassPropertyInterface>> rollData = MapFact.mAddRemoveMap();
            for(int i=0,size=data.size();i<size;i++) {
                DataProperty prop = data.getKey(i);

                PropertyChangeTableUsage<ClassPropertyInterface> table = DataSession.this.data.get(prop);
                OperationOwner owner = getOwner();
                if(table==null) {
                    table = prop.createChangeTable("rlldata");
                    table.drop(sql, owner);
                }

                table.rollData(sql, data.getValue(i), owner);
                rollData.put(prop, table);
            }
            DataSession.this.data = rollData;
        }

        private void rollNews() throws SQLException {
            if(news!=null) {
                OperationOwner owner = getOwner();
                if(DataSession.this.news==null) {
                    DataSession.this.news = new SingleKeyPropertyUsage("rllnews", ObjectType.instance, ObjectType.instance);
                    DataSession.this.news.drop(sql, owner);
                }
                DataSession.this.news.rollData(sql, news, owner);
            } else
                DataSession.this.news = null;
        }

        private void rollback() throws SQLException, SQLHandledException {
            ServerLoggers.assertLog(sessionEventChangedOld.isEmpty(), "SESSION EVENTS NOT EMPTY"); // в транзакции никаких сессионных event'ов быть не может
            ServerLoggers.assertLog(applyModifier.getHintProps().isEmpty(), "APPLY HINTS NOT EMPTY"); // равно как и хинт'ов

            dropTables(SetFact.<SessionDataProperty>EMPTY()); // старые вернем, таблицу удалятся (но если нужны будут, rollback откатит эти изменения)

            // assert что новые включают старые
            DataSession.this.add = add;
            DataSession.this.remove = remove;
            DataSession.this.usedOldClasses = usedOldClases;
            DataSession.this.usedNewClasses = usedNewClases;
            DataSession.this.singleAdd = singleAdd;
            DataSession.this.singleRemove = singleRemove;
            DataSession.this.newClasses = newClasses;

            rollData();
            rollNews();
            
            dataModifier.eventDataChanges(getChangedProps(fromJavaSet(add), fromJavaSet(remove), fromJavaSet(usedOldClasses), fromJavaSet(usedNewClasses), data.keys()));
        }
    }
    private Transaction applyTransaction; // restore point
    private boolean isInTransaction;

    private void startTransaction(BusinessLogics<?> BL, Map<String, Integer> attemptCountMap, boolean deadLockPriority, long applyStartTime) throws SQLException, SQLHandledException {
        sql.startTransaction(DBManager.getCurrentTIL(), getOwner(), attemptCountMap, deadLockPriority, applyStartTime);
        ServerLoggers.assertLog(!isInSessionEvent(), "CANNOT START TRANSACTION IN SESSION EVENT");
        isInTransaction = true;
        if(applyFilter == ApplyFilter.ONLY_DATA)
            onlyDataModifier = new OverrideSessionModifier("onlydata", new IncrementChangeProps(BL.getDataChangeEvents()), applyModifier);
    }
    
    private void cleanOnlyDataModifier() throws SQLException {
        if(onlyDataModifier != null) {
            assert applyFilter == ApplyFilter.ONLY_DATA;
            onlyDataModifier.clean(sql, getOwner());
            onlyDataModifier = null;
        }
    }
    
    private void checkTransaction() {
        if(isInTransaction() && applyTransaction==null)
            applyTransaction = new Transaction();
    }
    public void rollbackTransaction() throws SQLException, SQLHandledException {
        try {
            for (Runnable info : rollbackInfo)
                info.run();
        } catch (Throwable t) {
            ServerLoggers.assertLog(false, "SHOULD NOT BE");
            throw ExceptionUtils.propagate(t, SQLException.class, SQLHandledException.class);
        } finally {
            try {
                if(applyTransaction!=null)
                    applyTransaction.rollback();
            } finally {
                try {
                    endTransaction();
                } catch (Throwable t) {
                    ServerLoggers.assertLog(false, "SHOULD NOT BE");
                    throw ExceptionUtils.propagate(t, SQLException.class, SQLHandledException.class);
                } finally {
                    sql.rollbackTransaction(getOwner());
                }
            }
        }
//        checkSessionTableMap();
    }

    private void endTransaction() throws SQLException {
        applyTransaction = null;
        isInTransaction = false;
        rollbackInfo.clear();
        cleanOnlyDataModifier();
    }
/*    private void checkSessionTableMap() {
        checkSessionTableMap(add);
        checkSessionTableMap(remove);
        checkSessionTableMap(data);
        checkSessionTableMap(news);
    }
    private void checkSessionTableMap(Map<?, ? extends SessionTableUsage> usages) {
        for(SessionTableUsage usage : usages.values())
            checkSessionTableMap(usage);
    }
    private void checkSessionTableMap(SessionTableUsage usage) {
        if(usage!=null && usage.table instanceof SessionDataTable)
            sql.checkSessionTableMap(((SessionDataTable)usage.table).getTable(), usage);
    }*/
    

    private void commitTransaction() throws SQLException {
        endTransaction();
        lastAttemptCountMap = keepLastAttemptCountMap ? sql.getAttemptCountMap() : null;
        sql.commitTransaction(getOwner());
    }

    private ImSet<CalcProperty<ClassPropertyInterface>> getClassChanges(ImSet<CustomClass> addClasses, ImSet<CustomClass> removeClasses, ImSet<ConcreteObjectClass> oldClasses, ImSet<ConcreteObjectClass> newClasses) {
        return SetFact.addExcl(CustomClass.getProperties(addClasses, removeClasses, oldClasses, newClasses), baseClass.getObjectClassProperty());
    }

    public boolean hasChanges() {
        return news != null || !data.isEmpty();
    }

    public boolean isStoredDataChanged() {
        return isStoredDataChanged;
    }

    private boolean hasStoredChanges() {
        if (news != null)
            return true;

        for (DataProperty property : data.keySet())
            if (property.isStored())
                return true;

        return false;
    }

    public String getStoredChanges() {
        String result = "";
        if (news != null)
            result += " " + news;

        for (DataProperty property : data.keySet())
            if (property.isStored())
                result += " " + property;

        return result;
    }

    private PropertyChange<ClassPropertyInterface> getObjectClassChange(ObjectClassProperty property) {
        if(news!=null)
            return SingleKeyPropertyUsage.getChange(news, property.interfaces.single());
        return null;
    }

    private boolean containsClassDataChange(ClassDataProperty property) {
        for(ConcreteCustomClass child : property.set.getSetConcreteChildren())
            if(usedOldClasses.contains(child) || usedNewClasses.contains(child))
                return true;
        return false;
    }
    private PropertyChange<ClassPropertyInterface> getClassDataChange(ClassDataProperty property) {
        if(news!=null && containsClassDataChange(property)) {
            ImRevMap<ClassPropertyInterface, KeyExpr> mapKeys = property.getMapKeys();
            KeyExpr keyExpr = mapKeys.singleValue();

            Join<String> join = news.join(keyExpr);
            Expr newClassExpr = join.getExpr("value");
            Where where = join.getWhere();

            Where newClass = isValueClass(newClassExpr, property.set, usedNewClasses);
/*            Where oldClass = keyExpr.isClass(property.set); // в общем то оптимизация (чтобы например не делать usedOldClasses)
            where = where.and(oldClass.or(newClass));*/

            return new PropertyChange<>(mapKeys, // на не null меняем только тех кто подходит по классу
                    newClassExpr.and(newClass), where);
        }
        return null;
    }

    private PropertyChange<ClassPropertyInterface> getClassChange(IsClassProperty property) { // важно чтобы совпадало с инкрементальным алгритмом в DataSession.aspectChangeClass
        ValueClass isClass = property.getInterfaceClass();
        if(isClass instanceof CustomClass) {
            CustomClass customClass = (CustomClass) isClass;
            boolean added = add.contains(customClass);
            boolean removed = remove.contains(customClass);
            if(added || removed) { // оптимизация в том числе
                ImRevMap<ClassPropertyInterface, KeyExpr> mapKeys = property.getMapKeys();
                KeyExpr key = mapKeys.singleValue();

                Join<String> join = news.join(key);
                Expr newClassExpr = join.getExpr("value");

                Where hasClass = isValueClass(newClassExpr, customClass, usedNewClasses);
                Where hadClass = key.isUpClass(isClass);

                Where changedWhere = Where.FALSE;
                Expr changeExpr;
                if(added) {
                    Where addWhere;
                    DataObject dataObject = singleAdd.get(customClass);
                    if(dataObject!=null) // оптимизация
                        addWhere = key.compare(dataObject, Compare.EQUALS);
                    else
                        addWhere = hasClass.and(hadClass.not());
                    changeExpr = ValueExpr.get(addWhere);
                    changedWhere = changedWhere.or(addWhere);
                } else
                    changeExpr = Expr.NULL;

                if(removed) {
                    Where removeWhere;
                    DataObject dataObject = singleRemove.get(customClass);
                    if(dataObject!=null)
                        removeWhere = key.compare(dataObject, Compare.EQUALS);
                    else
                        removeWhere = hadClass.and(join.getWhere().and(hasClass.not())); 
                    changedWhere = changedWhere.or(removeWhere); // был класс и изменился, но не на новый
                }
                return new PropertyChange<>(mapKeys, changeExpr, changedWhere);
            }
        }
        return null;
    }

    public PropertyChange<ClassPropertyInterface> getDataChange(DataProperty property) {
        PropertyChangeTableUsage<ClassPropertyInterface> dataChange = data.get(property);
        if(dataChange!=null)
            return PropertyChangeTableUsage.getChange(dataChange);
        return null;
    }

    public final SQLSession sql;
    public final SQLSession idSession;
    
    @Override
    protected void onClose(Object o) throws SQLException {
        assert o == null;
        
//        SQLSession.fifo.add("DC " + getOwner() + SQLSession.getCurrentTimeStamp() + " " + this + '\n' + ExceptionUtils.getStackTrace());
        try {
            dropTables(SetFact.<SessionDataProperty>EMPTY());
            sessionEventChangedOld.clear(sql, getOwner());

            sessionEventNotChangedOld.clear();
        } catch (SQLHandledException e) {
            throw Throwables.propagate(e);
        }

        updateNotChangedOld.clear();
    }

    public static class UpdateChanges {

        public ImSet<CalcProperty> properties;

        public UpdateChanges() {
            properties = SetFact.EMPTY();
        }

        public UpdateChanges(ImSet<CalcProperty> properties) {
            this.properties = properties;
        }

        public void add(ImSet<? extends CalcProperty> set) {
            properties = properties.merge(set);
        }
        public void add(UpdateChanges changes) {
            add(changes.properties);
        }
    }
    
    // assert использования всех 3-х нижних map внутри updateLock и внутри этих lock'ов нет других lock'ов иначе легко будет dead получить
    // потом можно будет в отдельную структуру со всеми synchronized методами выделить 
    private final Object updateLock = new Object(); 
    
    // формы, для которых с момента последнего update уже был restart, соотвественно в значениях - изменения от посл. update (prev) до посл. apply
    public WeakIdentityHashMap<FormInstance, UpdateChanges> appliedChanges = new WeakIdentityHashMap<>();

    // формы для которых с момента последнего update не было restart, соответственно в значениях - изменения от посл. update (prev) до посл. изменения
    public WeakIdentityHashMap<FormInstance, UpdateChanges> incrementChanges = new WeakIdentityHashMap<>();

    // assert что те же формы что и в increment, соответственно в значениях - изменения от посл. apply до посл. update (prev)
    public WeakIdentityHashMap<FormInstance, UpdateChanges> updateChanges = new WeakIdentityHashMap<>();

    public final BaseClass baseClass;
    public final ConcreteCustomClass sessionClass;
    public final LCP<?> currentSession;

    // для отладки
    public static boolean reCalculateAggr = false;

    private final IsServerRestartingController isServerRestarting;
    public final TimeoutController timeout;
    public final ComputerController computer;
    public final FormController form;
    public final ConnectionController connection;
    public final UserController user;
    public final ChangesController changes;
    public final LocaleController locale;

    public String prevFormCanonicalName = null;

    public DataObject applyObject = null;
    
    private final ImOrderMap<ActionProperty, SessionEnvEvent> sessionEvents;

    private ImOrderSet<ActionProperty> activeSessionEvents;
    @ManualLazy
    private ImOrderSet<ActionProperty> getActiveSessionEvents() {
        if(activeSessionEvents == null)
            activeSessionEvents = filterOrderEnv(sessionEvents);
        return activeSessionEvents;
    }
    public void dropActiveSessionEventsCaches() {
        activeSessionEvents = null;
    }

    private ImSet<OldProperty> sessionEventOldDepends;
    @ManualLazy
    private ImSet<OldProperty> getSessionEventOldDepends() { // assert что OldProperty, при этом у которых Scope соответствующий локальному событию
        if(sessionEventOldDepends==null) {
            MSet<OldProperty> mResult = SetFact.mSet();
            for(ActionProperty<?> action : getActiveSessionEvents())
                mResult.addAll(action.getSessionEventOldDepends());
            sessionEventOldDepends = mResult.immutable();
        }
        return sessionEventOldDepends;
    }

    public DataSession(SQLSession sql, final UserController user, final ComputerController computer, final FormController form, final ConnectionController connection,
                       TimeoutController timeout, ChangesController changes, LocaleController locale, IsServerRestartingController isServerRestarting, BaseClass baseClass,
                       ConcreteCustomClass sessionClass, LCP currentSession, SQLSession idSession, ImOrderMap<ActionProperty, SessionEnvEvent> sessionEvents,
                       OperationOwner upOwner) throws SQLException {
        this.sql = sql;
        this.isServerRestarting = isServerRestarting;

        this.baseClass = baseClass;
        this.sessionClass = sessionClass;
        this.currentSession = currentSession;

        this.user = user;
        this.computer = computer;
        this.form = form;
        this.connection = connection;
        this.timeout = timeout;
        this.changes = changes;
        this.locale = locale;

        this.sessionEvents = sessionEvents;

        this.idSession = idSession;
        
        if(upOwner == null)
            upOwner = new OperationOwner() {}; 
        this.owner = upOwner;

        if(Settings.get().isIsClustered())
            registerClassRemove = NOREGISTER;
        else
            registerClassRemove = new RegisterClassRemove() {

                private long lastChecked;

                @Override
                public void removed(ImSet<ValueClass> classes, long timestamp) {
                    MapFact.addJavaAll(lastRemoved, classes.toMap(timestamp));
                }

                @Override
                public void checked(long timestamp) {
                    lastChecked = timestamp;
                }

                @Override
                public boolean removedAfterChecked(ValueClass checkClass, long timestamp) {
                    Long lastClassRemoved = lastRemoved.get(checkClass);
                    if (lastClassRemoved == null)
                        return false;
                    return lastClassRemoved >= lastChecked;
                }
            };
        registerClassRemove.checked(getTimestamp());

        registerThreadStack(); // создающий поток также является владельцем сессии
        createdInTransaction = sql.isInTransaction(); // при synchronizeDB есть такой странный кейс
//        SQLSession.fifo.add("DCR " + getOwner() + SQLSession.getCurrentTimeStamp() + " " + this + '\n' + ExceptionUtils.getStackTrace());
    }
    
    private final static RegisterClassRemove NOREGISTER = new RegisterClassRemove() {
            public void removed(ImSet<ValueClass> classes, long timestamp) {
            }
            public void checked(long timestamp) {
            }
            public boolean removedAfterChecked(ValueClass checkClass, long timestamp) {
                return true;
            }
        };

    private boolean createdInTransaction;

    public DataSession createSession() throws SQLException {
        return createSession(sql);
    }
    public DataSession createSession(SQLSession sql) throws SQLException {
        return new DataSession(sql, user, computer, form, connection, timeout, changes, locale, isServerRestarting, baseClass, sessionClass, currentSession, idSession, sessionEvents, null);
    }

    // по хорошему надо было в класс оформить чтоб избежать ошибок, но абстракция получится слишком дырявой
    public static FunctionSet<SessionDataProperty> adjustKeep(final boolean manageSession, final FunctionSet<SessionDataProperty> operationKeep) {
        return new SFunctionSet<SessionDataProperty>() {
            public boolean contains(SessionDataProperty element) {
                if (element.nestedType != null) {
                    if (element.nestedType == LocalNestedType.ALL) 
                        return true;
                    // MANAGESESSION, NOMANAGESESSION
                    if ((element.nestedType == LocalNestedType.MANAGESESSION) == manageSession) 
                        return true;
                    if(operationKeep == DataSession.keepAllSessionProperties) // нужно чтобы sessionOwners не nest'ся, хак, потом надо будет как-то хитрее сделать 
                        return false;
                }
                return operationKeep.contains(element);
            }
        };
    }

    private final static FunctionSet<SessionDataProperty> NONESTING = new SFunctionSet<SessionDataProperty>() {
        public boolean contains(SessionDataProperty element) {
            return element.noNestingInNestedSession;
        }
    };

    public void restart(boolean cancel, FunctionSet<SessionDataProperty> keep) throws SQLException, SQLHandledException {

        // apply
        //      по кому был restart : добавляем changes -> applied
        //      по кому не было restart : to -> applied (помечая что был restart)

        // cancel
        //    по кому не было restart :  from -> в applied (помечая что был restart)

        ImSet<CalcProperty> changedProps = null;
        if(!cancel) {
            changedProps = getChangedProps();
            changes.regChange(changedProps, this);
        }

        synchronized (updateLock) {
            if (changedProps != null)
                for (Pair<FormInstance, UpdateChanges> appliedChange : appliedChanges.entryIt())
                    appliedChange.second.add(new UpdateChanges(changedProps));
            assert appliedChanges.disjointKeys(cancel ? updateChanges : incrementChanges);
            appliedChanges.putAll(cancel ? updateChanges : incrementChanges);
            incrementChanges = new WeakIdentityHashMap<>();
            updateChanges = new WeakIdentityHashMap<>();
        }

        dropTables(keep);
        add.clear();
        remove.clear();
        usedOldClasses.clear();
        usedNewClasses.clear();
        singleAdd.clear();
        singleRemove.clear();
        newClasses.clear();

        clearNotSessionData(keep);
        news = null;
        isStoredDataChanged = false;

        assert dataModifier.getHintProps().isEmpty(); // hint'ы все должны также уйти

        if(cancel) {
            sessionEventChangedOld.clear(sql, getOwner());
        } else
            assert sessionEventChangedOld.isEmpty();
        sessionEventNotChangedOld.clear();
        updateNotChangedOld.clear();

        applyObject = null; // сбрасываем в том числе когда cancel потому как cancel drop'ает в том числе и добавление объекта

        registerChange(null);
    }

    public DataObject addObject() throws SQLException {
        return new DataObject(IDTable.instance.generateID(idSession, IDTable.OBJECT),baseClass.unknown);
    }
    
    public <P extends PropertyInterface> DataObject addObjectAutoSet(ConcreteCustomClass customClass, DataObject object, BusinessLogics BL, CustomClassListener classListener) throws SQLException, SQLHandledException {
        DataObject dataObject = addObject(customClass, object);
        BL.resolveAutoSet(this, customClass, dataObject, classListener);
        return dataObject;
    }

    public DataObject addObject(ConcreteCustomClass customClass) throws SQLException, SQLHandledException {
        return addObject(customClass, null);
    }

    // с fill'ами addObject'ы
    public DataObject addObject(ConcreteCustomClass customClass, DataObject object) throws SQLException, SQLHandledException {
        if(object==null)
            object = addObject();

        // запишем объекты, которые надо будет сохранять
        changeClass(object, customClass);

        return object;
    }

    private static Pair<Long, Long>[] toZeroBased(Pair<Long, Long>[] shifts) {
        Pair<Long, Long>[] result = new Pair[shifts.length];
        for(int i=0;i<shifts.length;i++)
            result[i] = new Pair<>(shifts[i].first - 1, shifts[i].second);
        return result;
    }

    public <T extends PropertyInterface> SinglePropertyTableUsage<T> addObjects(String debugInfo, ConcreteCustomClass cls, PropertyOrderSet<T> set) throws SQLException, SQLHandledException {
        SinglePropertyTableUsage<T> table;

        SessionTableUsage<?, ?> matSetTable = null;
        if(set.needMaterialize()) {
            Pair<PropertyOrderSet<T>, SessionTableUsage> materialize = set.materialize(debugInfo, this);
            set = materialize.first;
            matSetTable = materialize.second;
        }

        try {
            final Query<T, String> query = set.getAddQuery(baseClass); // query, который генерит номера записей (one-based)
            // сначала закидываем в таблицу set с номерами рядов (!!! нужно гарантировать однозначность)
            table = new SinglePropertyTableUsage<>(debugInfo, query.getMapKeys().keys().toOrderSet(), new Type.Getter<T>() {
                public Type getType(T key) {
                    return query.getKeyType(key);
                }
            }, ObjectType.instance);
            table.modifyRows(sql, query, baseClass, Modify.ADD, env, SessionTable.matLocalQuery);
        } finally {
            if(matSetTable != null)
                matSetTable.drop(sql, getOwner());
        }

        if(table.isEmpty()) // оптимизация, не зачем генерить id и все такое
            return table;

        OperationOwner owner = getOwner();
        try {
            // берем количество рядов - резервируем ID'ки
            Pair<Long, Long>[] startFrom = IDTable.instance.generateIDs(table.getCount(), idSession, IDTable.OBJECT);
    
            // update'им на эту разницу ключи, чтобы сгенерить объекты
            table.updateAdded(sql, baseClass, toZeroBased(startFrom), owner); // так как не zero-based отнимаем 1
    
            // вообще избыточно, если compile'ить отдельно в for() + changeClass, который сам сгруппирует, но тогда currentClass будет unknown в свойстве что вообщем то не возможно
            KeyExpr keyExpr = new KeyExpr("keyExpr");
            changeClass(new ClassChange(keyExpr, GroupExpr.create(MapFact.singleton("key", table.join(table.getMapKeys()).getExpr("value")),
                    Where.TRUE, MapFact.singleton("key", keyExpr)).getWhere(), cls));
        } catch(Throwable t) {
            table.drop(sql, owner);
            throw ExceptionUtils.propagate(t, SQLException.class, SQLHandledException.class);
        }
            
        // возвращаем таблицу
        return table;
    }

    public void changeClass(PropertyObjectInterfaceInstance objectInstance, DataObject dataObject, ConcreteObjectClass cls) throws SQLException, SQLHandledException {
        changeClass(dataObject, cls);
    }

    public void changeClass(DataObject change, ConcreteObjectClass toClass) throws SQLException, SQLHandledException {
        if(toClass==null) toClass = baseClass.unknown;

        changeClass(new ClassChange(change, toClass));
    }

    public <K extends PropertyInterface> void updateCurrentClasses(Collection<PropertyChangeTableUsage<K>> tables) throws SQLException, SQLHandledException {
        for(PropertyChangeTableUsage<K> table : tables)
            table.updateCurrentClasses(this);
    }

    public <K, T extends ObjectValue> ImMap<K, T> updateCurrentClasses(ImMap<K, T> objectValues) throws SQLException, SQLHandledException {
        ImValueMap<K, T> mvResult = objectValues.mapItValues(); // exception кидает
        for(int i=0,size=objectValues.size();i<size;i++)
            mvResult.mapValue(i, (T) updateCurrentClass(objectValues.getValue(i)));
        return mvResult.immutableValue();
    }

    public ObjectValue updateCurrentClass(ObjectValue value) throws SQLException, SQLHandledException {
        if(value instanceof NullValue)
            return value;
        else {
            DataObject dataObject = (DataObject)value;
            return new DataObject(dataObject.object, getCurrentClass(dataObject));
        }
    }

    public <K> ImOrderSet<ImMap<K, ConcreteObjectClass>> readDiffClasses(Where where, ImMap<K, ? extends Expr> classExprs, ImMap<K, ? extends Expr> objectExprs) throws SQLException, SQLHandledException {

        final ValueExpr unknownExpr = new ValueExpr(-1L, baseClass.unknown);

        ImRevMap<K,KeyExpr> keys = KeyExpr.getMapKeys(classExprs.keys().addExcl(objectExprs.keys()));
        ImMap<K, Expr> group = ((ImMap<K, Expr>)classExprs).mapValues(new GetValue<Expr, Expr>() {
                    public Expr getMapValue(Expr value) {
                        return value.nvl(unknownExpr);
            }}).addExcl(((ImMap<K, Expr>)objectExprs).mapValuesEx(new GetExValue<Expr, Expr, SQLException, SQLHandledException>() {
            public Expr getMapValue(Expr value) throws SQLException, SQLHandledException {
                return baseClass.getObjectClassProperty().getExpr(value, getModifier()).nvl(unknownExpr);
            }
        }));

        return new Query<K, String>(keys, GroupExpr.create(group, where, keys).getWhere()).execute(this).keyOrderSet().mapMergeOrderSetValues(new GetValue<ImMap<K, ConcreteObjectClass>, ImMap<K, Object>>() {
            public ImMap<K, ConcreteObjectClass> getMapValue(ImMap<K, Object> readClasses) {
                return readClasses.mapValues(new GetValue<ConcreteObjectClass, Object>() {
                    public ConcreteObjectClass getMapValue(Object id) {
                        return baseClass.findConcreteClassID((Long) id, -1);
                    }
                });
            }
        });
    }

    public void changeClass(ClassChange change) throws SQLException, SQLHandledException {
        if(change.isEmpty()) // оптимизация, важна так как во многих event'ах может участвовать
            return;
        
        SingleKeyPropertyUsage changeTable = null;
        FunctionSet<CalcProperty<ClassPropertyInterface>> updateSourceChanges;
        ImSet<CustomClass> addClasses; ImSet<CustomClass> removeClasses;
        ImSet<ConcreteObjectClass> changedOldClasses; ImSet<ConcreteObjectClass> changedNewClasses;
        ImSet<CalcProperty<ClassPropertyInterface>> updateChanges;

        try {
            MSet<CustomClass> mAddClasses = SetFact.mSet(); MSet<CustomClass> mRemoveClasses = SetFact.mSet();
            MSet<ConcreteObjectClass> mChangeOldClasses = SetFact.mSet(); MSet<ConcreteObjectClass> mChangeNewClasses = SetFact.mSet();
            if(change.keyValue !=null) { // оптимизация
                ConcreteObjectClass prevcl = (ConcreteObjectClass) getCurrentClass(change.keyValue);
                ConcreteObjectClass newcl = baseClass.findConcreteClassID((Long) change.propValue.getValue());
                newcl.getDiffSet(prevcl, mAddClasses, mRemoveClasses);
                mChangeOldClasses.add(prevcl);
                mChangeNewClasses.add(newcl);
            } else {
                if(change.needMaterialize()) {
                    changeTable = change.materialize("ccltable", sql, baseClass, env); // materialize'им изменение
                    change = changeTable.getChange();
                }
    
                if(change.isEmpty()) // оптимизация, важна так как во многих event'ах может участвовать
                    return;
    
                // читаем варианты изменения классов
                for(ImMap<String, ConcreteObjectClass> diffClasses : readDiffClasses(change.where, MapFact.singleton("newcl", change.expr), MapFact.singleton("prevcl", change.key))) {
                    ConcreteObjectClass newcl = diffClasses.get("newcl");
                    ConcreteObjectClass prevcl = diffClasses.get("prevcl");
                    newcl.getDiffSet(prevcl, mAddClasses, mRemoveClasses);
                    mChangeOldClasses.add(prevcl);
                    mChangeNewClasses.add(newcl);
                }
            }
            addClasses = mAddClasses.immutable(); removeClasses = mRemoveClasses.immutable();
            changedOldClasses = mChangeOldClasses.immutable(); changedNewClasses = mChangeNewClasses.immutable();

            delegateToDebugger(addClasses, removeClasses);

            updateChanges = getClassChanges(addClasses, removeClasses, changedOldClasses, changedNewClasses);

            if(needSessionEventMaterialize(changeTable, updateChanges)) {
                changeTable = change.materialize("cclsevtable", sql, baseClass, env); // materialize'им изменение
                change = changeTable.getChange();
            }

            updateSessionEvents(updateChanges);

            updateSourceChanges = aspectChangeClass(addClasses, removeClasses, changedOldClasses, changedNewClasses, change, changeTable);
        } finally {
            if(changeTable!=null)
                changeTable.drop(sql, getOwner());
        }

        if(updateSourceChanges != null) {
            if(updateSourceChanges.isFull()) { // не очень красиво, но иначе придется дополнительные параметры заводить
                ImSet<CalcProperty<ClassPropertyInterface>> allClasses = CustomClass.getProperties( // так как таблица news используется при определении изменений всех классов, то нужно обновить и их "источники" (если изменились)
                        fromJavaSet(add).remove(addClasses), fromJavaSet(remove).remove(removeClasses),
                        fromJavaSet(usedOldClasses).remove(changedOldClasses), fromJavaSet(usedNewClasses).remove(changedNewClasses));
                dataModifier.eventSourceChanges(allClasses);
            }

            updateProperties(updateChanges, updateSourceChanges);

            aspectAfterChange();
        }
    }

    public void delegateToDebugger(ImSet<CustomClass> addClasses, ImSet<CustomClass> removeClasses) throws SQLException, SQLHandledException {
        ActionPropertyDebugger debugger = ActionPropertyDebugger.getInstance();
        if (debugger.isEnabled()) {
            for (CustomClass addClass : addClasses) {
                ClassDebugInfo debugInfo = addClass.getDebugInfo();
                if (debugInfo != null) {
                    debugger.delegate(debugInfo);
                }
            }
            for (CustomClass removeClass : removeClasses) {
                ClassDebugInfo debugInfo = removeClass.getDebugInfo();
                if (debugInfo != null) {
                    debugger.delegate(debugInfo);
                }
            }
        }
    }

    public void dropChanges(DataProperty property) throws SQLException, SQLHandledException {
        if(!data.containsKey(property)) // оптимизация, см. использование
            return;

        updateSessionEvents(SetFact.singleton(property));

        aspectDropChanges(property);

        updateProperties(SetFact.singleton(property), true); // уже соптимизировано выше
    }

    public void dropAllDataChanges(FunctionSet<SessionDataProperty> keepProps) throws SQLException, SQLHandledException {
        if (!data.isEmpty()) {
            Map<DataProperty, PropertyChangeTableUsage<ClassPropertyInterface>> dropProps = filterNotSessionData(keepProps);
            ImSet<DataProperty> dataChanges = fromJavaSet(dropProps.keySet());
            
            updateSessionEvents(dataChanges);

            aspectDropAllChanges(keepProps, dropProps);

            updateProperties(dataChanges, true); // уже соптимизировано выше
        }
    }

    private void aspectDropAllChanges(FunctionSet<SessionDataProperty> keepProps, Map<DataProperty, PropertyChangeTableUsage<ClassPropertyInterface>> dropProps) throws SQLException {
        Map<DataProperty, PropertyChangeTableUsage<ClassPropertyInterface>> newData = new HashMap<DataProperty, PropertyChangeTableUsage<ClassPropertyInterface>>(filterSessionData(keepProps));
        for (Map.Entry<DataProperty, PropertyChangeTableUsage<ClassPropertyInterface>> e : dropProps.entrySet()) {
            e.getValue().drop(sql, getOwner());
        }
        data = newData;
    }

    public void dropClassChanges() throws SQLException, SQLHandledException {
        if (news != null) {
            ImSet<CalcProperty<ClassPropertyInterface>> classProps =
                    getClassChanges(fromJavaSet(add), fromJavaSet(remove), fromJavaSet(usedOldClasses), fromJavaSet(usedNewClasses));

            updateSessionEvents(classProps);

            aspectDropClassChanges();

            updateProperties(classProps, true);
        }
    }

    public void aspectDropClassChanges() throws SQLException {
        if (news != null) {
            news.drop(sql, getOwner());
            news = null;
            add.clear();
            remove.clear();
            usedOldClasses.clear();
            usedNewClasses.clear();
            singleAdd.clear();
            singleRemove.clear();
            newClasses.clear();
        }
    }

    public void changeProperty(DataProperty property, PropertyChange<ClassPropertyInterface> change) throws SQLException, SQLHandledException {
        if (property.getDebugInfo() != null && property.getDebugInfo().needToCreateDelegate()) {
            ActionPropertyDebugger.getInstance().delegate(this, property, change);
        } else {
            changePropertyImpl(property, change);
        } 
    }

    public void changePropertyImpl(DataProperty property, PropertyChange<ClassPropertyInterface> change) throws SQLException, SQLHandledException {
        PropertyChangeTableUsage<ClassPropertyInterface> changeTable = null;

        ImSet<DataProperty> updateChanges;
        ModifyResult changed;
        try {
            updateChanges = SetFact.singleton(property);

            if(neededProps!=null && property.isStored() && property.event==null) { // если транзакция, нет change event'а, singleApply'им
                assert isInTransaction();
    
                changeTable = splitApplySingleStored("cpimpl", property, property.readFixChangeTable("cpimpl", sql, change, baseClass, getQueryEnv()), ThreadLocalContext.getBusinessLogics());
                change = PropertyChangeTableUsage.getChange(changeTable);
            } else {
                if(change.needMaterialize(data.get(property)) || needSessionEventMaterialize(changeTable, updateChanges)) { // для защиты (неполной) от случаев f(a) <- f(a) + 1
                    changeTable = change.materialize("cpneedmimpl", property, sql, baseClass, getQueryEnv());
                    change = PropertyChangeTableUsage.getChange(changeTable);
                }
    
                if(change.isEmpty()) // оптимизация по аналогии с changeClass
                    return;
            }

            updateSessionEvents(updateChanges);
    
            changed = aspectChangeProperty(property, change);
        } finally {
            if(changeTable!=null)
                changeTable.drop(sql, getOwner());
        }

        if(changed.dataChanged()) {
            updateProperties(updateChanges, changed.sourceChanged());

            aspectAfterChange();
        }
    }

    private void aspectAfterChange() throws SQLException, SQLHandledException {
        if (!isStoredDataChanged && hasStoredChanges()) {
            setIsDataChangedProperty();
        }
    }

    private void setIsDataChangedProperty() throws SQLException, SQLHandledException {
        ImSet<SessionDataProperty> singleProp = SetFact.singleton(isDataChanged);

        updateSessionEvents(singleProp);

        ModifyResult changed = aspectChangeProperty(isDataChanged, new PropertyChange<ClassPropertyInterface>(new DataObject(true, LogicalClass.instance)));
        if (changed.dataChanged()) {
            updateProperties(singleProp, changed.sourceChanged());
        }

        isStoredDataChanged = true;
    }

    private void cleanIsDataChangedProperty() throws SQLException, SQLHandledException {
        dropChanges(isDataChanged);
        isStoredDataChanged = false;
    }

    public void updateProperties(ImSet<? extends CalcProperty> changes, boolean sourceChanged) throws SQLException, SQLHandledException {
        updateProperties(changes, sourceChanged ? FullFunctionSet.<CalcProperty>instance() : SetFact.<CalcProperty>EMPTY());
    }

    private final ThreadLocal<Boolean> changedFlag = new ThreadLocal<>();
    private void registerChange(ImSet<? extends CalcProperty> changes) { // debug метод
        if(changedFlag.get() != null) {
            ServerLoggers.assertLog(changes != null && ((ImSet<CalcProperty>)changes).filterFn(new SFunctionSet<CalcProperty>() {
                public boolean contains(CalcProperty element) {
                    return element.isLocal();
                }
            }).size() == changes.size(), "SHOULD NOT BE");
        }
    }

    public void updateProperties(ImSet<? extends CalcProperty> changes, FunctionSet<? extends CalcProperty> sourceChanges) throws SQLException, SQLHandledException {
        registerChange(changes);

        dataModifier.eventDataChanges(changes, sourceChanges);

        synchronized (updateLock) {
            for (Pair<FormInstance, UpdateChanges> incrementChange : incrementChanges.entryIt()) {
                incrementChange.second.add(changes);
            }
        }

        for (FormInstance form : getAllActiveForms()) {
            form.dataChanged = true;
        }
    }

    // для OldProperty хранит изменения с предыдущего execute'а
    private IncrementTableProps sessionEventChangedOld = new IncrementTableProps(); // assert что OldProperty, при этом у которых Scope соответствующий локальному событию
    private IncrementChangeProps sessionEventNotChangedOld = new IncrementChangeProps(); // assert что OldProperty, при этом у которых Scope соответствующий локальному событию
    private Map<OldProperty, Boolean> updateNotChangedOld = new HashMap<>(); // для того чтобы не заботиться об изменениях между локальными событиями

    // потом можно было бы оптимизировать создание OverrideSessionModifier'а (в рамках getPropertyChanges) и тогда можно создавать modifier'ы непосредственно при запуске
    private boolean inSessionEvent;
    private OverrideSessionModifier sessionEventModifier = new OverridePropSourceSessionModifier<OldProperty>("sessionEvent", sessionEventChangedOld, sessionEventNotChangedOld, false, dataModifier) {
        protected ImSet<CalcProperty> getSourceProperties(OldProperty old) {
            return SetFact.singleton(old.property);
        }
        protected void updateSource(OldProperty property, boolean dataChanged, boolean forceUpdate) throws SQLException, SQLHandledException {
            ServerLoggers.assertLog(forceUpdate || isInSessionEvent(), "UPDATING SOURCE SHOULD BE IN SESSION EVENT"); // так как идет в getPropertyChanges
            updateSessionNotChangedEvents(property, dataChanged);
        }
    };

    public boolean needSessionEventMaterialize(SessionTableUsage tableUsage, ImSet<? extends CalcProperty> changes) {
        if(tableUsage == null && isInSessionEvent()) { // если таблица не материализована, и мы в сессионном событии, то есть может измениться sessionEventModifier и drop'уть таблицы, которые используются в изменении
            assert !isInTransaction();
            for(OldProperty<PropertyInterface> old : getSessionEventOldDepends())
                if (!sessionEventChangedOld.contains(old) && CalcProperty.depends(old.property, changes)) // если влияет на old из сессионного event'а и еще не читалось
                    return true;
        }
        return false;
    }
    public <P extends PropertyInterface> void updateSessionEvents(ImSet<? extends CalcProperty> changes) throws SQLException, SQLHandledException {
        if(!isInTransaction())
            for(OldProperty<PropertyInterface> old : getSessionEventOldDepends()) {
                if (!sessionEventChangedOld.contains(old) && CalcProperty.depends(old.property, changes)) // если влияет на old из сессионного event'а и еще не читалось
                    updateSessionEventChangedOld(old);
            }
    }
    public <P extends PropertyInterface> void updateSessionNotChangedEvents(ImSet<? extends CalcProperty> changes) throws SQLException, SQLHandledException {
        if(!isInTransaction())
            for(OldProperty<PropertyInterface> old : getSessionEventOldDepends()) {
                if (!sessionEventChangedOld.contains(old) && !sessionEventNotChangedOld.contains(old) && CalcProperty.depends(old.property, changes)) // если влияет на old из сессионного event'а и еще не читалось и не помечено как notChanged
                    updateSessionNotChangedEvents(old, false);
            }
    }

    // вообще если какое-то свойство попало в sessionEventNotChangedOld, а потом изменился источник одного из его зависимых свойств, то в следствие updateSessionEvents "обновленное" изменение попадет в sessionEventChangedOld и "перекроет" изменение в notChanged (по сути последнее никогда использоваться не будет)
    // но есть проблема при изменении источника news, которое в depends не попадает и верхний инвариант будет нарушен
    private void updateSessionNotChangedEvents(OldProperty<PropertyInterface> old, boolean dataChanged) throws SQLException, SQLHandledException {
        ServerLoggers.assertLog(!isInTransaction(), "UPDATE NOTCHANGED SHOULD NOT BE IN TRANSACTION");
        if (!isInSessionEvent()) { // помечаем на обновление
            Boolean prevDataChanged = updateNotChangedOld.put(old, dataChanged);
            if(prevDataChanged != null && prevDataChanged && !dataChanged)
                updateNotChangedOld.put(old, true);
        } else { // если уже локальное событие, придется обновлять источник не откладывая на потом
            assert updateNotChangedOld.isEmpty();
            updateSessionEventNotChangedOld(this, old, dataChanged);
        }
    }

    private void updateSessionEventChangedOld(OldProperty<PropertyInterface> old) throws SQLException, SQLHandledException {
        sessionEventChangedOld.add(old, old.property.readChangeTable("upsevco", sql, getModifier(), baseClass, getQueryEnv()));
    }

    private void updateSessionEventNotChangedOld(ExecutionEnvironment env) throws SQLException, SQLHandledException {
        // обновляем прямо перед началом локального события, чтобы не заботиться о clearHints и других изменениях между локальными событиями
        for (Map.Entry<OldProperty, Boolean> old : updateNotChangedOld.entrySet())
            updateSessionEventNotChangedOld(env, old.getKey(), old.getValue());
        updateNotChangedOld.clear();
    }

    private void updateSessionEventNotChangedOld(ExecutionEnvironment env, OldProperty<PropertyInterface> changedOld, boolean dataChanged) throws SQLException, SQLHandledException {
        sessionEventNotChangedOld.add(changedOld, changedOld.property.getIncrementChange(env.getModifier()), dataChanged);
    }

    public boolean isInSessionEvent() {
        return inSessionEvent;
    }


    public <T extends PropertyInterface> void executeSessionEvents(ExecutionEnvironment env, ExecutionStack stack) throws SQLException, SQLHandledException {
        if(isInTransaction())
            ServerLoggers.exInfoLogger.info("LOCAL EVENTS IN TRANSACTION"); // так как LogPropertyAction создает форму

        // по идее можно будет assertion вернуть когда рефакторятся constraint'ы на работу с FormEntity
        if(!isInTransaction() && sessionEventChangedOld.getProperties().size() > 0) { // если в транзакции подменится modifier, туда похоже в хинты могут попадать таблицы из apply (правда не совсем понятно как), и приводит к table does not exist, в любом случае это очень опасная вещь в транзакции, поэтому уберем, второе - оптимизационная проверка

            if(env == null)
                env = this;

            dataModifier.updateSourceChanges(); // нужно обновить все пометки (тут главное что у этого modifier'а, чтобы notify'уть все пометки)
            
            inSessionEvent = true;

            updateSessionEventNotChangedOld(env); // важно после по идее чтобы правильный modifier обновился, а то так абы кто обновится 
            
            try {
                for(ActionProperty<?> action : getActiveSessionEvents()) {
                    executeSessionEvent(env, stack, action);
                    if(!isInSessionEvent())
                        return;
                }
            } finally {
                inSessionEvent = false;
            }

            dropSessionEventChangedOld();
        }
    }

    private void dropSessionEventChangedOld() throws SQLException, SQLHandledException {
        // закидываем старые изменения
        for(CalcProperty changedOld : sessionEventChangedOld.getProperties()) // assert что только old'ы
            updateNotChangedOld.put((OldProperty)changedOld, true);
        sessionEventChangedOld.clear(sql, getOwner());
    }

    @LogTime
    @StackMessage("{message.local.event.exec}")
    @ThisMessage (profile = false)
    private void executeSessionEvent(ExecutionEnvironment env, ExecutionStack stack, @ParamMessage ActionProperty<?> action) throws SQLException, SQLHandledException {
        if(noEventsInTransaction || !sessionEventChangedOld.getProperties().intersect(action.getSessionEventOldDepends()))// оптимизация аналогичная верхней
            return;

        action.execute(env, stack);
    }

    private void executeGlobalEvent(ExecutionEnvironment env, ExecutionStack stack, ActionProperty<?> action) throws SQLException, SQLHandledException {
        if(noEventsInTransaction)
            return;
        boolean hasChanges = false;
        for(SessionCalcProperty property : action.getGlobalEventSessionCalcDepends()) { // оптимизация основанная на отсутствии последействия
            // тут конечно для PREV брать CHANGED, не совсем правильно, так как новое значение может не использоваться вообще в действии, и PREV используется не для определения условия события, а для непосредственно тела
            // поэтому по хорошему надо смотреть либо на getUsedProps или как-то хитрее определять условие. Аналогичную оптимизацию нужно было бы реализовать в applySingleStored
            if(property.getChangedProperty().hasChanges(getModifier())) {
                hasChanges = true;
                break;
            }
        }

        if(Settings.get().isDisableGlobalEventOptimization()) {
            if (!hasChanges)
                changedFlag.set(true);
            executeGlobalEventWithChanges(env, stack, action);
            if (!hasChanges)
                changedFlag.set(null);
        } else {
            if(hasChanges)
                executeGlobalEventWithChanges(env, stack, action);
        }
    }

    @LogTime
    @StackMessage("{message.global.event.exec}")
    @ThisMessage (profile = false)
    private void executeGlobalEventWithChanges(ExecutionEnvironment env, ExecutionStack stack, @ParamMessage ActionProperty<?> action) throws SQLException, SQLHandledException {
        action.execute(env, stack);
    }

    @LogTime
    @ThisMessage (profile = false)
    private void executeActionInTransaction(ExecutionEnvironment env, ExecutionStack stack, @ParamMessage ActionPropertyValueImplement<?> action) throws SQLException, SQLHandledException {
        action.execute(env, stack);
    }

    @StackProgress
    @Cancelable
    private boolean executeGlobalEvent(BusinessLogics BL, ExecutionStack stack, Object property, @StackProgress final ProgressBar progressBar) throws SQLException, SQLHandledException {
        if(property instanceof ActionProperty || property instanceof ActionPropertyValueImplement) {
            startPendingSingles(property instanceof ActionPropertyValueImplement ? ((ActionPropertyValueImplement) property).property : (ActionProperty) property);

            if(property instanceof ActionPropertyValueImplement)
                executeActionInTransaction(this, stack, (ActionPropertyValueImplement)property);
            else
                executeGlobalEvent(this, stack, (ActionProperty) property);

            if(!isInTransaction()) // если ушли из транзакции вываливаемся
                return false;

            flushPendingSingles(BL);
        } else // постоянно-хранимые свойства
            readStored((CalcProperty<PropertyInterface>) property, BL);

        return true;
    }

    private OverrideSessionModifier resolveModifier = null;

    public <T extends PropertyInterface> void resolve(ActionProperty<?> action, ExecutionStack stack) throws SQLException, SQLHandledException {
        IncrementChangeProps changes = new IncrementChangeProps();
        for(SessionCalcProperty sessionCalcProperty : action.getSessionCalcDepends(false))
            if(sessionCalcProperty instanceof ChangedProperty) {
                PropertyChange fullChange = ((ChangedProperty) sessionCalcProperty).getFullChange(getModifier());
                if(fullChange!=null)
                    changes.add(sessionCalcProperty, fullChange);
            }
        
        resolveModifier = new OverridePropSourceSessionModifier<CalcProperty>("resolve", changes, true, dataModifier) {
            // надо бы реализовать, но не понятно для чего
            @Override
            protected ImSet<CalcProperty> getSourceProperties(CalcProperty property) {
                return SetFact.EMPTY();
            }

            @Override
            protected void updateSource(CalcProperty property, boolean dataChanged, boolean forceUpdate) throws SQLException, SQLHandledException {
                assert false;
            }
        };
        try {
            action.execute(this, stack);
        } finally {
            resolveModifier.clean(sql, getOwner());
            resolveModifier = null;
        }
    }

    public static String checkClasses(final SQLSession sql, BaseClass baseClass) throws SQLException, SQLHandledException {
        return checkClasses(sql, null, baseClass);
    }

    public static String checkClasses(final SQLSession sql, final QueryEnvironment env, BaseClass baseClass) throws SQLException, SQLHandledException {

        final Result<String> incorrect = new Result<>();
        runExclusiveness(new RunExclusiveness() {
            public void run(Query<String, String> query) throws SQLException, SQLHandledException {
                incorrect.set(env == null ? query.readSelect(sql) : query.readSelect(sql, env));
            }
        }, sql, baseClass);

        if (!incorrect.result.isEmpty())
            return "---- Checking Classes Exclusiveness -----" + '\n' + incorrect.result;
        return "";
    }
    
    public interface RunExclusiveness {
        void run(Query<String, String> query) throws SQLException, SQLHandledException;
    }

    public static void runExclusiveness(RunExclusiveness run, SQLSession sql, BaseClass baseClass) throws SQLException, SQLHandledException {

        // тут можно было бы использовать нижнюю конструкцию, но с учетом того что не все базы поддерживают FULL JOIN, на UNION'ах и их LEFT JOIN'ах с проталкиванием, запросы получаются мегабайтные и СУБД не справляется
//        KeyExpr key = new KeyExpr("key");
//        String incorrect = new Query<String,String>(MapFact.singletonRev("key", key), key.classExpr(baseClass, IsClassType.SUMCONSISTENT).compare(ValueExpr.COUNT, Compare.GREATER)).readSelect(sql, env);

        // пока не вытягивает определение, для каких конкретно классов образовалось пересечение, ни сервер приложение ни СУБД
        final KeyExpr key = new KeyExpr("key");
        final int threshold = 30;
        final ImOrderSet<ObjectClassField> tables = baseClass.getUpObjectClassFields().keys().toOrderSet();

        final MLinearOperandMap mSum = new MLinearOperandMap();
        final MList<Expr> mAgg = ListFact.mList();
        final MAddCol<SingleKeyTableUsage<String>> usedTables = ListFact.mAddCol();
        for(ImSet<ObjectClassField> group : tables.getSet().group(new BaseUtils.Group<Integer, ObjectClassField>() {
            public Integer group(ObjectClassField key) {
                return tables.indexOf(key) % threshold;
            }}).values()) {
            SingleKeyTableUsage<String> table = new SingleKeyTableUsage<>("runexls", ObjectType.instance, SetFact.toOrderExclSet("sum", "agg"), new Type.Getter<String>() {
                public Type getType(String key) {
                    return key.equals("sum") ? ValueExpr.COUNTCLASS : StringClass.getv(false, ExtInt.UNLIMITED);
                }
            });
            Expr sumExpr = IsClassExpr.create(key, group, IsClassType.SUMCONSISTENT);
            Expr aggExpr = IsClassExpr.create(key, group, IsClassType.AGGCONSISTENT);
            table.writeRows(sql, new Query<>(MapFact.singletonRev("key", key), MapFact.toMap("sum", sumExpr, "agg", aggExpr), sumExpr.getWhere()), baseClass, DataSession.emptyEnv(OperationOwner.unknown), SessionTable.nonead);

            Join<String> tableJoin = table.join(key);
            mSum.add(tableJoin.getExpr("sum"), 1);
            mAgg.add(tableJoin.getExpr("agg"));

            usedTables.add(table);
        }

        // FormulaUnionExpr.create(new StringAggConcatenateFormulaImpl(","), mAgg.immutableList()) , "value",
        Expr sumExpr = mSum.getExpr();
        Expr aggExpr = FormulaUnionExpr.create(new StringAggConcatenateFormulaImpl(","), mAgg.immutableList());
        run.run(new Query<>(MapFact.singletonRev("key", key), sumExpr.compare(ValueExpr.COUNT, Compare.GREATER), MapFact.<String, DataObject>EMPTY(),
                MapFact.toMap("sum", sumExpr, "agg", aggExpr)));

        for(SingleKeyTableUsage<String> usedTable : usedTables.it())
            usedTable.drop(sql, OperationOwner.unknown);
    }

    public static String checkClasses(@ParamMessage CalcProperty property, SQLSession sql, BaseClass baseClass) throws SQLException, SQLHandledException {
        return checkClasses(property, sql, null, baseClass);
    }

    @StackMessage("{logics.checking.data.classes}")
    public static String checkClasses(@ParamMessage CalcProperty property, SQLSession sql, QueryEnvironment env, BaseClass baseClass) throws SQLException, SQLHandledException {
        assert property.isStored();
        
        ImRevMap<ClassPropertyInterface, KeyExpr> mapKeys = property.getMapKeys();
        Where where = getIncorrectWhere(property, baseClass, mapKeys);
        Query<ClassPropertyInterface, String> query = new Query<>(mapKeys, where);

        String incorrect = env == null ? query.readSelect(sql) : query.readSelect(sql, env);
        if(!incorrect.isEmpty())
            return "---- Checking Classes for " + (property instanceof DataProperty ? "data" : "aggregate") + " property : " + property + "-----" + '\n' + incorrect;
        return "";
    }

    public static <P extends PropertyInterface> Where getIncorrectWhere(CalcProperty<P> property, BaseClass baseClass, final ImRevMap<P, KeyExpr> mapKeys) {
        assert property.isStored();
                
        final Expr dataExpr = property.getInconsistentExpr(mapKeys, baseClass);

        Where correctClasses = property.getClassValueWhere(ClassType.storedPolicy).getWhere(new GetValue<Expr, Object>() {
            public Expr getMapValue(Object value) {
                if(value instanceof PropertyInterface) {
                    return mapKeys.get((P)value);
                }
                assert value.equals("value");
                return dataExpr;
            }}, true);
        return dataExpr.getWhere().and(correctClasses.not());
    }

    public static <P extends PropertyInterface> Where getIncorrectWhere(ImplementTable table, BaseClass baseClass, final ImRevMap<KeyField, KeyExpr> mapKeys) {
        final Where inTable = baseClass.getInconsistentTable(table).join(mapKeys).getWhere();
        Where correctClasses = table.getClasses().getWhere(mapKeys, true);
        return inTable.and(correctClasses.not());
    }

    public static <P extends PropertyInterface> Where getIncorrectWhere(ImplementTable table, PropertyField field, BaseClass baseClass, final ImRevMap<KeyField, KeyExpr> mapKeys, Result<Expr> resultExpr) {
        Expr fieldExpr = baseClass.getInconsistentTable(table).join(mapKeys).getExpr(field);
        resultExpr.set(fieldExpr);
        final Where inTable = fieldExpr.getWhere();
        Where correctClasses = table.getClassWhere(field).getWhere(MapFact.addExcl(mapKeys, field, fieldExpr), true);
        return inTable.and(correctClasses.not());
    }

    public static String checkTableClasses(@ParamMessage ImplementTable table, SQLSession sql, BaseClass baseClass, boolean includeProps) throws SQLException, SQLHandledException {
        return checkTableClasses(table, sql, null, baseClass, includeProps);
    }

    public static String checkTableClasses(@ParamMessage ImplementTable table, SQLSession sql, QueryEnvironment env, BaseClass baseClass, boolean includeProps) throws SQLException, SQLHandledException {
        Query<KeyField, Object> query = getIncorrectQuery(table, baseClass, includeProps, true);

        String incorrect = env == null ? query.readSelect(sql) : query.readSelect(sql, env);
        if(!incorrect.isEmpty())
            return "---- Checking Classes for table : " + table + "-----" + '\n' + incorrect;
        return "";
    }

    private static Query<KeyField, Object> getIncorrectQuery(ImplementTable table, BaseClass baseClass, boolean includeProps, boolean check) {
        ImRevMap<KeyField, KeyExpr> mapKeys = table.getMapKeys();
        Where where = (includeProps && !check) ? Where.FALSE : getIncorrectWhere(table, baseClass, mapKeys);
        ImMap<Object, Expr> propExprs;
        if(includeProps) {
            Where keyWhere = where;
            final ImValueMap<PropertyField, Expr> mPropExprs = table.properties.mapItValues();
            for (int i=0,size=table.properties.size();i<size;i++) {
                final PropertyField field = table.properties.get(i);
                Result<Expr> propExpr = new Result<>();
                Where propWhere = getIncorrectWhere(table, field, baseClass, mapKeys, propExpr);
                where = where.or(propWhere);
                mPropExprs.mapValue(i, check ? ValueExpr.TRUE.and(propWhere) : propExpr.result.and(propWhere.not()));
            }
            propExprs = BaseUtils.immutableCast(mPropExprs.immutableValue());
            if(check)
                propExprs = propExprs.addExcl("KEYS", ValueExpr.TRUE.and(keyWhere));
        } else
            propExprs = MapFact.EMPTY();

        return new Query<>(mapKeys, propExprs, where);
    }

    public static void recalculateTableClasses(ImplementTable table, SQLSession sql, BaseClass baseClass) throws SQLException, SQLHandledException {
        recalculateTableClasses(table, sql, null, baseClass);
    }

    @StackMessage("{logics.recalculating.data.classes}")
    public static void recalculateTableClasses(ImplementTable table, SQLSession sql, QueryEnvironment env, BaseClass baseClass) throws SQLException, SQLHandledException {
        Query<KeyField, PropertyField> query;

        query = BaseUtils.immutableCast(getIncorrectQuery(table, baseClass, false, false));
        sql.deleteRecords(new ModifyQuery(table, query, env == null ? OperationOwner.unknown : env.getOpOwner(), TableOwner.global));

        query = BaseUtils.immutableCast(getIncorrectQuery(table, baseClass, true, false));
        if(!query.properties.isEmpty())
            sql.updateRecords(new ModifyQuery(table, query, env == null ? OperationOwner.unknown : env.getOpOwner(), TableOwner.global));
    }

    // для оптимизации
    public DataChanges getUserDataChanges(DataProperty property, PropertyChange<ClassPropertyInterface> change, QueryEnvironment env) throws SQLException, SQLHandledException {
        Pair<ImMap<ClassPropertyInterface, DataObject>, ObjectValue> simple;
        if((simple = change.getSimple(env))!=null) {
            if(IsClassProperty.fitClasses(getCurrentClasses(simple.first), property.value,
                                          simple.second instanceof DataObject ? getCurrentClass((DataObject) simple.second) : null))
                return new DataChanges(property, change);
            else
                return DataChanges.EMPTY;
        }
        return null;
    }

    public ConcreteClass getCurrentClass(DataObject value) throws SQLException, SQLHandledException {
        ConcreteObjectClass newClass = null;
        if(news!=null && value.objectClass instanceof ConcreteObjectClass) {
            if(newClasses.containsKey(value))
                newClass = newClasses.get(value);
            else {
                ImCol<ImMap<String, Object>> read = news.read(this, value);
                if(read.size()==0)
                    newClass = null;
                else
                    newClass = baseClass.findConcreteClassID((Long) read.single().get("value"));
                newClasses.put(value, newClass);
            }
        }

        if(newClass==null)
            return value.objectClass;
        else
            return newClass;
    }

    public <K> ImMap<K, ConcreteClass> getCurrentClasses(ImMap<K, DataObject> map) throws SQLException, SQLHandledException {
        ImValueMap<K, ConcreteClass> mvResult = map.mapItValues(); // exception
        for(int i=0,size=map.size();i<size;i++)
            mvResult.mapValue(i, getCurrentClass(map.getValue(i)));
        return mvResult.immutableValue();
    }

    public ObjectValue getCurrentValue(ObjectValue value) throws SQLException, SQLHandledException {
        if(value instanceof NullValue)
            return value;
        else {
            DataObject dataObject = (DataObject)value;
            return new DataObject(dataObject.object, getCurrentClass(dataObject));
        }
    }

    public <K, V extends ObjectValue> ImMap<K, V> getCurrentObjects(ImMap<K, V> map) throws SQLException, SQLHandledException {
        ImValueMap<K, V> mvResult = map.mapItValues(); // exception
        for(int i=0,size=map.size();i<size;i++)
            mvResult.mapValue(i, (V) getCurrentValue(map.getValue(i)));
        return mvResult.immutableValue();
    }

    public DataObject getDataObject(CustomClass valueClass, Long value) throws SQLException, SQLHandledException {
        return getDataObject((ValueClass)valueClass, value);
    }

    public DataObject getDataObject(ValueClass valueClass, Object value) throws SQLException, SQLHandledException {
        return baseClass.getDataObject(sql, value, valueClass.getUpSet(), getOwner());
    }

    public ObjectValue getObjectValue(CustomClass valueClass, Long value) throws SQLException, SQLHandledException {
        return getObjectValue((ValueClass)valueClass, value);
    }

    public ObjectValue getObjectValue(ValueClass valueClass, Object value) throws SQLException, SQLHandledException {
        return baseClass.getObjectValue(sql, value, valueClass.getUpSet(), getOwner());
    }

    // узнает список изменений произошедших без него у других сессий
    public ChangedData updateExternal(FormInstance<?> form) throws SQLException {
        assert this == form.session;
        return new ChangedData(changes.update(this, form));
    }

    // узнает список изменений произошедших без него
    public ChangedData update(FormInstance<?> form) throws SQLException {
        // мн-во св-в constraints/persistent или все св-ва формы (то есть произвольное)
        assert activeForms.containsKey(form);

        synchronized (updateLock) { // важно не получить внутри другие локи чтобы не было дедлоков
            UpdateChanges incrementChange = incrementChanges.get(form);
            boolean wasRestart = false;
            if (incrementChange != null) // если не было restart
                //    to -> from или from = changes, to = пустому
                updateChanges.get(form).add(incrementChange);
                //    возвращаем to
            else { // иначе
                wasRestart = true;
                incrementChange = appliedChanges.remove(form);
                if (incrementChange == null) // совсем не было
                    incrementChange = new UpdateChanges();
                UpdateChanges formChanges = new UpdateChanges(getChangedProps());
                // from = changes (сбрасываем пометку что не было restart'а)
                updateChanges.put(form, formChanges);
                // возвращаем applied + changes
                incrementChange.add(formChanges);
            }
            incrementChanges.put(form, new UpdateChanges());

            return new ChangedData(incrementChange.properties, wasRestart);
        }
    }

    public String applyMessage(BusinessLogics<?> BL, ExecutionStack stack) throws SQLException, SQLHandledException {
        return applyMessage(BL, stack, null);
    }

    public String applyMessage(ExecutionContext context) throws SQLException, SQLHandledException {
        return applyMessage(context.getBL(), context.stack, context);
    }

    public String applyMessage(BusinessLogics<?> BL, ExecutionStack stack, UserInteraction interaction) throws SQLException, SQLHandledException {
        if(apply(BL, stack, interaction))
            return null;
        else
            return ThreadLocalContext.getLogMessage();
//            return ((LogMessageClientAction)BaseUtils.single(actions)).message;
    }

    public boolean apply(BusinessLogics BL, ExecutionStack stack, UserInteraction interaction, ImOrderSet<ActionPropertyValueImplement> applyActions, FunctionSet<SessionDataProperty> keepProperties, ExecutionEnvironment sessionEventFormEnv) throws SQLException, SQLHandledException {
        return apply(BL, sessionEventFormEnv, stack, interaction, applyActions, keepProperties);
    }

    public boolean check(BusinessLogics BL, ExecutionEnvironment sessionEventFormEnv, ExecutionStack stack, UserInteraction interaction) throws SQLException, SQLHandledException {
        setApplyFilter(ApplyFilter.ONLYCHECK);

        boolean result = apply(BL, sessionEventFormEnv, stack, interaction, SetFact.<ActionPropertyValueImplement>EMPTYORDER(), SetFact.<SessionDataProperty>EMPTY());

        setApplyFilter(ApplyFilter.NO);
        return result;
    }

    public static <T extends PropertyInterface> boolean fitKeyClasses(CalcProperty<T> property, PropertyChangeTableUsage<T> change) {
        return change.getClassWhere(property.mapTable.mapKeys).means(property.mapTable.table.getClasses(), true); // если только по ширинам отличаются то тоже подходят
    }

    public static <T extends PropertyInterface> boolean fitClasses(CalcProperty<T> property, PropertyChangeTableUsage<T> change) {
        return change.getClassWhere(property.mapTable.mapKeys, property.field).means(property.fieldClassWhere, true); // если только по ширинам отличаются то тоже подходят
    }

    public static <T extends PropertyInterface> boolean notFitKeyClasses(CalcProperty<T> property, PropertyChangeTableUsage<T> change) {
        return change.getClassWhere(property.mapTable.mapKeys).and(property.mapTable.table.getClasses()).isFalse();
    }

    // см. splitSingleApplyClasses почему не используется сейчас
    public static <T extends PropertyInterface> boolean notFitClasses(CalcProperty<T> property, PropertyChangeTableUsage<T> change) {
        return change.getClassWhere(property.mapTable.mapKeys, property.field).and(property.fieldClassWhere).isFalse();
    }

    public static <T extends PropertyInterface> boolean fitClasses(AndClassSet property, AndClassSet change) {
        return property.containsAll(change, true);
    }

    public static <T extends PropertyInterface> boolean notFitClasses(AndClassSet property, AndClassSet change) {
        return property.and(change).isEmpty();
    }

    // для Single Apply
    private class EmptyModifier extends SessionModifier {

        private EmptyModifier() {
            super("empty");
        }

        @Override
        public void addHintIncrement(CalcProperty property) {
            throw new RuntimeException("should not be"); // так как нет изменений то и hint не может придти
        }

        public ImSet<CalcProperty> calculateProperties() {
            return SetFact.EMPTY();
        }

        protected <P extends PropertyInterface> ModifyChange<P> calculateModifyChange(CalcProperty<P> property, PrereadRows<P> preread, FunctionSet<CalcProperty> overrided) {
            if(!preread.isEmpty())
                return new ModifyChange<>(property.getNoChange(), preread, false);
            return null;
        }

        public SQLSession getSQL() {
            return sql;
        }

        public OperationOwner getOpOwner() {
            return DataSession.this.getOwner();
        }

        public BaseClass getBaseClass() {
            return baseClass;
        }

        public QueryEnvironment getQueryEnv() {
            return env;
        }

        public long getMaxCount(CalcProperty recDepends) {
            return 0;
        }
    }
    public final EmptyModifier emptyModifier = new EmptyModifier();

    private <T extends PropertyInterface, D extends PropertyInterface> PropertyChangeTableUsage<T> splitApplySingleStored(String debugInfo, CalcProperty<T> property, PropertyChangeTableUsage<T> changeTable, BusinessLogics<?> BL) throws SQLException, SQLHandledException {
        if(property.isEnabledSingleApply()) {
            Pair<PropertyChangeTableUsage<T>, PropertyChangeTableUsage<T>> split = property.splitSingleApplyClasses(debugInfo, changeTable, sql, baseClass, env);
            try {
                applySingleStored(property, split.first, BL);
            } catch (Throwable e) {
                split.second.drop(sql, getOwner());
                throw ExceptionUtils.propagate(e, SQLException.class, SQLHandledException.class);
            }
            return split.second;
        }
        return changeTable;
    }

    private <T extends PropertyInterface, D extends PropertyInterface> void applySingleStored(CalcProperty<T> property, PropertyChangeTableUsage<T> change, BusinessLogics<?> BL) throws SQLException, SQLHandledException {
        assert isInTransaction();

        // assert что у change классы совпадают с property
        ServerLoggers.assertLog(property.isSingleApplyStored(), "NOT SINGLE APPLY STORED");
        assert fitClasses(property, change); // проверяет гипотезу
        assert fitKeyClasses(property, change); // дополнительная проверка, она должна обеспечиваться тем что в change не должно быть замен null на null

        if(change.isEmpty())
            return;

        // тут есть assert что в increment+noUpdate не будет noDB, то есть не пересекется с NoEventModifier, то есть можно в любом порядке increment'ить
        IncrementTableProps increment = new IncrementTableProps(property, change);
        IncrementChangeProps noUpdate = new IncrementChangeProps(BL.getDataChangeEvents());

        OverrideSessionModifier modifier = new OverrideSessionModifier("assd", new OverrideIncrementProps(noUpdate, increment), emptyModifier);

        ImOrderSet<CalcProperty> dependProps = BL.getSingleApplyDependFrom(property, this); // !!! важно в лексикографическом порядке должно быть

        try {
            if(neededProps!=null && !flush) { // придется отдельным прогоном чтобы правильную лексикографику сохранить
                for(CalcProperty<D> depend : dependProps)
                    if(!neededProps.contains(depend)) {
                        updatePendingApplyStart(property, change);
                        break;
                    }
            }

            // здесь нужно было бы добавить, что если есть oldProperty с DB и EVENT scope'ами считать их один раз (для этого сделать applyTables и applyChanges), но с учетом setPrevScope'ов, ситуация когда таки oldProperty будут встречаться достаточно редкая

            for(CalcProperty<D> depend : dependProps) {
                assert depend.isSingleApplyStored() || depend instanceof OldProperty;
    
                if(neededProps!=null) { // управление pending'ом
                    assert !flush || !pendingSingleTables.containsKey(depend); // assert что если flush то уже обработано (так как в обратном лексикографике идет)
                    if(!neededProps.contains(depend)) { // если не нужная связь не обновляем
                        if(!flush)
                            continue;
                    } else { // если нужная то уже обновили
                        if(flush) {
                            if(depend.isSingleApplyStored())
                                noUpdate.addNoChange(depend);
                            continue;
                        }
                    }
                }
    
                if(depend.isSingleApplyStored()) { // читаем новое значение, запускаем рекурсию
                    PropertyChangeTableUsage<D> dependChange = depend.readChangeTable("asssto:sa", sql, modifier, baseClass, env);
                    applySingleStored((CalcProperty)depend, (PropertyChangeTableUsage)dependChange, BL);
                    noUpdate.addNoChange(depend); // докидываем noUpdate чтобы по нескольку раз одну ветку не отрабатывать
                } else { // тут по аналогии с оптимизацией в executeGlobalEvent, можно было бы сделать оптимизацию на PREV, без used (то есть не входящих в условие событие) - смотреть на условия события в котором используется (в том числе рекурсивно по другим событием, и проверкой выполнилось или нет)
                    PropertyChangeTableUsage<D> dependChange = ((OldProperty<D>) depend).property.readChangeTable("asssto:nsa", sql, modifier, baseClass, env);
                    updateApplyStart((OldProperty<D>) depend, dependChange);
                }
            }
            savePropertyChanges(property, change);
        } finally {
            OperationOwner owner = getOwner();
            change.drop(sql, owner);
            modifier.clean(sql, owner); // hint'ы и ссылки почистить
        }
    }

    private OrderedMap<CalcProperty, PropertyChangeTableUsage> pendingSingleTables = new OrderedMap<>();
    boolean flush = false;

    private FunctionSet<CalcProperty> neededProps = null;
    private void startPendingSingles(ActionProperty action) throws SQLException {
        assert isInTransaction();

        if(!action.singleApply)
            return;

        neededProps = action.getDependsUsedProps();
    }

    private <P extends PropertyInterface> void updatePendingApplyStart(CalcProperty<P> property, PropertyChangeTableUsage<P> tableUsage) throws SQLException, SQLHandledException { // изврат конечно
        assert isInTransaction();

        PropertyChangeTableUsage<P> prevTable = pendingSingleTables.get(property);
        if(prevTable==null) {
            prevTable = property.createChangeTable("updpend");
            pendingSingleTables.put(property, prevTable);
        }
        ImRevMap<P, KeyExpr> mapKeys = property.getMapKeys();
        ModifyResult result = prevTable.modifyRows(sql, mapKeys, property.getExpr(mapKeys), tableUsage.join(mapKeys).getWhere(), baseClass, Modify.LEFT, env, SessionTable.matGlobalQueryFromTable);// если он уже был в базе он не заместится
        if(!result.dataChanged() && prevTable.isEmpty())
            pendingSingleTables.remove(property);
    }

    // assert что в pendingSingleTables в обратном лексикографике
    private <T extends PropertyInterface> void flushPendingSingles(BusinessLogics BL) throws SQLException, SQLHandledException {
        assert isInTransaction();

        if(neededProps==null)
            return;

        flush = true;

        try {
            // сначала "возвращаем" изменения в базе на предыдущее
            for(Map.Entry<CalcProperty, PropertyChangeTableUsage> pendingSingle : pendingSingleTables.entrySet()) {
                CalcProperty<T> property = pendingSingle.getKey();
                PropertyChangeTableUsage<T> prevTable = pendingSingle.getValue();
    
                ImRevMap<T, KeyExpr> mapKeys = property.getMapKeys();
                PropertyChangeTableUsage<T> newTable = property.readChangeTable("flupendsin", sql, new PropertyChange<>(mapKeys, property.getExpr(mapKeys), prevTable.join(mapKeys).getWhere()), baseClass, env);
                try {
                    savePropertyChanges(property, prevTable); // записываем старые изменения
                } finally {
                    prevTable.drop(sql, getOwner());
                    pendingSingle.setValue(newTable); // сохраняем новые изменения
                }
            }
    
            for (Map.Entry<CalcProperty, PropertyChangeTableUsage> pendingSingle : pendingSingleTables.reverse().entrySet()) {
                try {
                    applySingleStored(pendingSingle.getKey(), pendingSingle.getValue(), BL);
                } finally {
                    pendingSingleTables.remove(pendingSingle.getKey());
                }
            }
        } finally {
            flush = false;
        }

        neededProps = null;
    }

    private void savePropertyChanges(Table implementTable, SessionTableUsage<KeyField, CalcProperty> changeTable) throws SQLException, SQLHandledException {
        savePropertyChanges(implementTable, changeTable.getValues().toMap(), changeTable.getKeys().toRevMap(), changeTable, true);
    }

    private <T extends PropertyInterface> void savePropertyChanges(CalcProperty<T> property, PropertyChangeTableUsage<T> change) throws SQLException, SQLHandledException {
        savePropertyChanges(property.mapTable.table, MapFact.singleton("value", (CalcProperty) property), property.mapTable.mapKeys, change, false);
    }

    private <K,V> void savePropertyChanges(Table implementTable, ImMap<V, CalcProperty> props, ImRevMap<K, KeyField> mapKeys, SessionTableUsage<K, V> changeTable, boolean onlyNotNull) throws SQLException, SQLHandledException {
        QueryBuilder<KeyField, PropertyField> modifyQuery = new QueryBuilder<>(implementTable);
        Join<V> join = changeTable.join(mapKeys.join(modifyQuery.getMapExprs()));
        
/*        Where reupdateWhere = null;
        Join<PropertyField> dbJoin = null;
        if(!DBManager.PROPERTY_REUPDATE && props.size() < Settings.get().getDisablePropertyReupdateCount()) {
            reupdateWhere = Where.TRUE;
            dbJoin = implementTable.join(modifyQuery.getMapExprs());
        }*/
                    
        for (int i=0,size=props.size();i<size;i++) {
            PropertyField field = props.getValue(i).field;
            Expr newExpr = join.getExpr(props.getKey(i));
            modifyQuery.addProperty(field, newExpr);

//            if(reupdateWhere != null)
//                reupdateWhere = reupdateWhere.and(newExpr.equalsFull(dbJoin.getExpr(field)));
        }
        modifyQuery.and(join.getWhere());
        
//        if(reupdateWhere != null)
//            modifyQuery.and(reupdateWhere.not());
        
        sql.modifyRecords(new ModifyQuery(implementTable, modifyQuery.getQuery(), env, TableOwner.global));
    }

    // хранит агрегированные изменения для уменьшения сложности (в транзакции очищает ветки от single applied)
    private IncrementTableProps apply = new IncrementTableProps();
    private OverrideSessionModifier applyModifier = new OverrideSessionModifier("apply", apply, dataModifier);
    private OverrideSessionModifier onlyDataModifier = null;

    @Override
    public SessionModifier getModifier() {
        if(resolveModifier != null)
            return resolveModifier;

        if(isInSessionEvent())
            return sessionEventModifier;

        if(isInTransaction()) {
            if(onlyDataModifier!=null) {
                assert applyFilter == ApplyFilter.ONLY_DATA;
                return onlyDataModifier;
            }
            return applyModifier;
        }

        return dataModifier;
    }

    public FunctionSet<SessionDataProperty> recursiveUsed = SetFact.EMPTY();
    public List<ActionPropertyValueImplement> recursiveActions = ListFact.mAddRemoveList();
    public void addRecursion(ActionPropertyValueImplement action, FunctionSet<SessionDataProperty> sessionUsed, boolean singleApply) {
        action.property.singleApply = singleApply; // жестко конечно, но пока так
        recursiveActions.add(action);
        recursiveUsed = BaseUtils.merge(recursiveUsed, sessionUsed);
    }

    private long getMaxDataUsed(CalcProperty prop) {
        PropertyChangeTableUsage<ClassPropertyInterface> tableUsage;
        long count = 0;
        if (prop instanceof DataProperty && (tableUsage = data.get((DataProperty) prop)) != null)
            count = tableUsage.getCount();
        if (news != null && (prop instanceof IsClassProperty || prop instanceof ObjectClassProperty || prop instanceof ClassDataProperty))
            count = news.getCount();
        return count;
    }

    @AssertSynchronized
    public boolean apply(final BusinessLogics<?> BL, ExecutionEnvironment sessionEventFormEnv, ExecutionStack stack, UserInteraction interaction,
                         ImOrderSet<ActionPropertyValueImplement> applyActions, FunctionSet<SessionDataProperty> keepProps) throws SQLException, SQLHandledException {
        if(!hasChanges() && applyActions.isEmpty())
            return true;

        if(isInTransaction()) {
            ServerLoggers.assertLog(false, "NESTED APPLY");
            for(ActionPropertyValueImplement applyAction : applyActions)
                applyAction.execute(this, stack);
            return true;
        }

        keepProps = adjustKeep(true, keepProps);

        if (parentSession != null) {
            assert !isInTransaction() && !isInSessionEvent();

            executeSessionEvents(sessionEventFormEnv, stack);

            NotFunctionSet<SessionDataProperty> notKeepProps = new NotFunctionSet<>(keepProps);
            copyDataTo(parentSession, false, notKeepProps); // те которые не keep не копируем наверх, noNesting не копируем наверх
            parentSession.copyDataTo(this, BaseUtils.remove(notKeepProps, NONESTING)); // копируем их обратно как при не вложенной newSession, noNesting не копируем обратно

            cleanIsDataChangedProperty();

            return true;
        }

        // до чтения persistent свойств в сессию
        if (applyObject == null) {
            try {
                applyObject = addObject(sessionClass);
                Integer changed = data.size();
                String dataChanged = "";
                for(Map.Entry<DataProperty, PropertyChangeTableUsage<ClassPropertyInterface>> entry : data.entrySet()){
                    if(entry.getKey().getDBName() != null)
                        dataChanged+=entry.getKey().getDBName() + ": " + entry.getValue().getCount() + "\n";
                }

                String added = "";
                for (CustomClass addEntry : add)
                    added += (added.isEmpty() ? "" : ", ") + addEntry.getCanonicalName();
                if(!added.isEmpty())
                    dataChanged += "Added objects of classes: " + added + "\n";

                String removed = "";
                for (CustomClass removeEntry : remove)
                        removed += (removed.isEmpty() ? "" : ", ") + removeEntry.getCanonicalName();
                if(!removed.isEmpty())
                    dataChanged += "Removed objects of classes: " + removed + "\n";

                BL.systemEventsLM.changesSession.change(dataChanged, DataSession.this, applyObject);
                currentSession.change(applyObject, DataSession.this);
                DataObject cn = ThreadLocalContext.getConnection();
                if(cn != null)
                    BL.systemEventsLM.connectionSession.change(cn, (ExecutionEnvironment)DataSession.this, applyObject);
                if (sessionEventFormEnv instanceof FormInstance) { // в будущем имеет смысл из стека тянуть, так как оттуда логи берутся
                    FormEntity formEntity = ((FormInstance) sessionEventFormEnv).entity;
                    ObjectValue form = !formEntity.isNamed()
                            ? NullValue.instance
                            : BL.reflectionLM.formByCanonicalName.readClasses(sessionEventFormEnv, new DataObject(formEntity.getCanonicalName(), StringClass.get(50)));
                    if (form instanceof DataObject)
                        BL.systemEventsLM.formSession.change(form, (ExecutionEnvironment) DataSession.this, applyObject);
                }
                BL.systemEventsLM.quantityAddedClassesSession.change(add.size(), DataSession.this, applyObject);
                BL.systemEventsLM.quantityRemovedClassesSession.change(remove.size(), DataSession.this, applyObject);
                BL.systemEventsLM.quantityChangedClassesSession.change(changed, DataSession.this, applyObject);
            } catch (SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }

        executeSessionEvents(sessionEventFormEnv, stack);

        // очистим, так как в транзакции уже другой механизм используется, и старые increment'ы будут мешать
        clearDataHints(getOwner());

        dataModifier.updateSourceChanges(); // вызываем все getPropertyChanges, чтобы notifySourceChange, так как иначе начнется транзакция и уже ничего не получится обновлять
        return transactApply(BL, stack, interaction, new HashMap<String, Integer>(), 0, applyActions, keepProps, false, System.currentTimeMillis());
    }
    
    private void clearDataHints(OperationOwner owner) throws SQLException, SQLHandledException {
        dataModifier.clearHints(sql, owner);
    }

    private static final Map<ValueClass, Long> lastRemoved = MapFact.getGlobalConcurrentHashMap();

    private final RegisterClassRemove registerClassRemove;

    private ImSet<DataProperty> checkDataClasses(ImSet<DataProperty> checkOnlyProps, long timestamp) throws SQLException, SQLHandledException {
        if(Settings.get().isDisableCheckDataClasses())
            return SetFact.EMPTY();
        
        Map<DataProperty, PropertyChangeTableUsage<ClassPropertyInterface>> checkData = data;
        if(checkOnlyProps != null)
            checkData = BaseUtils.filterKeys(data, checkOnlyProps);
        
        Runnable checkTransaction = null;
        if(isInTransaction()) {
            checkTransaction = new Runnable() {
                public void run() {
                    checkTransaction();
                }
            };
        }

        MExclSet<DataProperty> mUpdated = SetFact.mExclSet();
        for(Map.Entry<DataProperty, PropertyChangeTableUsage<ClassPropertyInterface>> dataChange : checkData.entrySet()) {
            DataProperty property = dataChange.getKey();
            ModifyResult modifyResult = property.checkClasses(dataChange.getValue(), sql, baseClass, env, getModifier(), getOwner(), checkTransaction, registerClassRemove, timestamp);
            if(modifyResult.dataChanged()) {
                updateProperties(SetFact.singleton(property), modifyResult.sourceChanged());

                mUpdated.exclAdd(property);
            }
        }
        return mUpdated.immutable();
    }

    long transactionStartTimestamp;
            
    private boolean transactApply(BusinessLogics<?> BL, ExecutionStack stack,
                                  UserInteraction interaction,
                                  Map<String, Integer> attemptCountMap, int autoAttemptCount,
                                  ImOrderSet<ActionPropertyValueImplement> applyActions, FunctionSet<SessionDataProperty> keepProps, boolean deadLockPriority, long applyStartTime) throws SQLException, SQLHandledException {
//        assert !isInTransaction();
        long startTimeStamp = getTimestamp(); 
        transactionStartTimestamp = startTimeStamp;

        startTransaction(BL, attemptCountMap, deadLockPriority, applyStartTime);

        ImSet<DataProperty> updatedClasses = checkDataClasses(null, transactionStartTimestamp); // проверка на изменение классов в базе

        try {
            boolean suceeded = recursiveApply(applyActions, BL, stack, keepProps, SetFact.<ValueClass>EMPTY());
            if(!suceeded) {
                long timestamp = getTimestamp();
                
                checkDataClasses(updatedClasses, timestamp);

                // после checkDataClasses а то себя же учтут
                registerClassRemove.checked(startTimeStamp); // потому как есть оптимизация с updatedClasses (остальные не recheck'аются) 
            }
            return suceeded;
        } catch (Throwable t) { // assert'им что последняя SQL комманда, работа с транзакцией
            try {
                rollbackApply();
            } catch (Throwable rs) {
                ServerLoggers.sqlHandLogger.info("ROLLBACK EXCEPTION " + rs.toString() + '\n' + ExceptionUtils.getStackTrace(rs));
            }
                
            if(t instanceof SQLHandledException && ((SQLHandledException)t).repeatApply(sql, getOwner(), SQLSession.getAttemptCountSum(attemptCountMap))) { // update conflict или deadlock или timeout - пробуем еще раз
                boolean noTimeout = false;
                Settings settings = Settings.get();
                if(t instanceof SQLTimeoutException && ((SQLTimeoutException)t).isTransactTimeout()) {
                    if(interaction == null) {
                        autoAttemptCount++;
                        if(autoAttemptCount > settings.getApplyAutoAttemptCountLimit()) {
                            ThreadLocalContext.delayUserInteraction(new LogMessageClientAction(localize("{logics.server.apply.timeout.canceled}"), true));                            
                            return false;
                        }
                    } else {
                        int option = (Integer)interaction.requestUserInteraction(new ConfirmClientAction("lsFusion",localize("{logics.server.restart.transaction}"), true, settings.getDialogTransactionTimeout(), JOptionPane.CANCEL_OPTION));
                        if(option == JOptionPane.CANCEL_OPTION)
                            return false;
                        if(option == JOptionPane.YES_OPTION)
                            noTimeout = true;
                    }
                }

                if(t instanceof SQLConflictException) {
                    SQLConflictException conflict = (SQLConflictException) t;
                    Integer attempts = attemptCountMap.get(conflict.getDescription(true));
                    if(attempts != null) {
                        if(conflict.updateConflict) { // update conflicts
                            if (attempts >= settings.getConflictSleepThreshold())
                                try {
                                    ServerLoggers.sqlHandLogger.info("Sleep started after conflict updates : " + attempts);
                                    Thread.sleep((long) (BaseUtils.pow(settings.getConflictSleepTimeDegree(), attempts) * 1000));
                                    ServerLoggers.sqlHandLogger.info("Sleep ended after conflict updates : " + attempts);
                                } catch (InterruptedException e) {
                                    ThreadUtils.interruptThread(sql, Thread.currentThread()); // тут SQL
                                }
                        } else { // dead locks
                            if(attempts >= settings.getDeadLockThreshold()) {
                                deadLockPriority = true;
                                ServerLoggers.sqlHandLogger.info("Using deterministic dead-lock : " + attempts + ", " + deadLockPriority);
                            }
                        }
                    }
                }

                if(noTimeout)
                    sql.pushNoTransactTimeout();
                    
                try {
                    SQLSession.incAttemptCount(attemptCountMap, ((SQLHandledException) t).getDescription(true));
                    return transactApply(BL, stack, interaction, attemptCountMap, autoAttemptCount, applyActions, keepProps, deadLockPriority, applyStartTime);
                } finally {
                    if(noTimeout)
                        sql.popNoTransactTimeout();
                }
            }

            if (t instanceof SQLTimeoutException && ((SQLTimeoutException) t).isCancel())
                return false;
            else
                throw ExceptionUtils.propagate(t, SQLException.class, SQLHandledException.class);
        }
    }

    private WeakIdentityHashMap<FormInstance, Boolean> activeForms = new WeakIdentityHashMap<>();
    public void registerForm(FormInstance form) throws SQLException, SQLHandledException {
        synchronized (closeLock) {
            activeForms.put(form, isInTransaction());
            changes.registerForm(form); // пометка что есть форма
        }

        dropFormCaches();

        updateSessionNotChangedEvents(getChangedProps()); // помечаем все свойства как not changed чтобы предотвратить выполнение новых событий
    }

    // должен быть thread-safe
    private interface Cleaner extends ExceptionRunnable<SQLException> {
    }

    public void unregisterForm(FormInstance<?> form) throws SQLException {
        changes.unregisterForm(form); // synced

        dropFormCaches();

        final WeakReference<FormInstance> wForm = new WeakReference<FormInstance>(form);
        Cleaner cleaner = new Cleaner() {
            @Override
            public void run() throws SQLException {
                FormInstance<?> form = wForm.get();
                if(form == null) // уже все очистилось само
                    return;

                OperationOwner owner = getOwner();
                for (GroupObjectInstance group : form.getGroups()) {
                    if (group.keyTable != null)
                        group.keyTable.drop(sql, owner);
                    if (group.expandTable != null)
                        group.expandTable.drop(sql, owner);
                }

                for(SessionModifier modifier : form.modifiers.values())
                    modifier.clean(sql, owner);

                synchronized (updateLock) {
                    incrementChanges.remove(form);
                    appliedChanges.remove(form);
                    updateChanges.remove(form);
                }
            }
        };

        boolean closed;
        synchronized (closeLock) {
            Boolean createdInTransaction = activeForms.remove(form);

            if(createdInTransaction || form.local) { // если создана в транзакции, очищаем сразу, так как все таблицы тоже получались в транзакции, а значит rollback'ся (и в pendingCleaner зависнет очистка ресурсов, которые и так уйдут, а значит в registerChange нарушится assertion), local'ы тоже смотри коммент
                ServerLoggers.assertLog(createdInTransaction == isInTransaction(), "FORM CREATED IN TRANSACTION SHOULD BE CLOSED IN TRANSACTION");
                cleaner.run();
            } else {
//                ServerLoggers.assertLog(!isInTransaction(), "SHOULD NOT CLOSE FORM IN TRANSACTION, THAT WHERE CREATED NOT IN TRANSACTION"); как раз может, для этого в том числе pendingCleaners и делались
                synchronized (pendingCleaners) {
                    pendingCleaners.add(cleaner);
                }
            }

            closed = tryClose();
        }
        if(!closed) // за пределами closeLock чтобы не было dead-lock'ов 
            asyncFlushPendingCleaners();
    }

    // дополнительные formEntity в локальных событиях
    private Stack<FormEntity> sessionEventActiveFormEntities = new Stack<>();
    
    public boolean hasSessionEventActiveForms(ImSet<FormEntity> forms) {
        if(!sessionEventActiveFormEntities.isEmpty()) { // оптимизация
            for(FormEntity form : sessionEventActiveFormEntities)
                if(forms.contains(form))
                    return true;
        }
        return false;
    }

    public void pushSessionEventActiveForm(FormEntity form) {
        sessionEventActiveFormEntities.push(form);
        dropActiveSessionEventsCaches();
    }

    public void popSessionEventActiveForm() {
        sessionEventActiveFormEntities.pop();
        dropActiveSessionEventsCaches();
    }

    private void dropFormCaches() throws SQLException {
        dropActiveSessionEventsCaches();
        sessionEventOldDepends = null;
    }
    public Iterable<FormInstance> getAllActiveForms() { // including nested
        Iterable<FormInstance> result;
        synchronized(closeLock) {
            result = BaseUtils.toList(activeForms.keysIt());
        }
        if(parentSession != null)
            result = Iterables.concat(result, parentSession.getAllActiveForms());
        return result;
    }
    public <K> ImOrderSet<K> filterOrderEnv(ImOrderMap<K, SessionEnvEvent> elements) {
        return elements.filterOrderValues(new SFunctionSet<SessionEnvEvent>() {
            public boolean contains(SessionEnvEvent elements) {
                return elements.contains(DataSession.this);
            }});
    }

    private boolean noCancelInTransaction;

    public boolean isNoCancelInTransaction() {
        return noCancelInTransaction;
    }

    public void setNoCancelInTransaction(boolean noCancelInTransaction) {
        this.noCancelInTransaction = noCancelInTransaction;
    }

    private boolean noEventsInTransaction;

    public boolean isNoEventsInTransaction() {
        return noEventsInTransaction;
    }

    public void setNoEventsInTransaction(boolean noEventsInTransaction) {
        this.noEventsInTransaction = noEventsInTransaction;
    }

    public ApplyFilter applyFilter = ApplyFilter.NO;
    public void setApplyFilter(ApplyFilter applyFilter) {
        this.applyFilter = applyFilter;
    }
    
    private List<Runnable> rollbackInfo = new ArrayList<>(); 
    public void addRollbackInfo(Runnable run) {
        assert isInTransaction();
        
        rollbackInfo.add(run);
    }

    private boolean recursiveApply(ImOrderSet<ActionPropertyValueImplement> actions, BusinessLogics BL, ExecutionStack stack, FunctionSet<SessionDataProperty> keepProps, ImSet<ValueClass> removedClasses) throws SQLException, SQLHandledException {
        // тоже нужен посередине, чтобы он успел dataproperty изменить до того как они обработаны
        ImOrderSet<Object> execActions = SetFact.addOrderExcl(actions, BL.getAppliedProperties(this));
        for (int i = 0, size = execActions.size(); i < size; i++) {
            try {
                sql.statusMessage = new StatusMessage("event", execActions.get(i), i, size);
                if (!executeGlobalEvent(BL, stack, execActions.get(i), new ProgressBar(localize("{logics.server.apply.message}"), i, size, execActions.get(i) + ", " + i + " of " + size)))
                    return false;
            } finally {
                sql.statusMessage = null;
            }
        }

        if (applyFilter == ApplyFilter.ONLYCHECK) {
            cancel(stack);
            return true;
        }

        sql.inconsistent = true;

        ImOrderSet<ActionPropertyValueImplement> updatedRecursiveActions;
        try {
            updatedRecursiveActions = updateCurrentClasses(keepProps);
            assert stack != null;
            if(stack != null)
                stack.updateOnApply(this);

            // записываем в базу, то что туда еще не сохранено, приходится сохранять группами, так могут не подходить по классам
            packRemoveClasses(BL); // нужно делать до, так как классы должны быть актуальными, иначе спакует свои же изменения
            ImMap<ImplementTable, ImSet<CalcProperty>> groupTables = groupPropertiesByTables();
            OperationOwner owner = getOwner();
            for (int i = 0, size = groupTables.size(); i < size; i++) {
                try {
                    sql.statusMessage = new StatusMessage("save", groupTables.getKey(i), i, size);
                    ImplementTable table = groupTables.getKey(i);
                    SessionTableUsage<KeyField, CalcProperty> saveTable = readSave(table, groupTables.getValue(i));
                    try {
                        savePropertyChanges(table, saveTable);
                    } finally {
                        saveTable.drop(sql, owner);
                    }
                } finally {
                    sql.statusMessage = null;
                }
            }
    
            apply.clear(sql, owner); // все сохраненные хинты обнуляем
            clearDataHints(owner); // drop'ем hint'ы (можно и без sql но пока не важно)

            removedClasses = removedClasses.merge(SetFact.fromJavaSet(remove));
            restart(false, BaseUtils.merge(recursiveUsed,keepProps)); // оставляем usedSessiona
        } finally {
            sql.inconsistent = false;
        }

        if(recursiveActions.size() > 0) {
            recursiveUsed = SetFact.EMPTY();
            recursiveActions.clear();
            return recursiveApply(updatedRecursiveActions, BL, stack, keepProps, removedClasses);
        }

        long checkedTimestamp;
        if(keepProps.isEmpty()) {
            assert data.isEmpty();
            checkedTimestamp = getTimestamp(); // здесь еще есть lockWrite поэтому новых изменений не появится
        } else
            checkedTimestamp = transactionStartTimestamp; // // так как keepProps проверили только на начало транзакции - берем соответствующий timestamp 

        registerClassRemove.removed(removedClasses, Long.MAX_VALUE); // надо так как между commit'ом и регистрацией изменения может начаться новая транзакция и update conflict'а не будет, поэтому временно включим режим будущего
        
        commitTransaction();

        registerClassRemove.removed(removedClasses, getTimestamp());

        registerClassRemove.checked(checkedTimestamp);
        
        return true;
    }
    
    private static long getTimestamp() {
        return System.currentTimeMillis();
    }

    private ImOrderSet<ActionPropertyValueImplement> updateCurrentClasses(FunctionSet<SessionDataProperty> keepProps) throws SQLException, SQLHandledException {
        ImOrderSet<ActionPropertyValueImplement> updatedRecursiveActions = null;
        if(recursiveActions.size()>0) {
            recursiveUsed = BaseUtils.merge(recursiveUsed, SetFact.singleton((SessionDataProperty) currentSession.property));

            MOrderExclSet<ActionPropertyValueImplement> mUpdatedRecursiveActions = SetFact.mOrderExclSet();
            for(ActionPropertyValueImplement recursiveAction : recursiveActions)
                mUpdatedRecursiveActions.exclAdd(recursiveAction.updateCurrentClasses(this));
            updatedRecursiveActions = mUpdatedRecursiveActions.immutableOrder();
        }

        updateSessionCurrentClasses(keepProps);
        
        return updatedRecursiveActions;
    }

    private void updateSessionCurrentClasses(FunctionSet<SessionDataProperty> keepProps) throws SQLException, SQLHandledException {
        updateCurrentClasses(filterSessionData(BaseUtils.merge(recursiveUsed, keepProps)).values());
    }

    private void packRemoveClasses(BusinessLogics BL) throws SQLException, SQLHandledException {
        if(news==null)
            return;

        // проводим "мини-паковку", то есть удаляем все записи, у которых ключем является удаляемый объект
        for(ImplementTable table : BL.LM.tableFactory.getImplementTables(fromJavaSet(remove))) {
            QueryBuilder<KeyField, PropertyField> query = new QueryBuilder<>(table);
            Where removeWhere = Where.FALSE;
            ImMap<KeyField, ValueClass> mapFields = table.getMapFields();
            for (int i = 0, size = mapFields.size(); i < size; i++) {
                try {
                    sql.statusMessage = new StatusMessage("delete", mapFields.getKey(i), i, size);
                    ValueClass value = mapFields.getValue(i);
                    if (value instanceof CustomClass && remove.contains(value)) {
                        Join<String> newJoin = news.join(query.getMapExprs().get(mapFields.getKey(i)));
                        removeWhere = removeWhere.or(newJoin.getWhere().and(isValueClass(newJoin.getExpr("value"), (CustomClass) value, usedNewClasses).not()));
                    }
                } finally {
                    sql.statusMessage = null;
                }
            }
            query.and(table.join(query.getMapExprs()).getWhere().and(removeWhere));
            sql.deleteRecords(new ModifyQuery(table, query.getQuery(), getQueryEnv(), TableOwner.global));
        }
    }

    private <P extends PropertyInterface> void readStored(CalcProperty<P> property, BusinessLogics<?> BL) throws SQLException, SQLHandledException {
        assert isInTransaction();
        assert property.isStored();
        if(property.hasChanges(getModifier())) {
            readStoredWithChanges(property, BL);
        }
    }

    @StackMessage("{message.session.apply.write}")
    private <P extends PropertyInterface> void readStoredWithChanges(@ParamMessage CalcProperty<P> property, BusinessLogics<?> BL) throws SQLException, SQLHandledException {
        PropertyChangeTableUsage<P> changeTable = property.readChangeTable("rswc", sql, getModifier(), baseClass, env);

        apply.add(property, splitApplySingleStored("rswc", property,
                changeTable, BL));
    }

    protected SQLSession getSQL() {
        return sql;
    }

    protected BaseClass getBaseClass() {
        return baseClass;
    }

    public OperationOwner getOwner() {
        return owner;
    }

    public static QueryEnvironment emptyEnv(final OperationOwner owner) {
        return new QueryEnvironment() {
            public ParseInterface getSQLUser() {
                return ParseInterface.empty;
            }

            public ParseInterface getIsFullClient() {
                return ParseInterface.empty;
            }

            public ParseInterface getSQLComputer() {
                return ParseInterface.empty;
            }

            public ParseInterface getSQLForm() {
                return ParseInterface.empty;
            }

            @Override
            public ParseInterface getSQLConnection() {
                return ParseInterface.empty;
            }

            @Override
            public Locale getLocale() {
                return Locale.getDefault();
            }

            public ParseInterface getIsServerRestarting() {
                return ParseInterface.empty;
            }

            public int getTransactTimeout() {
                return 0;
            }

            public OperationOwner getOpOwner() {
                return owner;
            }
        };
    }

    private final OperationOwner owner;

    public final QueryEnvironment env = new QueryEnvironment() {
        public ParseInterface getSQLUser() {
            ObjectValue currentUser = user.getCurrentUser();
            if (currentUser instanceof DataObject) {
                return new TypeObject(((DataObject) currentUser).object, ObjectType.instance);
            } else {
                return ((NullValue) currentUser).getParse(ObjectType.instance);
            }
        }

        public OperationOwner getOpOwner() {
            return DataSession.this.getOwner();
        }

        public ParseInterface getIsFullClient() {
            return new LogicalParseInterface() {
                public boolean isTrue() {
                    return computer.isFullClient();
                }
            };
        }

        public ParseInterface getSQLComputer() {
            ObjectValue currentComputer = computer.getCurrentComputer();
            if (currentComputer instanceof DataObject) {
                return new TypeObject(((DataObject) currentComputer).object, ObjectType.instance);
            } else {
                return ((NullValue) currentComputer).getParse(ObjectType.instance);
            }
        }

        public ParseInterface getSQLForm() {
            ObjectValue currentForm = form.getCurrentForm();
            if(currentForm instanceof DataObject) {
                return new TypeObject(((DataObject)currentForm).object, ObjectType.instance);
            } else {
                return ((NullValue)currentForm).getParse(ObjectType.instance);
            }
        }

        public ParseInterface getSQLConnection() {
            ObjectValue currentConnection = connection.getCurrentConnection();
            if(currentConnection instanceof DataObject) {
                return new TypeObject(((DataObject)currentConnection).object, ObjectType.instance);
            } else {
                return ((NullValue)currentConnection).getParse(ObjectType.instance);
            }
        }

        @Override
        public Locale getLocale() {
            return locale.getLocale();
        }

        public int getTransactTimeout() {
            return timeout.getTransactionTimeout();
        }

        public ParseInterface getIsServerRestarting() {
            return new LogicalParseInterface() {
                public boolean isTrue() {
                    return isServerRestarting.isServerRestarting();
                }
            };
        }
    };

    private static ImSet<ConcreteObjectClass> addUsed(Set<ConcreteObjectClass> used, ImSet<ConcreteObjectClass> add, boolean checkChanged) { // последний параметр оптимизация
        MFilterSet<ConcreteObjectClass> result = (checkChanged ? SetFact.mFilter(add) : null);
        for(ConcreteObjectClass element : add) {
            boolean added = used.add(element);
            if(checkChanged && added)
                result.keep(element);
        }
        return checkChanged ? SetFact.imFilter(result, add) : null;
    }

    private static ImSet<CustomClass> addUsed(ImSet<CustomClass> classes, final ImSet<ConcreteObjectClass> usedNews) {
        return classes.filterFn(new SFunctionSet<CustomClass>() {
            public boolean contains(CustomClass element) {
                UpClassSet upSet = element.getUpSet();
                for(ConcreteObjectClass usedClass : usedNews)
                    if(usedClass instanceof ConcreteCustomClass) {
                        ConcreteCustomClass customUsedClass = (ConcreteCustomClass) usedClass;
                        if(customUsedClass.inSet(upSet)) // добавляется еще один or
                            return true;
                    }
                return false;
            }});
    }

    private FunctionSet<CalcProperty<ClassPropertyInterface>> aspectChangeClass(ImSet<CustomClass> addClasses, ImSet<CustomClass> removeClasses, ImSet<ConcreteObjectClass> oldClasses, ImSet<ConcreteObjectClass> newClasses, ClassChange change, SingleKeyPropertyUsage upChangeTable) throws SQLException, SQLHandledException {
        checkTransaction(); // важно что, вначале

        if(news ==null)
            news = new SingleKeyPropertyUsage("chcl:news", ObjectType.instance, ObjectType.instance);

        SingleKeyPropertyUsage changeTable = null;
        try {
            if(change.needMaterialize(news)) { // safe modify, 2 раза повторяется, обобщать сложнее
                changeTable = change.materialize("chcl:nmn", sql, baseClass, env);
                change = changeTable.getChange();
            }

            ModifyResult tableChanged = change.modifyRows(news, sql, baseClass, Modify.MODIFY, env, getOwner(), SessionTable.matGlobalQuery);
            if(!tableChanged.dataChanged()) {
                if(news.isEmpty())
                    news = null;
                return null;
            }
    
            boolean sourceNotChanged = !tableChanged.sourceChanged();
            this.newClasses.clear();

            // интересует изменения, только если не изменилась структура таблицы (в этом случае все равно все обновлять)
            ImSet<ConcreteObjectClass> changedUsedOld = addUsed(usedOldClasses, oldClasses, sourceNotChanged);
            ImSet<ConcreteObjectClass> changedUsedNew = addUsed(usedNewClasses, newClasses, sourceNotChanged);
    
            // оптимизация
            Pair<ImSet<CustomClass>, ImSet<CustomClass>> changedAdd = changeSingle(addClasses, change, singleAdd, add, singleRemove, remove, sourceNotChanged, sql, baseClass, env);
            Pair<ImSet<CustomClass>, ImSet<CustomClass>> changedRemove = changeSingle(removeClasses, change, singleRemove, remove, singleAdd, add, sourceNotChanged, sql, baseClass, env);
    
            ImSet<CustomClass> changedAddFull = sourceNotChanged ? changedAdd.first.addExcl(changedRemove.second).merge(addUsed(addClasses, changedUsedNew)) : null;
            ImSet<CustomClass> changeRemoveFull = sourceNotChanged ? changedRemove.first.addExcl(changedAdd.second).merge(addUsed(removeClasses, changedUsedNew)) : null;
    
            for(Map.Entry<DataProperty, PropertyChangeTableUsage<ClassPropertyInterface>> dataChange : data.entrySet()) { // удаляем существующие изменения
                DataProperty property = dataChange.getKey();
                if(property.depends(removeClasses)) { // оптимизация
                    PropertyChangeTableUsage<ClassPropertyInterface> table = dataChange.getValue();
    
                    // кейс с удалением, похож на getEventChange и в saveClassChanges - "мини паковка"
                    Where removeWhere = Where.FALSE;
                    ImRevMap<ClassPropertyInterface, KeyExpr> mapKeys = property.getMapKeys();
                    for(ClassPropertyInterface propertyInterface : property.interfaces)
                        if(SetFact.contains(propertyInterface.interfaceClass, removeClasses)) {
                            Join<String> newJoin = change.join(mapKeys.get(propertyInterface));
                            removeWhere = removeWhere.or(newJoin.getWhere().and(isValueClass(newJoin.getExpr("value"), (CustomClass) propertyInterface.interfaceClass, usedNewClasses).not()));
                        }
                    Join<String> join = table.join(mapKeys);
                    removeWhere = removeWhere.and(join.getWhere());
    
                    if(SetFact.contains(property.value, removeClasses)) {
                        Join<String> newJoin = change.join(join.getExpr("value"));
                        removeWhere = removeWhere.or(newJoin.getWhere().and(isValueClass(newJoin.getExpr("value"), (CustomClass) property.value, usedNewClasses).not()));
                    }
    
                    if(!removeWhere.isFalse()) { // оптимизация
                        if(changeTable == null && change.needMaterialize(table)) { // safe modify, 2 раза повторяется, обобщать сложнее
                            changeTable = change.materialize("chcl:nm2", sql, baseClass, env);
                            change = changeTable.getChange();
                        }
                        
                        updateSessionEvents(SetFact.singleton(property));
                        
                        ModifyResult tableRemoveChanged = table.modifyRows(sql, new Query<ClassPropertyInterface, String>(mapKeys, removeWhere), baseClass, Modify.DELETE, getQueryEnv(), SessionTable.matGlobalQuery);
                        if(tableRemoveChanged.dataChanged())
                            dataModifier.eventChange(property, true, tableRemoveChanged.sourceChanged());
                    }
                }
            }
            return sourceNotChanged ? CustomClass.getProperties(changedAddFull, changeRemoveFull, changedUsedOld, changedUsedNew) : FullFunctionSet.<CalcProperty<ClassPropertyInterface>>instance();
        } finally {
            if(changeTable!=null)
                changeTable.drop(sql, getOwner());
        }
    }

    private static Pair<ImSet<CustomClass>, ImSet<CustomClass>> changeSingle(ImSet<CustomClass> thisChanged, ClassChange thisChange, Map<CustomClass, DataObject> single, Set<CustomClass> changed, Map<CustomClass, DataObject> singleBack, Set<CustomClass> changedBack, boolean checkChanged, SQLSession sql, BaseClass baseClass, QueryEnvironment queryEnv) throws SQLException, SQLHandledException {
        MFilterSet<CustomClass> mNotChanged = checkChanged ? SetFact.mFilter(thisChanged) : null;
        MFilterSet<CustomClass> mChangedBack = checkChanged ? SetFact.mFilter(thisChanged) : null;

        for(CustomClass changeClass : thisChanged) {
            if(changed.contains(changeClass)) {
                if((single.remove(changeClass) == null) && checkChanged) // если не было
                    mNotChanged.keep(changeClass);
            } else {
                if(thisChange.keyValue !=null)
                    single.put(changeClass, thisChange.keyValue);
                changed.add(changeClass);
            }

            DataObject removeObject = singleBack.get(changeClass);
            if(removeObject!=null && thisChange.containsObject(sql, removeObject, baseClass, queryEnv)) {
                singleBack.remove(changeClass);
                changedBack.remove(changeClass);
                if(checkChanged)
                    mChangedBack.keep(changeClass);
            }
        }
        return checkChanged ? new Pair<>(
                thisChanged.remove(SetFact.<CustomClass>imFilter(mNotChanged, thisChanged)), SetFact.<CustomClass>imFilter(mChangedBack, thisChanged)) : null;
    }

    private void aspectDropChanges(final DataProperty property) throws SQLException {
        PropertyChangeTableUsage<ClassPropertyInterface> dataChange = data.remove(property);
        if(dataChange!=null)
            dataChange.drop(sql, getOwner());
    }

    @AssertSynchronized
    private ModifyResult aspectChangeProperty(final DataProperty property, PropertyChange<ClassPropertyInterface> change) throws SQLException, SQLHandledException {
        checkTransaction();

        PropertyChangeTableUsage<ClassPropertyInterface> dataChange = data.get(property);
        if(dataChange == null) { // создадим таблицу, если не было
            dataChange = property.createChangeTable("achpr");
            data.put(property, dataChange);
        }
        ModifyResult result = change.modifyRows(dataChange, sql, baseClass, Modify.MODIFY, getQueryEnv(), getOwner(), SessionTable.matGlobalQuery);
        if(!result.dataChanged() && dataChange.isEmpty())
            data.remove(property);
        return result;
    }

    public void dropTables(FunctionSet<SessionDataProperty> keep) throws SQLException, SQLHandledException {
        OperationOwner owner = getOwner();
        for(PropertyChangeTableUsage<ClassPropertyInterface> dataTable : filterNotSessionData(keep).values())
            dataTable.drop(sql, owner);
        if(news !=null)
            news.drop(sql, owner);

        dataModifier.eventDataChanges(getChangedProps());
    }

    // тут вообще вопрос нужны или нет keepSessionProps так как речь идет о вложенных сессиях
    private void copyDataTo(DataSession other, boolean cleanIsDataChangedProp, FunctionSet<SessionDataProperty> ignoreSessionProps) throws SQLException, SQLHandledException {
        FunctionSet<SessionDataProperty> noNestingProps = NONESTING;
        
        other.cleanChanges(noNestingProps);
        
        ignoreSessionProps = BaseUtils.merge(noNestingProps, ignoreSessionProps);

        if (news != null) {
            other.changeClass(news.getChange());
        }
        for (Map.Entry<DataProperty, PropertyChangeTableUsage<ClassPropertyInterface>> e : filterNotSessionData(ignoreSessionProps).entrySet()) {
            other.change(e.getKey(), PropertyChangeTableUsage.getChange(e.getValue()));
        }
        
        if (cleanIsDataChangedProp) {
            other.cleanIsDataChangedProperty();
        }

        other.dropSessionEventChangedOld(); // так как сессия копируется, два раза события нельзя выполнять
    }

    // assertion что для sessionData уже adjustKeep выполнился
    private Map<DataProperty, PropertyChangeTableUsage<ClassPropertyInterface>> filterNotSessionData(FunctionSet<SessionDataProperty> sessionData) {
        return BaseUtils.filterNotKeys(data, sessionData, SessionDataProperty.class);
    }
    private void clearNotSessionData(FunctionSet<SessionDataProperty> sessionData) {
        BaseUtils.clearNotKeys(data, sessionData, SessionDataProperty.class);
    }
    private Map<SessionDataProperty, PropertyChangeTableUsage<ClassPropertyInterface>> filterSessionData(FunctionSet<SessionDataProperty> sessionData) {
        return BaseUtils.filterKeys(data, sessionData, SessionDataProperty.class);
    }
    public void copyDataTo(DataSession other, FunctionSet<SessionDataProperty> toCopy) throws SQLException, SQLHandledException {
        other.dropChanges(other.filterSessionData(toCopy).keySet());
        for (Map.Entry<SessionDataProperty, PropertyChangeTableUsage<ClassPropertyInterface>> e : filterSessionData(toCopy).entrySet()) {
            other.copyDataTo(e.getKey(), PropertyChangeTableUsage.getChange(e.getValue()));
        }
    }
    public void dropSessionChanges(ImSet<SessionDataProperty> props) throws SQLException, SQLHandledException {
        dropChanges(props.filterFn(new NotFunctionSet<>(recursiveUsed)));
    }
    public Set<SessionDataProperty> getSessionChanges(FunctionSet<SessionDataProperty> set) {
        return filterSessionData(set).keySet();        
    }
    public void dropChanges(Iterable<SessionDataProperty> props) throws SQLException, SQLHandledException {
        for (SessionDataProperty prop : props) { // recursiveUsed не drop'аем
            dropChanges(prop);
        }
    }

    private void cleanChanges(FunctionSet<SessionDataProperty> keepProps) throws SQLException, SQLHandledException {
        dropClassChanges();
        dropAllDataChanges(keepProps);
        isStoredDataChanged = false;
    }

    public void setParentSession(DataSession parentSession) throws SQLException, SQLHandledException {
        assert parentSession != null;
        
        parentSession.copyDataTo(this, true, SetFact.<SessionDataProperty>EMPTY()); // копируем все local'ы

        this.parentSession = parentSession;
    }

    public DataSession getSession() {
        return this;
    }

    public FormInstance getFormInstance() {
        return null;
    }

    public boolean isInTransaction() {
        return isInTransaction;
    }

    @Override
    public void cancel(ExecutionStack stack, FunctionSet<SessionDataProperty> keep) throws SQLException, SQLHandledException {
        cancelSession(keep);
    }

    public boolean cancelSession(FunctionSet<SessionDataProperty> keep) throws SQLException, SQLHandledException {
        if(isInSessionEvent()) {
            inSessionEvent = false;
        }

        if(isInTransaction()) {
            if(noCancelInTransaction) {
                ServerLoggers.systemLogger.info("CANCEL SUPPRESSED");
                return false;
            }

            rollbackApply();
            return true;
        }

        keep = adjustKeep(true, keep);

        restart(true, keep);

        if (parentSession != null) {
            parentSession.copyDataTo(this, true, keep);
        }

        return true;
    }

    private void rollbackApply() throws SQLException, SQLHandledException {
        try {
            OperationOwner owner = getOwner();
            if(neededProps!=null) {
                for(PropertyChangeTableUsage table : pendingSingleTables.values())
                    table.drop(sql, owner);
                pendingSingleTables.clear();
                neededProps = null;
                assert !flush;
            }
    
            recursiveUsed = SetFact.EMPTY();
            recursiveActions.clear();
    
            // не надо DROP'ать так как Rollback автоматически drop'ает все temporary таблицы
            apply.clear(sql, owner);
            clearDataHints(owner); // drop'ем hint'ы (можно и без sql но пока не важно)
        } finally {
            rollbackTransaction();
        }
    }

    private <P extends PropertyInterface> void updateApplyStart(OldProperty<P> property, PropertyChangeTableUsage<P> tableUsage) throws SQLException, SQLHandledException { // изврат конечно
        assert isInTransaction();

        OperationOwner owner = getOwner();
        try {
            PropertyChangeTableUsage<P> prevTable = apply.getTable(property);
            if(prevTable==null) {
                prevTable = property.createChangeTable("upps");
                apply.add(property, prevTable);
            }
            ImRevMap<P, KeyExpr> mapKeys = property.getMapKeys();
            ModifyResult tableChanges = prevTable.modifyRows(sql, mapKeys, property.getExpr(mapKeys), tableUsage.join(mapKeys).getWhere(), baseClass, Modify.LEFT, env, SessionTable.matGlobalQueryFromTable); // если он уже был в базе он не заместится
            if(tableChanges.dataChanged())
                apply.eventChange(property, tableChanges.sourceChanged());
            else
                if(prevTable.isEmpty())
                    apply.remove(property, sql, owner);
        } finally {
            tableUsage.drop(sql, owner);
        }
    }

    public ImMap<ImplementTable, ImSet<CalcProperty>> groupPropertiesByTables() {
        return apply.getProperties().group(
                new BaseUtils.Group<ImplementTable, CalcProperty>() {
                    public ImplementTable group(CalcProperty key) {
                        if (key.isStored())
                            return key.mapTable.table;
                        assert key instanceof OldProperty;
                        return null;
                    }
                });
    }

    private final static Comparator<CalcProperty> propCompare = new Comparator<CalcProperty>() {
        public int compare(CalcProperty o1, CalcProperty o2) {
            return ((Integer)o1.getID()).compareTo(o2.getID());
        }
    };
    public <P extends PropertyInterface> SessionTableUsage<KeyField, CalcProperty> splitReadSave(String debugInfo, ImplementTable table, ImSet<CalcProperty> properties) throws SQLException, SQLHandledException {
        IncrementChangeProps increment = new IncrementChangeProps();
        MAddSet<SessionTableUsage<KeyField, CalcProperty>> splitTables = SetFact.mAddSet();

        OperationOwner owner = getOwner();
        try {
            final int split = (int) Math.sqrt(properties.size());
            final ImOrderSet<CalcProperty> propertyOrder = properties.sort(propCompare).toOrderExclSet(); // для детерменированности
            for(ImSet<CalcProperty> splitProps : properties.group(new BaseUtils.Group<Integer, CalcProperty>() {
                        public Integer group(CalcProperty key) {
                            return propertyOrder.indexOf(key) / split;
                        }}).valueIt()) {
                SessionTableUsage<KeyField, CalcProperty> splitChangesTable = readSave(debugInfo+"-spct", table, splitProps, getModifier());
                splitTables.add(splitChangesTable);
                for(CalcProperty<P> splitProp : splitProps)
                    increment.add(splitProp, SessionTableUsage.getChange(splitChangesTable, splitProp.mapTable.mapKeys, splitProp));
            }
    
            OverrideSessionModifier modifier = new OverrideSessionModifier(debugInfo + "-splrs", increment, emptyModifier);
            try {
                return readSave(debugInfo + "-rs", table, properties, modifier);
            } finally {
                modifier.clean(sql, owner);
            }
        } finally {
            for(SessionTableUsage<KeyField, CalcProperty> splitTable : splitTables)
                splitTable.drop(sql, owner);
        }
    }

    @StackMessage("{message.increment.read.properties}")
    public SessionTableUsage<KeyField, CalcProperty> readSave(ImplementTable table, @ParamMessage ImSet<CalcProperty> properties) throws SQLException, SQLHandledException {
        assert isInTransaction();

        final int split = Settings.get().getSplitIncrementApply();
        if(properties.size() > split) // если слишком много групп, разделим на несколько read'ов
            return splitReadSave("sp", table, properties);

        return readSave("sres", table, properties, getModifier());
    }

    public <P extends PropertyInterface> SessionTableUsage<KeyField, CalcProperty> readSave(String debugInfo, ImplementTable table, ImSet<CalcProperty> properties, Modifier modifier) throws SQLException, SQLHandledException {
        // подготовили - теперь надо сохранить в курсор и записать классы
        SessionTableUsage<KeyField, CalcProperty> changeTable =
                new SessionTableUsage<>(debugInfo, table.keys, properties.toOrderSet(), Field.<KeyField>typeGetter(),
                        new Type.Getter<CalcProperty>() {
                            public Type getType(CalcProperty key) {
                                return key.getType();
                            }
                        });
        changeTable.writeRows(sql, table.getReadSaveQuery(properties, modifier), baseClass, env, SessionTable.nonead);
        return changeTable;
    }

    private boolean pushed = false;
    public void pushVolatileStats(String id) throws SQLException {
        if(pushed = !id.matches(Settings.get().getDisableExplicitVolatileStats()))
            sql.pushVolatileStats(getOwner());
    }

    public void popVolatileStats() throws SQLException {
        if(pushed)
            sql.popVolatileStats(getOwner());
    }

    public final static FunctionSet<SessionDataProperty> keepAllSessionProperties = new SFunctionSet<SessionDataProperty>() {
        public boolean contains(SessionDataProperty element) {
            return element != isDataChanged;
        }
    };

    @Override
    public String toString() {
        return "DS@"+System.identityHashCode(this);
    }

    // нет особого смысла хранить сами потоки, так как потоки все равно в pool'ах с большой вероятностью
//    private final WeakIdentityHashSet<Thread> threads = new WeakIdentityHashSet<>();
    private int threadCount = 0;
    private final Object closeLock = new Object();
    
    private final Object noOwnersLock = new Object();

    public void registerThreadStack() {
        synchronized (closeLock) {
            threadCount++;
        }
//        threads.add(Thread.currentThread());
    }

    public void unregisterThreadStack() throws SQLException {
        boolean closed;
        synchronized (closeLock) {
            threadCount--;

            closed = tryClose();
        }
        if(!closed) // за пределами closeLock чтобы не было dead-lock'ов
            asyncFlushPendingCleaners();
//        threads.remove(Thread.currentThread());
    }

    // необходимо, так как чистка ресурсов может быть асинхронной (closeLater, unreferenced)
    private final WeakIdentityHashSet<Cleaner> pendingCleaners = new WeakIdentityHashSet<>();

    public boolean tryClose() throws SQLException { // assert closedLock
        // не осталось владельцев - закрываем
        if(threadCount == 0 && activeForms.isEmpty()) { // AssertSynchronized со всеми остальными методами DataSession
            ServerLoggers.assertLog(!isInTransaction(), "SHOULD NOT CLOSE DATASESSION IN TRANSACTION");
            synchronized (noOwnersLock) { // чтобы гарантировать полный flushPendingCleaners при close
                flushPendingCleaners();
                explicitClose();
            }
            return true;
        }
        return false;
    }
    
    private final static WeakIdentityHashSet<DataSession> pendingTransactionCleaners = new WeakIdentityHashSet<>(); // assert synchronized
    
    public static void flushPendingTransactionCleaners() throws SQLException {        
        List<DataSession> cleaners;
        synchronized (pendingTransactionCleaners) {
            cleaners = BaseUtils.toList(pendingTransactionCleaners);
            pendingTransactionCleaners.clear();
        }
        if(!cleaners.isEmpty())
            ServerLoggers.exInfoLogger.info("FLUSH PENDING TRANSACTION CLEANERS : " + cleaners.size());
        for(DataSession cleaner : cleaners)
            cleaner.asyncFlushPendingCleaners();
    }
    
    private void asyncFlushPendingCleaners() throws SQLException {
        if(isClosed())
            return;

        // тут важно, что даже если сессия войдет в транзакцию, так как она это сделает в другом потоке, этот вызов lock-free (за исключение noOwnersLock, но он не может быть в транзакции), а реализации cleaner должны быть thread-safe (в частности все очистки таблиц повиснут на lockRead), dead-clock'ов и других проблем быть не должно
        if(isInTransaction()) { // нельзя чистить в транзакции, так как изменения могут rollback'ся, а rollDrop некому делать
            ServerLoggers.exInfoLogger.info("FLUSH PENDING CLEANERS IN TRANSACTION");
            // выполним чуть позже, когда транзакция предположительно закончится
            synchronized (pendingTransactionCleaners) {
                pendingTransactionCleaners.add(this);
            }
            return;
        }
        
        synchronized (noOwnersLock) {
            AssertSynchronizedAspect.pushSuppress(); // не синхронизированно относительно owner'а (DataSession, но это ожидаемое поведение)
            try {
                flushPendingCleaners();
            } finally {
                AssertSynchronizedAspect.popSuppress();
            }
        }
    }
    
    // assert closeLock и noOwnersLock
    private void flushPendingCleaners() throws SQLException { // по идее lock-free (кроме когда не осталось owner'ов( поэтому deadlock'ов быть не должно
        // тут нужно проверять что не только  транзакция, а еще то что транзакция в этом потоке (хотя нельзя так делать, потому как deadlock'и могут быть с closedLock)
        ServerLoggers.assertLog(createdInTransaction || sql.isRestarting || !sql.isWriteLockedByCurrentThread(), "SHOULD NOT BE WRITE LOCKED"); // соответственно не может быть deadLock с flush, так как в этом потоке максимум lockRead
        // если isRestarting и writeLocked ничего страшного, так как lockWrite взял restart, а он не берет noOwnersLock ни одной "асинхронной" сессии (только своей синхронной в getLogInfo, а значит deadLock'а по этому lock'у быть не может)  
        while(true) {
            List<Cleaner> snapPendingCleaners;
            synchronized (pendingCleaners) {
                snapPendingCleaners = BaseUtils.toList(pendingCleaners);
                pendingCleaners.clear();
            }
            if(snapPendingCleaners.isEmpty())
                return;
            for(Cleaner pendingCleaner : snapPendingCleaners)
                pendingCleaner.run();
        }
    }

    @Override
    public void close() throws SQLException {
        unregisterThreadStack();
    }
}
