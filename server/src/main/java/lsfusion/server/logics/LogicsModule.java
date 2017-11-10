package lsfusion.server.logics;

import com.google.common.base.Throwables;
import lsfusion.base.BaseUtils;
import lsfusion.base.FunctionSet;
import lsfusion.base.Pair;
import lsfusion.base.col.ListFact;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.*;
import lsfusion.base.col.interfaces.mutable.MList;
import lsfusion.base.col.interfaces.mutable.MMap;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetIndex;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.interop.*;
import lsfusion.server.caches.IdentityStrongLazy;
import lsfusion.server.classes.*;
import lsfusion.server.classes.sets.ResolveClassSet;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.Time;
import lsfusion.server.data.Union;
import lsfusion.server.data.expr.StringAggUnionProperty;
import lsfusion.server.data.expr.formula.*;
import lsfusion.server.data.expr.query.GroupType;
import lsfusion.server.data.expr.query.PartitionType;
import lsfusion.server.form.entity.*;
import lsfusion.server.form.entity.drilldown.DrillDownFormEntity;
import lsfusion.server.form.entity.filter.FilterEntity;
import lsfusion.server.form.entity.filter.RegularFilterGroupEntity;
import lsfusion.server.form.instance.FormSessionScope;
import lsfusion.server.form.navigator.NavigatorAction;
import lsfusion.server.form.navigator.NavigatorElement;
import lsfusion.server.form.view.PropertyDrawView;
import lsfusion.server.form.window.AbstractWindow;
import lsfusion.server.logics.debug.ActionDelegationType;
import lsfusion.server.logics.debug.ActionPropertyDebugger;
import lsfusion.server.logics.debug.DebugInfo;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.linear.LAP;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.linear.LP;
import lsfusion.server.logics.mutables.GlobalVersion;
import lsfusion.server.logics.mutables.LastVersion;
import lsfusion.server.logics.mutables.NFLazy;
import lsfusion.server.logics.mutables.Version;
import lsfusion.server.logics.property.*;
import lsfusion.server.logics.property.actions.*;
import lsfusion.server.logics.property.actions.file.OpenActionProperty;
import lsfusion.server.logics.property.actions.file.SaveActionProperty;
import lsfusion.server.logics.property.actions.flow.*;
import lsfusion.server.logics.property.cases.ActionCase;
import lsfusion.server.logics.property.cases.CalcCase;
import lsfusion.server.logics.property.derived.*;
import lsfusion.server.logics.property.group.AbstractGroup;
import lsfusion.server.logics.property.group.AbstractNode;
import lsfusion.server.logics.scripted.*;
import lsfusion.server.logics.table.ImplementTable;
import lsfusion.server.session.LocalNestedType;
import org.antlr.runtime.RecognitionException;
import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

import static lsfusion.base.BaseUtils.add;
import static lsfusion.server.logics.PropertyUtils.*;
import static lsfusion.server.logics.property.derived.DerivedProperty.createAnd;
import static lsfusion.server.logics.property.derived.DerivedProperty.createStatic;

/**
 * User: DAle
 * Date: 16.05.11
 * Time: 17:37
 */

public abstract class LogicsModule {
    protected static final Logger logger = Logger.getLogger(LogicsModule.class);

    protected static final ActionPropertyDebugger debugger = ActionPropertyDebugger.getInstance();

    public static List<ResolveClassSet> getResolveList(ValueClass[] classes) {
        List<ResolveClassSet> classSets;
        classSets = new ArrayList<>();
        for (ValueClass cls : classes) {
            classSets.add(cls.getResolveSet());
        }
        return classSets;
    }

    // после этого шага должны быть установлены name, namespace, requiredModules
    public abstract void initModuleDependencies() throws RecognitionException;

    public abstract void initModule() throws RecognitionException;

    public abstract void initClasses() throws RecognitionException;

    public abstract void initTables() throws RecognitionException;

    public abstract void initGroups() throws RecognitionException;

    public abstract void initProperties() throws FileNotFoundException, RecognitionException;

    public abstract void initIndexes() throws RecognitionException;

    public String getErrorsDescription() { return "";}

    // Используется для всех элементов системы кроме свойств и действий
    public String transformNameToSID(String name) {
        return transformNameToSID(getNamespace(), name);
    }

    public static String transformNameToSID(String modulePrefix, String name) {
        if (modulePrefix == null) {
            return name;
        } else {
            return modulePrefix + "_" + name;
        }
    }

    public BaseLogicsModule<?> baseLM;

    protected Map<String, List<LP<?,?>>> namedModuleProperties = new HashMap<>();
    protected final Map<String, AbstractGroup> moduleGroups = new HashMap<>();
    protected final Map<String, CustomClass> moduleClasses = new HashMap<>();
    protected final Map<String, AbstractWindow> windows = new HashMap<>();
    protected final Map<String, NavigatorElement<?>> moduleNavigators = new HashMap<>();
    protected final Map<String, ImplementTable> moduleTables = new HashMap<>();

    private final Set<NavigatorElement<?>> privateNavigators = new HashSet<>();
    
    public final Map<LP<?, ?>, List<ResolveClassSet>> propClasses = new HashMap<>();
    
    protected final Map<Pair<String, Integer>, MetaCodeFragment> metaCodeFragments = new HashMap<>();

    protected Map<String, List<LogicsModule>> namespaceToModules = new LinkedHashMap<>();
    
    protected LogicsModule() {}

    public LogicsModule(String name) {
        this(name, name);
    }

    public LogicsModule(String name, String namespace) {
        this(name, namespace, new HashSet<String>());
    }

    public LogicsModule(String name, String namespace, Set<String> requiredModules) {
        this.name = name;
        this.namespace = namespace;
        this.requiredModules = requiredModules;
    }

    private String name;
    private String namespace;
    private Set<String> requiredModules;
    private List<String> namespacePriority;
    private boolean defaultNamespace;

    public String getName() {
        return name;
    }

    protected void setName(String name) {
        this.name = name;
    }

    public String getLogName(int moduleCount, int orderNum) {
        String result = name;
        if(order != null)
            result = "#" + orderNum + " of " + moduleCount + " " + result;
        return result;
    }    

    protected LP<?, ?> getLPByName(String name) {
        List<LP<?, ?>> result = new ArrayList<>();
        for (List<LP<?, ?>> namedLPs : namedModuleProperties.values()) {
            for (LP<?, ?> property : namedLPs) {
                String actualName = property.property.getName(); 
                if (name.equals(actualName)) {
                    result.add(property);                        
                }
            }
        }
        assert result.size() == 1;
        return result.get(0);
    }

    private List<LP<?, ?>> getAllLPByName(String name) {
        List<LP<?, ?>> allLP = namedModuleProperties.get(name); 
        return allLP != null ? allLP : new ArrayList<LP<?, ?>>(); 
    }
    
    protected LCP<?> getLCPByName(String name) {
        return (LCP<?>) getLPByName(name);
    }

    public Map<String, List<LP<?,?>>> getNamedModuleProperties() {
        return namedModuleProperties;        
    }
    
    protected void addModuleLP(LP<?, ?> lp) {
        String name = lp.property.getName();
        if (name != null) {
            if (!namedModuleProperties.containsKey(name)) {
                namedModuleProperties.put(name, new ArrayList<LP<?, ?>>());
            } 
            namedModuleProperties.get(name).add(lp);
        }
    }

    protected void removeModuleLP(LP<?, ?> lp) {
        String name = lp.property.getName();
        if (name != null) {
            if (namedModuleProperties.containsKey(name)) {
                namedModuleProperties.get(name).remove(lp);
                if (namedModuleProperties.get(name).isEmpty()) {
                    namedModuleProperties.remove(name);
                }
            }
        }
    }

    @NFLazy
    protected <P extends PropertyInterface, T extends LP<P, ?>> void makePropertyPublic(T lp, String name, List<ResolveClassSet> signature) {
        lp.property.setCanonicalName(getNamespace(), name, signature, lp.listInterfaces, baseLM.getDBNamePolicy());
        propClasses.put(lp, signature);
        addModuleLP(lp);
    }

    protected <T extends LP> void makePropertyPublic(T lp, String name, ResolveClassSet... signature) {
        makePropertyPublic(lp, name, Arrays.asList(signature));
    }

    protected AbstractGroup getGroupBySID(String sid) {
        return moduleGroups.get(sid);
    }

    public AbstractGroup getGroup(String name) {
        return getGroupBySID(transformNameToSID(name));
    }

    protected void addModuleGroup(AbstractGroup group) {
        assert !moduleGroups.containsKey(group.getSID());
        moduleGroups.put(group.getSID(), group);
    }

    protected CustomClass getClassBySID(String sid) {
        return moduleClasses.get(sid);
    }

    public CustomClass getClass(String name) {
        return getClassBySID(transformNameToSID(name));
    }

    protected void addModuleClass(CustomClass valueClass) {
        assert !moduleClasses.containsKey(valueClass.getSID());
        moduleClasses.put(valueClass.getSID(), valueClass);
    }

    protected ImplementTable getTableBySID(String sid) {
        return moduleTables.get(sid);
    }

    public ImplementTable getTable(String name) {
        return getTableBySID(transformNameToSID(name));
    }

    protected void addModuleTable(ImplementTable table) {
        assert !moduleTables.containsKey(table.getName());
        moduleTables.put(table.getName(), table);
    }

    protected <T extends AbstractWindow> T addWindow(T window) {
        assert !windows.containsKey(window.getSID());
        windows.put(window.getSID(), window);
        return window;
    }

    protected <T extends AbstractWindow> T addWindow(String name, T window) {
        window.setSID(transformNameToSID(name));
        return addWindow(window);
    }

    public AbstractWindow getWindow(String name) {
        return getWindowBySID(transformNameToSID(name));
    }

    protected AbstractWindow getWindowBySID(String sid) {
        return windows.get(sid);
    }

    public MetaCodeFragment getMetaCodeFragment(String name, int paramCnt) {
        return getMetaCodeFragmentBySID(transformNameToSID(name), paramCnt);
    }

    protected MetaCodeFragment getMetaCodeFragmentBySID(String sid, int paramCnt) {
        return metaCodeFragments.get(new Pair<>(sid, paramCnt));
    }

    protected void addMetaCodeFragment(String name, MetaCodeFragment fragment) {
        assert !metaCodeFragments.containsKey(new Pair<>(transformNameToSID(name), fragment.parameters.size()));
        metaCodeFragments.put(new Pair<>(transformNameToSID(name), fragment.parameters.size()), fragment);
    }

    // aliases для использования внутри иерархии логических модулей
    protected BaseClass baseClass;

    public AbstractGroup rootGroup;
    public AbstractGroup publicGroup;
    public AbstractGroup baseGroup;
    public AbstractGroup recognizeGroup;

    protected void setBaseLogicsModule(BaseLogicsModule<?> baseLM) {
        this.baseLM = baseLM;
    }

    protected void initBaseGroupAliases() {
        this.rootGroup = baseLM.rootGroup;
        this.publicGroup = baseLM.publicGroup;
        this.baseGroup = baseLM.baseGroup;
        this.recognizeGroup = baseLM.recognizeGroup;
    }

    protected void initBaseClassAliases() {
        this.baseClass = baseLM.baseClass;
    }

    protected AbstractGroup addAbstractGroup(String name, LocalizedString caption) {
        return addAbstractGroup(name, caption, null);
    }

    public FunctionSet<Version> visible;
    public Integer order;
    private final Version version = new Version() {
        public boolean canSee(Version version) {
            assert !(version instanceof LastVersion);
            return version instanceof GlobalVersion || visible.contains(version);
        }

        public Integer getOrder() {
            return order;
        }

        public int compareTo(Version o) {
            return getOrder().compareTo(o.getOrder());
        }
    };
    public Version getVersion() {
        return version;
    }
    
    protected AbstractGroup addAbstractGroup(String name, LocalizedString caption, AbstractGroup parent) {
        return addAbstractGroup(name, caption, parent, true);
    }

    protected AbstractGroup addAbstractGroup(String name, LocalizedString caption, AbstractGroup parent, boolean toCreateContainer) {
        AbstractGroup group = new AbstractGroup(transformNameToSID(name), caption);
        Version version = getVersion();
        if (parent != null) {
            parent.add(group, version);
        } else {
            if (baseLM.privateGroup != null)
                baseLM.privateGroup.add(group, version);
        }
        group.createContainer = toCreateContainer;
        addModuleGroup(group);
        return group;
    }

    protected void storeCustomClass(CustomClass customClass) {
        addModuleClass(customClass);
    }

    protected BaseClass addBaseClass(String sID, LocalizedString caption) {
        BaseClass baseClass = new BaseClass(sID, caption, getVersion());
        storeCustomClass(baseClass);
        return baseClass;
    }

    protected ConcreteCustomClass addConcreteClass(String name, LocalizedString caption, CustomClass... parents) {
        return addConcreteClass(name, caption, new ArrayList<String>(), new ArrayList<LocalizedString>(), parents);
    }

    protected ConcreteCustomClass addConcreteClass(String name, LocalizedString caption, String[] sids, LocalizedString[] names, CustomClass... parents) {
        return addConcreteClass(name, caption, BaseUtils.toList(sids), BaseUtils.toList(names), parents);
    }

