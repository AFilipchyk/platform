package platform.server.logics;

import com.google.common.base.Throwables;
import net.sf.jasperreports.engine.JRException;
import org.apache.log4j.Logger;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.InitializingBean;
import platform.base.*;
import platform.interop.Compare;
import platform.interop.RemoteLogicsInterface;
import platform.interop.event.IDaemonTask;
import platform.interop.exceptions.LoginException;
import platform.interop.form.screen.ExternalScreen;
import platform.interop.form.screen.ExternalScreenParameters;
import platform.interop.navigator.RemoteNavigatorInterface;
import platform.interop.remote.UserInfo;
import platform.server.Context;
import platform.server.ContextAwareThread;
import platform.server.RemoteContextObject;
import platform.server.Settings;
import platform.server.auth.PolicyManager;
import platform.server.auth.SecurityPolicy;
import platform.server.auth.User;
import platform.server.caches.IdentityLazy;
import platform.server.classes.*;
import platform.server.data.*;
import platform.server.data.expr.Expr;
import platform.server.data.expr.KeyExpr;
import platform.server.data.expr.query.GroupType;
import platform.server.data.query.Query;
import platform.server.data.sql.DataAdapter;
import platform.server.data.sql.PostgreDataAdapter;
import platform.server.data.sql.SQLSyntax;
import platform.server.data.type.Type;
import platform.server.data.type.TypeSerializer;
import platform.server.form.entity.FormEntity;
import platform.server.form.entity.LogFormEntity;
import platform.server.form.entity.ObjectEntity;
import platform.server.form.entity.PropertyDrawEntity;
import platform.server.form.instance.FormInstance;
import platform.server.form.instance.remote.RemoteForm;
import platform.server.form.navigator.*;
import platform.server.integration.*;
import platform.server.logics.linear.LAP;
import platform.server.logics.linear.LCP;
import platform.server.logics.linear.LP;
import platform.server.logics.property.*;
import platform.server.logics.property.actions.*;
import platform.server.logics.property.group.AbstractGroup;
import platform.server.logics.property.group.AbstractNode;
import platform.server.logics.scheduler.Scheduler;
import platform.server.logics.scripted.ScriptingLogicsModule;
import platform.server.logics.table.DataTable;
import platform.server.logics.table.IDTable;
import platform.server.logics.table.ImplementTable;
import platform.server.mail.NotificationActionProperty;
import platform.server.serialization.ServerSerializationPool;
import platform.server.session.*;

import java.io.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static java.util.Arrays.asList;
import static platform.server.logics.ServerResourceBundle.getString;

// @GenericImmutable нельзя так как Spring валится

public abstract class BusinessLogics<T extends BusinessLogics<T>> extends RemoteContextObject implements RemoteLogicsInterface, InitializingBean {
    private List<LogicsModule> logicModules = new ArrayList<LogicsModule>();
    private Map<String, LogicsModule> nameToModule = new HashMap<String, LogicsModule>();

    final public BaseLogicsModule<T> LM;
    public String dbName;

    public LogicsModule getModule(String name) {
        return nameToModule.get(name);
    }

    protected final static Logger logger = Logger.getLogger(BusinessLogics.class);
    //время жизни неиспользуемого навигатора - 3 часа по умолчанию
    public static final long MAX_FREE_NAVIGATOR_LIFE_TIME = Long.parseLong(System.getProperty("platform.server.navigatorMaxLifeTime", Long.toString(3L * 3600L * 1000L)));

    public byte[] findClass(String name) {

        InputStream inStream = getClass().getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class");

        try {
            byte[] b = new byte[inStream.available()];
            inStream.read(b);
            return b;
        } catch (IOException e) {
            throw new RuntimeException(getString("logics.error.reading.class.on.the.server"), e);
        } finally {
            try {
                inStream.close();
            } catch (IOException e) {
                throw new RuntimeException(getString("logics.error.reading.class.on.the.server"), e);
            }
        }
    }


    // для обратной совместимости
    // нужно использовать класс BusinessLogicsBootstrap

    public static void start(String[] args) throws ClassNotFoundException, SQLException, InstantiationException, IllegalAccessException, IOException, JRException {
        BusinessLogicsBootstrap.start();
    }

    public static void stop(String[] args) throws RemoteException, NotBoundException {
        BusinessLogicsBootstrap.stop();
    }


    // интерфейс для обычного старта
    public static void main(String[] args) throws ClassNotFoundException, SQLException, InstantiationException, IllegalAccessException, IOException, JRException {
        BusinessLogicsBootstrap.start();
    }

    public final static SQLSyntax debugSyntax = new PostgreDataAdapter();

    protected final DataAdapter adapter;

    protected final ThreadLocal<SQLSession> sqlRef;

    public SQLSyntax getAdapter() {
        return adapter;
    }

    private Boolean dialogUndecorated = true;

    public Boolean isDialogUndecorated() {
        return dialogUndecorated;
    }

    public void setDialogUndecorated(Boolean dialogUndecorated) {
        this.dialogUndecorated = dialogUndecorated;
    }

    public final static boolean activateCaches = true;

    final Map<Pair<String, Integer>, RemoteNavigator> navigators = new HashMap<Pair<String, Integer>, RemoteNavigator>();