    protected static void printStaticObjectsChanges(String path, String staticName, List<String> sids) {
        try {
            PrintWriter w = new PrintWriter(new FileWriter(path, true));
            for (String sid : sids) {
                w.print("OBJECT " + sid + " -> " + staticName + "." + sid + "\n");
            }
            w.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected ConcreteCustomClass addConcreteClass(String name, LocalizedString caption, List<String> objNames, List<LocalizedString> objCaptions, CustomClass... parents) {
        assert parents.length > 0;
        ConcreteCustomClass customClass = new ConcreteCustomClass(transformNameToSID(name), caption, getVersion(), parents);
        customClass.addStaticObjects(objNames, objCaptions, getVersion());
        storeCustomClass(customClass);
        return customClass;
    }

    protected AbstractCustomClass addAbstractClass(String name, LocalizedString caption, CustomClass... parents) {
        AbstractCustomClass customClass = new AbstractCustomClass(transformNameToSID(name), caption, getVersion(), parents);
        storeCustomClass(customClass);
        return customClass;
    }

    protected ImplementTable addTable(String name, boolean isFull, ValueClass... classes) {
        ImplementTable table = baseLM.tableFactory.include(transformNameToSID(name), getVersion(), classes);
        addModuleTable(table);
        if(isFull) {
            if(classes.length == 1)
                table.markedFull = true;
            else
                markFull(table, classes);
        }
        return table;
    }

    protected void markFull(ImplementTable table, ValueClass... classes) {
        // создаем IS
        ImList<ValueClass> listClasses = ListFact.toList(classes);
        CalcPropertyRevImplement<?, Integer> mapProperty = IsClassProperty.getProperty(listClasses.toIndexedMap()); // тут конечно стремновато из кэша брать, так как остальные гарантируют создание
        LCP<?> lcp = addJProp(mapProperty.createLP(ListFact.consecutiveList(listClasses.size(), 0)), ListFact.consecutiveList(listClasses.size()).toArray(new Integer[listClasses.size()]));
//        addProperty(null, lcp);

        // делаем public, persistent
        makePropertyPublic(lcp, PropertyCanonicalNameUtils.fullPropPrefix + table.getName(), getResolveList(classes));
        addPersistent(lcp, table);

        // помечаем fullField из помеченного свойства
        table.setFullField(lcp.property.field);
    }

    //////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////
    /// Properties
    //////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////

    // ------------------- DATA ----------------- //

    protected LCP addDProp(LocalizedString caption, ValueClass value, ValueClass... params) {
        return addDProp(null, false, caption, value, params);
    }

    protected LCP addDProp(AbstractGroup group, LocalizedString caption, ValueClass value, ValueClass... params) {
        return addDProp(group, false, caption, value, params);
    }

    protected LCP addDProp(AbstractGroup group, boolean persistent, LocalizedString caption, ValueClass value, ValueClass... params) {
        StoredDataProperty dataProperty = new StoredDataProperty(caption, params, value);
        LCP lp = addProperty(group, persistent, new LCP<>(dataProperty));
        dataProperty.markStored(baseLM.tableFactory);
        return lp;
    }

    // ------------------- Loggable ----------------- //

    protected <D extends PropertyInterface> LCP addDCProp(AbstractGroup group, LocalizedString caption, int whereNum, LCP<D> derivedProp, Object... params) {
        Pair<ValueClass[], ValueClass> signature = getSignature(derivedProp, whereNum, params);

        // выполняем само создание свойства
        StoredDataProperty dataProperty = new StoredDataProperty(caption, signature.first, signature.second);
        LCP derDataProp = addProperty(group, false, new LCP<>(dataProperty));

        derDataProp.setEventChange(derivedProp, whereNum, params);
        return derDataProp;
    }

    protected <D extends PropertyInterface> LCP addLogProp(AbstractGroup group, LocalizedString caption, int whereNum, LCP<D> derivedProp, Object... params) {
        Pair<ValueClass[], ValueClass> signature = getSignature(derivedProp, whereNum, params);

        // выполняем само создание свойства
        StoredDataProperty dataProperty = new StoredDataProperty(caption, signature.first, LogicalClass.instance);
        return addProperty(group, false, new LCP<>(dataProperty));
    }

    private <D extends PropertyInterface> Pair<ValueClass[], ValueClass> getSignature(LCP<D> derivedProp, int whereNum, Object[] params) {
        // придется создавать Join свойство чтобы считать его класс
        int dersize = getIntNum(params);
        ImOrderSet<JoinProperty.Interface> listInterfaces = JoinProperty.getInterfaces(dersize);

        final ImList<CalcPropertyInterfaceImplement<JoinProperty.Interface>> list = readCalcImplements(listInterfaces, params);

        assert whereNum == list.size() - 1; // один ON CHANGED, то есть union делать не надо (выполняется, так как только в addLProp работает)

        AndFormulaProperty andProperty = new AndFormulaProperty(list.size());
        ImMap<AndFormulaProperty.Interface, CalcPropertyInterfaceImplement<JoinProperty.Interface>> mapImplement =
                MapFact.<AndFormulaProperty.Interface, CalcPropertyInterfaceImplement<JoinProperty.Interface>>addExcl(
                        andProperty.andInterfaces.mapValues(new GetIndex<CalcPropertyInterfaceImplement<JoinProperty.Interface>>() {
                            public CalcPropertyInterfaceImplement<JoinProperty.Interface> getMapValue(int i) {
                                return list.get(i);
                            }
                        }), andProperty.objectInterface, mapCalcListImplement(derivedProp, listInterfaces));

        JoinProperty<AndFormulaProperty.Interface> joinProperty = new JoinProperty<>(LocalizedString.create("sys"), listInterfaces,
                new CalcPropertyImplement<>(andProperty, mapImplement));
        LCP<JoinProperty.Interface> listProperty = new LCP<>(joinProperty, listInterfaces);

        // получаем классы
        ValueClass[] commonClasses = listProperty.getInterfaceClasses(ClassType.logPolicy); // есть и другие obsolete использования
        ValueClass valueClass = listProperty.property.getValueClass(ClassType.logPolicy);
        return new Pair<>(commonClasses, valueClass);
    }

    // ------------------- Scripted DATA ----------------- //

    protected LCP addSDProp(LocalizedString caption, boolean isLocalScope, ValueClass value, LocalNestedType nestedType, ValueClass... params) {
        return addSDProp(null, false, caption, isLocalScope, value, nestedType, params);
    }

    protected LCP addSDProp(AbstractGroup group, boolean persistent, LocalizedString caption, boolean isLocalScope, ValueClass value, LocalNestedType nestedType, ValueClass... params) {
        SessionDataProperty prop = new SessionDataProperty(caption, params, value);
        if (isLocalScope) {
            prop.setLocal(true);
        }
        prop.nestedType = nestedType;
        return addProperty(group, persistent, new LCP<>(prop));
    }

    // ------------------- Form actions ----------------- //


    protected LAP addFormAProp(LocalizedString caption, CustomClass cls, LAP action) {
        return addIfAProp(caption, is(cls), 1, action, 1); // по идее можно просто exec сделать, но на всякий случай
    }

    protected LAP addEditAProp(LocalizedString caption, CustomClass cls) {
        cls.markUsed(true);
        return addFormAProp(caption, cls, baseLM.getFormEdit());
    }

    protected LAP addDeleteAProp(LocalizedString caption, CustomClass cls) {
        return addFormAProp(caption, cls, baseLM.getFormDelete());
    }

    // loggable, security, drilldown
    public LAP addMFAProp(LocalizedString caption, FormEntity form, ObjectEntity[] objectsToSet, boolean newSession) {
        LAP result = addIFAProp(caption, form, BaseUtils.toList(objectsToSet), ManageSessionType.AUTO, FormEntity.DEFAULT_NOCANCEL, true, WindowFormType.FLOAT);
        return addSessionScopeAProp(newSession ? FormSessionScope.NEWSESSION : FormSessionScope.OLDSESSION, result);
    }

    protected <O extends ObjectSelector> LAP addIFAProp(LocalizedString caption, FormSelector<O> form, List<O> objectsToSet, ManageSessionType manageSession, Boolean noCancel, boolean syncType, WindowFormType windowType) {
        return addIFAProp(null, caption, form, objectsToSet, Collections.nCopies(objectsToSet.size(), false), manageSession, noCancel, syncType, windowType, false, false);
    }
    protected <O extends ObjectSelector> LAP addIFAProp(AbstractGroup group, LocalizedString caption, FormSelector<O> form, List<O> objectsToSet, List<Boolean> nulls, ManageSessionType manageSession, Boolean noCancel, boolean syncType, WindowFormType windowType, boolean checkOnOk, boolean readonly, Object... params) {
        return addIFAProp(group, caption, form, objectsToSet, nulls, ListFact.<O>EMPTY(), ListFact.<LCP>EMPTY(), ListFact.<Boolean>EMPTY(), manageSession, noCancel, ListFact.<O>EMPTY(), ListFact.<CalcProperty>EMPTY(), syncType, windowType, checkOnOk, readonly, params);
    }
    protected <O extends ObjectSelector> LAP addIFAProp(AbstractGroup group, LocalizedString caption, FormSelector<O> form, List<O> objectsToSet, List<Boolean> nulls, ImList<O> inputObjects, ImList<LCP> inputProps, ImList<Boolean> inputNulls, ManageSessionType manageSession, Boolean noCancel, ImList<O> contextObjects, ImList<CalcProperty> contextProperties, boolean syncType, WindowFormType windowType, boolean checkOnOk, boolean readonly, Object... params) {
        return addProperty(group, new LAP(new FormInteractiveActionProperty<O>(caption, form, objectsToSet, nulls, inputObjects, inputProps, inputNulls, contextObjects, contextProperties, manageSession, noCancel, syncType, windowType, checkOnOk, readonly)));
    }
    protected <O extends ObjectSelector> LAP addPFAProp(AbstractGroup group, LocalizedString caption, FormSelector<O> form, List<O> objectsToSet, List<Boolean> nulls, boolean hasPrinterProperty, FormPrintType staticType, boolean syncType, Integer selectTop, LCP targetProp, Object... params) {
        ImOrderSet<PropertyInterface> listInterfaces = genInterfaces(getIntNum(params));
        ImList<PropertyInterfaceImplement<PropertyInterface>> readImplements = readImplements(listInterfaces, params);
        CalcPropertyMapImplement printer = hasPrinterProperty ? (CalcPropertyMapImplement) readImplements.get(0) : null;
        return addProperty(group, new LAP(new PrintActionProperty(caption, form, objectsToSet, nulls, staticType, syncType, selectTop, targetProp, printer, listInterfaces, baseLM.formPageCount)));
    }
    protected <O extends ObjectSelector> LAP addEFAProp(AbstractGroup group, LocalizedString caption, FormSelector<O> form, List<O> objectsToSet, List<Boolean> nulls, FormExportType staticType, boolean noHeader, String separator, String charset, LCP targetProp, Object... params) {
        if(targetProp == null)
            targetProp = (staticType.isPlain() ? baseLM.formExportFiles : baseLM.formExportFile); 
        return addProperty(group, new LAP(new ExportActionProperty<O>(caption, form, objectsToSet, nulls, staticType, targetProp, noHeader, separator, charset)));
    }

    // ------------------- Change Class action ----------------- //

    protected LAP addChangeClassAProp(ConcreteObjectClass cls, int resInterfaces, int changeIndex, boolean extendedContext, boolean conditional, Object... params) {
        int innerIntCnt = resInterfaces + (extendedContext ? 1 : 0);
        ImOrderSet<PropertyInterface> innerInterfaces = genInterfaces(innerIntCnt);
        ImOrderSet<PropertyInterface> mappedInterfaces = extendedContext ? innerInterfaces.removeOrderIncl(innerInterfaces.get(changeIndex)) : innerInterfaces;
        ImList<CalcPropertyInterfaceImplement<PropertyInterface>> readImplements = readCalcImplements(innerInterfaces, params);
        CalcPropertyMapImplement<PropertyInterface, PropertyInterface> conditionalPart = (CalcPropertyMapImplement<PropertyInterface, PropertyInterface>)
                (conditional ? readImplements.get(resInterfaces) : null);

        return addAProp(new ChangeClassActionProperty<>(cls, false, innerInterfaces.getSet(),
                mappedInterfaces, innerInterfaces.get(changeIndex), conditionalPart, baseClass));
    }

    // ------------------- Set property action ----------------- //

    protected <C extends PropertyInterface, W extends PropertyInterface> LAP addSetPropertyAProp(int resInterfaces,boolean conditional, Object... params) {
        return addSetPropertyAProp(null, LocalizedString.create("sys"), resInterfaces, conditional, params);
    }

    protected <C extends PropertyInterface, W extends PropertyInterface> LAP addSetPropertyAProp(AbstractGroup group, LocalizedString caption, int resInterfaces,
                                                                                                 boolean conditional, Object... params) {
        ImOrderSet<PropertyInterface> innerInterfaces = genInterfaces(getIntNum(params));
        ImList<CalcPropertyInterfaceImplement<PropertyInterface>> readImplements = readCalcImplements(innerInterfaces, params);
        CalcPropertyMapImplement<W, PropertyInterface> conditionalPart = (CalcPropertyMapImplement<W, PropertyInterface>)
                (conditional ? readImplements.get(resInterfaces + 2) : DerivedProperty.createTrue());
        return addProperty(group, new LAP(new SetActionProperty<C, W, PropertyInterface>(caption,
                innerInterfaces.getSet(), (ImOrderSet) readImplements.subList(0, resInterfaces).toOrderExclSet(), conditionalPart,
                (CalcPropertyMapImplement<C, PropertyInterface>) readImplements.get(resInterfaces), readImplements.get(resInterfaces + 1))));
    }

    // ------------------- List action ----------------- //

    protected LAP addListAProp(Object... params) {
        return addListAProp(SetFact.<SessionDataProperty>EMPTY(), params);
    }
    protected LAP addListAProp(ImSet<SessionDataProperty> localsInScope, Object... params) {
        return addListAProp(null, 0, LocalizedString.create("sys"), localsInScope, params);
    }
    protected LAP addListAProp(int removeLast, Object... params) {
        return addListAProp(null, removeLast, LocalizedString.create("sys"), SetFact.<SessionDataProperty>EMPTY(), params);
    }
    protected LAP addListAProp(LocalizedString caption, Object... params) {
        return addListAProp(null, 0, caption, SetFact.<SessionDataProperty>EMPTY(), params);        
    }
    protected LAP addListAProp(AbstractGroup group, int removeLast, LocalizedString caption, ImSet<SessionDataProperty> localsInScope, Object... params) {
        ImOrderSet<PropertyInterface> listInterfaces = genInterfaces(getIntNum(params));
        return addProperty(group, new LAP(new ListActionProperty(caption, listInterfaces,
                readActionImplements(listInterfaces, removeLast > 0 ? Arrays.copyOf(params, params.length - removeLast) : params), localsInScope)));
    }

    protected LAP addAbstractListAProp(boolean isChecked, boolean isLast, ValueClass[] params) {
        ImOrderSet<PropertyInterface> listInterfaces = genInterfaces(params.length);
        return addProperty(null, new LAP(new ListActionProperty(LocalizedString.create("sys"), isChecked, isLast, listInterfaces, listInterfaces.mapList(ListFact.toList(params)))));
    }

    // ------------------- Try action ----------------- //

    protected LAP addTryAProp(AbstractGroup group, LocalizedString caption, Object... params) {
        ImOrderSet<PropertyInterface> listInterfaces = genInterfaces(getIntNum(params));
        ImList<PropertyInterfaceImplement<PropertyInterface>> readImplements = readImplements(listInterfaces, params);
        assert readImplements.size() >= 1 && readImplements.size() <= 2;

        return addProperty(group, new LAP(new TryActionProperty(caption, listInterfaces, (ActionPropertyMapImplement<?, PropertyInterface>) readImplements.get(0),
                readImplements.size() == 2 ? (ActionPropertyMapImplement<?, PropertyInterface>) readImplements.get(1) : null)));
    }
    
    // ------------------- If action ----------------- //

    protected LAP addIfAProp(Object... params) {
        return addIfAProp(null, LocalizedString.create("sys"), false, params);
    }

    protected LAP addIfAProp(LocalizedString caption, Object... params) {
        return addIfAProp(null, caption, false, params);
    }

    protected LAP addIfAProp(AbstractGroup group, LocalizedString caption, boolean not, Object... params) {
        ImOrderSet<PropertyInterface> listInterfaces = genInterfaces(getIntNum(params));
        ImList<PropertyInterfaceImplement<PropertyInterface>> readImplements = readImplements(listInterfaces, params);
        assert readImplements.size() >= 2 && readImplements.size() <= 3;

        return addProperty(group, new LAP(CaseActionProperty.createIf(caption, not, listInterfaces, (CalcPropertyInterfaceImplement<PropertyInterface>) readImplements.get(0),
                (ActionPropertyMapImplement<?, PropertyInterface>) readImplements.get(1), readImplements.size() == 3 ? (ActionPropertyMapImplement<?, PropertyInterface>) readImplements.get(2) : null)));
    }

    // ------------------- Case action ----------------- //

    protected LAP addCaseAProp(boolean isExclusive, Object... params) {
        ImOrderSet<PropertyInterface> listInterfaces = genInterfaces(getIntNum(params));
        ImList<PropertyInterfaceImplement<PropertyInterface>> readImplements = readImplements(listInterfaces, params);

        MList<ActionCase<PropertyInterface>> mCases = ListFact.mList();
        for (int i = 0; i*2+1 < readImplements.size(); i++) {
            mCases.add(new ActionCase((CalcPropertyMapImplement<?, PropertyInterface>) readImplements.get(i*2), (ActionPropertyMapImplement<?, PropertyInterface>) readImplements.get(i*2+1)));
        }
        if(readImplements.size() % 2 != 0) {
            mCases.add(new ActionCase(DerivedProperty.createTrue(), (ActionPropertyMapImplement<?, PropertyInterface>) readImplements.get(readImplements.size() - 1)));
        }
        return addProperty(null, new LAP(new CaseActionProperty(LocalizedString.create(""), isExclusive, listInterfaces, mCases.immutableList())));
    }

    protected LAP addMultiAProp(boolean isExclusive, Object... params) {
        ImOrderSet<PropertyInterface> listInterfaces = genInterfaces(getIntNum(params));
        ImList<PropertyInterfaceImplement<PropertyInterface>> readImplements = readImplements(listInterfaces, params);

        MList<ActionPropertyMapImplement> mCases = ListFact.mList();
        for (int i = 0; i < readImplements.size(); i++) {
            mCases.add((ActionPropertyMapImplement) readImplements.get(i));
        }
        return addProperty(null, new LAP(new CaseActionProperty(LocalizedString.create(""), isExclusive, mCases.immutableList(), listInterfaces)));
    }

    protected LAP addAbstractCaseAProp(ListCaseActionProperty.AbstractType type, boolean isExclusive, boolean isChecked, boolean isLast, ValueClass[] params) {
        ImOrderSet<PropertyInterface> listInterfaces = genInterfaces(params.length);
        return addProperty(null, new LAP(new CaseActionProperty(LocalizedString.create("sys"), isExclusive, isChecked, isLast, type, listInterfaces, listInterfaces.mapList(ListFact.toList(params)))));
    }

    // ------------------- For action ----------------- //

    protected LAP addForAProp(AbstractGroup group, LocalizedString caption, boolean ascending, boolean ordersNotNull, boolean recursive, boolean hasElse, int resInterfaces, CustomClass addClass, boolean autoSet, boolean hasCondition, int noInline, boolean forceInline, Object... params) {
        ImOrderSet<PropertyInterface> innerInterfaces = genInterfaces(getIntNum(params));
        ImList<PropertyInterfaceImplement<PropertyInterface>> readImplements = readImplements(innerInterfaces, params);

        int implCnt = readImplements.size();

        ImOrderSet<PropertyInterface> mapInterfaces = BaseUtils.immutableCast(readImplements.subList(0, resInterfaces).toOrderExclSet());

        CalcPropertyMapImplement<?, PropertyInterface> ifProp = hasCondition? (CalcPropertyMapImplement<?, PropertyInterface>) readImplements.get(resInterfaces) : null;

        ImOrderMap<CalcPropertyInterfaceImplement<PropertyInterface>, Boolean> orders =
                BaseUtils.<ImList<CalcPropertyInterfaceImplement<PropertyInterface>>>immutableCast(readImplements.subList(resInterfaces + (hasCondition ? 1 : 0), implCnt - (hasElse ? 2 : 1) - (addClass != null ? 1: 0) - noInline)).toOrderExclSet().toOrderMap(!ascending);

        PropertyInterface addedInterface = addClass!=null ? (PropertyInterface) readImplements.get(implCnt - (hasElse ? 3 : 2) - noInline) : null;

        ActionPropertyMapImplement<?, PropertyInterface> elseAction =
                !hasElse ? null : (ActionPropertyMapImplement<?, PropertyInterface>) readImplements.get(implCnt - 2 - noInline);

        ActionPropertyMapImplement<?, PropertyInterface> action =
                (ActionPropertyMapImplement<?, PropertyInterface>) readImplements.get(implCnt - 1 - noInline);

        ImSet<PropertyInterface> noInlineInterfaces = BaseUtils.<ImList<PropertyInterface>>immutableCast(readImplements.subList(implCnt - noInline, implCnt)).toOrderExclSet().getSet();

        return addProperty(group, new LAP<>(
                new ForActionProperty<>(caption, innerInterfaces.getSet(), mapInterfaces, ifProp, orders, ordersNotNull, action, elseAction, addedInterface, addClass, autoSet, recursive, noInlineInterfaces, forceInline))
        );
    }

    // ------------------- JOIN ----------------- //

    public LAP addJoinAProp(LAP action, Object... params) {
        return addJoinAProp(LocalizedString.create("sys"), action, params);
    }

    protected LAP addJoinAProp(LocalizedString caption, LAP action, Object... params) {
        return addJoinAProp(null, caption, action, params);
    }

    protected LAP addJoinAProp(AbstractGroup group, LocalizedString caption, LAP action, Object... params) {
        return addJoinAProp(group, caption, null, action, params);
    }

    protected LAP addJoinAProp(AbstractGroup group, LocalizedString caption, ValueClass[] classes, LAP action, Object... params) {
        ImOrderSet<PropertyInterface> listInterfaces = genInterfaces(getIntNum(params));
        ImList<CalcPropertyInterfaceImplement<PropertyInterface>> readImplements = readCalcImplements(listInterfaces, params);
        return addProperty(group, new LAP(new JoinActionProperty(caption, listInterfaces, mapActionImplement(action, readImplements))));
    }

    // ------------------------ APPLY / CANCEL ----------------- //

    protected LAP addApplyAProp(AbstractGroup group, LocalizedString caption, LAP action, boolean singleApply,
                                FunctionSet<SessionDataProperty> keepSessionProps, boolean serializable) {
        
        ImOrderSet<PropertyInterface> listInterfaces = genInterfaces(action.listInterfaces.size());
        ActionPropertyMapImplement<?, PropertyInterface> actionImplement = mapActionListImplement(action, listInterfaces);

        ApplyActionProperty applyAction = new ApplyActionProperty(baseLM, actionImplement, caption, listInterfaces, keepSessionProps, serializable);
        actionImplement.property.singleApply = singleApply;
        return addProperty(group, new LAP(applyAction));
    }

    protected LAP addCancelAProp(AbstractGroup group, LocalizedString caption, FunctionSet<SessionDataProperty> keepSessionProps) {

        CancelActionProperty applyAction = new CancelActionProperty(caption, keepSessionProps);
        return addProperty(group, new LAP(applyAction));
    }

    // ------------------- SESSION SCOPE ----------------- //

    protected LAP addSessionScopeAProp(FormSessionScope sessionScope, LAP action) {
        return addSessionScopeAProp(sessionScope, action, SetFact.<LCP>EMPTY());
    }
    protected LAP addSessionScopeAProp(FormSessionScope sessionScope, LAP action, ImCol<LCP> nestedProps) {
        if(sessionScope.isNewSession()) {
            action = addNewSessionAProp(null, action, sessionScope.isNestedSession(), false, false, nestedProps.mapMergeSetValues(new GetValue<SessionDataProperty, LCP>() {
                public SessionDataProperty getMapValue(LCP value) {
                    return (SessionDataProperty) value.property;
                }
            }));
        }
        return action;
    }

    // ------------------- NEWSESSION ----------------- //

    protected LAP addNewSessionAProp(AbstractGroup group,
                                     LAP action, boolean isNested, boolean singleApply, boolean newSQL,
                                     FunctionSet<SessionDataProperty> migrateSessionProps) {
        ImOrderSet<PropertyInterface> listInterfaces = genInterfaces(action.listInterfaces.size());
        ActionPropertyMapImplement<?, PropertyInterface> actionImplement = mapActionListImplement(action, listInterfaces);

        NewSessionActionProperty actionProperty = new NewSessionActionProperty(
                LocalizedString.create("sys"), listInterfaces, actionImplement, singleApply, newSQL, migrateSessionProps, isNested);
        
        actionProperty.drawOptions.inheritDrawOptions(action.property.drawOptions);
        actionProperty.inheritCaption(action.property);
        
        return addProperty(group, new LAP(actionProperty));
    }

    protected LAP addNewThreadAProp(AbstractGroup group, LocalizedString caption, boolean withConnection, boolean hasPeriod, boolean hasDelay, Object... params) {
        ImOrderSet<PropertyInterface> listInterfaces = genInterfaces(getIntNum(params));
        ImList<PropertyInterfaceImplement<PropertyInterface>> readImplements = readImplements(listInterfaces, params);
        CalcPropertyInterfaceImplement connection = withConnection ? (CalcPropertyInterfaceImplement) readImplements.get(1) : null;
        CalcPropertyInterfaceImplement period = hasPeriod ? (CalcPropertyInterfaceImplement) readImplements.get(1) : null;
        CalcPropertyInterfaceImplement delay = hasDelay ? (CalcPropertyInterfaceImplement) readImplements.get(hasPeriod ? 2 : 1) : null;
        return addProperty(group, new LAP(new NewThreadActionProperty(caption, listInterfaces, (ActionPropertyMapImplement) readImplements.get(0), period, delay, connection)));
    }

    protected LAP addNewExecutorAProp(AbstractGroup group, LocalizedString caption, Object... params) {
        ImOrderSet<PropertyInterface> listInterfaces = genInterfaces(getIntNum(params));
        ImList<PropertyInterfaceImplement<PropertyInterface>> readImplements = readImplements(listInterfaces, params);
        return addProperty(group, new LAP(new NewExecutorActionProperty(caption, listInterfaces,
                (ActionPropertyMapImplement) readImplements.get(0), (CalcPropertyInterfaceImplement) readImplements.get(1))));
    }

    // ------------------- Request action ----------------- //

    protected LP addRequestAProp(AbstractGroup group, LocalizedString caption, Object... params) {
        ImOrderSet<PropertyInterface> listInterfaces = genInterfaces(getIntNum(params));
        ImList<PropertyInterfaceImplement<PropertyInterface>> readImplements = readImplements(listInterfaces, params);
        assert readImplements.size() >= 2;

        ActionPropertyMapImplement<?, PropertyInterface> elseAction =  readImplements.size() == 3 ? (ActionPropertyMapImplement<?, PropertyInterface>) readImplements.get(2) : null;
        return addProperty(group, new LAP(new RequestActionProperty(caption, listInterfaces, 
                (ActionPropertyMapImplement<?, PropertyInterface>) readImplements.get(0), (ActionPropertyMapImplement<?, PropertyInterface>) readImplements.get(1),
                elseAction))
        );
    }

    // ------------------- Input ----------------- //

    protected LP addInputAProp(AbstractGroup group, LocalizedString caption, DataClass dataClass, LCP<?> targetProp, Object... params) {
        return addJoinAProp(group, caption, addInputAProp(dataClass, targetProp != null ? targetProp.property : null), params);
    }
    @IdentityStrongLazy
    protected LAP addInputAProp(DataClass dataClass, CalcProperty targetProp) { // так как у LCP нет 
        return addProperty(null, new LAP(new InputActionProperty(LocalizedString.create("Input"), dataClass, targetProp != null ? new LCP(targetProp) : null)));
    }

    // ------------------- Constant ----------------- //

    protected <T extends PropertyInterface> LCP addCProp(StaticClass valueClass, Object value) {
        return baseLM.addCProp(valueClass, value);
    }

    // ------------------- TIME ----------------- //

    protected LCP addTProp(LocalizedString caption, Time time) {
        return addProperty(null, new LCP<>(new TimeFormulaProperty(caption, time)));
    }

    // ------------------- Random ----------------- //

    protected LCP addRMProp(LocalizedString caption) {
        return addProperty(null, new LCP<>(new RandomFormulaProperty(caption)));
    }

    // ------------------- FORMULA ----------------- //

    protected LCP addSFProp(String formula, int paramCount) {
        return addSFProp(formula, null, paramCount);
    }

    protected LCP addSFProp(CustomFormulaSyntax formula, int paramCount, boolean hasNotNull) {
        return addSFProp(formula, (DataClass) null, paramCount, hasNotNull);
    }

    protected LCP addSFProp(String formula, DataClass value, int paramCount) {
        return addSFProp(new CustomFormulaSyntax(formula), value, paramCount, false);
    }
    
    protected LCP addSFProp(CustomFormulaSyntax formula, DataClass value, int paramCount, boolean hasNotNull) {
        return addProperty(null, new LCP<>(new StringFormulaProperty(value, formula, paramCount, hasNotNull)));
    }

    // ------------------- Операции сравнения ----------------- //

    protected LCP addCFProp(Compare compare) {
        return addProperty(null, new LCP<>(new CompareFormulaProperty(compare)));
    }

    // ------------------- Алгебраические операции ----------------- //

    protected LCP addSumProp() {
        return addProperty(null, new LCP<>(new FormulaImplProperty(LocalizedString.create("sum"), 2, new SumFormulaImpl())));
    }

    protected LCP addMultProp() {
        return addProperty(null, new LCP<>(new FormulaImplProperty(LocalizedString.create("multiply"), 2, new MultiplyFormulaImpl())));
    }

    protected LCP addSubtractProp() {
        return addProperty(null, new LCP<>(new FormulaImplProperty(LocalizedString.create("subtract"), 2, new SubtractFormulaImpl())));
    }

    protected LCP addDivideProp() {
        return addProperty(null, new LCP<>(new FormulaImplProperty(LocalizedString.create("divide"), 2, new DivideFormulaImpl())));
    }

    // ------------------- cast ----------------- //

    protected <P extends PropertyInterface> LCP addCastProp(DataClass castClass) {
        return baseLM.addCastProp(castClass);
    }

    // ------------------- Операции со строками ----------------- //

    protected <P extends PropertyInterface> LCP addSProp(int intNum) {
        return addSProp(intNum, " ");
    }

    protected <P extends PropertyInterface> LCP addSProp(int intNum, String separator) {
        return addProperty(null, new LCP<>(new StringConcatenateProperty(LocalizedString.create("{logics.join}"), intNum, separator)));
    }

    protected <P extends PropertyInterface> LCP addInsensitiveSProp(int intNum) {
        return addInsensitiveSProp(intNum, " ");
    }

    protected <P extends PropertyInterface> LCP addInsensitiveSProp(int intNum, String separator) {
        return addProperty(null, new LCP<>(new StringConcatenateProperty(LocalizedString.create("{logics.join}"), intNum, separator, true)));
    }

    // ------------------- AND ----------------- //

    protected LCP addAFProp(boolean... nots) {
        return addAFProp(null, nots);
    }

    protected LCP addAFProp(AbstractGroup group, boolean... nots) {
        ImOrderSet<PropertyInterface> interfaces = genInterfaces(nots.length + 1);
        MList<Boolean> mList = ListFact.mList(nots.length);
        boolean wasNot = false;
        for(boolean not : nots) {
            mList.add(not);
            wasNot = wasNot || not;
        }
        if(wasNot)
            return mapLProp(group, false, DerivedProperty.createAnd(interfaces, mList.immutableList()), interfaces);
        else
            return addProperty(group, new LCP<>(new AndFormulaProperty(nots.length)));
    }

    // ------------------- concat ----------------- //

    protected LCP addCCProp(int paramCount) {
        return addProperty(null, new LCP<>(new ConcatenateProperty(paramCount)));
    }

    protected LCP addDCCProp(int paramIndex) {
        return addProperty(null, new LCP<>(new DeconcatenateProperty(paramIndex, baseLM.baseClass)));
    }

    // ------------------- JOIN (продолжение) ----------------- //

    public LCP addJProp(LCP mainProp, Object... params) {
        return addJProp((AbstractGroup) null, LocalizedString.create("sys"), mainProp, params);
    }

    protected LCP addJProp(boolean user, LocalizedString caption, LCP mainProp, Object... params) {
        return addJProp(false, user, caption, mainProp, params);
    }

    protected LCP addJProp(boolean persistent, boolean user, LocalizedString caption, LCP mainProp, Object... params) {
        return addJProp(null, persistent, user, caption, mainProp, params);
    }

    protected LCP addJProp(AbstractGroup group, LocalizedString caption, LCP mainProp, Object... params) {
        return addJProp(group, false, caption, mainProp, params);
    }

    protected LCP addJProp(AbstractGroup group, boolean persistent, LocalizedString caption, LCP mainProp, Object... params) {
        return addJProp(group, persistent, false, caption, mainProp, params);
    }

    protected LCP addJProp(AbstractGroup group, boolean persistent, boolean user, LocalizedString caption, LCP<?> mainProp, Object... params) {

        ImOrderSet<JoinProperty.Interface> listInterfaces = JoinProperty.getInterfaces(getIntNum(params));
        ImList<CalcPropertyInterfaceImplement<JoinProperty.Interface>> listImplements = readCalcImplements(listInterfaces, params);
        JoinProperty<?> property = new JoinProperty(caption, listInterfaces, user,
                mapCalcImplement(mainProp, listImplements));

        for(CalcProperty andProp : mainProp.property.getAndProperties())
            property.drawOptions.inheritDrawOptions(andProp.drawOptions);

        return addProperty(group, persistent, new LCP<>(property, listInterfaces));
    }

    // ------------------- mapLProp ----------------- //

    private <P extends PropertyInterface, L extends PropertyInterface> LCP mapLProp(AbstractGroup group, boolean persistent, CalcPropertyMapImplement<L, P> implement, ImOrderSet<P> listInterfaces) {
        return addProperty(group, persistent, new LCP<>(implement.property, listInterfaces.mapOrder(implement.mapping.reverse())));
    }

    protected <P extends PropertyInterface, L extends PropertyInterface> LCP mapLProp(AbstractGroup group, boolean persistent, CalcPropertyMapImplement<L, P> implement, LCP<P> property) {
        return mapLProp(group, persistent, implement, property.listInterfaces);
    }

    private <P extends PropertyInterface, L extends PropertyInterface> LCP mapLGProp(AbstractGroup group, CalcPropertyImplement<L, CalcPropertyInterfaceImplement<P>> implement, ImList<CalcPropertyInterfaceImplement<P>> listImplements) {
        return mapLGProp(group, false, implement, listImplements);
    }

    private <P extends PropertyInterface, L extends PropertyInterface> LCP mapLGProp(AbstractGroup group, boolean persistent, CalcPropertyImplement<L, CalcPropertyInterfaceImplement<P>> implement, ImList<CalcPropertyInterfaceImplement<P>> listImplements) {
        return addProperty(group, persistent, new LCP<>(implement.property, listImplements.toOrderExclSet().mapOrder(implement.mapping.toRevExclMap().reverse())));
    }

    private <P extends PropertyInterface> LCP mapLGProp(AbstractGroup group, boolean persistent, GroupProperty property, ImList<CalcPropertyInterfaceImplement<P>> listImplements) {
        return mapLGProp(group, persistent, new CalcPropertyImplement<GroupProperty.Interface<P>, CalcPropertyInterfaceImplement<P>>(property, property.getMapInterfaces()), listImplements);
    }

    // ------------------- Order property ----------------- //

    protected <P extends PropertyInterface> LCP addOProp(AbstractGroup group, boolean persistent, LocalizedString caption, PartitionType partitionType, boolean ascending, boolean ordersNotNull, boolean includeLast, int partNum, Object... params) {
        ImOrderSet<PropertyInterface> interfaces = genInterfaces(getIntNum(params));
        ImList<CalcPropertyInterfaceImplement<PropertyInterface>> listImplements = readCalcImplements(interfaces, params);

        ImList<CalcPropertyInterfaceImplement<PropertyInterface>> mainProp = listImplements.subList(0, 1);
        ImSet<CalcPropertyInterfaceImplement<PropertyInterface>> partitions = listImplements.subList(1, partNum + 1).toOrderSet().getSet();
        ImOrderMap<CalcPropertyInterfaceImplement<PropertyInterface>, Boolean> orders = listImplements.subList(partNum + 1, listImplements.size()).toOrderSet().toOrderMap(!ascending);

        return mapLProp(group, persistent, DerivedProperty.createOProp(caption, partitionType, interfaces.getSet(), mainProp, partitions, orders, ordersNotNull, includeLast), interfaces);
    }

    protected <P extends PropertyInterface> LCP addRProp(AbstractGroup group, boolean persistent, LocalizedString caption, Cycle cycle, ImList<Integer> resInterfaces, ImRevMap<Integer, Integer> mapPrev, Object... params) {
        int innerCount = getIntNum(params);
        final ImOrderSet<PropertyInterface> innerInterfaces = genInterfaces(innerCount);
        ImList<CalcPropertyInterfaceImplement<PropertyInterface>> listImplement = readCalcImplements(innerInterfaces, params);

        GetValue<PropertyInterface, Integer> getInnerInterface = new GetValue<PropertyInterface, Integer>() {
            public PropertyInterface getMapValue(Integer value) {
                return innerInterfaces.get(value);
            }
        };

        final ImOrderSet<RecursiveProperty.Interface> interfaces = RecursiveProperty.getInterfaces(resInterfaces.size());
        ImRevMap<RecursiveProperty.Interface, PropertyInterface> mapInterfaces = resInterfaces.mapListRevKeyValues(new GetIndex<RecursiveProperty.Interface>() {
            public RecursiveProperty.Interface getMapValue(int i) {
                return interfaces.get(i);
            }}, getInnerInterface);
        ImRevMap<PropertyInterface, PropertyInterface> mapIterate = mapPrev.mapRevKeyValues(getInnerInterface, getInnerInterface); // старые на новые

        CalcPropertyMapImplement<?, PropertyInterface> initial = (CalcPropertyMapImplement<?, PropertyInterface>) listImplement.get(0);
        CalcPropertyMapImplement<?, PropertyInterface> step = (CalcPropertyMapImplement<?, PropertyInterface>) listImplement.get(1);

        boolean convertToLogical = false;
        assert initial.property.getType() instanceof IntegralClass == (step.property.getType() instanceof IntegralClass);
        if(!(initial.property.getType() instanceof IntegralClass) && (cycle == Cycle.NO || (cycle==Cycle.IMPOSSIBLE && persistent))) {
            CalcPropertyMapImplement<?, PropertyInterface> one = createStatic(1, LongClass.instance);
            initial = createAnd(innerInterfaces.getSet(), one, initial);
            step = createAnd(innerInterfaces.getSet(), one, step);
            convertToLogical = true;
        }

        RecursiveProperty<PropertyInterface> property = new RecursiveProperty<>(caption, interfaces, cycle,
                mapInterfaces, mapIterate, initial, step);
        if(cycle==Cycle.NO)
            addConstraint(property.getConstrainedProperty(), false);

        LCP result = new LCP<>(property, interfaces);
//        if (convertToLogical)
//            return addJProp(group, name, false, caption, baseLM.notZero, directLI(addProperty(null, persistent, result)));
//        else
            return addProperty(group, persistent, result);
    }

    // ------------------- Ungroup property ----------------- //

    protected <L extends PropertyInterface> LCP addUGProp(AbstractGroup group, boolean persistent, boolean over, LocalizedString caption, int intCount, boolean ascending, boolean ordersNotNull, LCP<L> ungroup, Object... params) {
        int partNum = ungroup.listInterfaces.size();
        ImOrderSet<PropertyInterface> innerInterfaces = genInterfaces(intCount);
        final ImList<CalcPropertyInterfaceImplement<PropertyInterface>> listImplements = readCalcImplements(innerInterfaces, params);
        CalcPropertyInterfaceImplement<PropertyInterface> restriction = listImplements.get(0);
        ImMap<L, CalcPropertyInterfaceImplement<PropertyInterface>> groupImplement = ungroup.listInterfaces.mapOrderValues(new GetIndex<CalcPropertyInterfaceImplement<PropertyInterface>>() {
            public CalcPropertyInterfaceImplement<PropertyInterface> getMapValue(int i) {
                return listImplements.get(i+1);
            }});
        ImOrderMap<CalcPropertyInterfaceImplement<PropertyInterface>, Boolean> orders = listImplements.subList(partNum + 1, listImplements.size()).toOrderSet().toOrderMap(!ascending);

        return mapLProp(group, persistent, DerivedProperty.createUGProp(caption, innerInterfaces.getSet(),
                new CalcPropertyImplement<>(ungroup.property, groupImplement), orders, ordersNotNull, restriction, over), innerInterfaces);
    }

    protected <L extends PropertyInterface> LCP addPGProp(AbstractGroup group, boolean persistent, int roundlen, boolean roundfirst, LocalizedString caption, int intCount, List<ResolveClassSet> explicitInnerClasses, boolean ascending, boolean ordersNotNull, LCP<L> ungroup, Object... params) {
        int partNum = ungroup.listInterfaces.size();
        ImOrderSet<PropertyInterface> innerInterfaces = genInterfaces(intCount);
        final ImList<CalcPropertyInterfaceImplement<PropertyInterface>> listImplements = readCalcImplements(innerInterfaces, params);
        CalcPropertyInterfaceImplement<PropertyInterface> proportion = listImplements.get(0);
        ImMap<L, CalcPropertyInterfaceImplement<PropertyInterface>> groupImplement = ungroup.listInterfaces.mapOrderValues(new GetIndex<CalcPropertyInterfaceImplement<PropertyInterface>>() {
            public CalcPropertyInterfaceImplement<PropertyInterface> getMapValue(int i) {
                return listImplements.get(i+1);
            }});
        ImOrderMap<CalcPropertyInterfaceImplement<PropertyInterface>, Boolean> orders =
                listImplements.subList(partNum + 1, listImplements.size()).toOrderSet().toOrderMap(!ascending);

        return mapLProp(group, persistent, DerivedProperty.createPGProp(caption, roundlen, roundfirst, baseLM.baseClass, innerInterfaces, explicitInnerClasses,
                new CalcPropertyImplement<>(ungroup.property, groupImplement), proportion, orders, ordersNotNull), innerInterfaces);
    }

    /*
      // свойство обратное группируещему - для этого задается ограничивающее свойство, результирующее св-во с группировочными, порядковое св-во
      protected LF addUGProp(AbstractGroup group, String title, LF maxGroupProp, LF unGroupProp, Object... params) {
          List<LI> lParams = readLI(params);
          List<LI> lUnGroupParams = lParams.subList(0,unGroupProp.listInterfaces.size());
          List<LI> orderParams = lParams.subList(unGroupProp.listInterfaces.size(),lParams.size());

          int intNum = maxGroupProp.listInterfaces.size();

          // "двоим" интерфейсы, для результ. св-ва
          // ставим equals'ы на группировочные свойства (раздвоенные)
          List<Object[]> groupParams = new ArrayList<Object[]>();
          groupParams.add(directLI(maxGroupProp));
          for(LI li : lUnGroupParams)
              groupParams.add(li.compare(equals2, this, intNum));

          boolean[] andParams = new boolean[groupParams.size()-1];
          for(int i=0;i<andParams.length;i++)
              andParams[i] = false;
          LF groupPropSet = addJProp(addAFProp(andParams),BaseUtils.add(groupParams));

          for(int i=0;i<intNum;i++) { // докинем не достающие порядки
              boolean found = false;
              for(LI order : orderParams)
                  if(order instanceof LII && ((LII)order).intNum==i+1) {
                      found = true;
                      break;
                  }
              if(!found)
                  orderParams.add(new LII(i+1));
          }

          // ставим на предшествие сначала order'а, потом всех интерфейсов
          LF[] orderProps = new LF[orderParams.size()];
          for(int i=0;i<orderParams.size();i++) {
              orderProps[i] = (addJProp(and1, BaseUtils.add(directLI(groupPropSet),orderParams.get(i).compare(greater2, this, intNum))));
              groupPropSet = addJProp(and1, BaseUtils.add(directLI(groupPropSet),orderParams.get(i).compare(equals2, this, intNum)));
          }
          LF groupPropPrev = addSUProp(Union.OVERRIDE, orderProps);

          // группируем суммируя по "задвоенным" св-вам maxGroup
          Object[] remainParams = new Object[intNum];
          for(int i=1;i<=intNum;i++)
              remainParams[i-1] = i+intNum;
          LF remainPrev = addSGProp(groupPropPrev, remainParams);

          // создадим группировочное св-во с маппом на общий интерфейс, нужно поубирать "дырки"


          // возвращаем MIN2(unGroup-MU(prevGroup,0(maxGroup)),maxGroup) и не unGroup<=prevGroup
          LF zeroQuantity = addJProp(and1, BaseUtils.add(new Object[]{vzero},directLI(maxGroupProp)));
          LF zeroRemainPrev = addSUProp(Union.OVERRIDE , zeroQuantity, remainPrev);
          LF calc = addSFProp("prm3+prm1-prm2-GREATEST(prm3,prm1-prm2)",DoubleClass.instance,3);
          LF maxRestRemain = addJProp(calc, BaseUtils.add(BaseUtils.add(unGroupProp.write(),directLI(zeroRemainPrev)),directLI(maxGroupProp)));
          LF exceed = addJProp(groeq2, BaseUtils.add(directLI(remainPrev),unGroupProp.write()));
          return addJProp(group, title, andNot1, BaseUtils.add(directLI(maxRestRemain),directLI(exceed)));
      }
    */

    protected ImOrderSet<PropertyInterface> genInterfaces(int interfaces) {
        return SetFact.toOrderExclSet(interfaces, Property.genInterface);
    }

    // ------------------- GROUP SUM ----------------- //

    protected LCP addSGProp(AbstractGroup group, boolean persistent, boolean notZero, LocalizedString caption, int interfaces, List<ResolveClassSet> explicitInnerClasses, Object... params) {
        ImOrderSet<PropertyInterface> innerInterfaces = genInterfaces(interfaces);
        return addSGProp(group, persistent, notZero, caption, innerInterfaces, explicitInnerClasses, readCalcImplements(innerInterfaces, params));
    }

    protected <T extends PropertyInterface> LCP addSGProp(AbstractGroup group, boolean persistent, boolean notZero, LocalizedString caption, ImOrderSet<T> innerInterfaces, List<ResolveClassSet> explicitInnerClasses, ImList<CalcPropertyInterfaceImplement<T>> implement) {
        ImList<CalcPropertyInterfaceImplement<T>> listImplements = implement.subList(1, implement.size());
        SumGroupProperty<T> property = new SumGroupProperty<>(caption, innerInterfaces.getSet(), listImplements, implement.get(0));
        property.setExplicitInnerClasses(innerInterfaces, explicitInnerClasses);

        return mapLGProp(group, persistent, property, listImplements);
    }

    // ------------------- Override property ----------------- //

    public <T extends PropertyInterface> LCP addOGProp(AbstractGroup group, boolean persist, LocalizedString caption, GroupType type, int numOrders, boolean ordersNotNull, boolean descending, int interfaces, List<ResolveClassSet> explicitInnerClasses, Object... params) {
        ImOrderSet<PropertyInterface> innerInterfaces = genInterfaces(interfaces);
        return addOGProp(group, persist, caption, type, numOrders, ordersNotNull, descending, innerInterfaces, explicitInnerClasses, readCalcImplements(innerInterfaces, params));
    }
    public <T extends PropertyInterface> LCP addOGProp(AbstractGroup group, boolean persist, LocalizedString caption, GroupType type, int numOrders, boolean ordersNotNull, boolean descending, ImOrderSet<T> innerInterfaces, List<ResolveClassSet> explicitInnerClasses, ImList<CalcPropertyInterfaceImplement<T>> listImplements) {
        int numExprs = type.numExprs();
        ImList<CalcPropertyInterfaceImplement<T>> props = listImplements.subList(0, numExprs);
        ImOrderMap<CalcPropertyInterfaceImplement<T>, Boolean> orders = listImplements.subList(numExprs, numExprs + numOrders).toOrderSet().toOrderMap(descending);
        ImList<CalcPropertyInterfaceImplement<T>> groups = listImplements.subList(numExprs + numOrders, listImplements.size());
        OrderGroupProperty<T> property = new OrderGroupProperty<>(caption, innerInterfaces.getSet(), groups.getCol(), props, type, orders, ordersNotNull);
        property.setExplicitInnerClasses(innerInterfaces, explicitInnerClasses);

        return mapLGProp(group, persist, property, groups);
    }

    // ------------------- GROUP MAX ----------------- //

    protected LCP addMGProp(AbstractGroup group, boolean persist, LocalizedString caption, boolean min, int interfaces, List<ResolveClassSet> explicitInnerClasses, Object... params) {
        return addMGProp(group, persist, new LocalizedString[]{caption}, 1, min, interfaces, explicitInnerClasses, params)[0];
    }

    protected LCP[] addMGProp(AbstractGroup group, boolean persist, LocalizedString[] captions, int exprs, boolean min, int interfaces, List<ResolveClassSet> explicitInnerClasses, Object... params) {
        ImOrderSet<PropertyInterface> innerInterfaces = genInterfaces(interfaces);
        return addMGProp(group, persist, captions, exprs, min, innerInterfaces, explicitInnerClasses, readCalcImplements(innerInterfaces, params));
    }

    protected <T extends PropertyInterface> LCP[] addMGProp(AbstractGroup group, boolean persist, LocalizedString[] captions, int exprs, boolean min, ImOrderSet<T> listInterfaces, List<ResolveClassSet> explicitInnerClasses, ImList<CalcPropertyInterfaceImplement<T>> listImplements) {
        LCP[] result = new LCP[exprs];

        MSet<CalcProperty> mOverridePersist = SetFact.mSet();

        ImList<CalcPropertyInterfaceImplement<T>> groupImplements = listImplements.subList(exprs, listImplements.size());
        ImList<CalcPropertyImplement<?, CalcPropertyInterfaceImplement<T>>> mgProps = DerivedProperty.createMGProp(captions, listInterfaces, explicitInnerClasses, baseLM.baseClass,
                listImplements.subList(0, exprs), groupImplements.getCol(), mOverridePersist, min);

        ImSet<CalcProperty> overridePersist = mOverridePersist.immutable();

        for (int i = 0; i < mgProps.size(); i++)
            result[i] = mapLGProp(group, mgProps.get(i), groupImplements);

        if (persist) {
            if (overridePersist.size() > 0) {
                for (CalcProperty property : overridePersist)
                    addProperty(null, true, new LCP(property));
            } else
                for (LCP lcp : result) addPersistent(lcp);
        }

        return result;
    }

    // ------------------- CGProperty ----------------- //

    protected <T extends PropertyInterface, P extends PropertyInterface> LCP addCGProp(AbstractGroup group, boolean checkChange, boolean persistent, LocalizedString caption, LCP<PropertyInterface> dataProp, int interfaces, List<ResolveClassSet> explicitInnerClasses, Object... params) {
        ImOrderSet<PropertyInterface> innerInterfaces = genInterfaces(interfaces);
        return addCGProp(group, checkChange, persistent, caption, dataProp, innerInterfaces, explicitInnerClasses, readCalcImplements(innerInterfaces, params));
    }

    protected <T extends PropertyInterface, P extends PropertyInterface> LCP addCGProp(AbstractGroup group, boolean checkChange, boolean persistent, LocalizedString caption, LCP<P> dataProp, ImOrderSet<T> innerInterfaces, List<ResolveClassSet> explicitInnerClasses, ImList<CalcPropertyInterfaceImplement<T>> listImplements) {
        CycleGroupProperty<T, P> property = new CycleGroupProperty<>(caption, innerInterfaces.getSet(), listImplements.subList(1, listImplements.size()).getCol(), listImplements.get(0), dataProp == null ? null : dataProp.property);
        property.setExplicitInnerClasses(innerInterfaces, explicitInnerClasses);

        // нужно добавить ограничение на уникальность
        addConstraint(property.getConstrainedProperty(), checkChange);

        return mapLGProp(group, persistent, property, listImplements.subList(1, listImplements.size()));
    }

//    protected static <T extends PropertyInterface<T>> AggregateGroupProperty create(String sID, LocalizedString caption, CalcProperty<T> property, T aggrInterface, Collection<CalcPropertyMapImplement<?, T>> groupProps) {

    // ------------------- GROUP AGGR ----------------- //

    protected LCP addAGProp(AbstractGroup group, boolean checkChange, boolean persistent, LocalizedString caption, boolean noConstraint, int interfaces, List<ResolveClassSet> explicitInnerClasses, Object... props) {
        ImOrderSet<PropertyInterface> innerInterfaces = genInterfaces(interfaces);
        return addAGProp(group, checkChange, persistent, caption, noConstraint, innerInterfaces, explicitInnerClasses, readCalcImplements(innerInterfaces, props));
    }

    protected <T extends PropertyInterface<T>, I extends PropertyInterface> LCP addAGProp(AbstractGroup group, boolean checkChange, boolean persistent, LocalizedString caption, boolean noConstraint, ImOrderSet<T> innerInterfaces, List<ResolveClassSet> explicitInnerClasses, ImList<CalcPropertyInterfaceImplement<T>> listImplements) {
        T aggrInterface = (T) listImplements.get(0);
        CalcPropertyInterfaceImplement<T> whereProp = listImplements.get(1);
        ImList<CalcPropertyInterfaceImplement<T>> groupImplements = listImplements.subList(2, listImplements.size());

        AggregateGroupProperty<T> aggProp = AggregateGroupProperty.create(caption, innerInterfaces.getSet(), whereProp, aggrInterface, groupImplements.toOrderExclSet().getSet());
        aggProp.setExplicitInnerClasses(innerInterfaces, explicitInnerClasses);
        return addAGProp(group, checkChange, persistent, noConstraint, aggProp, groupImplements);
    }

    // чисто для generics
    private <T extends PropertyInterface<T>> LCP addAGProp(AbstractGroup group, boolean checkChange, boolean persistent, boolean noConstraint, AggregateGroupProperty<T> property, ImList<CalcPropertyInterfaceImplement<T>> listImplements) {
        // нужно добавить ограничение на уникальность
        if(!noConstraint)
            addConstraint(property.getConstrainedProperty(), checkChange);

        return mapLGProp(group, persistent, property, listImplements);
    }

    // ------------------- UNION ----------------- //

    protected LCP addUProp(AbstractGroup group, LocalizedString caption, Union unionType, String separator, int[] coeffs, Object... params) {
        return addUProp(group, false, caption, unionType, null, coeffs, params);
    }

    protected LCP addUProp(AbstractGroup group, boolean persistent, LocalizedString caption, Union unionType, String separator, int[] coeffs, Object... params) {

        assert (unionType==Union.SUM)==(coeffs!=null);
        assert (unionType==Union.STRING_AGG)==(separator !=null);

        int intNum = getIntNum(params);
        ImOrderSet<UnionProperty.Interface> listInterfaces = UnionProperty.getInterfaces(intNum);
        ImList<CalcPropertyInterfaceImplement<UnionProperty.Interface>> listOperands = readCalcImplements(listInterfaces, params);

        UnionProperty property = null;
        switch (unionType) {
            case MAX:
            case MIN:
                property = new MaxUnionProperty(unionType == Union.MIN, caption, listInterfaces, listOperands.getCol());
                break;
            case SUM:
                MMap<CalcPropertyInterfaceImplement<UnionProperty.Interface>, Integer> mMapOperands = MapFact.mMap(MapFact.<CalcPropertyInterfaceImplement<UnionProperty.Interface>>addLinear());
                for(int i=0;i<listOperands.size();i++)
                    mMapOperands.add(listOperands.get(i), coeffs[i]);
                property = new SumUnionProperty(caption, listInterfaces, mMapOperands.immutable());
                break;
            case OVERRIDE:
                property = new CaseUnionProperty(caption, listInterfaces, listOperands, false, false, false);
                break;
            case XOR:
                property = new XorUnionProperty(caption, listInterfaces, listOperands);
                break;
            case EXCLUSIVE:
                property = new CaseUnionProperty(caption, listInterfaces, listOperands.getCol(), false);
                break;
            case CLASS:
                property = new CaseUnionProperty(caption, listInterfaces, listOperands.getCol(), true);
                break;
            case CLASSOVERRIDE:
                property = new CaseUnionProperty(caption, listInterfaces, listOperands, true, false, false);
                break;
            case STRING_AGG:
                property = new StringAggUnionProperty(caption, listInterfaces, listOperands, separator);
                break;
        }

        return addProperty(group, persistent, new LCP<>(property, listInterfaces));
    }

    protected LCP addAUProp(AbstractGroup group, boolean persistent, boolean isExclusive, boolean isChecked, boolean isLast, CaseUnionProperty.Type type, LocalizedString caption, ValueClass valueClass, ValueClass... interfaces) {
        ImOrderSet<UnionProperty.Interface> listInterfaces = UnionProperty.getInterfaces(interfaces.length);
        return addProperty(group, persistent, new LCP<>(
                new CaseUnionProperty(isExclusive, isChecked, isLast, type, caption, listInterfaces, valueClass, listInterfaces.mapList(ListFact.toList(interfaces))), listInterfaces));
    }

    protected LCP addCaseUProp(AbstractGroup group, boolean persistent, LocalizedString caption, Object... params) {
        return addCaseUProp(group, persistent, caption, false, params);
    }

    protected LCP addCaseUProp(AbstractGroup group, boolean persistent, LocalizedString caption, boolean isExclusive, Object... params) {
        ImOrderSet<UnionProperty.Interface> listInterfaces = UnionProperty.getInterfaces(getIntNum(params));
        MList<CalcCase<UnionProperty.Interface>> mListCases = ListFact.mList();
        ImList<CalcPropertyMapImplement<?,UnionProperty.Interface>> mapImplements = (ImList<CalcPropertyMapImplement<?, UnionProperty.Interface>>) (ImList<?>) readCalcImplements(listInterfaces, params);
        for (int i = 0; i < mapImplements.size() / 2; i++)
            mListCases.add(new CalcCase<>(mapImplements.get(2 * i), mapImplements.get(2 * i + 1)));
        if (mapImplements.size() % 2 != 0)
            mListCases.add(new CalcCase<>(new CalcPropertyMapImplement<PropertyInterface, UnionProperty.Interface>((CalcProperty<PropertyInterface>) baseLM.vtrue.property), mapImplements.get(mapImplements.size() - 1)));

        return addProperty(group, persistent, new LCP<>(new CaseUnionProperty(caption, listInterfaces, isExclusive, mListCases.immutableList()), listInterfaces));
    }

    public static List<ResolveClassSet> getSignatureForLogProperty(List<ResolveClassSet> basePropSignature, SystemEventsLogicsModule systemEventsLM) {
        List<ResolveClassSet> signature = new ArrayList<>(basePropSignature);
        signature.add(systemEventsLM.currentSession.property.getValueClass(ClassType.aroundPolicy).getResolveSet());
        return signature;
    }
    
    public static List<ResolveClassSet> getSignatureForLogProperty(LCP lp, SystemEventsLogicsModule systemEventsLM) {
        List<ResolveClassSet> signature = new ArrayList<>();
        for (ValueClass cls : lp.getInterfaceClasses(ClassType.logPolicy)) {
            signature.add(cls.getResolveSet());
        }
        return getSignatureForLogProperty(signature, systemEventsLM);    
    } 

    public static String getLogPropertyCN(LCP lp, String logNamespace, SystemEventsLogicsModule systemEventsLM) {
        String name = "";
        try {
            String namespace = PropertyCanonicalNameParser.getNamespace(lp.property.getCanonicalName());
            name = getLogPropertyName(namespace, lp.property.getName());
        } catch (PropertyCanonicalNameParser.ParseException e) {
            Throwables.propagate(e);
        }

        List<ResolveClassSet> signature = getSignatureForLogProperty(lp, systemEventsLM);
        return PropertyCanonicalNameUtils.createName(logNamespace, name, signature);
    }

    private static String getLogPropertyName(LCP lp, boolean drop) {
        String name = "";
        try {
            String namespace = PropertyCanonicalNameParser.getNamespace(lp.property.getCanonicalName());
            name = getLogPropertyName(namespace, lp.property.getName(), drop);
        } catch (PropertyCanonicalNameParser.ParseException e) {
            Throwables.propagate(e);
        }
        return name;
    }

    private static String getLogPropertyName(String namespace, String name) {
        return getLogPropertyName(namespace, name, false);
    }


    private static String getLogPropertyName(String namespace, String name, boolean drop) {
        return (drop ? PropertyCanonicalNameUtils.logDropPropPrefix : PropertyCanonicalNameUtils.logPropPrefix) + namespace + "_" + name;
    }
    
    public static String getLogPropertyCN(String logNamespace, String namespace, String name, List<ResolveClassSet> signature) {
        return PropertyCanonicalNameUtils.createName(logNamespace, getLogPropertyName(namespace, name), signature);            
    } 
    
    // ------------------- Loggable ----------------- //
    // todo [dale]: тут конечно страх, во-первых, сигнатура берется из интерфейсов свойства (issue #1725), 
    // во-вторых руками markStored вызывается, чтобы обойти проблему с созданием propertyField из addDProp 
    public LCP addLProp(SystemEventsLogicsModule systemEventsLM, LCP lp) {
        assert lp.property.isNamed();
        String name = getLogPropertyName(lp, false);
        
        List<ResolveClassSet> signature = getSignatureForLogProperty(lp, systemEventsLM);
        
        LCP result = addDCProp(baseLM.privateGroup, LocalizedString.create("{logics.log}" + " " + lp.property), 1, lp, add(new Object[]{addJProp(baseLM.equals2, 1, systemEventsLM.currentSession), lp.listInterfaces.size() + 1}, directLI(lp)));
        makePropertyPublic(result, name, signature);
        ((StoredDataProperty)result.property).markStored(baseLM.tableFactory);
        return result;
    }

    public LCP addLDropProp(SystemEventsLogicsModule systemEventsLM, LCP lp) {
        String name = getLogPropertyName(lp, true);

        List<ResolveClassSet> signature = getSignatureForLogProperty(lp, systemEventsLM);

        LCP equalsProperty = addJProp(baseLM.equals2, 1, systemEventsLM.currentSession);
        LCP logDropProperty = addLogProp(baseLM.privateGroup, LocalizedString.create("{logics.log}" + " " + lp.property + " {drop}"), 1, lp, add(new Object[]{equalsProperty, lp.listInterfaces.size() + 1}, directLI(lp)));

        LCP changedProperty = addCHProp(lp, IncrementType.DROP, PrevScope.EVENT);
        LCP whereProperty = addJProp(false, LocalizedString.create(""), baseLM.and1, add(directLI(changedProperty), new Object[] {equalsProperty, changedProperty.listInterfaces.size() + 1}));

        Object[] params = directLI(baseLM.vtrue);
        if (whereProperty != null) {
            params = BaseUtils.add(params, directLI(whereProperty));
        }
        logDropProperty.setEventChange(systemEventsLM, true, params);

        makePropertyPublic(logDropProperty, name, signature);
        ((StoredDataProperty)logDropProperty.property).markStored(baseLM.tableFactory);

        return logDropProperty;
    }

    // ------------------- UNION SUM ----------------- //

    protected LCP addSUProp(boolean persistent, LocalizedString caption, Union unionType, LCP... props) {
        return addSUProp(null, persistent, caption, unionType, props);
    }

    protected LCP addSUProp(AbstractGroup group, boolean persistent, LocalizedString caption, Union unionType, LCP... props) {
        return addUProp(group, persistent, caption, unionType, null, (unionType == Union.SUM ? BaseUtils.genArray(1, props.length) : null), getUParams(props));
    }

    // ------------------- CONCAT ----------------- //

    protected LCP addSFUProp(int intNum, String separator) {
        return addSFUProp(separator, intNum);
    }

    protected LCP addSFUProp(String separator, int intNum) {
        return addUProp(null, false, LocalizedString.create("{logics.join}"), Union.STRING_AGG, separator, null, getUParams(intNum));
    }

    // ------------------- ACTION ----------------- //

    public LAP addAProp(ActionProperty property) {
        return addAProp(null, property);
    }

    public LAP addAProp(AbstractGroup group, ActionProperty property) {
        return addProperty(group, new LAP(property));
    }

    // ------------------- MESSAGE ----------------- //

    protected LAP addMAProp(String title, boolean noWait, Object... params) {
        return addMAProp(null, LocalizedString.create(""), title, noWait, params);
    }

    protected LAP addMAProp(AbstractGroup group, LocalizedString caption, String title, boolean noWait, Object... params) {
        return addJoinAProp(group, caption, addMAProp(title, noWait), params);
    }

    @IdentityStrongLazy
    protected LAP addMAProp(String title, boolean noWait) {
        return addProperty(null, new LAP(new MessageActionProperty(LocalizedString.create("Message"), title, noWait)));
    }

    public LAP addFocusActionProp(int propertyId) {
        return addProperty(null, new LAP(new FocusActionProperty(propertyId)));
    }

    // ------------------- CONFIRM ----------------- //


    protected LAP addConfirmAProp(String title, boolean yesNo, LCP targetProp, Object... params) {
        return addConfirmAProp(null, LocalizedString.create(""), title, yesNo, targetProp, params);
    }

    protected LAP addConfirmAProp(AbstractGroup group, LocalizedString caption, String title, boolean yesNo, LCP<?> targetProp, Object... params) {
        return addJoinAProp(group, caption, addConfirmAProp(title, yesNo, targetProp != null ? targetProp.property : null), params);
    }

    @IdentityStrongLazy
    protected LAP addConfirmAProp(String title, boolean yesNo, CalcProperty property) {
        return addProperty(null, new LAP(new ConfirmActionProperty(LocalizedString.create("Confirm"), title, getConfirmedProperty(), yesNo, property != null ? new LCP(property) : null)));
    }

    // ------------------- Async Update Action ----------------- //

    protected LAP addAsyncUpdateAProp(Object... params) {
        return addAsyncUpdateAProp(LocalizedString.create(""), params);
    }

    protected LAP addAsyncUpdateAProp(LocalizedString caption, Object... params) {
        return addAsyncUpdateAProp(null, caption, params);
    }

    protected LAP addAsyncUpdateAProp(AbstractGroup group, LocalizedString caption, Object... params) {
        return addJoinAProp(group, caption, addAsyncUpdateAProp(), params);
    }

    @IdentityStrongLazy
    protected LAP addAsyncUpdateAProp() {
        return addProperty(null, new LAP(new AsyncUpdateEditValueActionProperty(LocalizedString.create("Async Update"))));
    }

    // ------------------- OPEN FILE ----------------- //

    protected LAP addOFAProp(ValueClass prop, ValueClass nameProp) {
        List<ValueClass> valueClasses = new ArrayList<>();
        valueClasses.add(prop);
        if(nameProp != null)
            valueClasses.add(nameProp);
        return addProperty(null, new LAP(new OpenActionProperty(LocalizedString.create("ofa"), valueClasses.toArray(new ValueClass[valueClasses.size()]))));
    }

    // ------------------- SAVE FILE ----------------- //

    protected LAP addSFAProp(ValueClass prop, ValueClass nameProp) {
        List<ValueClass> valueClasses = new ArrayList<>();
        valueClasses.add(prop);
        if(nameProp != null)
            valueClasses.add(nameProp);
        return addProperty(null, new LAP(new SaveActionProperty(LocalizedString.create("sfa"), valueClasses.toArray(new ValueClass[valueClasses.size()]))));
    }

    // ------------------- EVAL ----------------- //

    public LAP addEvalAProp(LCP<?> scriptSource) {
        return addAProp(null, new EvalActionProperty(LocalizedString.create(""), scriptSource));
    }

    // ------------------- DRILLDOWN ----------------- //

    public void setupDrillDownProperty(Property property, boolean isDebug) {
        if (property instanceof CalcProperty && ((CalcProperty) property).supportsDrillDown()) {
            LAP<?> drillDownFormProperty = isDebug ? addLazyAProp((CalcProperty) property) : addDDAProp((CalcProperty) property);
            ActionProperty formProperty = drillDownFormProperty.property;
            formProperty.checkReadOnly = false;
            property.setContextMenuAction(formProperty.getSID(), formProperty.caption);
            property.setEditAction(formProperty.getSID(), formProperty.getImplement(property.getReflectionOrderInterfaces()));
        }
    }
    
    public LAP addDrillDownAProp(LCP<?> property) {
        return addDDAProp(property);
    }

    public LAP<?> addDDAProp(LCP property) {
        assert property.property.getReflectionOrderInterfaces().equals(property.listInterfaces);
        if (property.property instanceof CalcProperty && ((CalcProperty) property.property).supportsDrillDown())
            return addDDAProp((CalcProperty) property.property);
        else 
            throw new UnsupportedOperationException();
    }

    private String nameForDrillDownAction(CalcProperty property, List<ResolveClassSet> signature) {
        assert property.isNamed();
        String name = null;
        try {
            PropertyCanonicalNameParser parser = new PropertyCanonicalNameParser(property.getCanonicalName(), baseLM.getClassFinder());
            name = PropertyCanonicalNameUtils.drillDownPrefix + parser.getNamespace() + "_" + property.getName();
            signature.addAll(parser.getSignature());
        } catch (PropertyCanonicalNameParser.ParseException e) {
            Throwables.propagate(e);
        }
        return name;
    }

    public LAP<?> addDDAProp(CalcProperty property) {
        List<ResolveClassSet> signature = new ArrayList<>();
        DrillDownFormEntity drillDownFormEntity = property.getDrillDownForm(this, null);
        LAP result = addMFAProp(LocalizedString.create("{logics.property.drilldown.action}"), drillDownFormEntity, drillDownFormEntity.paramObjects, property.drillDownInNewSession());
        if (property.isNamed()) {
            String name = nameForDrillDownAction(property, signature);
            makePropertyPublic(result, name, signature);
        }
        return result;
    }

    public LAP<?> addLazyAProp(CalcProperty property) {
        LAP result = addAProp(null, new LazyActionProperty(LocalizedString.create("{logics.property.drilldown.action}"), property));
        if (property.isNamed()) {
            List<ResolveClassSet> signature = new ArrayList<>();
            String name = nameForDrillDownAction(property, signature);
            makePropertyPublic(result, name, signature);
        }
        return result;
    }

    public SessionDataProperty getAddedObjectProperty() {
        return baseLM.getAddedObjectProperty();
    }

    public LCP getConfirmedProperty() {
        return baseLM.getConfirmedProperty();
    }

    public LCP getIsActiveFormProperty() {
        return baseLM.getIsActiveFormProperty();
    }

    public AnyValuePropertyHolder addAnyValuePropertyHolder(String sidPrefix, String captionPrefix, ValueClass... classes) {
        return new AnyValuePropertyHolder(
                getLCPByName(sidPrefix + "Object"),
                getLCPByName(sidPrefix + "String"),
                getLCPByName(sidPrefix + "Text"),
                getLCPByName(sidPrefix + "Integer"),
                getLCPByName(sidPrefix + "Long"),
                getLCPByName(sidPrefix + "Double"),
                getLCPByName(sidPrefix + "Numeric"),
                getLCPByName(sidPrefix + "Year"),
                getLCPByName(sidPrefix + "DateTime"),
                getLCPByName(sidPrefix + "Logical"),
                getLCPByName(sidPrefix + "Date"),           
                getLCPByName(sidPrefix + "Time"),
                getLCPByName(sidPrefix + "Color"),
                getLCPByName(sidPrefix + "WordFile"),
                getLCPByName(sidPrefix + "ImageFile"),
                getLCPByName(sidPrefix + "PdfFile"),
                getLCPByName(sidPrefix + "CustomFile"),
                getLCPByName(sidPrefix + "ExcelFile"),
                getLCPByName(sidPrefix + "WordLink"),
                getLCPByName(sidPrefix + "ImageLink"),
                getLCPByName(sidPrefix + "PdfLink"),
                getLCPByName(sidPrefix + "CustomLink"),
                getLCPByName(sidPrefix + "ExcelLink")
        );
    }

    // ---------------------- VALUE ---------------------- //

    public LCP getObjValueProp(FormEntity formEntity, ObjectEntity obj) {
        return baseLM.getObjValueProp(formEntity, obj);
    }

    // ---------------------- Add Object ---------------------- //

    public <T extends PropertyInterface, I extends PropertyInterface> LAP addAddObjAProp(CustomClass cls, boolean autoSet, int resInterfaces, boolean conditional, boolean resultExists, Object... params) {
        ImOrderSet<PropertyInterface> innerInterfaces = genInterfaces(getIntNum(params));
        ImList<CalcPropertyInterfaceImplement<PropertyInterface>> readImplements = readCalcImplements(innerInterfaces, params);
        CalcPropertyMapImplement<T, PropertyInterface> resultPart = (CalcPropertyMapImplement<T, PropertyInterface>)
                (resultExists ? readImplements.get(resInterfaces) : null);
        CalcPropertyMapImplement<T, PropertyInterface> conditionalPart = (CalcPropertyMapImplement<T, PropertyInterface>)
                (conditional ? readImplements.get(resInterfaces + (resultExists ? 1 : 0)) : null);

        return addAProp(null, new AddObjectActionProperty(cls, innerInterfaces.getSet(), (ImOrderSet) readImplements.subList(0, resInterfaces).toOrderExclSet(), conditionalPart, resultPart, MapFact.<CalcPropertyInterfaceImplement<I>, Boolean>EMPTYORDER(), false, autoSet));
    }

    public LAP getAddObjectAction(FormEntity formEntity, ObjectEntity obj, CustomClass explicitClass) {
        return baseLM.getAddObjectAction(formEntity, obj, explicitClass);
    }

    // ---------------------- Delete Object ---------------------- //

    public LAP addDeleteAction(CustomClass cls, FormSessionScope scope) {
//        LAP delete = addChangeClassAProp(baseClass.unknown, 1, 0, false, true, 1, is(cls), 1);
//
//        LAP<?> result = addIfAProp(LocalizedString.create("{logics.delete}"), baseLM.sessionOwners, // IF sessionOwners() THEN 
//                delete, 1, // DELETE
//                addListAProp( // ELSE
//                        addConfirmAProp("lsFusion", addCProp(StringClass.text, LocalizedString.create("{form.instance.do.you.really.want.to.take.action} '{logics.delete}'"))), // CONFIRM
//                        addIfAProp(baseLM.confirmed, // IF confirmed() THEN
//                                addListAProp(
//                                        delete, 1, // DELETE
//                                        baseLM.apply), 1), 1 // apply()
//                ), 1);
        
        LAP<?> result = addDeleteAProp(LocalizedString.create("{logics.delete}"), cls);

        result.property.setSimpleDelete(true);
        setDeleteActionOptions(result);

        return addSessionScopeAProp(scope, result);
    }

    protected void setDeleteActionOptions(LAP property) {
        setFormActions(property);

        property.setImage("delete.png");
        property.setChangeKey(KeyStrokes.getDeleteActionPropertyKeyStroke());
        property.setShowChangeKey(false);
    }

    // ---------------------- Add Form ---------------------- //

    protected LAP addAddFormAction(CustomClass cls, ObjectEntity contextObject, FormSessionScope scope) {
        LCP<ClassPropertyInterface> addedProperty = new LCP<ClassPropertyInterface>(baseLM.getAddedObjectProperty());

        LocalizedString caption = LocalizedString.create("sys");

        // NEW AUTOSET x=X DO {
        //      REQUEST
        //          edit(x);
        //      DO
        //          SEEK co=x;
        //      ELSE
        //          IF sessionOwners THEN
        //              DELETE x;
        // }

        LAP result = addForAProp(null, LocalizedString.create("{logics.add}"), false, false, false, false, 0, cls, true, false, 0, false,
                1, //NEW x=X
                addRequestAProp(null, caption, // REQUEST
                        baseLM.getPolyEdit(), 1, // edit(x);
                        (contextObject != null ? addOSAProp(contextObject, true, 1) : baseLM.getEmptyObject()), 1, // DO SEEK co = x
                        addIfAProp(baseLM.sessionOwners, baseLM.getPolyDelete(), 1), 1 // ELSE IF seekOwners THEN delete(x)
                ), 1
        );
//        LAP result = addListAProp(
//                            addAddObjAProp(cls, true, 0, false, true, addedProperty), // NEW (FORM with AUTOSET), addAddObjAProp(cls, false, true, 0, false, true, addedProperty),
//                            addJoinAProp(addListAProp( // так хитро делается чтобы заnest'ить addedProperty (иначе apply его сбрасывает)
//                                    addDMFAProp(caption, cls, ManageSessionType.AUTO, true), 1, // FORM EDIT class OBJECT prm
//                                    addSetPropertyAProp(1, false, 1, addedProperty, 1), 1), // addedProperty <- prm
//                            addedProperty)); // FORM EDIT class OBJECT prm
//
//        LCP formResultProperty = baseLM.getFormResultProperty();
//        result = addListAProp(LocalizedString.create("{logics.add}"), result,
//                addIfAProp(addJProp(baseLM.equals2, formResultProperty, addCProp(baseLM.formResult, "ok")), // IF formResult == ok
//                        (contextObject != null ? addJoinAProp(addOSAProp(contextObject, true, 1), addedProperty) : baseLM.getEmpty()), // THEN (contextObject != null) SEEK exf.o prm
//                        (addIfAProp(baseLM.sessionOwners, addJoinAProp(getDeleteAction(cls, contextObject, FormSessionScope.OLDSESSION), addedProperty)))) // ELSE IF sessionOwners DELETE prm, // предполагается что если нет
//                         );

        setAddActionOptions(result, contextObject);
        
        return addSessionScopeAProp(scope, result);
    }

    protected void setAddActionOptions(LAP property, final ObjectEntity objectEntity) {

        setFormActions(property);

        property.setImage("add.png");
        property.setChangeKey(KeyStrokes.getAddActionPropertyKeyStroke());
        property.setShowChangeKey(false);

        if(objectEntity != null) { // ADDFORM как оператор
            property.addProcessor(new Property.DefaultProcessor() {
                public void proceedDefaultDraw(PropertyDrawEntity entity, FormEntity<?> form) {
                    entity.toDraw = objectEntity.groupTo;
                }
                public void proceedDefaultDesign(PropertyDrawView propertyView) {
                }
            });
        }
    }

    // ---------------------- Edit Form ---------------------- //

    protected LAP addEditFormAction(FormSessionScope scope, CustomClass customClass) {
        LAP result = addEditAProp(LocalizedString.create("{logics.edit}"), customClass);

        setEditActionOptions(result);

        return addSessionScopeAProp(scope, result);
    }
    
    private void setFormActions(LAP result) {
        result.setShouldBeLast(true);
        result.setForceViewType(ClassViewType.TOOLBAR);
    }

    private void setEditActionOptions(LAP result) {
        setFormActions(result);
        
        result.setImage("edit.png");
        result.setChangeKey(KeyStrokes.getEditActionPropertyKeyStroke());
        result.setShowChangeKey(false);
    }

    public LAP addProp(ActionProperty prop) {
        return addProp(null, prop);
    }

    public LAP addProp(AbstractGroup group, ActionProperty prop) {
        return addProperty(group, new LAP(prop));
    }

    public LCP addProp(CalcProperty<? extends PropertyInterface> prop) {
        return addProp(null, prop);
    }

    public LCP addProp(AbstractGroup group, CalcProperty<? extends PropertyInterface> prop) {
        return addProperty(group, new LCP(prop));
    }

    protected <T extends LP<?, ?>> T addProperty(AbstractGroup group, T lp) {
        return addProperty(group, false, lp);
    }

    protected void addPropertyToGroup(Property<?> property, AbstractGroup group) {
        Version version = getVersion();
        if (group != null) {
            group.add(property, version);
        } else if (!property.isLocal()) {
            baseLM.privateGroup.add(property, version);
        }
    }

    protected <T extends LP<?, ?>> T addProperty(AbstractGroup group, boolean persistent, T lp) {
        addPropertyToGroup(lp.property, group);
        return lp;
    }

    public void addIndex(LCP lp) {
        ImOrderSet<String> keyNames = SetFact.toOrderExclSet(lp.listInterfaces.size(), new GetIndex<String>() {
            public String getMapValue(int i) {
                return "key"+i;
            }});
        addIndex(keyNames, directLI(lp));
    }

    public void addIndex(CalcProperty property) {
        addIndex(new LCP(property));
    }

    public void addIndex(ImOrderSet<String> keyNames, Object... params) {
        ImList<CalcPropertyObjectInterfaceImplement<String>> index = PropertyUtils.readObjectImplements(keyNames, params);
        ThreadLocalContext.getDbManager().addIndex(index);
    }

    protected void addPersistent(LCP lp) {
        addPersistent((AggregateProperty) lp.property, null);
    }

    protected void addPersistent(LCP lp, ImplementTable table) {
        addPersistent((AggregateProperty) lp.property, table);
    }

    private void addPersistent(AggregateProperty property, ImplementTable table) {
        assert property.isNamed();

        logger.debug("Initializing stored property " + property + "...");
        property.markStored(baseLM.tableFactory, table);
    }

    // нужен так как иначе начинает sID расширять

    public <T extends PropertyInterface> LCP<T> addOldProp(LCP<T> lp, PrevScope scope) {
        return baseLM.addOldProp(lp, scope);
    }

    public <T extends PropertyInterface> LCP<T> addCHProp(LCP<T> lp, IncrementType type, PrevScope scope) {
        return baseLM.addCHProp(lp, type, scope);
    }

    public <T extends PropertyInterface> LCP addClassProp(LCP<T> lp) {
        return baseLM.addClassProp(lp);
    }

    @IdentityStrongLazy // для ID
    public LCP addGroupObjectProp(GroupObjectEntity groupObject, GroupObjectProp prop) {
        CalcPropertyRevImplement<ClassPropertyInterface, ObjectEntity> filterProperty = groupObject.getProperty(prop);
        return addProperty(null, new LCP<>(filterProperty.property, groupObject.getOrderObjects().mapOrder(filterProperty.mapping.reverse())));
    }
    
    protected LAP addOSAProp(ObjectEntity object, boolean last, Object... params) {
        return addOSAProp(null, LocalizedString.create(""), object, last, params);
    }

    protected LAP addOSAProp(AbstractGroup group, LocalizedString caption, ObjectEntity object, boolean last, Object... params) {
        return addJoinAProp(group, caption, addOSAProp(object, last), params);
    }

    @IdentityStrongLazy // для ID
    public LAP addOSAProp(ObjectEntity object, boolean last) {
        SeekObjectActionProperty seekProperty = new SeekObjectActionProperty(object, last);
        return addProperty(null, new LAP<>(seekProperty));
    }

    protected LAP addGOSAProp(GroupObjectEntity object, List<ObjectEntity> objects, boolean last, Object... params) {
        return addGOSAProp(null, LocalizedString.create(""), object, objects, last, params);
    }

    protected LAP addGOSAProp(AbstractGroup group, LocalizedString caption, GroupObjectEntity object, List<ObjectEntity> objects, boolean last, Object... params) {
        return addJoinAProp(group, caption, addGOSAProp(object, objects, last), params);
    }

    @IdentityStrongLazy // для ID
    public LAP addGOSAProp(GroupObjectEntity object, List<ObjectEntity> objects, boolean last) {
        List<ValueClass> objectClasses = new ArrayList<>();
        for (ObjectEntity obj : objects) {
            objectClasses.add(obj.baseClass);
        }
        SeekGroupObjectActionProperty seekProperty = new SeekGroupObjectActionProperty(object, objects, last, objectClasses.toArray(new ValueClass[objectClasses.size()]));
        return addProperty(null, new LAP<>(seekProperty));
    }

    public void addConstraint(CalcProperty property, boolean checkChange) {
        addConstraint(property, null, checkChange);
    }

    public void addConstraint(CalcProperty property, CalcProperty messageProperty, boolean checkChange) {
        addConstraint(property, messageProperty, checkChange, null);
    }

    public void addConstraint(CalcProperty property, boolean checkChange, DebugInfo.DebugPoint debugPoint) {
        addConstraint(addProp(property), null, checkChange, debugPoint);
    }

    public void addConstraint(CalcProperty property, CalcProperty messageProperty, boolean checkChange, DebugInfo.DebugPoint debugPoint) {
        addConstraint(addProp(property), messageProperty == null ? null : addProp(messageProperty), checkChange, debugPoint);
    }

    public void addConstraint(LCP<?> lp, LCP<?> messageLP, boolean checkChange, DebugInfo.DebugPoint debugPoint) {
        addConstraint(lp, messageLP, (checkChange ? CalcProperty.CheckType.CHECK_ALL : CalcProperty.CheckType.CHECK_NO), null, Event.APPLY, this, debugPoint);
    }

    protected void addConstraint(LCP<?> lp, LCP<?> messageLP, CalcProperty.CheckType type, ImSet<CalcProperty<?>> checkProps, Event event, LogicsModule lm, DebugInfo.DebugPoint debugPoint) {
        if(!((CalcProperty)lp.property).noDB())
            lp = addCHProp(lp, IncrementType.SET, event.getScope());
        // assert что lp уже в списке properties
        setConstraint((CalcProperty) lp.property, messageLP == null ? null : messageLP.property, type, event, checkProps, debugPoint);
    }

    public <T extends PropertyInterface> void setConstraint(CalcProperty property, CalcProperty messageProperty, CalcProperty.CheckType type, Event event, ImSet<CalcProperty<?>> checkProperties, DebugInfo.DebugPoint debugPoint) {
        assert type != CalcProperty.CheckType.CHECK_SOME || checkProperties != null;
        assert property.noDB();

        property.checkChange = type;
        property.checkProperties = checkProperties;

        ActionPropertyMapImplement<ClassPropertyInterface, ClassPropertyInterface> logAction = new LogPropertyActionProperty<T>(property, messageProperty).getImplement();
        //  PRINT OUT property MESSAGE NOWAIT;
//        logAction = (ActionPropertyMapImplement<ClassPropertyInterface, ClassPropertyInterface>) addPFAProp(null, property.caption, new OutFormSelector<T>(property, messageProperty), new ArrayList<ObjectSelector>(), new ArrayList<Boolean>(), false, FormPrintType.MESSAGE, false, 30, null).property.getImplement();
        ActionPropertyMapImplement<?, ClassPropertyInterface> constraintAction =
                DerivedProperty.createListAction(
                        SetFact.<ClassPropertyInterface>EMPTY(),
                        ListFact.toList(logAction,
                                baseLM.cancel.property.getImplement(SetFact.<ClassPropertyInterface>EMPTYORDER())
                        )
                );
        constraintAction.mapEventAction(this, DerivedProperty.createAnyGProp(property).getImplement(), event, true, debugPoint);
        addProp(constraintAction.property);
    }

    public <T extends PropertyInterface> void addEventAction(Event event, boolean descending, boolean ordersNotNull, int noInline, boolean forceInline, DebugInfo.DebugPoint debugPoint, Object... params) {
        ImOrderSet<PropertyInterface> innerInterfaces = genInterfaces(getIntNum(params));

        ImList<PropertyInterfaceImplement<PropertyInterface>> listImplements = readImplements(innerInterfaces, params);
        int implCnt = listImplements.size();

        ImOrderMap<CalcPropertyInterfaceImplement<PropertyInterface>, Boolean> orders = BaseUtils.immutableCast(listImplements.subList(2, implCnt - noInline).toOrderSet().toOrderMap(descending));

        ImSet<PropertyInterface> noInlineInterfaces = BaseUtils.<ImList<PropertyInterface>>immutableCast(listImplements.subList(implCnt - noInline, implCnt)).toOrderExclSet().getSet();

        addEventAction(innerInterfaces.getSet(), (ActionPropertyMapImplement<?, PropertyInterface>) listImplements.get(0), (CalcPropertyMapImplement<?, PropertyInterface>) listImplements.get(1), orders, ordersNotNull, event, noInlineInterfaces, forceInline, false, debugPoint);
    }

    public <P extends PropertyInterface, D extends PropertyInterface> void addEventAction(ActionProperty<P> actionProperty, CalcPropertyMapImplement<?, P> whereImplement, ImOrderMap<CalcPropertyInterfaceImplement<P>, Boolean> orders, boolean ordersNotNull, Event event, boolean resolve, DebugInfo.DebugPoint debugPoint) {
        addEventAction(actionProperty.interfaces, actionProperty.getImplement(), whereImplement, orders, ordersNotNull, event, SetFact.<P>EMPTY(), false, resolve, debugPoint);
    }

    public <P extends PropertyInterface, D extends PropertyInterface> void addEventAction(ImSet<P> innerInterfaces, ActionPropertyMapImplement<?, P> actionProperty, CalcPropertyMapImplement<?, P> whereImplement, ImOrderMap<CalcPropertyInterfaceImplement<P>, Boolean> orders, boolean ordersNotNull, Event event, ImSet<P> noInline, boolean forceInline, boolean resolve, DebugInfo.DebugPoint debugPoint) {
        if(!((CalcProperty)whereImplement.property).noDB())
            whereImplement = whereImplement.mapChanged(IncrementType.SET, event.getScope());

        ActionProperty<? extends PropertyInterface> action =
                innerInterfaces.isEmpty() ?
                    DerivedProperty.createIfAction(innerInterfaces, whereImplement, actionProperty, null).property :
                    DerivedProperty.createForAction(innerInterfaces, SetFact.<P>EMPTY(), whereImplement, orders, ordersNotNull, actionProperty, null, false, noInline, forceInline).property;

        if(debugPoint != null) { // создано getEventDebugPoint
            if(debugger.isEnabled()) // topContextActionPropertyDefinitionBodyCreated
                debugger.setNewDebugStack(action);

            assert action.getDelegationType(true) == ActionDelegationType.AFTER_DELEGATE;
            ScriptingLogicsModule.setDebugInfo(true, debugPoint, action); // actionPropertyDefinitionBodyCreated
        }

//        action.setStrongUsed(whereImplement.property); // добавить сильную связь, уже не надо поддерживается более общий механизм - смотреть на Session Calc
//        action.caption = "WHEN " + whereImplement.property + " " + actionProperty;
        addProp(action);

        addBaseEvent(action, event, resolve, false);
    }

    public <P extends PropertyInterface> void addBaseEvent(ActionProperty<P> action, Event event, boolean resolve, boolean single) {
        action.addEvent(event.base, event.session);
        if(event.after != null)
            action.addStrongUsed(event.after);
        action.singleApply = single;
        action.resolve = resolve;
    }

    public <P extends PropertyInterface> void addAspectEvent(int interfaces, ActionPropertyImplement<P, Integer> action, String mask, boolean before) {
        // todo: непонятно что пока с полными каноническими именами и порядками параметров делать
    }

    public <P extends PropertyInterface, T extends PropertyInterface> void addAspectEvent(ActionProperty<P> action, ActionPropertyMapImplement<T, P> aspect, boolean before) {
        if(before)
            action.addBeforeAspect(aspect);
        else
            action.addAfterAspect(aspect);
    }

    protected <L extends PropertyInterface, T extends PropertyInterface> void follows(LCP<T> first, LCP<L> second, Integer... mapping) {
        follows(first, null, ListFact.toList(new PropertyFollowsDebug(true, null), new PropertyFollowsDebug(false, null)), Event.APPLY, second, mapping);
    }

    protected <L extends PropertyInterface, T extends PropertyInterface> void follows(final LCP<T> first, DebugInfo.DebugPoint debugPoint, ImList<PropertyFollowsDebug> options, Event event, LCP<L> second, final Integer... mapping) {
        addFollows(first.property, new CalcPropertyMapImplement<>(second.property, second.getRevMap(first.listInterfaces, mapping)), debugPoint, options, event);
    }

    public <T extends PropertyInterface, L extends PropertyInterface> void setNotNull(CalcProperty<T> property, DebugInfo.DebugPoint debugPoint, ImList<PropertyFollowsDebug> options, Event event) {
        CalcPropertyMapImplement<L, T> mapClasses = (CalcPropertyMapImplement<L, T>) IsClassProperty.getMapProperty(property.getInterfaceClasses(ClassType.logPolicy));
        property.setNotNull = true;
        addFollows(mapClasses.property, new CalcPropertyMapImplement<>(property, mapClasses.mapping.reverse()),
                LocalizedString.concatList(LocalizedString.create("{logics.property} "), property.caption, " [" + property.getSID(), LocalizedString.create("] {logics.property.not.defined}")),
                debugPoint, options, event);
    }

    public <T extends PropertyInterface, L extends PropertyInterface> void addFollows(CalcProperty<T> property, CalcPropertyMapImplement<L, T> implement, DebugInfo.DebugPoint debugPoint, ImList<PropertyFollowsDebug> options, Event event) {
        addFollows(property, implement, LocalizedString.create("{logics.property.violated.consequence.from}" + "(" + this + ") => (" + implement.property + ")"), debugPoint, options, event);
    }

    public <T extends PropertyInterface, L extends PropertyInterface> void addFollows(CalcProperty<T> property, CalcPropertyMapImplement<L, T> implement, LocalizedString caption, DebugInfo.DebugPoint debugPoint, ImList<PropertyFollowsDebug> options, Event event) {
//        PropertyFollows<T, L> propertyFollows = new PropertyFollows<T, L>(this, implement, options);

        for(PropertyFollowsDebug option : options) {
            assert !option.isTrue || property.interfaces.size() == implement.mapping.size(); // assert что количество
            ActionPropertyMapImplement<?, T> setAction = option.isTrue ? implement.getSetNotNullAction(true) : property.getSetNotNullAction(false);
            if(setAction!=null) {
//                setAction.property.caption = "RESOLVE " + option.isTrue + " : " + property + " => " + implement.property;
                CalcPropertyMapImplement<?, T> condition;
                if(option.isFull)
                    condition = DerivedProperty.createAndNot(property, implement).mapChanged(IncrementType.SET, event.getScope());
                else {
                    if (option.isTrue)
                        condition = DerivedProperty.createAndNot(property.getChanged(IncrementType.SET, event.getScope()), implement);
                    else
                        condition = DerivedProperty.createAnd(property, implement.mapChanged(IncrementType.DROP, event.getScope()));
                }
                setAction.mapEventAction(this, condition, event, true, option.debugPoint);
            }
        }

        CalcProperty constraint = DerivedProperty.createAndNot(property, implement).property;
        constraint.caption = caption;
        addConstraint(constraint, false, debugPoint);
    }

    protected <P extends PropertyInterface, C extends PropertyInterface> void setNotNull(LCP<P> lp, ImList<PropertyFollowsDebug> resolve) {
        setNotNull(lp, null, Event.APPLY, resolve);
    }

    protected <P extends PropertyInterface, C extends PropertyInterface> void setNotNull(LCP<P> lp, DebugInfo.DebugPoint debugPoint, Event event, ImList<PropertyFollowsDebug> resolve) {
        setNotNull(lp.property, debugPoint, resolve, event);
    }

    public static <P extends PropertyInterface, T extends PropertyInterface> ActionPropertyMapImplement<P, T> mapActionListImplement(LAP<P> property, ImOrderSet<T> mapList) {
        return new ActionPropertyMapImplement<>(property.property, getMapping(property, mapList));
    }
    public static <P extends PropertyInterface, T extends PropertyInterface> CalcPropertyMapImplement<P, T> mapCalcListImplement(LCP<P> property, ImOrderSet<T> mapList) {
        return new CalcPropertyMapImplement<>(property.property, getMapping(property, mapList));
    }

    private static <P extends PropertyInterface, T extends PropertyInterface> ImRevMap<P, T> getMapping(LP<P, ?> property, final ImOrderSet<T> mapList) {
        return property.getRevMap(mapList);
    }

    protected void makeUserLoggable(SystemEventsLogicsModule systemEventsLM, LCP... lps) {
        for (LCP lp : lps)
            lp.makeUserLoggable(this, systemEventsLM);
    }

    public LCP not() {
        return baseLM.not();
    }

    // получает свойство is
    public LCP<?> is(ValueClass valueClass) {
        return baseLM.is(valueClass);
    }

    public LCP object(ValueClass valueClass) {
        return baseLM.object(valueClass);
    }

    protected LCP and(boolean... nots) {
        return addAFProp(nots);
    }

    protected NavigatorElement addNavigatorElement(String name, LocalizedString caption, String creationPath) {
        String canonicalName = NavigatorElementCanonicalNameUtils.createNavigatorElementCanonicalName(getNamespace(), name);
        
        NavigatorElement elem = new NavigatorElement(null, canonicalName, caption, creationPath, null, getVersion());
        addModuleNavigator(elem);
        return elem;
    }

    protected NavigatorAction addNavigatorAction(String name, LocalizedString caption, LAP<?> property, String creationPath) {
        String canonicalName = NavigatorElementCanonicalNameUtils.createNavigatorElementCanonicalName(getNamespace(), name);

        NavigatorAction navigatorAction = new NavigatorAction(null, canonicalName, caption, creationPath, null, getVersion());
        navigatorAction.setProperty(property.property);
        addModuleNavigator(navigatorAction);
        return navigatorAction;
    }

    public Collection<NavigatorElement<?>> getModuleNavigators() {
        return moduleNavigators.values();
    }

    // в том числе и приватные 
    public Collection<NavigatorElement<?>> getAllModuleNavigators() {
        List<NavigatorElement<?>> elements = new ArrayList<>();
        elements.addAll(privateNavigators);
        elements.addAll(moduleNavigators.values());
        return elements;
    }
    
    public NavigatorElement getNavigatorElement(String name) {
        String canonicalName = NavigatorElementCanonicalNameUtils.createNavigatorElementCanonicalName(getNamespace(), name);
        return getNavigatorElementByCanonicalName(canonicalName);
    }

    private NavigatorElement getNavigatorElementByCanonicalName(String canonicalName) {
        return moduleNavigators.get(canonicalName);
    }

    public <T extends FormEntity> T addFormEntity(T form) {
        if (form.isNamed()) {
            addModuleNavigator(form);
        } else {
            addPrivateModuleNavigator(form);
        }
        return form;
    }
    
    @NFLazy
    private void addModuleNavigator(NavigatorElement<?> element) {
        assert !moduleNavigators.containsKey(element.getCanonicalName());
        moduleNavigators.put(element.getCanonicalName(), element);
    }

    @NFLazy
    private void addPrivateModuleNavigator(NavigatorElement<?> element) {
        privateNavigators.add(element);
    }
    
    public void addObjectActions(FormEntity form, ObjectEntity object) {
        Version version = getVersion();
        form.addPropertyDraw(getAddObjectAction(form, object, null), version);
        form.addPropertyDraw(getDeleteAction(object, FormSessionScope.OLDSESSION), version, object);
    }

    public void addFormActions(FormEntity form, ObjectEntity object) {
        addFormActions(form, object, FormSessionScope.NEWSESSION);
    }

    public void addFormActions(FormEntity form, ObjectEntity object, FormSessionScope scope) {
        Version version = getVersion();
        form.addPropertyDraw(getAddFormAction(form, object, null, scope, version), version);
        form.addPropertyDraw(getEditFormAction(object, null, scope, version), version, object);
        form.addPropertyDraw(getDeleteAction(object, scope), version, object);
    }

    public LAP getAddFormAction(FormEntity contextForm, ObjectEntity contextObject, CustomClass explicitClass, FormSessionScope scope, Version version) {
        CustomClass cls = explicitClass;
        if(cls == null)
            cls = (CustomClass)contextObject.baseClass;
        return baseLM.getAddFormAction(cls, contextForm, contextObject, scope, cls.getEditForm(baseLM, version));
    }

    public LAP getEditFormAction(ObjectEntity object, CustomClass explicitClass, FormSessionScope scope, Version version) {
        CustomClass cls = explicitClass;
        if(cls == null)
            cls = (CustomClass) object.baseClass;
        return baseLM.getEditFormAction(cls, scope, cls.getEditForm(baseLM, version));
    }

    public LAP getDeleteAction(ObjectEntity object, FormSessionScope scope) {
        CustomClass cls = (CustomClass) object.baseClass;
        return getDeleteAction(cls, object, scope);
    }
    public LAP getDeleteAction(CustomClass cls, ObjectEntity object, FormSessionScope scope) {
        return baseLM.getDeleteAction(cls, scope);
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public boolean isDefaultNamespace() {
        return defaultNamespace;
    }

    public void setDefaultNamespace(boolean defaultNamespace) {
        this.defaultNamespace = defaultNamespace;
    }

    public Set<String> getRequiredModules() {
        return requiredModules;
    }

    public void setRequiredModules(Set<String> requiredModules) {
        this.requiredModules = requiredModules;
    }

    public List<String> getNamespacePriority() {
        return namespacePriority;
    }

    public void setNamespacePriority(List<String> namespacePriority) {
        this.namespacePriority = namespacePriority;
    }

    public List<LP<?, ?>> getNamedProperties() {
        List<LP<?, ?>> properties = new ArrayList<>();
        for (List<LP<?, ?>> propList : namedModuleProperties.values()) {
            properties.addAll(propList);    
        }
        return properties; 
    }
    
    public interface ModuleFinder<T, P> {
        List<T> resolveInModule(LogicsModule module, String simpleName, P param);
    }

    public List<ResolveClassSet> getParamClasses(LP<?, ?> lp) {
        List<ResolveClassSet> paramClasses = propClasses.get(lp);
        return paramClasses == null ? Collections.<ResolveClassSet>nCopies(lp.listInterfaces.size(), null) : paramClasses;                   
    }

    public static class OldLPNameModuleFinder implements ModuleFinder<LP<?, ?>, List<ResolveClassSet>> {
        @Override
        public List<LP<?, ?>> resolveInModule(LogicsModule module, String simpleName, List<ResolveClassSet> classes)  {
            List<LP<?, ?>> result = new ArrayList<>();
            for (List<LP<?, ?>> lps : module.getNamedModuleProperties().values()) {
                for (LP<?, ?> lp : lps) {
                    String actualName = lp.property.getName();
                    if (simpleName.equals(actualName)) {
                        result.add(lp);
                    }
                }
            }
            return result;
        }
    }

    public static class SoftLPModuleFinder implements ModuleFinder<LP<?, ?>, List<ResolveClassSet>> {
        @Override
        public List<LP<?, ?>> resolveInModule(LogicsModule module, String simpleName, List<ResolveClassSet> classes)  {
            List<LP<?, ?>> result = new ArrayList<>();
            for (LP<?, ?> lp : module.getAllLPByName(simpleName)) {
                if (softMatch(module.getParamClasses(lp), classes)) {
                    result.add(lp);
                }
            }
            return result;
        }
    }                           
    
    public static class LPModuleFinder implements ModuleFinder<LP<?, ?>, List<ResolveClassSet>> {
        @Override
        public List<LP<?, ?>> resolveInModule(LogicsModule module, String simpleName, List<ResolveClassSet> classes)  {
            List<LP<?, ?>> result = new ArrayList<>();
            for (LP<?, ?> lp : module.getAllLPByName(simpleName)) {
                if (match(module.getParamClasses(lp), classes, false, false)) {
                    result.add(lp);
                }
            }
            return result;
        }
    }

    public static class AbstractLPModuleFinder implements ModuleFinder<LP<?, ?>, List<ResolveClassSet>> {
        @Override
        public List<LP<?, ?>> resolveInModule(LogicsModule module, String simpleName, List<ResolveClassSet> classes)  {
            List<LP<?, ?>> result = new ArrayList<>();
            for (LP<?, ?> lp : module.getAllLPByName(simpleName)) {
                if(((lp.property instanceof CaseUnionProperty && ((CaseUnionProperty)lp.property).isAbstract()) ||
                        lp.property instanceof ListCaseActionProperty && ((ListCaseActionProperty)lp.property).isAbstract()) &&
                    match(module.getParamClasses(lp), classes, false, false)) {
                    result.add(lp);
                }
            }
            return result;
        }
    }

    // Находит идентичные по имени и сигнатуре свойства.   
    public static class EqualLPModuleFinder implements ModuleFinder<LP<?, ?>, List<ResolveClassSet>> {
        private final boolean findLocals;
        
        public EqualLPModuleFinder(boolean findLocals) {
            this.findLocals = findLocals;        
        }
        
        @Override
        public List<LP<?, ?>> resolveInModule(LogicsModule module, String simpleName, List<ResolveClassSet> classes)  {
            List<LP<?, ?>> result = new ArrayList<>();
            for (LP<?, ?> lp : module.getAllLPByName(simpleName)) {
                if ((findLocals || !lp.property.isLocal()) && match(module.getParamClasses(lp), classes, false, false) && match(classes, module.getParamClasses(lp), false, false)) {
                    result.add(lp);
                }
            }
            return result;
        }
    }
    
    public final static boolean softMode = true; 
        
    public static boolean match(List<ResolveClassSet> interfaceClasses, List<ResolveClassSet> paramClasses, boolean strict, boolean falseImplicitClass) {
        assert interfaceClasses != null;
        if (paramClasses == null) {
            return true;
        }
        if (interfaceClasses.size() != paramClasses.size()) {
            return false;
        }

        for (int i = 0, size = interfaceClasses.size(); i < size; i++) {
            ResolveClassSet whoClass = interfaceClasses.get(i);
            ResolveClassSet whatClass = paramClasses.get(i);
            if (whoClass == null && whatClass == null)
                continue;

            if (whoClass != null && whatClass != null && !whoClass.containsAll(whatClass, !strict))
                return false;

            if (whatClass != null)
                continue;

            if (softMode && falseImplicitClass)
                return false;
        }
        return true;
    }

    public static boolean softMatch(List<ResolveClassSet> interfaceClasses, List<ResolveClassSet> paramClasses) {
        assert interfaceClasses != null;
        if (paramClasses == null) {
            return true;
        }
        if (interfaceClasses.size() != paramClasses.size()) {
            return false;
        }

        for (int i = 0; i < interfaceClasses.size(); i++) {
            if (interfaceClasses.get(i) != null && paramClasses.get(i) != null && (interfaceClasses.get(i).and(paramClasses.get(i))).isEmpty()) {
                return false;
            }
        }
        return true;
    }
    
    public static class GroupNameModuleFinder implements ModuleFinder<AbstractGroup, Object> {
        @Override
        public List<AbstractGroup> resolveInModule(LogicsModule module, String simpleName, Object param) {
            AbstractGroup group = module.getGroup(simpleName); 
            return group == null ? new ArrayList<AbstractGroup>() : Collections.singletonList(group);
        }
    }

    public static class NavigatorElementNameModuleFinder implements ModuleFinder<NavigatorElement, Object> {
        @Override
        public List<NavigatorElement> resolveInModule(LogicsModule module, String simpleName, Object param) {
            NavigatorElement ne = module.getNavigatorElement(simpleName); 
            return ne == null ? new ArrayList<NavigatorElement>() : Collections.singletonList(ne);
        }
    }                                           

    public static class WindowNameModuleFinder implements ModuleFinder<AbstractWindow, Object> {
        @Override
        public List<AbstractWindow> resolveInModule(LogicsModule module, String simpleName, Object param) {
            AbstractWindow wnd = module.getWindow(simpleName); 
            return wnd == null ? new ArrayList<AbstractWindow>() : Collections.singletonList(wnd);
        }
    }

    public static class MetaCodeNameModuleFinder implements ModuleFinder<MetaCodeFragment, Integer> {
        @Override
        public List<MetaCodeFragment> resolveInModule(LogicsModule module, String simpleName, Integer paramCnt) {
            MetaCodeFragment code = module.getMetaCodeFragment(simpleName, paramCnt); 
            return code == null ? new ArrayList<MetaCodeFragment>() : Collections.singletonList(code);
        }
    }

    public static class TableNameModuleFinder implements ModuleFinder<ImplementTable, Object> {
        @Override
        public List<ImplementTable> resolveInModule(LogicsModule module, String simpleName, Object param) {
            ImplementTable table = module.getTable(simpleName); 
            return table == null ? new ArrayList<ImplementTable>() : Collections.singletonList(table);
        }
    }

    public static class ClassNameModuleFinder implements ModuleFinder<CustomClass, Object> {
        @Override
        public List<CustomClass> resolveInModule(LogicsModule module, String simpleName, Object param) {
            CustomClass cls = module.getClass(simpleName);             
            return cls == null ? new ArrayList<CustomClass>() : Collections.singletonList(cls);
        }
    }
    
    // для обратной совместимости
    public void addFormFixedFilter(FormEntity form, FilterEntity filter) {
        form.addFixedFilter(filter, getVersion());
    }

    public RegularFilterGroupEntity newRegularFilterGroupEntity(int id) {
        return new RegularFilterGroupEntity(id, getVersion());
    }

    public void addFormHintsIncrementTable(FormEntity form, LCP... lps) {
        form.addHintsIncrementTable(getVersion(), lps);
    }

    public List<PropertyDrawEntity> addFormPropertyDraw(FormEntity form, AbstractNode group, boolean upClasses, ObjectEntity... objects) {
        return form.addPropertyDraw(group, upClasses, getVersion(), objects);
    }
    
    public void addFormPropertyDraw(FormEntity form, AbstractNode group, boolean upClasses, boolean useObjSubsets, ObjectEntity... objects) {
        form.addPropertyDraw(group, upClasses, useObjSubsets, getVersion(), objects);
    }

    public void addFormPropertyDraw(FormEntity form, ObjectEntity object1, ObjectEntity object2, ObjectEntity object3, Object... groups) {
        form.addPropertyDraw(object1, object2, object3, getVersion(), groups);
    }

    public void addFormPropertyDraw(FormEntity form, ObjectEntity object1, ObjectEntity object2, Object... groups) {
        form.addPropertyDraw(object1, object2, getVersion(), groups);
    }

    public void addFormPropertyDraw(FormEntity form, ObjectEntity object1, ObjectEntity object2, ObjectEntity object3, ObjectEntity object4, Object... groups) {
        form.addPropertyDraw(object1, object2, object3, object4, getVersion(), groups);
    }

    public void addFormPropertyDraw(FormEntity form, ObjectEntity object, Object... groups) {
        form.addPropertyDraw(object, getVersion(), groups);
    }

    public PropertyDrawEntity addFormPropertyDraw(FormEntity form, LP property, PropertyObjectInterfaceEntity... objects) {
        return form.addPropertyDraw(property, getVersion(), objects);
    }

    public <P extends PropertyInterface> PropertyDrawEntity addFormPropertyDraw(FormEntity form, LP<P, ?> property, GroupObjectEntity groupObject, PropertyObjectInterfaceEntity... objects) {
        return form.addPropertyDraw(property, groupObject, getVersion(), objects);
    }

    public void addFormPropertyDraw(FormEntity form, LP[] properties, ObjectEntity... objects) {
        form.addPropertyDraw(properties, getVersion(), objects);
    }

    public void addFormRegularFilterGroup(FormEntity form, RegularFilterGroupEntity group) {
        form.addRegularFilterGroup(group, getVersion());
    }

    protected PropertyDrawEntity<?> getFormPropertyDraw(FormEntity form, LP<?, ?> lp, ObjectEntity object) {
        return form.getNFPropertyDraw(lp.property, object.groupTo, getVersion());
    }

    public PropertyDrawEntity getFormPropertyDraw(FormEntity form, AbstractNode group, ObjectEntity object) {
        return form.getNFPropertyDraw(group, object, getVersion());
    }

    public int getModuleComplexity() {
        return 1;
    }
}