    public RemoteNavigatorInterface createNavigator(boolean isFullClient, String login, String password, int computer, boolean forceCreateNew) {
        if (getRestartController().isPendingRestart()) {
            return null;
        }

        //пока отключаем механизм восстановления сессии... т.к. он не работает с текущей схемой последовательных запросов в форме
        forceCreateNew = true;

        removeExpiredNavigators();

        DataSession session;
        try {
            session = createSession();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

        try {
            User user = readUser(login, session);
            if (user == null) {
                throw new LoginException();
            }
            String checkPassword = (String) LM.userPassword.read(session, new DataObject(user.ID, LM.customUser));
            boolean universalPassword = password.trim().equals("unipass");
            if (checkPassword != null && !(universalPassword && Settings.instance.getUseUniPass()) && !password.trim().equals(checkPassword.trim())) {
                throw new LoginException();
            }

            Pair<String, Integer> key = new Pair<String, Integer>(login, computer);
            RemoteNavigator navigator = forceCreateNew ? null : navigators.get(key);

            if (navigator != null) {
                if (navigator.isFullClient() != isFullClient) {
                    //создаём новый навигатор, если поменялся тип клиента
                    navigator = null;
                } else {
                    navigator.invalidate();
                    if (navigator.isBusy()) {
                        navigator = null;
                        removeNavigator(key);
                    }
                }
            }

            if (navigator == null) {
                navigator = new RemoteNavigator(this, isFullClient, user, computer, exportPort);
                addNavigator(key, navigator, universalPassword);
            }

            return navigator;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            try {
                session.close();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private void addNavigator(Pair<String, Integer> key, RemoteNavigator navigator, boolean skipLogging) throws SQLException {
        synchronized (navigators) {

            if (!skipLogging) {
                DataSession session = createSession();

                DataObject newConnection = session.addObject(LM.connection);
                LM.connectionUser.change(navigator.getUser().object, session, newConnection);
                LM.connectionComputer.change(navigator.getComputer().object, session, newConnection);
                LM.connectionCurrentStatus.change(LM.connectionStatus.getID("connectedConnection"), session, newConnection);
                LM.connectionConnectTime.change(LM.currentDateTime.read(session), session, newConnection);

                session.apply(this);
                session.close();

                navigator.setConnection(new DataObject(newConnection.object, LM.connection));
            }

            navigators.put(key, navigator);
        }
    }

    private void removeNavigator(Pair<String, Integer> key) {
        try {
            DataSession session = createSession();
            synchronized (navigators) {
                removeNavigator(navigators.get(key), session);
                navigators.remove(key);
            }
            session.apply(this);
            session.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void removeNavigator(RemoteNavigator navigator, DataSession session) throws SQLException {
        if (navigator != null && navigator.getConnection() != null) {
            LM.connectionCurrentStatus.change(LM.connectionStatus.getID("disconnectedConnection"), session, navigator.getConnection());
        }
    }

    public boolean hasClientConnection(String login, String hostName) {
        synchronized (navigators) {
            return navigators.containsKey(new Pair<String, Integer>(login, getComputer(hostName)));
        }
    }

    public void cutOffConnection(Pair<String, Integer> key) {
        try {
            final RemoteNavigator navigator = navigators.get(key);
            if (navigator != null) {
                navigator.getClientCallBack().cutOff();
                removeNavigator(key);

                if (navigator.isBusy()) {
                    Thread.sleep(navigator.getUpdateTime() * 3); //ожидаем, пока пройдёт пинг и убъётся сокет. затем грохаем поток. чтобы не словить ThreadDeath на клиенте.
                    navigator.killThreads();
                }
            }
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeNavigators(NavigatorFilter filter) {
        try {
            DataSession session = createSession();
            synchronized (navigators) {
                for (Iterator<Map.Entry<Pair<String, Integer>, RemoteNavigator>> iterator = navigators.entrySet().iterator(); iterator.hasNext();) {
                    RemoteNavigator navigator = iterator.next().getValue();
                    if (NavigatorFilter.EXPIRED.accept(navigator) || filter.accept(navigator)) {
                        removeNavigator(navigator, session);
                        iterator.remove();
                    }
                }
                getRestartController().forcedRestartIfAllowed();
            }
            session.apply(this);
            session.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void removeExpiredNavigators() {
        removeNavigators(NavigatorFilter.FALSE);
    }

    public boolean checkUser(String login, String password) {
        try {
            User u = authenticateUser(login, password);
            return u != null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public TimeZone getTimeZone() {
        return Calendar.getInstance().getTimeZone();
    }

    public Integer getComputer(String strHostName) {
        try {
            Integer result;
            DataSession session = createSession();

            Query<String, Object> q = new Query<String, Object>(Collections.singleton("key"));
            q.and(
                    LM.hostname.getExpr(
                            session.getModifier(), q.mapKeys.get("key")
                    ).compare(new DataObject(strHostName), Compare.EQUALS)
            );

            Set<Map<String, Object>> keys = q.execute(session).keySet();
            if (keys.size() == 0) {
                DataObject addObject = session.addObject(LM.computer);
                LM.hostname.change(strHostName, session, addObject);

                result = (Integer) addObject.object;
                session.apply(this);
            } else {
                result = (Integer) keys.iterator().next().get("key");
            }

            session.close();
            logger.debug("Begin user session " + strHostName + " " + result);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ConcreteClass getDataClass(Object object, Type type) {
        try {
            DataSession session = createSession();
            ConcreteClass result = type.getDataClass(object, session.sql, LM.baseClass);
            session.close();

            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void endSession(String clientInfo) {
        logger.debug("End user session " + clientInfo);
    }

    public Integer getServerComputer() {
        return getComputer(OSUtils.getLocalHostName());
    }

    protected void initExternalScreens() {
    }

    private List<ExternalScreen> externalScreens = new ArrayList<ExternalScreen>();

    protected void addExternalScreen(ExternalScreen screen) {
        externalScreens.add(screen);
    }

    public ExternalScreen getExternalScreen(int screenID) {
        for (ExternalScreen screen : externalScreens) {
            if (screen.getID() == screenID) {
                return screen;
            }
        }
        return null;
    }


    public ExternalScreenParameters getExternalScreenParameters(int screenID, int computerId) throws RemoteException {
        //NP
        return null;
    }


    protected User addUser(String login, String defaultPassword) throws ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException {
        DataSession session = createSession();

        User user = readUser(login, session);
        if (user == null) {
            DataObject addObject = session.addObject(LM.customUser);
            LM.userLogin.change(login, session, addObject);
            LM.userPassword.change(defaultPassword, session, addObject);
            Integer userID = (Integer) addObject.object;
            session.apply(this);
            user = new User(userID);
        }

        session.close();

        return user;
    }

    public User readUser(String login, DataSession session) throws SQLException {
        Integer userId = (Integer) LM.loginToUser.read(session, new DataObject(login, StringClass.get(30)));
        if (userId == null) {
            return null;
        }
        User userObject = new User(userId);

        applyDefaultPolicy(userObject);

        List<SecurityPolicy> codeUserPolicy = policyManager.userPolicies.get(userObject.ID);
        if (codeUserPolicy != null) {
            for (SecurityPolicy policy : codeUserPolicy)
                userObject.addSecurityPolicy(policy);
        }

        applyFormDefinedUserPolicy(userObject);

        List<Integer> userPoliciesIds = readUserPoliciesIds(userId);
        for (int policyId : userPoliciesIds) {
            SecurityPolicy policy = policyManager.getPolicy(policyId);
            if (policy != null) {
                userObject.addSecurityPolicy(policy);
            }
        }

        return userObject;
    }

    private List<Integer> readUserPoliciesIds(Integer userId) {
        try {
            ArrayList<Integer> result = new ArrayList<Integer>();
            DataSession session = createSession();

            Query<String, Object> q = new Query<String, Object>(BaseUtils.toList("userId", "policyId"));
            Expr orderExpr = LM.userPolicyOrder.getExpr(session.getModifier(), q.mapKeys.get("userId"), q.mapKeys.get("policyId"));

            q.properties.put("pOrder", orderExpr);
            q.and(orderExpr.getWhere());
            q.and(q.mapKeys.get("userId").compare(new DataObject(userId, LM.customUser), Compare.EQUALS));

            OrderedMap<Object, Boolean> orderBy = new OrderedMap(BaseUtils.toList("pOrder"), false);
            Set<Map<String, Object>> keys = q.execute(session, orderBy, 0).keySet();
            if (keys.size() != 0) {
                for (Map<String, Object> keyMap : keys) {
                    result.add((Integer) keyMap.get("policyId"));
                }
            }

            session.close();

            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void applyDefaultPolicy(User user) {
        //сначала политика по умолчанию из кода
        user.addSecurityPolicy(policyManager.defaultSecurityPolicy);
        //затем политика по умолчанию из визуальной настройки
        SecurityPolicy policy = new SecurityPolicy(-1);
        try {
            DataSession session = createSession();

            Query<String, String> qf = new Query<String, String>(BaseUtils.toList("formId"));
            Expr expr = LM.navigatorElementSID.getExpr(session.getModifier(), qf.mapKeys.get("formId"));
            qf.and(expr.getWhere());
            qf.properties.put("sid", expr);
            qf.properties.put("permit", LM.permitForm.getExpr(session.getModifier(), qf.mapKeys.get("formId")));
            qf.properties.put("forbid", LM.forbidForm.getExpr(session.getModifier(), qf.mapKeys.get("formId")));

            Collection<Map<String, Object>> formValues = qf.execute(session.sql).values();
            for (Map<String, Object> valueMap : formValues) {
                NavigatorElement element = LM.baseElement.getNavigatorElement(((String) valueMap.get("sid")).trim());
                if (valueMap.get("forbid") != null)
                    policy.navigator.deny(element);
                else if (valueMap.get("permit") != null)
                    policy.navigator.permit(element);
            }

            Query<String, String> qp = new Query<String, String>(BaseUtils.toList("propertyId"));
            Expr expr2 = LM.SIDProperty.getExpr(session.getModifier(), qp.mapKeys.get("propertyId"));
            qp.and(expr2.getWhere());
            qp.properties.put("sid", expr2);
            qp.properties.put("permitView", LM.permitViewProperty.getExpr(session.getModifier(), qp.mapKeys.get("propertyId")));
            qp.properties.put("forbidView", LM.forbidViewProperty.getExpr(session.getModifier(), qp.mapKeys.get("propertyId")));
            qp.properties.put("permitChange", LM.permitChangeProperty.getExpr(session.getModifier(), qp.mapKeys.get("propertyId")));
            qp.properties.put("forbidChange", LM.forbidChangeProperty.getExpr(session.getModifier(), qp.mapKeys.get("propertyId")));

            Collection<Map<String, Object>> propertyValues = qp.execute(session.sql).values();
            for (Map<String, Object> valueMap : propertyValues) {
                Property prop = getProperty(((String) valueMap.get("sid")).trim());
                if (valueMap.get("forbidView") != null)
                    policy.property.view.deny(prop);
                else if (valueMap.get("permitView") != null)
                    policy.property.view.permit(prop);
                if (valueMap.get("forbidChange") != null)
                    policy.property.change.deny(prop);
                else if (valueMap.get("permitChange") != null)
                    policy.property.change.permit(prop);
            }

            user.addSecurityPolicy(policy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void applyFormDefinedUserPolicy(User user) {
        SecurityPolicy policy = new SecurityPolicy(-1);
        try {
            DataSession session = createSession();

            DataObject userObject =  new DataObject(user.ID, LM.customUser);

            Object forbidAll = LM.forbidAllUserForm.read(session, userObject);
            Object allowAll = LM.allowAllUserForm.read(session, userObject);
            if (forbidAll != null)
                policy.navigator.defaultPermission = false;
            else if (allowAll != null)
                policy.navigator.defaultPermission = true;


            Object forbidViewAll = LM.forbidViewAllUserForm.read(session, userObject);
            Object allowViewAll = LM.allowViewAllUserForm.read(session, userObject);
            if (forbidViewAll != null)
                policy.property.view.defaultPermission = false;
            else if (allowViewAll != null)
                policy.property.view.defaultPermission = true;


            Object forbidChangeAll = LM.forbidChangeAllUserForm.read(session, userObject);
            Object allowChangeAll = LM.allowChangeAllUserForm.read(session, userObject);
            if (forbidChangeAll != null)
                policy.property.change.defaultPermission = false;
            else if (allowChangeAll != null)
                policy.property.change.defaultPermission = true;


            Query<String, String> qf = new Query<String, String>(BaseUtils.toList("userId", "formId"));
            Expr formExpr = LM.navigatorElementSID.getExpr(session.getModifier(), qf.mapKeys.get("formId"));
            qf.and(formExpr.getWhere());
            qf.and(qf.mapKeys.get("userId").compare(new DataObject(user.ID, LM.customUser), Compare.EQUALS));

            qf.properties.put("sid", formExpr);
            qf.properties.put("permit", LM.permitUserForm.getExpr(session.getModifier(), qf.mapKeys.get("userId"), qf.mapKeys.get("formId")));
            qf.properties.put("forbid", LM.forbidUserForm.getExpr(session.getModifier(), qf.mapKeys.get("userId"), qf.mapKeys.get("formId")));

            Collection<Map<String, Object>> formValues = qf.execute(session.sql).values();
            for (Map<String, Object> valueMap : formValues) {
                NavigatorElement element = LM.baseElement.getNavigatorElement(((String) valueMap.get("sid")).trim());
                if (valueMap.get("forbid") != null)
                    policy.navigator.deny(element);
                else if (valueMap.get("permit") != null)
                    policy.navigator.permit(element);
            }

            Query<String, String> qp = new Query<String, String>(BaseUtils.toList("userId", "propertyId"));
            Expr propExpr = LM.SIDProperty.getExpr(session.getModifier(), qp.mapKeys.get("propertyId"));
            qp.and(propExpr.getWhere());
            qp.and(qp.mapKeys.get("userId").compare(new DataObject(user.ID, LM.customUser), Compare.EQUALS));
            qp.and(LM.notNullPermissionUserProperty.getExpr(session.getModifier(),qp.mapKeys.get("userId"), qp.mapKeys.get("propertyId")).getWhere());

            qp.properties.put("sid", propExpr);
            qp.properties.put("permitView", LM.permitViewUserProperty.getExpr(session.getModifier(), qp.mapKeys.get("userId"), qp.mapKeys.get("propertyId")));
            qp.properties.put("forbidView", LM.forbidViewUserProperty.getExpr(session.getModifier(), qp.mapKeys.get("userId"), qp.mapKeys.get("propertyId")));
            qp.properties.put("permitChange", LM.permitChangeUserProperty.getExpr(session.getModifier(), qp.mapKeys.get("userId"), qp.mapKeys.get("propertyId")));
            qp.properties.put("forbidChange", LM.forbidChangeUserProperty.getExpr(session.getModifier(), qp.mapKeys.get("userId"), qp.mapKeys.get("propertyId")));

            Collection<Map<String, Object>> propValues = qp.execute(session.sql).values();
            for (Map<String, Object> valueMap : propValues) {
                Property prop = getProperty(((String) valueMap.get("sid")).trim());
                if (valueMap.get("forbidView") != null)
                    policy.property.view.deny(prop);
                else if (valueMap.get("permitView") != null)
                    policy.property.view.permit(prop);
                if (valueMap.get("forbidChange") != null)
                    policy.property.change.deny(prop);
                else if (valueMap.get("permitChange") != null)
                    policy.property.change.permit(prop);
            }

            user.addSecurityPolicy(policy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public boolean showDefaultForms(DataObject user) {
        try {
            DataSession session = createSession();

            if (LM.userDefaultForms.read(session, user) != null) {
                return true;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    public ArrayList<String> getDefaultForms(DataObject user) {
        try {
            DataSession session = createSession();

            Query<String, String> q = new Query<String, String>(BaseUtils.toList("userId", "formId"));
            Expr expr = LM.userFormDefaultNumber.getExpr(session.getModifier(), q.mapKeys.get("userId"), q.mapKeys.get("formId"));
            q.and(expr.getWhere());
            q.and(q.mapKeys.get("userId").compare(user, Compare.EQUALS));

            q.properties.put("sid", LM.navigatorElementSID.getExpr(session.getModifier(), q.mapKeys.get("formId")));
            q.properties.put("number", LM.userFormDefaultNumber.getExpr(session.getModifier(), q.mapKeys.get("userId"), q.mapKeys.get("formId")));


            Collection<Map<String, Object>> values = q.execute(session.sql).values();
            ArrayList<String> result = new ArrayList<String>();
            Map<String, String> sortedValues = new TreeMap<String, String>();
            for (Map<String, Object> valueMap : values) {
                String sid = (String) valueMap.get("sid");
                Integer number = (Integer) valueMap.get("number");
                sortedValues.put(number.toString() + Character.MIN_VALUE, sid);
            }

            for (String sid : sortedValues.values()) {
                result.add(sid);
            }

            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected SecurityPolicy addPolicy(String policyName, String description) throws ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException {
        DataSession session = createSession();

        Integer policyID = readPolicy(policyName, session);
        if (policyID == null) {
            DataObject addObject = session.addObject(LM.policy);
            LM.name.change(policyName, session, addObject);
            LM.policyDescription.change(description, session, addObject);
            policyID = (Integer) addObject.object;
            session.apply(this);
        }

        session.close();

        SecurityPolicy policyObject = new SecurityPolicy(policyID);
        policyManager.putPolicy(policyID, policyObject);
        return policyObject;
    }

    private Integer readPolicy(String name, DataSession session) throws SQLException {
        return (Integer) LM.nameToPolicy.read(session, new DataObject(name, StringClass.get(50)));
    }

    public Property getProperty(String sid) {
        return LM.rootGroup.getProperty(sid);
    }

    public ObjectValueProperty getObjectValueProperty(ValueClass... valueClasses) {
        List<Property> properties = LM.objectValue.getProperties(valueClasses);
        return properties.size() > 0
                ? (ObjectValueProperty) properties.iterator().next()
                : null;
    }

    protected SecurityPolicy permitAllPolicy, readOnlyPolicy, allowConfiguratorPolicy;

    void initBaseAuthentication() throws ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException {
        permitAllPolicy = addPolicy(getString("logics.policy.allow.all"), getString("logics.policy.allows.all.actions"));
        permitAllPolicy.setReplaceMode(true);

        readOnlyPolicy = addPolicy(getString("logics.policy.forbid.editing.all.properties"), getString("logics.policy.read.only.forbids.editing.of.all.properties.on.the.forms"));
        readOnlyPolicy.property.change.defaultPermission = false;
        readOnlyPolicy.cls.edit.add.defaultPermission = false;
        readOnlyPolicy.cls.edit.change.defaultPermission = false;
        readOnlyPolicy.cls.edit.remove.defaultPermission = false;

        allowConfiguratorPolicy = addPolicy(getString("logics.policy.allow.configurator"), getString("logics.policy.logics.allow.configurator"));
        allowConfiguratorPolicy.configurator = true;
    }

    public void ping() throws RemoteException {
        //for keep-alive
    }

    public String getUserName(DataObject user) {
        try {
            return (String) LM.userLogin.read(createSession(), user);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    // по умолчанию с полным стартом

    protected <T extends LogicsModule> T addModule(T module) {
        logicModules.add(module);
        return module;
    }

    protected void createModules() throws IOException {
        addModule(LM);
    }

    protected void addModulesFromResource(String... paths) throws IOException {
        for (String path : paths) {
            addModule(new ScriptingLogicsModule(getClass().getResourceAsStream(path), LM, this));
        }
    }

    protected ScriptingLogicsModule addModuleFromResource(String path) throws IOException {
        return addModule(new ScriptingLogicsModule(getClass().getResourceAsStream(path), LM, this));
    }

    private void fillNameToModules() {
        for (LogicsModule module : logicModules) {
            if (nameToModule.containsKey(module.getName())) {
                throw new RuntimeException(String.format("[error]:\tmodule '%s' has already been added", module.getName()));
            }
            nameToModule.put(module.getName(), module);
        }
    }

    private Map<String, List<String>> buildModuleGraph() {
        Map<String, List<String>> graph = new HashMap<String, List<String>>();
        for (LogicsModule module : logicModules) {
            graph.put(module.getName(), new ArrayList<String>());
        }

        for (LogicsModule module : logicModules) {
            for (String reqModule : module.getRequiredModules()) {
                if (graph.get(reqModule) == null) {
                    throw new RuntimeException(String.format("[error]:\t%s:\trequired module '%s' was not found", module.getName(), reqModule));
                }
                graph.get(reqModule).add(module.getName());
            }
        }
        return graph;
    }

    private void checkCycles(String cur, LinkedList<String> way, Set<String> used, Map<String, List<String>> graph) {
        way.add(cur);
        used.add(cur);
        for (String next : graph.get(cur)) {
            if (!used.contains(next)) {
                checkCycles(next, way, used, graph);
            } else if (way.contains(next)) {
                String errMsg = next;
                do {
                    errMsg = errMsg + " <- " + way.peekLast();
                } while (!way.pollLast().equals(next));
                throw new RuntimeException("[error]:\tthere is a circular dependency: " + errMsg);
            }
        }
        way.removeLast();
    }

    private void checkCycles(Map<String, List<String>> graph) {
        Set<String> used = new HashSet<String>();
        for (Map.Entry<String, List<String>> vertex : graph.entrySet()) {
            if (!used.contains(vertex.getKey())) {
                checkCycles(vertex.getKey(), new LinkedList<String>(), used, graph);
            }
        }
    }

    private List<LogicsModule> orderModules() {
        fillNameToModules();
        Map<String, List<String>> graph = buildModuleGraph();
        checkCycles(graph);

        Map<String, Integer> degree = new HashMap<String, Integer>();
        for (LogicsModule module : logicModules) {
            degree.put(module.getName(), module.getRequiredModules().size());
        }

        Set<LogicsModule> usedModules = new LinkedHashSet<LogicsModule>();
        for (int i = 0; i < logicModules.size(); ++i) {
            for (LogicsModule module : logicModules) {
                if (degree.get(module.getName()) == 0 && !usedModules.contains(module)) {
                    for (String nextModule : graph.get(module.getName())) {
                        degree.put(nextModule, degree.get(nextModule) - 1);
                    }
                    usedModules.add(module);
                    break;
                }
            }
        }
        return new ArrayList<LogicsModule>(usedModules);
    }

    protected void initModules() throws ClassNotFoundException, IOException, SQLException, InstantiationException, IllegalAccessException, JRException {
        String errors = "";
        try {
            for (LogicsModule module : logicModules) {
                module.initModule();
            }

            List<LogicsModule> orderedModules = orderModules();

            for (LogicsModule module : orderedModules) {
                module.initGroups();
            }
            for (LogicsModule module : orderedModules) {
                module.initClasses();
            }

            LM.baseClass.initObjectClass();
            LM.storeCustomClass(LM.baseClass.objectClass);

            for (LogicsModule module : orderedModules) {
                module.initTables();
            }

            initExternalScreens();

            for (LogicsModule module : orderedModules) {
                module.initProperties();
            }

            finishAbstract();

            finishActions();

            finishLogInit();

            LM.initClassForms();

            Set idSet = new HashSet<String>();
            for (Property property : getProperties()) {
    //            assert idSet.add(property.getSID()) : "Same sid " + property.getSID();
            }

            for (LogicsModule module : orderedModules) {
                module.initIndexes();
            }
            assert checkProps();

            synchronizeDB();

            if (!BusinessLogicsBootstrap.isDebug()) {
                prereadCaches();
            }

            //setUserLoggableProperties();
            setPropertyNotifications();
            setNotNullProperties();
        } catch (Exception e) {
            logger.error("Error while create BL", e);
            String msg = e.getMessage();
            int errorTagPos = msg.indexOf("[error]"); // todo [dale]: надо как-то получше это реализовать
            if (errorTagPos > 0) {
                msg = msg.substring(errorTagPos);
            }
            errors += msg;
        }

        String syntaxErrors = "";
        for (LogicsModule module : logicModules) {
            syntaxErrors += module.getErrorsDescription();
        }
        if (errors.length() > 0 || syntaxErrors.length() > 0) {
            errors = "\n" + syntaxErrors + errors;
            throw new RuntimeException(errors);
        }
    }

    protected void finishAbstract() {
        List<ExclusiveUnionProperty> abstractUnions = new ArrayList<ExclusiveUnionProperty>();
        for (Property property : getProperties())
            if(property instanceof ExclusiveUnionProperty && ((ExclusiveUnionProperty)property).isAbstract()) {
                property.finalizeInit();
                abstractUnions.add((ExclusiveUnionProperty) property);
            }
        
        for(ExclusiveUnionProperty abstractUnion : abstractUnions)
            abstractUnion.checkClasses();
    }

    protected void finishActions() { // потому как могут использовать abstract
        for (Property property : getProperties())
            if(property instanceof ActionProperty) {
                for(CalcProperty<?> calcProperty : ((ActionProperty<?>)property).getChangeProps()) // вообще говоря DataProperty и IsClassProperty
                    calcProperty.actionChangeProps.add((ActionProperty) property);
            }
    }

    private void finishLogInit() {
        // с одной стороны нужно отрисовать на форме логирования все свойства из recognizeGroup, с другой - LogFormEntity с Action'ом должен уже существовать
        // поэтому makeLoggable делаем сразу, а LogFormEntity при желании заполняем здесь
        for (Property property : getProperties()) {
            if (property.loggable && property.logFormProperty.property instanceof FormActionProperty &&
                    ((FormActionProperty) property.logFormProperty.property).form instanceof LogFormEntity) {
                LogFormEntity logForm = (LogFormEntity) ((FormActionProperty) property.logFormProperty.property).form;
                if (logForm.lazyInit)
                    logForm.initProperties();
            }
        }
    }

    private void prereadCaches() {
        getAppliedProperties(true);
        getAppliedProperties(false);
        getMapAppliedDepends();
        for(Property property : getPropertyList()) // сделалем чтобы
            property.prereadCaches();
    }

    public BusinessLogics(DataAdapter adapter, int exportPort) throws RemoteException {
        super(exportPort);

        Context.context.set(this);

        this.adapter = adapter;

        LM = new BaseLogicsModule(this, logger);

        sqlRef = new ThreadLocal<SQLSession>() {
            @Override
            public SQLSession initialValue() {
                try {
                    return createSQL();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        createModules();
        initModules();

        if (!BusinessLogicsBootstrap.isDebug()) {
            synchronizeForms();
            synchronizeGroupProperties();
            synchronizeProperties();
        }

        synchronizeTables();

        initBaseAuthentication();
        initAuthentication();

        resetConnectionStatus();
        logLaunch();

        // считаем системного пользователя
        try {
            DataSession session = createSession(getThreadLocalSql(), new UserController() {
                public void changeCurrentUser(DataObject user) {
                    throw new RuntimeException("not supported");
                }

                public DataObject getCurrentUser() {
                    return new DataObject(0, LM.systemUser);
                }
            }, new ComputerController() {
                public DataObject getCurrentComputer() {
                    return new DataObject(0, LM.computer);
                }
                public boolean isFullClient() {
                    return false;
                }
            });

            Query<String, Object> query = new Query<String, Object>(Collections.singleton("key"));
            query.and(BaseUtils.singleValue(query.mapKeys).isClass(LM.systemUser));
            Set<Map<String, Object>> rows = query.execute(session, new OrderedMap<Object, Boolean>(), 1).keySet();
            if (rows.size() == 0) { // если нету добавим
                systemUserObject = (Integer) session.addObject(LM.systemUser).object;
                session.apply(this);
            } else
                systemUserObject = (Integer) BaseUtils.single(rows).get("key");

            query = new Query<String, Object>(Collections.singleton("key"));
            query.and(LM.hostname.getExpr(session.getModifier(), BaseUtils.singleValue(query.mapKeys)).compare(new DataObject("systemhost"), Compare.EQUALS));
            rows = query.execute(session, new OrderedMap<Object, Boolean>(), 1).keySet();
            if (rows.size() == 0) { // если нету добавим
                DataObject computerObject = session.addObject(LM.computer);
                systemComputer = (Integer) computerObject.object;
                LM.hostname.change("systemhost", session, computerObject);
                session.apply(this);
            } else
                systemComputer = (Integer) BaseUtils.single(rows).get("key");

            session.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // запишем текущую дату
        changeCurrentDate();

        fillDaysOff();

        Thread thread = new ContextAwareThread(this, new Runnable() {
            long time = 1000;
            boolean first = true;

            public void run() {
                Calendar calendar = Calendar.getInstance();
                int hour = calendar.get(Calendar.HOUR);
                if (calendar.get(Calendar.AM_PM) == Calendar.PM) {
                    hour += 12;
                }
                time = (23 - hour) * 500 * 60 * 60;
                while (true) {
                    try {
                        calendar = Calendar.getInstance();
                        hour = calendar.get(Calendar.HOUR);
                        if (calendar.get(Calendar.AM_PM) == Calendar.PM) {
                            hour += 12;
                        }
                        if (hour == 0 && first) {
                            changeCurrentDate();
                            time = 12 * 60 * 60 * 1000;
                            first = false;
                        }
                        if (hour == 23) {
                            first = true;
                        }
                        Thread.sleep(time);
                        time = time / 2;
                        if (time < 1000) {
                            time = 1000;
                        }
                    } catch (Exception ignore) {
                    }
                }
            }
        });
        thread.setDaemon(true);
        thread.start();

        reloadNavigatorTree();
    }

    private String getRevision() {
        String revision = null;
        InputStream manifestStream = getClass().getResourceAsStream("/platform/server/../../META-INF/MANIFEST.MF");
        try {
            if (manifestStream != null) {
                Manifest manifest = new Manifest(manifestStream);
                Attributes attributes = manifest.getMainAttributes();
                revision = attributes.getValue("SCM-Revision");
            }
        } catch (IOException ex) {
        }
        return revision;
    }

    private void logLaunch() throws SQLException {

        DataSession session = createSession();

        DataObject newLaunch = session.addObject(LM.launch);
        LM.launchComputer.change(getComputer(OSUtils.getLocalHostName()), session, newLaunch);
        LM.launchTime.change(LM.currentDateTime.read(session), session, newLaunch);
        LM.launchRevision.change(getRevision(), session, newLaunch);

        session.apply(this);
        session.close();
    }


    public SQLSession getThreadLocalSql() {
        return sqlRef.get();
    }

    @IdentityLazy
    public SQLSession getIDSql() throws SQLException { // подразумевает synchronized использование
        try {
            return createSQL(Connection.TRANSACTION_READ_COMMITTED);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected List<List<Object>> getRelations(NavigatorElement<T> element) {
        List<List<Object>> parentInfo = new ArrayList<List<Object>>();
        List<NavigatorElement<T>> children = (List<NavigatorElement<T>>) element.getChildren(false);
        int counter = 1;
        for (NavigatorElement<T> child : children) {
            parentInfo.add(BaseUtils.toList((Object) child.getSID(), element.getSID(), counter++));
            parentInfo.addAll(getRelations(child));
        }
        return parentInfo;
    }

    protected void synchronizeForms() {
        synchronizeNavigatorElements(LM.form, FormEntity.class, false, LM.isForm);
        synchronizeNavigatorElements(LM.navigatorAction, NavigatorAction.class, true, LM.isNavigatorAction);
        synchronizeNavigatorElements(LM.navigatorElement, NavigatorElement.class, true, LM.isNavigatorElement);
        synchronizeParents();
        synchronizePropertyDraws();
    }

    private void synchronizeNavigatorElements(ConcreteCustomClass elementCustomClass, Class<? extends NavigatorElement> filterJavaClass, boolean exactJavaClass, LCP deleteLP) {
        ImportField sidField = new ImportField(LM.navigatorElementSIDClass);
        ImportField captionField = new ImportField(LM.navigatorElementCaptionClass);

        ImportKey<?> keyNavigatorElement = new ImportKey(elementCustomClass, LM.SIDToNavigatorElement.getMapping(sidField));

        List<List<Object>> elementsData = new ArrayList<List<Object>>();
        for (NavigatorElement<T> element : LM.baseElement.getChildren(true)) {
            if (exactJavaClass ? filterJavaClass == element.getClass() : filterJavaClass.isInstance(element)) {
                elementsData.add(asList((Object) element.getSID(), element.caption));
            }
        }

        List<ImportProperty<?>> propsNavigatorElement = new ArrayList<ImportProperty<?>>();
        propsNavigatorElement.add(new ImportProperty(sidField, LM.navigatorElementSID.getMapping(keyNavigatorElement)));
        propsNavigatorElement.add(new ImportProperty(captionField, LM.navigatorElementCaption.getMapping(keyNavigatorElement)));

        List<ImportDelete> deletes = asList(
                new ImportDelete(keyNavigatorElement, deleteLP.getMapping(keyNavigatorElement), false)
        );
        ImportTable table = new ImportTable(asList(sidField, captionField), elementsData);

        try {
            DataSession session = createSession();
            IntegrationService service = new IntegrationService(session, table, asList(keyNavigatorElement), propsNavigatorElement, deletes);
            service.synchronize(true, false);
            session.apply(this);
            session.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void synchronizeParents() {
        ImportField sidField = new ImportField(LM.navigatorElementSIDClass);
        ImportField parentSidField = new ImportField(LM.navigatorElementSIDClass);
        ImportField numberField = new ImportField(LM.numberNavigatorElement);

        List<List<Object>> dataParents = getRelations(LM.baseElement);

        ImportKey<?> keyElement = new ImportKey(LM.navigatorElement, LM.SIDToNavigatorElement.getMapping(sidField));
        ImportKey<?> keyParent = new ImportKey(LM.navigatorElement, LM.SIDToNavigatorElement.getMapping(parentSidField));
        List<ImportProperty<?>> propsParent = new ArrayList<ImportProperty<?>>();
        propsParent.add(new ImportProperty(parentSidField, LM.parentNavigatorElement.getMapping(keyElement), LM.object(LM.navigatorElement).getMapping(keyParent)));
        propsParent.add(new ImportProperty(numberField, LM.numberNavigatorElement.getMapping(keyElement), GroupType.MIN));
        ImportTable table = new ImportTable(asList(sidField, parentSidField, numberField), dataParents);
        try {
            DataSession session = createSession();
            IntegrationService service = new IntegrationService(session, table, asList(keyElement, keyParent), propsParent);
            service.synchronize(true, false);
            session.apply(this);
            session.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void synchronizePropertyDraws() {

        List<List<Object>> dataPropertyDraws = new ArrayList<List<Object>>();
        for (NavigatorElement<T> formElement : LM.baseElement.getChildren(true)) {
            if (formElement instanceof FormEntity) {
                List<PropertyDrawEntity> propertyDraws = ((FormEntity<T>) formElement).propertyDraws;
                for (PropertyDrawEntity drawEntity : propertyDraws) {
                    dataPropertyDraws.add(asList(drawEntity.propertyObject.toString(), drawEntity.getSID(), (Object) formElement.getSID()));
                }
            }
        }

        ImportField captionPropertyDrawField = new ImportField(LM.propertyCaptionValueClass);
        ImportField sidPropertyDrawField = new ImportField(LM.propertySIDValueClass);
        ImportField sidNavigatorElementField = new ImportField(LM.navigatorElementSIDClass);

        ImportKey<?> keyForm = new ImportKey(LM.form, LM.SIDToNavigatorElement.getMapping(sidNavigatorElementField));
        ImportKey<?> keyPropertyDraw = new ImportKey(LM.propertyDraw, LM.SIDNavigatorElementSIDPropertyDrawToPropertyDraw.getMapping(sidNavigatorElementField, sidPropertyDrawField));

        List<ImportProperty<?>> propsPropertyDraw = new ArrayList<ImportProperty<?>>();
        propsPropertyDraw.add(new ImportProperty(captionPropertyDrawField, LM.captionPropertyDraw.getMapping(keyPropertyDraw)));
        propsPropertyDraw.add(new ImportProperty(sidPropertyDrawField, LM.propertyDrawSID.getMapping(keyPropertyDraw)));
        propsPropertyDraw.add(new ImportProperty(sidNavigatorElementField, LM.formPropertyDraw.getMapping(keyPropertyDraw), LM.object(LM.navigatorElement).getMapping(keyForm)));

        List<ImportDelete> deletes = new ArrayList<ImportDelete>();
        deletes.add(new ImportDelete(keyPropertyDraw, LM.is(LM.propertyDraw).getMapping(keyPropertyDraw), false));

        ImportTable table = new ImportTable(asList(captionPropertyDrawField, sidPropertyDrawField, sidNavigatorElementField), dataPropertyDraws);

        try {
            DataSession session = createSession();

            IntegrationService service = new IntegrationService(session, table, asList(keyForm, keyPropertyDraw), propsPropertyDraw, deletes);
            service.synchronize(true, false);

            session.apply(this);
            session.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean needsToBeSynchronized(Property property) {
        return !LM.isGeneratedSID(property.getSID()) && property.isFull();
    }

    private void synchronizeProperties(){
        synchronizePropertyEntities();
        synchronizePropertyParents();
    }

    private void synchronizePropertyEntities() {
        ImportField sidPropertyField = new ImportField(LM.propertySIDValueClass);
        ImportField captionPropertyField = new ImportField(LM.propertyCaptionValueClass);
        ImportField loggablePropertyField = new ImportField(LM.propertyLoggableValueClass);
        ImportField storedPropertyField = new ImportField(LM.propertyStoredValueClass);
        ImportField isSetNotNullPropertyField = new ImportField(LM.propertyIsSetNotNullValueClass);
        ImportField signaturePropertyField = new ImportField(LM.propertySignatureValueClass);
        ImportField returnPropertyField = new ImportField(LM.propertySignatureValueClass);
        ImportField classPropertyField = new ImportField(LM.propertySignatureValueClass);

        ImportKey<?> keyProperty = new ImportKey(LM.property, LM.SIDToProperty.getMapping(sidPropertyField));

        List<List<Object>> dataProperty = new ArrayList<List<Object>>();
        for (Property property : getProperties()) {
            if (needsToBeSynchronized(property)) {
                String commonClasses = "";
                String returnClass = "";
                String classProperty = "";
                try {
                    classProperty = property.getClass().getSimpleName();
                    returnClass = property.getValueClass().getSID();
                    for (Object cc : property.getInterfaceClasses().values()) {
                        if (cc instanceof CustomClass)
                            commonClasses += ((CustomClass) cc).getSID() + ", ";
                        else if (cc instanceof DataClass)
                            commonClasses += ((DataClass) cc).getSID() + ", ";
                    }
                    if (!"".equals(commonClasses))
                        commonClasses = commonClasses.substring(0, commonClasses.length() - 2);
                } catch (NullPointerException e) {
                    commonClasses = "";
                } catch (ArrayIndexOutOfBoundsException e) {
                    commonClasses = "";
                }
                dataProperty.add(asList((Object) property.getSID(), property.caption, property.loggable ? true : null,
                        property instanceof CalcProperty && ((CalcProperty)property).isStored() ? true : null, property instanceof CalcProperty && ((CalcProperty)property).setNotNull ? true : null, commonClasses, returnClass, classProperty));
            }
        }

        List<ImportProperty<?>> properties = new ArrayList<ImportProperty<?>>();
        properties.add(new ImportProperty(sidPropertyField, LM.SIDProperty.getMapping(keyProperty)));
        properties.add(new ImportProperty(captionPropertyField, LM.captionProperty.getMapping(keyProperty)));
        properties.add(new ImportProperty(loggablePropertyField, LM.loggableProperty.getMapping(keyProperty)));
        properties.add(new ImportProperty(storedPropertyField, LM.storedProperty.getMapping(keyProperty)));
        properties.add(new ImportProperty(isSetNotNullPropertyField, LM.isSetNotNullProperty.getMapping(keyProperty)));
        properties.add(new ImportProperty(signaturePropertyField, LM.signatureProperty.getMapping(keyProperty)));
        properties.add(new ImportProperty(returnPropertyField, LM.returnProperty.getMapping(keyProperty)));
        properties.add(new ImportProperty(classPropertyField, LM.classProperty.getMapping(keyProperty)));

        List<ImportDelete> deletes = new ArrayList<ImportDelete>();
        deletes.add(new ImportDelete(keyProperty, LM.is(LM.property).getMapping(keyProperty), false));

        ImportTable table = new ImportTable(asList(sidPropertyField, captionPropertyField, loggablePropertyField, storedPropertyField, isSetNotNullPropertyField, signaturePropertyField, returnPropertyField, classPropertyField), dataProperty);

        try {
            DataSession session = createSession();
            IntegrationService service = new IntegrationService(session, table, asList(keyProperty), properties, deletes);
            service.synchronize(true, false);

            session.apply(this);
            session.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void synchronizePropertyParents() {
        ImportField sidPropertyField = new ImportField(LM.propertySIDValueClass);
        ImportField numberPropertyField = new ImportField(LM.numberProperty);
        ImportField parentSidField = new ImportField(LM.navigatorElementSIDClass);

        List<List<Object>> dataParent = new ArrayList<List<Object>>();
        for (Property property : getProperties()) {
            if (needsToBeSynchronized(property))
                dataParent.add(asList(property.getSID(), (Object) property.getParent().getSID(), getNumberInListOfChildren(property)));
        }

        ImportKey<?> keyProperty = new ImportKey(LM.property, LM.SIDToProperty.getMapping(sidPropertyField));
        ImportKey<?> keyParent = new ImportKey(LM.abstractGroup, LM.SIDToAbstractGroup.getMapping(parentSidField));
        List<ImportProperty<?>> properties = new ArrayList<ImportProperty<?>>();

        properties.add(new ImportProperty(parentSidField, LM.parentProperty.getMapping(keyProperty), LM.object(LM.abstractGroup).getMapping(keyParent)));
        properties.add(new ImportProperty(numberPropertyField, LM.numberProperty.getMapping(keyProperty)));
        ImportTable table = new ImportTable(asList(sidPropertyField, parentSidField, numberPropertyField), dataParent);

        try {
            DataSession session = createSession();
            IntegrationService service = new IntegrationService(session, table, asList(keyProperty, keyParent), properties);
            service.synchronize(true, false);

            session.apply(this);
            session.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void synchronizeGroupProperties() {
        ImportField sidField = new ImportField(LM.navigatorElementSIDClass);
        ImportField captionField = new ImportField(LM.navigatorElementCaptionClass);
        ImportField numberField = new ImportField(LM.numberAbstractGroup);

        ImportKey<?> key = new ImportKey(LM.abstractGroup, LM.SIDToAbstractGroup.getMapping(sidField));

        List<List<Object>> data = new ArrayList<List<Object>>();

        for (AbstractGroup group : getParentGroups()) {
            data.add(asList(group.getSID(), (Object) group.caption));
        }

        List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
        props.add(new ImportProperty(sidField, LM.SIDAbstractGroup.getMapping(key)));
        props.add(new ImportProperty(captionField, LM.captionAbstractGroup.getMapping(key)));

        List<ImportDelete> deletes = new ArrayList<ImportDelete>();
        deletes.add(new ImportDelete(key, LM.is(LM.abstractGroup).getMapping(key), false));

        ImportTable table = new ImportTable(asList(sidField, captionField), data);

        List<List<Object>> data2 = new ArrayList<List<Object>>();

        for (AbstractGroup group : getParentGroups()) {
            if (group.getParent() != null) {
                data2.add(asList(group.getSID(), (Object) group.getParent().getSID(), getNumberInListOfChildren(group)));
            }
        }

        ImportField parentSidField = new ImportField(LM.navigatorElementSIDClass);
        ImportKey<?> key2 = new ImportKey(LM.abstractGroup, LM.SIDToAbstractGroup.getMapping(parentSidField));
        List<ImportProperty<?>> props2 = new ArrayList<ImportProperty<?>>();
        props2.add(new ImportProperty(parentSidField, LM.parentAbstractGroup.getMapping(key), LM.object(LM.abstractGroup).getMapping(key2)));
        props2.add(new ImportProperty(numberField, LM.numberAbstractGroup.getMapping(key)));
        ImportTable table2 = new ImportTable(asList(sidField, parentSidField, numberField), data2);

        try {
            DataSession session = createSession();
            IntegrationService service = new IntegrationService(session, table, asList(key), props, deletes);
            service.synchronize(true, false);

            service = new IntegrationService(session, table2, asList(key, key2), props2);
            service.synchronize(true, false);

            session.apply(this);
            session.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    Integer getNumberInListOfChildren(AbstractNode abstractNode) {
        int counter = 0;
        for (AbstractNode node : abstractNode.getParent().children) {
            counter++;
            if (abstractNode instanceof Property) {
                if (node instanceof Property)
                    if (((Property) node).getSID().equals(((Property) abstractNode).getSID())) {
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

    protected void resetConnectionStatus() {
        try {
            DataSession session = createSession();

            PropertyChange statusChanges = new PropertyChange(LM.connectionStatus.getDataObject("disconnectedConnection"),
                        BaseUtils.single(LM.connectionCurrentStatus.property.interfaces), LM.connectionStatus.getDataObject("connectedConnection"));

            session.change((CalcProperty) LM.connectionCurrentStatus.property, statusChanges);

            session.apply(this);
            session.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final String navigatorTreeFilePath = "conf/navigatorTree.data";

    private void reloadNavigatorTree() throws IOException {
        if (new File(navigatorTreeFilePath).exists()) {
            FileInputStream inStream = new FileInputStream(navigatorTreeFilePath);
            try {
                mergeNavigatorTree(new DataInputStream(inStream));
            } finally {
                inStream.close();
            }
        }
    }

    private void fillDaysOff() throws ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException {
        DataSession session = createSession();

        LCP<PropertyInterface> generateOrDefaultCountry = LM.addSUProp(Union.OVERRIDE, LM.generateDatesCountry, LM.addJProp(LM.equals2, LM.defaultCountry, 1));

        OrderedMap<PropertyInterface, KeyExpr> keys = generateOrDefaultCountry.getMapKeys();

        Query<PropertyInterface, Object> query = new Query<PropertyInterface, Object>(keys);
        query.properties.put("id", generateOrDefaultCountry.property.getExpr(keys));

        query.and(generateOrDefaultCountry.property.getExpr(keys).getWhere());

        OrderedMap<Map<PropertyInterface, Object>, Map<Object, Object>> result = query.execute(session.sql);
        for (Map<PropertyInterface, Object> key : result.keyList()) {
            Integer id = (Integer) BaseUtils.singleValue(key);
            generateDates(id);
        }
        session.close();
    }

    private void generateDates(int countryId) throws ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException {
        DataSession session = createSession();
        Calendar current = Calendar.getInstance();
        int currentYear = current.get(Calendar.YEAR);
        //если проставлен выходной 1 января через 2 года, пропускаем генерацию
        DataObject countryObject = new DataObject(countryId, LM.country);
        if (LM.isDayOffCountryDate.read(session, countryObject, new DataObject(new java.sql.Date(new GregorianCalendar(currentYear + 2, 0, 1).getTimeInMillis()), DateClass.instance)) != null) {
            return;
        }

        long wholeYearMillisecs = new GregorianCalendar(currentYear + 3, 0, 1).getTimeInMillis() - current.getTimeInMillis();
        long wholeYearDays = wholeYearMillisecs / 1000 / 60 / 60 / 24;
        Calendar cal = new GregorianCalendar(currentYear, 0, 1);
        for (int i = 0; i < wholeYearDays; i++) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
            int day = cal.get(Calendar.DAY_OF_WEEK);
            if (day == 1 || day == 7) {
                addDayOff(session, countryId, cal.getTimeInMillis());
            }
        }

        for (int i = 0; i < 3; i++) {
            Calendar calendar = new GregorianCalendar(currentYear + i, 0, 1);
            int day = calendar.get(Calendar.DAY_OF_WEEK);
            if (day != 1 && day != 7)
                addDayOff(session, countryId, calendar.getTimeInMillis());
        }

        session.apply(this);
        session.close();
    }

    private void addDayOff(DataSession session, int countryId, long timeInMillis) throws ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException {
        DataObject countryObject = new DataObject(countryId, LM.country);
        LM.isDayOffCountryDate.change(true, session, countryObject, new DataObject(new java.sql.Date(timeInMillis), DateClass.instance));
    }

    public void mergeNavigatorTree(DataInputStream inStream) throws IOException {
        //читаем новую структуру навигатора, в процессе подчитывая сохранённые элементы
        Map<String, List<String>> treeStructure = new HashMap<String, List<String>>();
        int mapSize = inStream.readInt();
        for (int i = 0; i < mapSize; ++i) {
            String parentSID = inStream.readUTF();
            int childrenCnt = inStream.readInt();
            List<String> childrenSIDs = new ArrayList<String>();
            for (int j = 0; j < childrenCnt; ++j) {
                String childSID = inStream.readUTF();
                childrenSIDs.add(childSID);
            }
            treeStructure.put(parentSID, childrenSIDs);
        }

        //формируем полное дерево, сохраняя мэппинг элементов
        Map<String, NavigatorElement<?>> elementsMap = new HashMap<String, NavigatorElement<?>>();
        for (NavigatorElement<?> parent : LM.baseElement.getChildren(true)) {
            String parentSID = parent.getSID();
            elementsMap.put(parentSID, parent);

            if (!treeStructure.containsKey(parentSID)) {
                List<String> children = new ArrayList<String>();
                for (NavigatorElement<?> child : parent.getChildren(false)) {
                    children.add(child.getSID());
                }

                treeStructure.put(parentSID, children);
            }
        }

        //override элементов
        for (Map.Entry<String, List<String>> entry : treeStructure.entrySet()) {
            overrideElement(elementsMap, entry.getKey());
            for (String childSID : entry.getValue()) {
                overrideElement(elementsMap, childSID);
            }
        }

        //перестраиваем
        for (Map.Entry<String, List<String>> entry : treeStructure.entrySet()) {
            String parentSID = entry.getKey();
            NavigatorElement parent = elementsMap.get(parentSID);
            if (parent != null) {
                parent.removeAllChildren();

                for (String childSID : entry.getValue()) {
                    NavigatorElement<?> element = elementsMap.get(childSID);

                    if (element != null) {
                        parent.add(element);
                    }
                }
            }
        }
    }

    private void overrideElement(Map<String, NavigatorElement<?>> elementsMap, String elementSID) {
        NavigatorElement<T> element = getOverridenElement(elementSID);
        if (element == null) {
            element = getOverridenForm(elementSID);
        }

        if (element != null) {
            elementsMap.put(elementSID, element);
        }
    }

    public String getFormSerializationPath(String formSID) {
        return "conf/forms/" + formSID;
    }

    private FormEntity<T> getOverridenForm(String formSID) {
        try {
            byte[] formState = IOUtils.getFileBytes(new File(getFormSerializationPath(formSID)));
            return (FormEntity<T>) FormEntity.deserialize(this, formState);
        } catch (Exception e) {
            return null;
        }
    }

    public String getElementSerializationPath(String elementSID) {
        return "conf/elements/" + elementSID;
    }

    private NavigatorElement<T> getOverridenElement(String elementSID) {
        try {
            byte[] elementState = IOUtils.getFileBytes(new File(getElementSerializationPath(elementSID)));
            return (NavigatorElement<T>) NavigatorElement.deserialize(elementState);
        } catch (Exception e) {
            return null;
        }
    }

    public void saveNavigatorTree() throws IOException {
        Collection<NavigatorElement<T>> children = LM.baseElement.getChildren(true);
        File treeFile = new File(navigatorTreeFilePath);
        if (!treeFile.getParentFile().exists()) {
            treeFile.getParentFile().mkdirs();
        }

        FileOutputStream fileOutStream = new FileOutputStream(treeFile);
        try {
            DataOutputStream outStream = new DataOutputStream(fileOutStream);
            outStream.writeInt(children.size());
            for (NavigatorElement child : children) {
                outStream.writeUTF(child.getSID());

                Collection<NavigatorElement<T>> thisChildren = child.getChildren(false);
                outStream.writeInt(thisChildren.size());
                for (NavigatorElement<T> thisChild : thisChildren) {
                    outStream.writeUTF(thisChild.getSID());
                }
            }
        } finally {
            fileOutStream.close();
        }
    }

    private void changeCurrentDate() throws SQLException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        DataSession session = createSession();

        java.sql.Date currentDate = (java.sql.Date)LM.currentDate.read(session);
        java.sql.Date newDate = DateConverter.dateToSql(new Date());
        if (currentDate == null || currentDate.getDay() != newDate.getDay() || currentDate.getMonth() != newDate.getMonth() || currentDate.getYear() != newDate.getYear()) {
            LM.currentDate.change(newDate, session);
            session.apply(this);
        }

        session.close();
    }

    public int systemUserObject;
    public int systemComputer;

    public PolicyManager policyManager = new PolicyManager();

    protected abstract void initAuthentication() throws ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException;

    public SQLSession createSQL() throws SQLException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        return createSQL(-1);
    }

    public SQLSession createSQL(int isolationLevel) throws SQLException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        return new SQLSession(adapter, isolationLevel);
    }

    public DataSession createSession() throws SQLException {
        return createSession(getThreadLocalSql(), new UserController() {
            public void changeCurrentUser(DataObject user) {
                throw new RuntimeException("not supported");
            }

            public DataObject getCurrentUser() {
                return new DataObject(systemUserObject, LM.systemUser);
            }
        }, new ComputerController() {
            public DataObject getCurrentComputer() {
                return new DataObject(systemComputer, LM.computer);
            }
            public boolean isFullClient() {
                return false;
            }
        });
    }

    public DataSession createSession(SQLSession sql, UserController userController, ComputerController computerController) throws SQLException {
        return new DataSession(sql, userController, computerController,
                new IsServerRestartingController() {
                    public boolean isServerRestarting() {
                        return getRestartController().isPendingRestart();
                    }
                },
                LM.baseClass, LM.baseClass.named, LM.session, LM.name, LM.recognizeGroup, LM.transaction, LM.date, LM.currentDate, LM.currentSession, getIDSql(), getSessionEvents());
    }

    public List<Property> getProperties() {
        return LM.rootGroup.getProperties();
    }

    public List<AbstractGroup> getParentGroups() {
        return LM.rootGroup.getParentGroups();
    }

    public Iterable<Property> getPropertyList() {
        return getPropertyList(false);
    }

    // находит свойство входящее в "верхнюю" сильносвязную компоненту
    private static QuickSet<Link> goDown(Property<?> property, QuickMap<Property, QuickSet<Link>> linksMap, List<Property> order, QuickSet<Link> removedLinks, boolean include, QuickSet<Property> component) {
        QuickSet<Link> linksIn = linksMap.get(property);
        if(linksIn==null) { // уже были, linksMap - одновременно используется и как пометки, и как список, и как обратный обход
            linksIn = new QuickSet<Link>();
            linksMap.add(property, linksIn);

            QuickSet<Link> links = property.getLinks();
            for(int i=0;i<links.size;i++) {
                Link link = links.get(i);
                if(!removedLinks.contains(link) && component.contains(link.to)==include)
                    goDown(link.to, linksMap, order, removedLinks, include, component).add(link);
            }
            order.add(property);
        }
        return linksIn;
    }
    
    // бежим вниз отсекая выбирая ребро с минимальным приоритетом из этой компоненты
    private static void goUp(Property<?> property, QuickMap<Property, QuickSet<Link>> linksMap, QuickSet<Property> proceeded, Result<Link> minLink, QuickSet<Property> component) {
        if(component.add(property))
            return;

        QuickSet<Link> linksIn = linksMap.get(property);
        for(int i=0;i<linksIn.size;i++) {
            Link link = linksIn.get(i);
            if(!proceeded.contains(link.from)) { // если не в верхней компоненте
                goUp(link.from, linksMap, proceeded, minLink, component);
                if(minLink.result==null || link.type.getNum() > minLink.result.type.getNum()) // сразу же ищем минимум из ребер
                    minLink.set(link);
            }
        }
    }

    // upComponent нужен так как изначально неизвестны все элементы
    private static QuickSet<Property> buildList(QuickSet<Property> props, QuickSet<Property> exclude, QuickSet<Link> removedLinks, List<Property> result) {
        QuickSet<Property> proceeded;

        List<Property> order = new ArrayList<Property>();
        QuickMap<Property, QuickSet<Link>> linksMap = new SimpleMap<Property, QuickSet<Link>>();
        for(int i=0;i<props.size;i++) {
            Property property = props.get(i);
            if(linksMap.get(property)==null) // проверка что не было
                goDown(property, linksMap, order, removedLinks, exclude==null, exclude!=null ? exclude : props);
        }

        Result<Link> minLink = new Result<Link>();
        proceeded = new QuickSet<Property>();
        for(int i=0;i<order.size();i++) { // тут нужн
            Property orderProperty = order.get(order.size()-1-i);
            if(!proceeded.contains(orderProperty)) {
                minLink.set(null);
                QuickSet<Property> innerComponent = new QuickSet<Property>();
                goUp(orderProperty, linksMap, proceeded, minLink, innerComponent);
                assert innerComponent.size > 0;
                if(innerComponent.size==1) // если цикла нет все ОК
                    result.add(innerComponent.getSingle());
                else {
                    boolean was = removedLinks.add(minLink.result);
                    assert !was;

                    if(minLink.result.type.equals(LinkType.DEPEND)) { // нашли цикл
                        List<Property> cycle = new ArrayList<Property>();
                        buildList(innerComponent, null, removedLinks, cycle);
                        String print = "";
                        for(Property property : cycle)
                            print = (print.length()==0 ? "" : print + " -> ") + property.toString();
                        throw new RuntimeException(ServerResourceBundle.getString("message.cycle.detected") + " : " + print + " -> " + minLink.result.to);
                    }
                    buildList(innerComponent, null, removedLinks, result);
                }
                proceeded.addAll(innerComponent);
            }
        }

        return proceeded;
    }

    @IdentityLazy
    public List<Property> getPropertyList(boolean onlyCheck) {
        // жестковато тут конечно написано, но пока не сильно времени жрет

        // сначала бежим по Action'ам с cancel'ами
        QuickSet<Property> cancelActions = new QuickSet<Property>();
        QuickSet<Property> rest = new QuickSet<Property>();
        for (Property property : getProperties())
            if(property instanceof ActionProperty && ((ActionProperty)property).hasCancel())
                cancelActions.add(property);
            else
                rest.add(property);

        List<Property> cancelResult = new ArrayList<Property>();
        QuickSet<Property> proceeded = buildList(cancelActions, new QuickSet<Property>(), new QuickSet<Link>(), cancelResult);
        if(onlyCheck)
            return BaseUtils.reverse(cancelResult);

        // потом бежим по всем остальным, за исключением proceeded
        List<Property> restResult = new ArrayList<Property>();
        buildList(rest.remove(proceeded), proceeded, new QuickSet<Link>(), restResult);

        // затем по всем кроме proceeded на прошлом шаге
        assert Collections.disjoint(cancelResult, restResult);
        return BaseUtils.mergeList(BaseUtils.reverse(cancelResult), BaseUtils.reverse(restResult));
    }

    @IdentityLazy
    public List<CalcProperty> getStoredProperties() {
        List<CalcProperty> result = new ArrayList<CalcProperty>();
        for (Property property : getPropertyList())
            if (property instanceof CalcProperty && ((CalcProperty)property).isStored())
                result.add((CalcProperty) property);
        return result;
    }

    @IdentityLazy
    public LinkedHashSet<OldProperty> getApplyEventDependProps() {
        LinkedHashSet<OldProperty> result = new LinkedHashSet<OldProperty>();
        for (Property<?> property : getPropertyList()) {
            if (property instanceof ActionProperty && ((ActionProperty) property).events.contains(SystemEvent.APPLY))
                result.addAll(property.getOldDepends());
            if (property instanceof DataProperty && ((DataProperty) property).event!=null)
                result.addAll(((DataProperty)property).event.getOldDepends());
        }
        return result;
    }

    @IdentityLazy
    public List<Property> getAppliedProperties(boolean onlyCheck) {
        // здесь нужно вернуть список stored или тех кто
        List<Property> result = new ArrayList<Property>();
        for (Property property : getPropertyList(onlyCheck)) {
            if (property instanceof CalcProperty && ((CalcProperty)property).isStored())
                result.add(property);
            if (property instanceof ActionProperty && ((ActionProperty) property).events.contains(SystemEvent.APPLY))
                result.add(property);
        }
        return result;
    }

    @IdentityLazy
    public List<CalcProperty> getDataChangeEvents() {
        List<CalcProperty> result = new ArrayList<CalcProperty>();
        for(Property property : getPropertyList())
            if(property instanceof DataProperty && ((DataProperty)property).event!=null)
                result.add((((DataProperty) property).event).getWhere());
        return result;
    }

    private void fillAppliedDependFrom(CalcProperty<?> fill, CalcProperty<?> applied, Map<CalcProperty, Set<CalcProperty>> mapDepends) {
        if(!fill.equals(applied) && fill.isStored())
            mapDepends.get(fill).add(applied);
        else
            for(CalcProperty depend : fill.getDepends(false)) // derived'ы отдельно отрабатываются
                fillAppliedDependFrom(depend, applied, mapDepends);
    }

    @IdentityLazy
    public List<ActionProperty> getSessionEvents() {
        List<ActionProperty> result = new ArrayList<ActionProperty>();
        for(Property property : getPropertyList())
            if (property instanceof ActionProperty && ((ActionProperty) property).events.contains(SystemEvent.SESSION))
                result.add((ActionProperty)property);
        return result;
    }

    // assert что key property is stored, а value property is stored или instanceof OldProperty
    @IdentityLazy
    private Map<CalcProperty, List<CalcProperty>> getMapAppliedDepends() {
        Map<CalcProperty, Set<CalcProperty>> mapDepends = new HashMap<CalcProperty, Set<CalcProperty>>();
        for(CalcProperty property : getStoredProperties()) {
            mapDepends.put(property, new HashSet<CalcProperty>());
            fillAppliedDependFrom(property, property, mapDepends);
        }
        for(OldProperty old : getApplyEventDependProps())
            fillAppliedDependFrom(old.property, old, mapDepends);

        Iterable<Property> propertyList = getPropertyList();
        Map<CalcProperty, List<CalcProperty>> orderedMapDepends = new HashMap<CalcProperty, List<CalcProperty>>();
        for(Map.Entry<CalcProperty, Set<CalcProperty>> mapDepend : mapDepends.entrySet()) {
            List<CalcProperty> dependList = BaseUtils.orderList(mapDepend.getValue(), propertyList);
            assert dependList.size()==mapDepend.getValue().size();
            orderedMapDepends.put(mapDepend.getKey(), dependList);
        }
        return orderedMapDepends;
    }

    // определяет для stored свойства зависимые от него stored свойства, а также свойства которым необходимо хранить изменения с начала транзакции (constraints и derived'ы)
    public List<CalcProperty> getAppliedDependFrom(CalcProperty property) {
        assert property.isStored();
        return getMapAppliedDepends().get(property);
    }

    public List<AggregateProperty> getAggregateStoredProperties() {
        List<AggregateProperty> result = new ArrayList<AggregateProperty>();
        for (Property property : getStoredProperties())
            if (property instanceof AggregateProperty)
                result.add((AggregateProperty) property);
        return result;
    }

    @IdentityLazy
    public List<CalcProperty> getCheckConstrainedProperties() {
        List<CalcProperty> result = new ArrayList<CalcProperty>();
        for (Property property : getPropertyList()) {
            if (property instanceof CalcProperty && ((CalcProperty)property).checkChange != CalcProperty.CheckType.CHECK_NO) {
                result.add((CalcProperty) property);
            }
        }
        return result;
    }
    
    public List<CalcProperty> getCheckConstrainedProperties(CalcProperty<?> changingProp) {
        List<CalcProperty> result = new ArrayList<CalcProperty>();
        for (CalcProperty property : getCheckConstrainedProperties()) {
            if (property.checkChange == CalcProperty.CheckType.CHECK_ALL ||
                property.checkChange == CalcProperty.CheckType.CHECK_SOME && property.checkProperties.contains(changingProp)) {
                result.add(property);
            }
        }
        return result;
    }

    public void fillIDs() throws SQLException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        DataSession session = createSession();

        LM.baseClass.fillIDs(session, LM.name, LM.classSID);

        session.apply(this);

        session.close();
    }

    public void updateClassStat(SQLSession session) throws SQLException {
        LM.baseClass.updateClassStat(session);
    }

    public void synchronizeDB() throws SQLException, IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {

        SQLSession sql = getThreadLocalSql();

        // инициализируем таблицы
        LM.tableFactory.fillDB(sql, LM.baseClass);

        // "старое" состояние базы
        DataInputStream inputDB = null;
        byte[] struct = (byte[]) sql.readRecord(StructTable.instance, new HashMap<KeyField, DataObject>(), StructTable.instance.struct);
        if (struct != null)
            inputDB = new DataInputStream(new ByteArrayInputStream(struct));

        sql.startTransaction();

        // новое состояние базы
        ByteArrayOutputStream outDBStruct = new ByteArrayOutputStream();
        DataOutputStream outDB = new DataOutputStream(outDBStruct);

        Map<String, ImplementTable> implementTables = LM.tableFactory.getImplementTablesMap();

        Map<ImplementTable, Map<List<String>, Boolean>> mapIndexes = new HashMap<ImplementTable, Map<List<String>, Boolean>>();
        for (ImplementTable table : implementTables.values())
            mapIndexes.put(table, new HashMap<List<String>, Boolean>());

        // привяжем индексы
        for (Map.Entry<List<? extends CalcProperty>, Boolean> index : LM.indexes.entrySet()) {
            Iterator<? extends CalcProperty> i = index.getKey().iterator();
            if (!i.hasNext())
                throw new RuntimeException(getString("logics.policy.forbidden.to.create.empty.indexes"));
            CalcProperty baseProperty = i.next();
            if (!baseProperty.isStored())
                throw new RuntimeException(getString("logics.policy.forbidden.to.create.indexes.on.non.regular.properties")+" (" + baseProperty + ")");
            ImplementTable indexTable = baseProperty.mapTable.table;

            List<String> tableIndex = new ArrayList<String>();
            tableIndex.add(baseProperty.field.name);

            while (i.hasNext()) {
                CalcProperty property = i.next();
                if (!property.isStored())
                    throw new RuntimeException(getString("logics.policy.forbidden.to.create.indexes.on.non.regular.properties") + " (" + baseProperty + ")");
                if (indexTable.findProperty(property.field.name) == null)
                    throw new RuntimeException(getString("logics.policy.forbidden.to.create.indexes.on.properties.in.different.tables", baseProperty, property));
                tableIndex.add(property.field.name);
            }
            mapIndexes.get(indexTable).put(tableIndex, index.getValue());
        }

        // запишем новое состояние таблиц (чтобы потом изменять можно было бы)
        outDB.write('v'+2);  //для поддержки обратной совместимости

        outDB.writeInt(mapIndexes.size());
        for (Map.Entry<ImplementTable, Map<List<String>, Boolean>> mapIndex : mapIndexes.entrySet()) {
            mapIndex.getKey().serialize(outDB);
            outDB.writeInt(mapIndex.getValue().size());
            for (Map.Entry<List<String>, Boolean> index : mapIndex.getValue().entrySet()) {
                outDB.writeInt(index.getKey().size());
                for (String indexField : index.getKey())
                    outDB.writeUTF(indexField);
                outDB.writeBoolean(index.getValue());
            }
        }

        List<CalcProperty> storedProperties = new ArrayList<CalcProperty>(getStoredProperties());
        // запишем новое состояние таблиц (чтобы потом изменять можно было бы)
        outDB.writeInt(storedProperties.size());
        for (CalcProperty<?> property : storedProperties) {
            outDB.writeUTF(property.getSID());
            outDB.writeBoolean(property instanceof DataProperty);
            outDB.writeUTF(property.mapTable.table.name);
            for (Map.Entry<? extends PropertyInterface, KeyField> mapKey : property.mapTable.mapKeys.entrySet()) {
                outDB.writeInt(mapKey.getKey().ID);
                outDB.writeUTF(mapKey.getValue().name);
            }
        }

        // если не совпали sID или идентификаторы из базы удаляем сразу
        Map<String, SerializedTable> prevTables = new HashMap<String, SerializedTable>();

        //для поддержки обратной совместимости
        int version;
        if(inputDB==null)
            version = -2;
        else {
            version = inputDB.read()-'v';
            if(version<0)
                inputDB.reset();
        }

        for (int i = inputDB == null ? 0 : inputDB.readInt(); i > 0; i--) {
            SerializedTable prevTable = new SerializedTable(inputDB, LM.baseClass, version);
            prevTables.put(prevTable.name, prevTable);

            ImplementTable implementTable = implementTables.get(prevTable.name);
            Map<List<String>, Boolean> tableIndexes = null;
            if(implementTable!=null)
                tableIndexes = mapIndexes.get(implementTable);

            for (int j = inputDB.readInt(); j > 0; j--) {
                List<String> index = new ArrayList<String>();
                for (int k = inputDB.readInt(); k > 0; k--)
                    index.add(inputDB.readUTF());
                boolean prevOrdered = version >= 1 && inputDB.readBoolean();
                boolean drop = (implementTable == null); // ушла таблица
                if(!drop) {
                    Boolean newOrdered = tableIndexes.get(index);
                    if(newOrdered!=null && newOrdered.equals(prevOrdered))
                        tableIndexes.remove(index); // не трогаем индекс
                    else
                        drop = true;
                }
                if(drop)
                    sql.dropIndex(prevTable.name, prevTable.keys, index, prevOrdered);
            }
        }

        // добавим таблицы которых не было
        for (ImplementTable table : implementTables.values()) {
            if (!prevTables.containsKey(table.name))
                sql.createTable(table.name, table.keys);
        }

        Set<ImplementTable> packTables = new HashSet<ImplementTable>();

        // бежим по свойствам
        int prevStoredNum = inputDB == null ? 0 : inputDB.readInt();
        Map<String, String> columnsToDrop = new HashMap<String, String>();
        for (int i = 0; i < prevStoredNum; i++) {
            String sID = inputDB.readUTF();
            boolean isDataProperty = true;
            if (version >= 0)
                isDataProperty = inputDB.readBoolean();
            SerializedTable prevTable = prevTables.get(inputDB.readUTF());
            Map<Integer, KeyField> mapKeys = new HashMap<Integer, KeyField>();
            for (int j = 0; j < prevTable.keys.size(); j++)
                mapKeys.put(inputDB.readInt(), prevTable.findKey(inputDB.readUTF()));

            boolean keep = false, moved = false;
            for (Iterator<CalcProperty> is = storedProperties.iterator(); is.hasNext();) {
                CalcProperty<?> property = is.next();
                if (property.getSID().equals(sID)) {
                    Map<KeyField, PropertyInterface> foundInterfaces = new HashMap<KeyField, PropertyInterface>();
                    for (PropertyInterface propertyInterface : property.interfaces) {
                        KeyField mapKeyField = mapKeys.get(propertyInterface.ID);
                        if (mapKeyField != null) foundInterfaces.put(mapKeyField, propertyInterface);
                    }
                    if (foundInterfaces.size() == mapKeys.size()) { // если все нашли
                        if (!(keep = property.mapTable.table.name.equals(prevTable.name))) { // если в другой таблице
                            sql.addColumn(property.mapTable.table.name, property.field);
                            // делаем запрос на перенос

                            logger.info(getString("logics.info.property.is.transferred.from.table.to.table", property.field, property.caption, prevTable.name, property.mapTable.table.name));
                            property.mapTable.table.moveColumn(sql, property.field, prevTable,
                                    BaseUtils.join(foundInterfaces, (Map<PropertyInterface, KeyField>) property.mapTable.mapKeys), prevTable.findProperty(sID));
                            logger.info("Done");
                            moved = true;
                        } else // надо проверить что тип не изменился
                            if (!prevTable.findProperty(sID).type.equals(property.field.type))
                                sql.modifyColumn(property.mapTable.table.name, property.field, prevTable.findProperty(sID).type);
                        is.remove();
                    }
                    break;
                }
            }
            if (!keep) {
                if (isDataProperty && !moved) {
                    String newName = sID + "_deleted";
                    Savepoint savepoint = sql.getConnection().setSavepoint();
                    try {
                        savepoint = sql.getConnection().setSavepoint();
                        sql.renameColumn(prevTable.name, sID, newName);
                        columnsToDrop.put(newName, prevTable.name);
                    } catch (PSQLException e) { // колонка с новым именем (с '_deleted') уже существует
                        sql.getConnection().rollback(savepoint);
                        sql.dropColumn(prevTable.name, sID);
                        ImplementTable table = implementTables.get(prevTable.name);
                        if (table != null) packTables.add(table);
                    }
                } else {
                    sql.dropColumn(prevTable.name, sID);
                    ImplementTable table = implementTables.get(prevTable.name); // надо упаковать таблицу если удалили колонку
                    if (table != null) packTables.add(table);
                }
            }
        }

        List<AggregateProperty> recalculateProperties = new ArrayList<AggregateProperty>();
        for (CalcProperty property : storedProperties) { // добавляем оставшиеся
            sql.addColumn(property.mapTable.table.name, property.field);
            if (struct != null && property instanceof AggregateProperty) // если все свойства "новые" то ничего перерасчитывать не надо
                recalculateProperties.add((AggregateProperty) property);
        }

        // удаляем таблицы старые
        for (String table : prevTables.keySet())
            if (!implementTables.containsKey(table))
                sql.dropTable(table);

        packTables(sql, packTables); // упакуем таблицы

        updateStats();  // пересчитаем статистику

        // создадим индексы в базе
        for (Map.Entry<ImplementTable, Map<List<String>, Boolean>> mapIndex : mapIndexes.entrySet())
            for (Map.Entry<List<String>, Boolean> index : mapIndex.getValue().entrySet())
                sql.addIndex(mapIndex.getKey().name, mapIndex.getKey().keys, index.getKey(), index.getValue());

        try {
            sql.insertRecord(StructTable.instance, new HashMap<KeyField, DataObject>(), Collections.singletonMap(StructTable.instance.struct, (ObjectValue) new DataObject((Object) outDBStruct.toByteArray(), ByteArrayClass.instance)), true);
        } catch (Exception e) {
            Map<PropertyField, ObjectValue> propFields = Collections.singletonMap(StructTable.instance.struct, (ObjectValue) new DataObject((Object) new byte[0], ByteArrayClass.instance));
            sql.insertRecord(StructTable.instance, new HashMap<KeyField, DataObject>(), propFields, true);
        }

        fillIDs();

        updateClassStat(sql);

        recalculateAggregations(sql, recalculateProperties); // перерасчитаем агрегации
//        recalculateAggregations(sql, getAggregateStoredProperties());

        sql.commitTransaction();

        DataSession session = createSession();
        for (String sid : columnsToDrop.keySet()) {
            DataObject object = session.addObject(LM.dropColumn);
            LM.sidDropColumn.change(sid, session, object);
            LM.sidTableDropColumn.change(columnsToDrop.get(sid), session, object);
            LM.timeDropColumn.change(new Timestamp(Calendar.getInstance().getTimeInMillis()), session, object);
            LM.revisionDropColumn.change(getRevision(), session, object);
        }
        session.apply(this);
        session.close();
    }

    public void synchronizeTables() {
        ImportField tableSidField = new ImportField(LM.sidTable);
        ImportField tableKeySidField = new ImportField(LM.sidTableKey);
        ImportField tableKeyNameField = new ImportField(LM.nameTableKey);
        ImportField tableKeyClassField = new ImportField(LM.classTableKey);
        ImportField tableColumnSidField = new ImportField(LM.sidTableColumn);

        ImportKey<?> tableKey = new ImportKey(LM.table, LM.sidToTable.getMapping(tableSidField));
        ImportKey<?> tableKeyKey = new ImportKey(LM.tableKey, LM.sidToTableKey.getMapping(tableKeySidField));
        ImportKey<?> tableColumnKey = new ImportKey(LM.tableColumn, LM.sidToTableColumn.getMapping(tableColumnSidField));

        List<List<Object>> data = new ArrayList<List<Object>>();
        for(DataTable dataTable : LM.tableFactory.getDataTables(LM.baseClass)) {
            Object tableName = dataTable.name;
            Map<KeyField, ValueClass> classes = dataTable.getClasses().getCommonParent(dataTable.keys);
            for (KeyField key : dataTable.keys) {
                data.add(asList(tableName, key.name, tableName + "." + key.name, classes.get(key).getCaption()));
            }
        }

        List<ImportProperty<?>> properties = new ArrayList<ImportProperty<?>>();
        properties.add(new ImportProperty(tableSidField, LM.sidTable.getMapping(tableKey)));
        properties.add(new ImportProperty(tableKeySidField, LM.sidTableKey.getMapping(tableKeyKey)));
        properties.add(new ImportProperty(tableKeyNameField, LM.nameTableKey.getMapping(tableKeyKey)));
        properties.add(new ImportProperty(tableSidField, LM.tableTableKey.getMapping(tableKeyKey), LM.object(LM.table).getMapping(tableKey)));
        properties.add(new ImportProperty(tableKeyClassField, LM.classTableKey.getMapping(tableKeyKey)));

        List<ImportDelete> deletes = new ArrayList<ImportDelete>();
        deletes.add(new ImportDelete(tableKey, LM.is(LM.table).getMapping(tableKey), false));
        deletes.add(new ImportDelete(tableKeyKey, LM.is(LM.tableKey).getMapping(tableKeyKey), false));

        ImportTable table = new ImportTable(asList(tableSidField, tableKeyNameField, tableKeySidField, tableKeyClassField), data);

        List<List<Object>> data2 = new ArrayList<List<Object>>();
        for(DataTable dataTable : LM.tableFactory.getDataTables(LM.baseClass)) {
            Object tableName = dataTable.name;
            for (PropertyField property : dataTable.properties) {
                data2.add(asList(tableName, property.name));
            }
        }

        List<ImportProperty<?>> properties2 = new ArrayList<ImportProperty<?>>();
        properties2.add(new ImportProperty(tableSidField, LM.sidTable.getMapping(tableKey)));
        properties2.add(new ImportProperty(tableColumnSidField, LM.sidTableColumn.getMapping(tableColumnKey)));
        properties2.add(new ImportProperty(tableSidField, LM.tableTableColumn.getMapping(tableColumnKey), LM.object(LM.table).getMapping(tableKey)));

        List<ImportDelete> deletes2 = new ArrayList<ImportDelete>();
        deletes2.add(new ImportDelete(tableColumnKey, LM.is(LM.tableColumn).getMapping(tableColumnKey), false));

        ImportTable table2 = new ImportTable(asList(tableSidField, tableColumnSidField), data2);

        try {
            DataSession session = createSession();
            IntegrationService service = new IntegrationService(session, table, asList(tableKey, tableKeyKey), properties, deletes);
            service.synchronize(true, false);

            service = new IntegrationService(session, table2, asList(tableKey, tableColumnKey), properties2, deletes2);
            service.synchronize(true, false);

            session.apply(this);

            session.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void recalculateStats(DataSession session) throws SQLException {
        for (DataTable dataTable : LM.tableFactory.getDataTables(LM.baseClass)) {
            dataTable.calculateStat(LM, session);
        }
    }

    public void recalculateFollows(DataSession session) throws SQLException {
        for(Property property : getPropertyList())
            if(property instanceof ActionProperty) {
                ActionProperty<?> action = (ActionProperty) property;
                if(action.events.contains(SystemEvent.APPLY) && action.resolve)
                    session.resolve(action);
            }
    }

    public void updateStats() throws SQLException {
        updateStats(true); // чтобы сами таблицы статистики получили статистику
        if(!"true".equals(System.getProperty("platform.server.logics.donotcalculatestats")))
            updateStats(false);
    }

    public void updateStats(boolean statDefault) throws SQLException {
        DataSession session = createSession();
        for (DataTable dataTable : LM.tableFactory.getDataTables(LM.baseClass)) {
            dataTable.updateStat(LM, session, statDefault);
        }
    }

    public void dropColumn(String tableName, String columnName) throws SQLException {
        SQLSession sql = getThreadLocalSql();
        sql.startTransaction();
        sql.dropColumn(tableName, columnName);
        ImplementTable table = LM.tableFactory.getImplementTablesMap().get(tableName); // надо упаковать таблицу, если удалили колонку
        if (table != null)
            sql.packTable(table);
        sql.commitTransaction();
    }

    protected LP getLP(String sID) {
        return LM.getLP(sID);
    }

    protected LCP getLCP(String sID) {
        return (LCP)LM.getLP(sID);
    }

    private boolean intersect(LCP[] props) {
        for (int i = 0; i < props.length; i++)
            for (int j = i + 1; j < props.length; j++)
                if (((LCP<?>) props[i]).intersect((LCP<?>) props[j]))
                    return true;
        return false;
    }

    public final static boolean checkClasses = false;

    public boolean isRequiredPassword() {
        return requiredPassword;
    }

    public void setRequiredPassword(boolean requiredPassword) {
        this.requiredPassword = requiredPassword;
    }

    public boolean requiredPassword = true;


    private boolean checkProps() {
        if (checkClasses)
            for (Property prop : getProperties()) {
                logger.debug("Checking property : " + prop + "...");
                assert prop.check();
            }
        for (LCP[] props : LM.checkCUProps) {
            logger.debug("Checking class properties : " + props + "...");
            assert !intersect(props);
        }
        for (LCP[] props : LM.checkSUProps) {
            logger.debug("Checking union properties : " + props + "...");
//            assert intersect(props);
        }
        return true;
    }

    public void fillData() throws SQLException, ClassNotFoundException, IllegalAccessException, InstantiationException {
    }

    public String checkAggregations(SQLSession session) throws SQLException {
        String message = "";
        for (AggregateProperty property : getAggregateStoredProperties())
            message += property.checkAggregation(session);
        return message;
    }

    public void recalculateAggregations(SQLSession session, List<AggregateProperty> recalculateProperties) throws SQLException {
        for (AggregateProperty property : recalculateProperties)
            property.recalculateAggregation(session);
    }

    public void recalculateAggregationTableColumn(SQLSession session, String propertySID) throws SQLException {
        for (CalcProperty property : getAggregateStoredProperties())
            if (property.getSID().equals(propertySID)) {
                AggregateProperty aggregateProperty = (AggregateProperty) property;
                aggregateProperty.recalculateAggregation(session);
            }
    }

    void packTables(SQLSession session, Collection<ImplementTable> tables) throws SQLException {
        for (Table table : tables) {
            logger.debug(getString("logics.info.packing.table")+" (" + table + ")... ");
            session.packTable(table);
            logger.debug("Done");
        }
    }

    protected Scheduler scheduler;

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
        this.scheduler.start();
    }

    protected LAP addRestartActionProp() {
        return LM.addProperty(null, new LAP(new RestartActionProperty(LM.genSID(), "")));
    }

    protected LAP addCancelRestartActionProp() {
        return LM.addProperty(null, new LAP(new CancelRestartActionProperty(LM.genSID(), "")));
    }

    protected LAP addGarbageCollectorActionProp() {
        return LM.addProperty(null, new LAP(new GarbageCollectorActionProperty(LM.genSID(), getString("logics.garbage.collector"))));
    }

    public void updateEnvironmentProperty(CalcProperty property, ObjectValue value) throws SQLException {
        synchronized (navigators) {
            for (RemoteNavigator remoteNavigator : navigators.values()) {
                remoteNavigator.updateEnvironmentProperty(property, value);
            }
        }
    }

    private void updateRestartProperty() throws SQLException {
        Boolean isRestarting = getRestartController().isPendingRestart() ? Boolean.TRUE : null;
        updateEnvironmentProperty((CalcProperty) LM.isServerRestarting.property, ObjectValue.getValue(isRestarting, LogicalClass.instance));
    }

    public int getNumberOfClients() {
        synchronized (navigators) {
            return navigators.size();
        }
    }

    public class RestartActionProperty extends CustomActionProperty {
        private RestartActionProperty(String sID, String caption) {
            super(sID, caption, new ValueClass[]{});
        }

        public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
            getRestartController().initRestart();
            updateRestartProperty();
        }
    }

    public class CancelRestartActionProperty extends CustomActionProperty {
        private CancelRestartActionProperty(String sID, String caption) {
            super(sID, caption, new ValueClass[]{});
        }

        public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
            getRestartController().cancelRestart();
            updateRestartProperty();
        }
    }

    public class GarbageCollectorActionProperty extends CustomActionProperty {
        private GarbageCollectorActionProperty(String sid, String caption) {
            super(sid, caption, new ValueClass[]{});
        }

        public void executeCustom(ExecutionContext<ClassPropertyInterface> context) {
            System.runFinalization();
            System.gc();
        }
    }

    @IdentityLazy
    private synchronized RestartController getRestartController() {
        return new RestartController(this);
    }

    private Map<String, String> formSets;

    public void setFormSets(Map<String, String> formSets) {
        this.formSets = formSets;
    }

    public String getForms(String formSet) {
        if (formSets != null)
            return formSets.get(formSet);
        else
            return null;
    }

    public String getName() throws RemoteException {
        return getClass().getSimpleName();
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }
    
    public String getDisplayName() throws RemoteException {
        return null;
    }

    public byte[] getMainIcon() throws RemoteException {
        return null;
    }

    public byte[] getLogo() throws RemoteException {
        return null;
    }

    public void outputPropertyClasses() {
        for (LP lp : LM.lproperties) {
            logger.debug(lp.property.getSID() + " : " + lp.property.caption + " - " + lp.getClassWhere());
        }
    }

    public void outputPersistent() {
        String result = "";

        result += '\n' + getString("logics.info.by.tables") + '\n' + '\n';
        for (Map.Entry<ImplementTable, Collection<CalcProperty>> groupTable : BaseUtils.group(new BaseUtils.Group<ImplementTable, CalcProperty>() {
            public ImplementTable group(CalcProperty key) {
                return key.mapTable.table;
            }
        }, getStoredProperties()).entrySet()) {
            result += groupTable.getKey().outputKeys() + '\n';
            for (CalcProperty property : groupTable.getValue())
                result += '\t' + property.outputStored(false) + '\n';
        }
        result += '\n' + getString("logics.info.by.properties") + '\n' + '\n';
        for (CalcProperty property : getStoredProperties())
            result += property.outputStored(true) + '\n';
        System.out.println(result);
    }

    public static int generateStaticNewID() {
        return BaseLogicsModule.generateStaticNewID();
    }

    public int generateNewID() throws RemoteException {
        return generateStaticNewID();
    }

    public void setUserLoggableProperties() throws SQLException {

        DataSession session = createSession();

        LCP isProperty = LM.is(LM.property);
        Map<Object, KeyExpr> keys = isProperty.getMapKeys();
        KeyExpr key = BaseUtils.singleValue(keys);
        Query<Object, Object> query = new Query<Object, Object>(keys);
        query.properties.put("SIDProperty", LM.SIDProperty.getExpr(session.getModifier(), key));
        query.properties.put("userLoggableProperty", LM.userLoggableProperty.getExpr(session.getModifier(), key));
        query.and(isProperty.getExpr(key).getWhere());
        OrderedMap<Map<Object, Object>, Map<Object, Object>> result = query.execute(session.sql);

        for (Map<Object, Object> values : result.values()) {
            Object userLoggable = values.get("userLoggableProperty");
            if (userLoggable != null) {
                LM.makeUserLoggable((LCP)getLP(values.get("SIDProperty").toString().trim()));
            }
        }

    }

    public void setPropertyNotifications() throws SQLException {

        DataSession session = createSession();

        LCP isNotification = LM.is(LM.notification);
        Map<Object, KeyExpr> keys = isNotification.getMapKeys();
        KeyExpr key = BaseUtils.singleValue(keys);
        Query<Object, Object> query = new Query<Object, Object>(keys);
        query.properties.put("isDerivedChange", LM.isEventNotification.getExpr(session.getModifier(), key));
        query.properties.put("subject", LM.subjectNotification.getExpr(session.getModifier(), key));
        query.properties.put("text", LM.textNotification.getExpr(session.getModifier(), key));
        query.properties.put("emailFrom", LM.emailFromNotification.getExpr(session.getModifier(), key));
        query.properties.put("emailTo", LM.emailToNotification.getExpr(session.getModifier(), key));
        query.properties.put("emailToCC", LM.emailToCCNotification.getExpr(session.getModifier(), key));
        query.properties.put("emailToBC", LM.emailToBCNotification.getExpr(session.getModifier(), key));
        query.and(isNotification.getExpr(key).getWhere());
        OrderedMap<Map<Object, Object>, Map<Object, Object>> result = query.execute(session.sql);

        for (Map.Entry<Map<Object, Object>, Map<Object, Object>> rows : result.entrySet()) {
            DataObject notificationObject = new DataObject(rows.getKey().values().iterator().next(), LM.notification);
            KeyExpr propertyExpr2 = new KeyExpr("property");
            KeyExpr notificationExpr2 = new KeyExpr("notification");
            Map<Object, KeyExpr> newKeys2 = new HashMap<Object, KeyExpr>();
            newKeys2.put("property", propertyExpr2);
            newKeys2.put("notification", notificationExpr2);

            Query<Object, Object> query2 = new Query<Object, Object>(newKeys2);
            query2.properties.put("SIDProperty", LM.SIDProperty.getExpr(session.getModifier(), propertyExpr2));
            query2.and(LM.inNotificationProperty.getExpr(session.getModifier(), notificationExpr2, propertyExpr2).getWhere());
            query2.and(notificationExpr2.compare(notificationObject, Compare.EQUALS));
            OrderedMap<Map<Object, Object>, Map<Object, Object>> result2 = query2.execute(session.sql);
            List<LCP> listInNotificationProperty = new ArrayList();
            for (Map.Entry<Map<Object, Object>, Map<Object, Object>> rows2 : result2.entrySet()) {
                listInNotificationProperty.add((LCP) getLP(rows2.getValue().get("SIDProperty").toString().trim()));
            }

            for (LCP prop : listInNotificationProperty) {
                Map<Object, Object> rowValue = rows.getValue();
                boolean isDerivedChange = rowValue.get("isDerivedChange") == null ? false : true;
                String subject = rowValue.get("subject") == null ? "" : rowValue.get("subject").toString().trim();
                String text = rowValue.get("text") == null ? "" : rowValue.get("text").toString().trim();
                String emailFrom = rowValue.get("emailFrom") == null ? "" : rowValue.get("emailFrom").toString().trim();
                String emailTo = rowValue.get("emailTo") == null ? "" : rowValue.get("emailTo").toString().trim();
                String emailToCC = rowValue.get("emailToCC") == null ? "" : rowValue.get("emailToCC").toString().trim();
                String emailToBC = rowValue.get("emailToBC") == null ? "" : rowValue.get("emailToBC").toString().trim();
                LAP emailNotificationProperty = LM.addProperty(LM.actionGroup, new LAP(new NotificationActionProperty(prop.property.getSID() + "emailNotificationProperty", "emailNotificationProperty", prop, subject, text, emailFrom, emailTo, emailToCC, emailToBC, LM.BL)));

                Integer[] params = new Integer[prop.listInterfaces.size()];
                for (int i = 0; i < prop.listInterfaces.size(); i++)
                    params[i] = i+1;
                if (isDerivedChange)
                    emailNotificationProperty.setEventAction(LM, prop, params);
                else
                    emailNotificationProperty.setEventSetAction(LM, prop, params);
            }
        }
    }

    public void setNotNullProperties() throws SQLException {

        DataSession session = createSession();

        LCP isProperty = LM.is(LM.property);
        Map<Object, KeyExpr> keys = isProperty.getMapKeys();
        KeyExpr key = BaseUtils.singleValue(keys);
        Query<Object, Object> query = new Query<Object, Object>(keys);
        query.properties.put("SIDProperty", LM.SIDProperty.getExpr(session.getModifier(), key));
        query.properties.put("isSetNotNullProperty", LM.isSetNotNullProperty.getExpr(session.getModifier(), key));
        query.and(isProperty.getExpr(key).getWhere());
        OrderedMap<Map<Object, Object>, Map<Object, Object>> result = query.execute(session.sql);

        for (Map<Object, Object> values : result.values()) {
            Object isSetNotNull = values.get("isSetNotNullProperty");
            if (isSetNotNull != null) {
                LCP<?> prop = (LCP) getLP(values.get("SIDProperty").toString().trim());
                prop.property.setNotNull = true;
                LM.setNotNull(prop);
            }
        }
    }
    
    public byte[] getBaseClassByteArray() throws RemoteException {
        try {
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            DataOutputStream dataStream = new DataOutputStream(outStream);
            LM.baseClass.serialize(dataStream);
            return outStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getPropertyObjectsByteArray(byte[] byteClasses, boolean isCompulsory, boolean isAny) {
        try {
            DataInputStream inStream = new DataInputStream(new ByteArrayInputStream(byteClasses));

            Map<Integer, Integer> groupMap = new HashMap<Integer, Integer>();
            Map<ValueClassWrapper, Integer> classes = new HashMap<ValueClassWrapper, Integer>();
            int size = inStream.readInt();
            for (int i = 0; i < size; i++) {
                Integer ID = inStream.readInt();
                ValueClass valueClass = TypeSerializer.deserializeValueClass(this, inStream);
                classes.put(new ValueClassWrapper(valueClass), ID);

                int groupId = inStream.readInt();
                if (groupId >= 0) {
                    groupMap.put(ID, groupId);
                }
            }

            ArrayList<Property> result = new ArrayList<Property>();
            ArrayList<ArrayList<Integer>> idResult = new ArrayList<ArrayList<Integer>>();

            addProperties(classes, groupMap, result, idResult, isCompulsory, isAny);

            List<Property> newResult = BaseUtils.filterList(result, getProperties());

            ByteArrayOutputStream outStream = new ByteArrayOutputStream();

            DataOutputStream dataStream = new DataOutputStream(outStream);

            ServerSerializationPool pool = new ServerSerializationPool();

            dataStream.writeInt(result.size());
            int num = 0;
            for (Property<?> property : newResult) {
                pool.serializeObject(dataStream, property);
                Iterator<Integer> it = idResult.get(num++).iterator();
                for (PropertyInterface propertyInterface : property.interfaces) {
                    pool.serializeObject(dataStream, propertyInterface);
                    dataStream.writeInt(it.next());
                }
            }

            return outStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addProperties(Map<ValueClassWrapper, Integer> classes, Map<Integer, Integer> groupMap, ArrayList<Property> result, ArrayList<ArrayList<Integer>> idResult, boolean isCompulsory, boolean isAny) {
        Set<Integer> allGroups = new HashSet<Integer>(groupMap.values());
        List<List<ValueClassWrapper>> classLists = new ArrayList<List<ValueClassWrapper>>();

        for (Set<ValueClassWrapper> classSet : new Subsets<ValueClassWrapper>(classes.keySet())) {
            List<ValueClassWrapper> classList = new ArrayList<ValueClassWrapper>(classSet);
            Set<Integer> classesGroups = new HashSet<Integer>();
            for (ValueClassWrapper wrapper : classList) {
                int id = classes.get(wrapper);
                if (groupMap.containsKey(id)) {
                    classesGroups.add(groupMap.get(id));
                }
            }
            if ((isCompulsory && classesGroups.size() == allGroups.size()) ||
                    (!isCompulsory && classesGroups.size() > 0 || groupMap.isEmpty()) || classList.isEmpty()) {
                classLists.add(classList);
            }
        }

        for (PropertyClassImplement implement : LM.rootGroup.getProperties(classLists, isAny)) {
            result.add(implement.property);
            ArrayList<Integer> ids = new ArrayList<Integer>();
            for (Object iface : implement.property.interfaces) {
                ids.add(classes.get(implement.mapping.get(iface)));
            }
            idResult.add(ids);
        }
    }

    @Override
    public FormInstance createFormInstance(FormEntity formEntity, Map<ObjectEntity, DataObject> mapObjects, DataSession session, boolean isModal, boolean newSession, boolean checkOnOk, boolean interactive) throws SQLException {
        return new FormInstance(formEntity, this, session, PolicyManager.serverSecurityPolicy, null, null, new DataObject(getServerComputer(), LM.computer), mapObjects, isModal, newSession, checkOnOk, interactive, null);
    }

    @Override
    public RemoteForm createRemoteForm(FormInstance formInstance) {
        try {
            return new RemoteForm<T, FormInstance<T>>(formInstance, exportPort, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void close() throws SQLException {
        getThreadLocalSql().close();
    }

    public UserInfo getUserInfo(String username) throws RemoteException {
        try {
            DataSession session = createSession();
            try {
                User user = readUser(username, session);
                if (user == null) {
                    throw new LoginException();
                }

                String password = (String) LM.userPassword.read(session, new DataObject(user.ID, LM.customUser));
                if (password != null) {
                    password = password.trim();
                }

                return new UserInfo(username, password, getUserRolesNames(username));
            } finally {
                session.close();
            }
        } catch (SQLException se) {
            throw new RuntimeException(getString("logics.info.error.reading.user.data"), se);
        }
    }

    @Override
    public int generateID() throws RemoteException {
        try {
            return IDTable.instance.generateID(getIDSql(), IDTable.OBJECT);
        } catch (SQLException e) {
            throw new RuntimeException(getString("logics.info.error.reading.user.data"), e);
        }
    }

    public boolean getUseUniPass() throws RemoteException {
        return Settings.instance.getUseUniPass();
    }

    private List<String> getUserRolesNames(String username) {
        try {
            DataSession session = createSession();
            try {
                Map<String, KeyExpr> keys = KeyExpr.getMapKeys(asList("user", "role"));
                Expr userExpr = keys.get("user");
                Expr roleExpr = keys.get("role");

                Query<String, String> q = new Query<String, String>(keys);
                q.and(LM.inUserMainRole.getExpr(session.getModifier(), userExpr, roleExpr).getWhere());
                q.and(LM.userLogin.getExpr(session.getModifier(), userExpr).compare(new DataObject(username), Compare.EQUALS));

                q.properties.put("roleName", LM.userRoleSID.getExpr(session.getModifier(), roleExpr));

                OrderedMap<Map<String, Object>, Map<String, Object>> values = q.execute(session.sql);

                List<String> roles = new ArrayList<String>();
                for (Map<String, Object> value : values.values()) {
                    Object rn = value.get("roleName");
                    if (rn instanceof String) {
                        String roleName = ((String) rn).trim();
                        if (!roleName.isEmpty()) {
                            roles.add(roleName);
                        }
                    }
                }
                roles.addAll(getExtraUserRoleNames(new DataObject(username)));

                return roles;
            } finally {
                session.close();
            }

        } catch (SQLException e) {
            throw new RuntimeException(getString("logics.info.error.reading.list.of.roles"), e);
        }
    }

    protected List<String> getExtraUserRoleNames(DataObject user) {
        return new ArrayList<String>();
    }

    private User authenticateUser(String login, String password) throws LoginException, SQLException {
        DataSession session = createSession();
        try {
            User user = readUser(login, session);
            if (user == null) {
                throw new LoginException();
            }
            String checkPassword = (String) LM.userPassword.read(session, new DataObject(user.ID, LM.customUser));
            if (checkPassword != null && !password.trim().equals(checkPassword.trim())) {
                throw new LoginException();
            }

            return user;
        } finally {
            session.close();
        }
    }

    public void logException(String message, String errorType, String erTrace, DataObject user, String clientName, boolean client) throws SQLException {
        DataSession session = createSession();
        DataObject exceptionObject;
        if (client) {
            exceptionObject = session.addObject(LM.clientException);
            LM.clientClientException.change(clientName, session, exceptionObject);
            String userLogin = (String) LM.userLogin.read(session, user);
            LM.loginClientException.change(userLogin, session, exceptionObject);
        } else {
            exceptionObject = session.addObject(LM.serverException);
        }
        LM.messageException.change(message, session, exceptionObject);
        LM.typeException.change(errorType, session, exceptionObject);
        LM.erTraceException.change(erTrace, session, exceptionObject);
        LM.dateException.change(DateConverter.dateToStamp(Calendar.getInstance().getTime()), session, exceptionObject);

        session.apply(this);
        session.close();
    }

    public ArrayList<IDaemonTask> getDaemonTasks(int compId) {
        return new ArrayList<IDaemonTask>();
    }

    protected Integer getUserByEmail(DataSession session, String email) throws SQLException {
        return (Integer) LM.emailToObject.read(session, new DataObject(email));
    }

    @Override
    public void remindPassword(String email, String localeLanguage) throws RemoteException {
        assert email != null;
            //todo: в будущем нужно поменять на проставление локали в Context
//            ServerResourceBundle.load(localeLanguage);
        try {
            DataSession session = createSession();
            try {
                Integer userId = getUserByEmail(session, email);
                if (userId == null) {
                    throw new RuntimeException(ServerResourceBundle.getString("mail.user.not.found") + ": " + email);
                }

                LM.emailUserPassUser.execute(session, new DataObject(userId, LM.customUser));
            } finally {
                session.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RemoteException(ServerResourceBundle.getString("mail.error.sending.password.remind"), e);
        }
    }

    @Override
    public boolean checkPropertyViewPermission(String userName, String propertySID) {
        boolean forbidView;
        try {
            User user = readUser(userName, createSession());
            SecurityPolicy policy = user.getSecurityPolicy();
            forbidView = policy.property.view.checkPermission(getProperty(propertySID));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return forbidView;
    }

    @Override
    public boolean checkDefaultViewPermission(String propertySid) throws RemoteException {
        Property property = getProperty(propertySid);
        boolean default1 = policyManager.defaultSecurityPolicy.property.view.checkPermission(property);
        Boolean default2;
        try {
            DataSession session = createSession();
            DataObject propertyObject = new DataObject(LM.SIDToProperty.read(session, new DataObject(propertySid)), LM.property);
            default2 = (Boolean) LM.permitViewProperty.read(session, propertyObject);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return default1 && default2 != null;
    }

    @Override
    public byte[] readFile(String sid, String... params) throws RemoteException {
        LCP property = (LCP) getLP(sid);
        List<PropertyInterface> interfaces = new ArrayList<PropertyInterface>(property.listInterfaces);
        DataObject[] objects = new DataObject[interfaces.size()];
        byte[] fileBytes;
        try {
            DataSession session = createSession();
            for (int i = 0; i < interfaces.size(); i++) {
                objects[i] = session.getDataObject(Integer.decode(params[i]), property.property.getInterfaceType(interfaces.get(i)));
            }
            fileBytes = (byte[]) property.read(session, objects);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return fileBytes;
    }

    @Override
    public String addUser(String username, String email, String password, String firstName, String lastName, String localeLanguage) throws RemoteException {
        try {
            //todo: в будущем нужно поменять на проставление локали в Context
//            ServerResourceBundle.load(localeLanguage);
            DataSession session = createSession();
            Object userId = LM.loginToUser.read(session, new DataObject(username, StringClass.get(30)));
            if (userId != null)
                return ServerResourceBundle.getString("logics.error.user.duplicate");

            Object emailId = LM.emailToObject.read(session, new DataObject(email, StringClass.get(50)));
            if (emailId != null) {
                return ServerResourceBundle.getString("logics.error.email.duplicate");
            }

            DataObject userObject = session.addObject(LM.customUser);
            LM.userLogin.change(username, session, userObject);
            LM.email.change(email, session, userObject);
            LM.userPassword.change(password, session, userObject);
            LM.userFirstName.change(firstName, session, userObject);
            LM.userLastName.change(lastName, session, userObject);
            session.apply(this);
        } catch (SQLException e) {
            return ServerResourceBundle.getString("logics.error.registration");
        }
        return null;
    }

    @Override
    public BusinessLogics getBL() {
        return this;
    }

    public FormInstance getFormInstance() {
        return null;
    }
}
