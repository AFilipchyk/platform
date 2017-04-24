package lsfusion.server.logics.scripted;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import lsfusion.base.*;
import lsfusion.base.col.ListFact;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.*;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.interop.*;
import lsfusion.interop.form.layout.Alignment;
import lsfusion.server.ServerLoggers;
import lsfusion.server.Settings;
import lsfusion.server.caches.IdentityLazy;
import lsfusion.server.classes.*;
import lsfusion.server.classes.sets.ResolveClassSet;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.Union;
import lsfusion.server.data.expr.formula.CustomFormulaSyntax;
import lsfusion.server.data.expr.formula.SQLSyntaxType;
import lsfusion.server.data.expr.query.GroupType;
import lsfusion.server.data.expr.query.PartitionType;
import lsfusion.server.data.type.ConcatenateType;
import lsfusion.server.data.type.ObjectType;
import lsfusion.server.data.type.Type;
import lsfusion.server.form.entity.*;
import lsfusion.server.form.instance.FormSessionScope;
import lsfusion.server.form.navigator.DefaultIcon;
import lsfusion.server.form.navigator.NavigatorElement;
import lsfusion.server.form.view.ComponentView;
import lsfusion.server.form.view.FormView;
import lsfusion.server.form.window.*;
import lsfusion.server.logics.*;
import lsfusion.server.logics.debug.*;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.linear.LAP;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.linear.LP;
import lsfusion.server.logics.mutables.Version;
import lsfusion.server.logics.property.*;
import lsfusion.server.logics.property.Event;
import lsfusion.server.logics.property.actions.*;
import lsfusion.server.logics.property.actions.file.FileActionType;
import lsfusion.server.logics.property.actions.flow.BreakActionProperty;
import lsfusion.server.logics.property.actions.flow.ListCaseActionProperty;
import lsfusion.server.logics.property.actions.flow.ReturnActionProperty;
import lsfusion.server.logics.property.actions.importing.ImportDataActionProperty;
import lsfusion.server.logics.property.actions.importing.csv.ImportCSVDataActionProperty;
import lsfusion.server.logics.property.actions.importing.csv.ImportFormCSVDataActionProperty;
import lsfusion.server.logics.property.actions.importing.dbf.ImportFormDBFDataActionProperty;
import lsfusion.server.logics.property.actions.importing.json.ImportFormJSONDataActionProperty;
import lsfusion.server.logics.property.actions.importing.xls.ImportXLSDataActionProperty;
import lsfusion.server.logics.property.actions.importing.xml.ImportFormXMLDataActionProperty;
import lsfusion.server.logics.property.actions.importing.xml.ImportXMLDataActionProperty;
import lsfusion.server.logics.property.derived.DerivedProperty;
import lsfusion.server.logics.property.group.AbstractGroup;
import lsfusion.server.logics.table.ImplementTable;
import lsfusion.server.mail.AttachmentFormat;
import lsfusion.server.mail.SendEmailActionProperty;
import lsfusion.server.mail.SendEmailActionProperty.FormStorageType;
import lsfusion.server.session.DataSession;
import lsfusion.server.session.LocalNestedType;
import org.antlr.runtime.*;
import org.apache.log4j.Logger;
import org.codehaus.janino.SimpleCompiler;

import javax.mail.Message;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static lsfusion.base.BaseUtils.*;
import static lsfusion.server.logics.NamespaceElementFinder.FoundItem;
import static lsfusion.server.logics.PropertyUtils.*;
import static lsfusion.server.logics.scripted.AlignmentUtils.*;

/**
 * User: DAle
 * Date: 03.06.11
 * Time: 14:54
 */

public class ScriptingLogicsModule extends LogicsModule {

    private static final Logger scriptLogger = ServerLoggers.scriptLogger;
    
    private final CompoundNameResolver<LP<?, ?>, List<ResolveClassSet>> directLPResolver = new LPResolver(new LPModuleFinder(), true, false);
    private final CompoundNameResolver<LP<?, ?>, List<ResolveClassSet>> abstractLPResolver = new LPResolver(new AbstractLPModuleFinder(), true, false);
    private final CompoundNameResolver<LP<?, ?>, List<ResolveClassSet>> abstractNotEqualLPResolver = new LPResolver(new AbstractLPModuleFinder(), true, true);
    private final CompoundNameResolver<LP<?, ?>, List<ResolveClassSet>> indirectLPResolver = new LPResolver(new SoftLPModuleFinder(), false, false);
    private final CompoundNameResolver<AbstractGroup, ?> groupResolver = new CompoundNameResolver<>(new GroupNameModuleFinder());
    private final CompoundNameResolver<NavigatorElement, ?> navigatorResolver = new CompoundNameResolver<>(new NavigatorElementNameModuleFinder());
    private final CompoundNameResolver<AbstractWindow, ?> windowResolver = new CompoundNameResolver<>(new WindowNameModuleFinder());
    private final CompoundNameResolver<ImplementTable, ?> tableResolver = new CompoundNameResolver<>(new TableNameModuleFinder());
    private final CompoundNameResolver<CustomClass, ?> classResolver = new CompoundNameResolver<>(new ClassNameModuleFinder());

    private final BusinessLogics<?> BL;

    private String code = null;
    private String filename = null;
    private String path = null;
    private final ScriptingErrorLog errLog;
    private ScriptParser parser;
    private List<String> warningList = new ArrayList<>();
    private Map<Property, String> alwaysNullProperties = new HashMap<>();

    private String lastOpimizedJPropSID = null;

    public static List<String> getUsedNames(List<TypedParameter> context, List<Integer> usedParams) {
        List<String> usedNames = new ArrayList<>();
        for (int usedIndex : usedParams) {
            usedNames.add(context.get(usedIndex).paramName);
        }
        return usedNames;
    }

    public static List<ResolveClassSet> getUsedClasses(List<TypedParameter> context, List<Integer> usedParams) {
        List<ResolveClassSet> usedClasses = new ArrayList<>();
        for (int usedIndex : usedParams) {
            ValueClass cls = context.get(usedIndex).cls;
            if(cls == null)
                usedClasses.add(null);
            else
                usedClasses.add(cls.getResolveSet());
        }
        return usedClasses;
    }

    public enum ConstType { STATIC, INT, REAL, NUMERIC, STRING, LOGICAL, LONG, DATE, DATETIME, TIME, COLOR, NULL }
    public enum InsertPosition {IN, BEFORE, AFTER, FIRST}
    public enum WindowType {MENU, PANEL, TOOLBAR, TREE}
    public enum GroupingType {SUM, MAX, MIN, CONCAT, AGGR, EQUAL, LAST, NAGGR}

    private ScriptingLogicsModule(BaseLogicsModule<?> baseModule, BusinessLogics<?> BL) {
        setBaseLogicsModule(baseModule);
        this.BL = BL;
        errLog = new ScriptingErrorLog("");
        parser = new ScriptParser(errLog);
    }

    public ScriptingLogicsModule(String filename, BaseLogicsModule<?> baseModule, BusinessLogics<?> BL) {
        this(baseModule, BL);
        this.filename = filename;
    }

    public ScriptingLogicsModule(InputStream stream, String path, BaseLogicsModule<?> baseModule, BusinessLogics<?> BL) throws IOException {
        this(stream, path, "utf-8", baseModule, BL);
    }

    public ScriptingLogicsModule(InputStream stream, String path, String charsetName, BaseLogicsModule<?> baseModule, BusinessLogics<?> BL) throws IOException {
        this(baseModule, BL);
        this.code = IOUtils.readStreamToString(stream, charsetName);
        this.path = path;
    }

    public ScriptingLogicsModule(BaseLogicsModule<?> baseModule, BusinessLogics<?> BL, String code) {
        this(baseModule, BL);
        this.code = code;
    }

    protected DataSession createSession() throws SQLException {
        return ThreadLocalContext.getDbManager().createSession();
    }

    private void setModuleName(String moduleName) {
        setName(moduleName);
        errLog.setModuleName(moduleName);
    }

    private CharStream createStream() throws IOException {
        if (code != null) {
            return new ANTLRStringStream(code);
        } else {
            return new ANTLRFileStream(filename, "UTF-8");
        }
    }

    @Override
    @IdentityLazy
    public int getModuleComplexity() {
        try {
            return createStream().size();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public ScriptingErrorLog getErrLog() {
        return errLog;
    }

    public ScriptParser getParser() {
        return parser;
    }

    public String getPath() {
        return path;
    }

    public String getCode() {
        return code;
    }
    
    protected LogicsModule findModule(String name) throws ScriptingErrorLog.SemanticErrorException {
        LogicsModule module = BL.getSysModule(name);
        checkModule(module, name);
        return module;
    }

    public String transformStringLiteral(String s) throws ScriptingErrorLog.SemanticErrorException {
        try {
            return ScriptedStringUtils.transformStringLiteral(s);
        } catch (ScriptedStringUtils.TransformationError e) {
            errLog.emitSimpleError(parser, e.getMessage());
        }
        return null;
    }

    public LocalizedString transformLocalizedStringLiteral(String s) throws ScriptingErrorLog.SemanticErrorException {
        try {
            return ScriptedStringUtils.transformLocalizedStringLiteral(s);
        } catch (ScriptedStringUtils.TransformationError | LocalizedString.FormatError e) {
            errLog.emitSimpleError(parser, e.getMessage());
        }
        return null;
    }
    
    private Type getPredefinedType(String name) {
        if ("OBJECT".equals(name)) {
            return ObjectType.instance;
        } else {
            return ClassCanonicalNameUtils.getScriptedDataClass(name); 
        }
    }

    public ObjectEntity[] getMappingObjectsArray(FormEntity form, List<String> mapping) throws ScriptingErrorLog.SemanticErrorException {
        ObjectEntity[] objects = new ObjectEntity[mapping.size()];
        for (int i = 0; i < mapping.size(); i++) {
            objects[i] = getNFObjectEntityByName(form, mapping.get(i));
        }
        return objects;
    }

    public List<ResolveClassSet> getMappingClassesArray(FormEntity form, List<String> mapping) throws ScriptingErrorLog.SemanticErrorException {
        List<ResolveClassSet> classes = new ArrayList<>();
        for (String paramName : mapping) {
            ObjectEntity obj = getNFObjectEntityByName(form, paramName);
            classes.add(obj.getResolveClassSet());
        }
        return classes;
    }

    public ObjectEntity getObjectEntityByName(FormEntity form, String name) throws ScriptingErrorLog.SemanticErrorException {
        ObjectEntity obj = form.getObject(name);
        if (obj == null) {
            getErrLog().emitObjectNotFoundError(parser, name);
        }
        return obj;
    }

    public ObjectEntity getNFObjectEntityByName(FormEntity form, String name) throws ScriptingErrorLog.SemanticErrorException {
        ObjectEntity obj = form.getNFObject(name, getVersion());
        if (obj == null) {
            getErrLog().emitObjectNotFoundError(parser, name);
        }
        return obj;
    }

    public MappedProperty getPropertyWithMapping(FormEntity form, PropertyUsage pUsage, List<String> mapping) throws ScriptingErrorLog.SemanticErrorException {
        LP<?, ?> property;
        if (pUsage.classNames != null) {
            property = findLPByPropertyUsage(pUsage);            
        } else {
            List<ResolveClassSet> classes = getMappingClassesArray(form, mapping);
            property = findLPByNameAndClasses(pUsage.name, pUsage.getSourceName(), classes);            
        }
        
        if (property.property.interfaces.size() != mapping.size()) {
            getErrLog().emitParamCountError(parser, property, mapping.size());
        }
        return new MappedProperty(property, getMappingObjectsArray(form, mapping));
    }

    public ValueClass findClass(String name) throws ScriptingErrorLog.SemanticErrorException {
        ValueClass valueClass = ClassCanonicalNameUtils.getScriptedDataClass(name);
        if (valueClass == null) {
            valueClass = classResolver.resolve(name);
        }
        checkClass(valueClass, name);
        return valueClass;
    }

    public void addScriptedClass(String className, LocalizedString captionStr, boolean isAbstract,
                                 List<String> instNames, List<LocalizedString> instCaptions, List<String> parentNames, boolean isComplex,
                                 DebugInfo.DebugPoint point) throws ScriptingErrorLog.SemanticErrorException {
        checkDuplicateClass(className);
        checkStaticClassConstraints(isAbstract, instNames, instCaptions);
        checkClassParents(parentNames);

        LocalizedString caption = (captionStr == null ? LocalizedString.create(className) : captionStr);

        CustomClass[] parents;
        if (parentNames.isEmpty()) {
            parents = new CustomClass[] {baseLM.baseClass};
        } else {
            parents = new CustomClass[parentNames.size()];
            for (int i = 0; i < parentNames.size(); i++) {
                String parentName = parentNames.get(i);
                parents[i] = (CustomClass) findClass(parentName);
            }
        }

        List<LocalizedString> captions = new ArrayList<>();
        for (int i = 0; i < instCaptions.size(); i++) {
            captions.add(instCaptions.get(i) == null ? LocalizedString.create(instNames.get(i)) : instCaptions.get(i));
        }

        CustomClass cls;
        if (isAbstract) {
            cls = addAbstractClass(className, caption, parents);
        } else {
            cls = addConcreteClass(className, caption, instNames, captions, parents);
        }
        cls.isComplex = isComplex;

        ClassDebugInfo debugInfo = new ClassDebugInfo(point);
        if (debugger.isEnabled() && point.needToCreateDelegate()) {
            debugger.addDelegate(debugInfo);
            cls.setDebugInfo(debugInfo);
        }
    }

    public void extendClass(String className, List<String> instNames, List<LocalizedString> instCaptions, List<String> parentNames) throws ScriptingErrorLog.SemanticErrorException {
        Version version = getVersion();

        CustomClass cls = (CustomClass) findClass(className);
        boolean isAbstract = cls instanceof AbstractCustomClass;

        List<String> names = instNames;
        List<LocalizedString> captions = instCaptions;
        if (!isAbstract) {
            ((ConcreteCustomClass) cls).addStaticObjects(instNames, instCaptions, version);
            names = ((ConcreteCustomClass) cls).getNFStaticObjectsNames(version);
            captions = ((ConcreteCustomClass) cls).getNFStaticObjectsCaptions(version);
        }

        checkStaticClassConstraints(isAbstract, names, captions);
        checkClassParents(parentNames);

        for (String parentName : parentNames) {
            CustomClass parentClass = (CustomClass) findClass(parentName);
            if (cls.containsNFParents(parentClass, version)) {
                errLog.emitDuplicateClassParentError(parser, parentName);
            }
            cls.addParentClass(parentClass, version);
        }
    }

    public AbstractGroup findGroup(String name) throws ScriptingErrorLog.SemanticErrorException {
        AbstractGroup group = groupResolver.resolve(name);
        checkGroup(group, name);
        return group;
    }

    public LAP<?> findAction(String name) throws ScriptingErrorLog.SemanticErrorException {
        return (LAP<?>) findLP(name);
    }

    public LCP<?> findProperty(String name) throws ScriptingErrorLog.SemanticErrorException {
        return (LCP<?>) findLP(name);
    }

    public LCP<?>[] findProperties(String... names) throws ScriptingErrorLog.SemanticErrorException {
        LCP<?>[] result = new LCP[names.length];
        for (int i = 0; i < names.length; i++) {
            result[i] = findProperty(names[i]);
        }
        return result;
    }

    private LP<?, ?> findLP(String name) throws ScriptingErrorLog.SemanticErrorException {
        PropertyUsageParser parser = new PropertyUsageParser(this, name);
        LP<?, ?> property = null;
        try {
            property = findLPByNameAndClasses(parser.getCompoundName(), name, parser.getSignature());
        } catch (AbstractPropertyNameParser.ParseException e) {
            Throwables.propagate(e);
        }
        return property;
    }

    public LP<?, ?> findLPByNameAndClasses(String name, String sourceName, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        List<ResolveClassSet> classSets = new ArrayList<>();
        for (ValueClass cls : classes) {
            classSets.add(cls.getResolveSet());
        }
        return findLPByNameAndClasses(name, sourceName, classSets);
    }

    private LP<?, ?> findLPByNameAndClasses(String name, String sourceName, List<ResolveClassSet> params) throws ScriptingErrorLog.SemanticErrorException {
        return findLPByNameAndClasses(name, sourceName, params, false, false);
    }
    
    private LP<?, ?> findLPByNameAndClasses(String name, String sourceName, List<ResolveClassSet> params, boolean onlyAbstract, boolean prioritizeNotEqual) throws ScriptingErrorLog.SemanticErrorException {
        LP<?, ?> property = null;
        try {
            if (softMode && onlyAbstract) {
                if (prioritizeNotEqual) {
                    property = abstractNotEqualLPResolver.resolve(name, params);
                } else {
                    property = abstractLPResolver.resolve(name, params);
                }
            } else {
                property = directLPResolver.resolve(name, params);
                if (property == null) {
                    property = indirectLPResolver.resolve(name, params);
                }
            }
        } catch (LPResolver.AmbiguousPropertyError e) {
            errLog.emitAmbiguousPropertyNameError(parser, e.items, sourceName == null ? name : sourceName);    
        } 
        checkProperty(property, sourceName == null ? name : sourceName);
        return property;
    }
    
    public LP<?, ?> findLPByPropertyUsage(PropertyUsage pUsage) throws ScriptingErrorLog.SemanticErrorException {
        return findLPByPropertyUsage(pUsage, false, false);
    }

    public LP<?, ?> findLPByPropertyUsage(PropertyUsage pUsage, boolean isAbstract, boolean prioritizeNotEquals) throws ScriptingErrorLog.SemanticErrorException {
        return findLPByNameAndClasses(pUsage.name, pUsage.getSourceName(), getParamClasses(pUsage), isAbstract, prioritizeNotEquals);
    }
    
    public AbstractWindow findWindow(String name) throws ScriptingErrorLog.SemanticErrorException {
        AbstractWindow window = windowResolver.resolve(name);
        checkWindow(window, name);
        return window;
    }

    public FormEntity findForm(String name) throws ScriptingErrorLog.SemanticErrorException {
        NavigatorElement navigator = navigatorResolver.resolve(name);
        checkForm(navigator, name);
        return (FormEntity) navigator;
    }

    public List<ObjectEntity> findObjectEntities(FormEntity form, List<String> objectNames) throws ScriptingErrorLog.SemanticErrorException {
        List<ObjectEntity> objects = new ArrayList<>();
        for (int i = 0; i < objectNames.size(); i++) {
            objects.add(findObjectEntity(form, objectNames.get(i)));
        }
        return objects;
    }

    private List<FormEntity> findForms(List<String> names) throws ScriptingErrorLog.SemanticErrorException {
        List<FormEntity> forms = new ArrayList<>();
        for (String name : names) {
            forms.add(findForm(name));
        }
        return forms;
    }

    public Event createScriptedEvent(BaseEvent base, List<String> formIds, List<PropertyUsage> afterIds) throws ScriptingErrorLog.SemanticErrorException {
        return new Event(base, formIds != null ? new SessionEnvEvent(SetFact.fromJavaSet(new HashSet<>(findForms(formIds)))) : SessionEnvEvent.ALWAYS, afterIds == null? null : SetFact.fromJavaSet(findPropsByPropertyUsages(afterIds)));
    }

    public MetaCodeFragment findMetaCodeFragment(String name, int paramCnt) throws ScriptingErrorLog.SemanticErrorException {
        CompoundNameResolver<MetaCodeFragment, Integer> resolver = new CompoundNameResolver<>(new MetaCodeNameModuleFinder());
        MetaCodeFragment code = resolver.resolve(name, paramCnt);
        checkMetaCodeFragment(code, name);
        return code;
    }

    public NavigatorElement findNavigatorElement(String name) throws ScriptingErrorLog.SemanticErrorException {
        NavigatorElement element = navigatorResolver.resolve(name);
        checkNavigatorElement(element, name);
        return element;
    }

    public ImplementTable findTable(String name) throws ScriptingErrorLog.SemanticErrorException {
        ImplementTable table = tableResolver.resolve(name);
        checkTable(table, name);
        return table;
    }

    public void addScriptedGroup(String groupName, LocalizedString captionStr, String parentName) throws ScriptingErrorLog.SemanticErrorException {
        checkDuplicateGroup(groupName);
        LocalizedString caption = (captionStr == null ? LocalizedString.create(groupName) : captionStr);
        AbstractGroup parentGroup = (parentName == null ? null : findGroup(parentName));
        addAbstractGroup(groupName, caption, parentGroup);
    }

    public ScriptingFormEntity createScriptedForm(String formName, LocalizedString caption, DebugInfo.DebugPoint point, String icon,
                                                  ModalityType modalityType, int autoRefresh) throws ScriptingErrorLog.SemanticErrorException {
        checkDuplicateNavigatorElement(formName);
        caption = (caption == null ? LocalizedString.create(formName) : caption);

        String canonicalName = NavigatorElementCanonicalNameUtils.createNavigatorElementCanonicalName(getNamespace(), formName);

        ScriptingFormEntity form = new ScriptingFormEntity(this, new FormEntity(canonicalName, point.toString(), caption, icon, getVersion()));
        form.setModalityType(modalityType);
        form.setAutoRefresh(autoRefresh);

        return form;
    }

    public ScriptingFormView getFormDesign(String formName, LocalizedString caption, boolean custom) throws ScriptingErrorLog.SemanticErrorException {
        Version version = getVersion();

        FormEntity form = findForm(formName);
        FormView view;
        if (custom) {
            view = new FormView(form, version);
            form.setRichDesign(view, version);
        } else {
            view = form.getNFRichDesign(version);
        }
        
        if (view != null && caption != null) {
            view.setCaption(caption);
        }
        
        return new ScriptingFormView(view, this);
    }
    
    public void addScriptedForm(ScriptingFormEntity form, DebugInfo.DebugPoint point) {
        FormEntity formEntity = addFormEntity(form.getForm());
        formEntity.creationPath = point.toString();
        formEntity.finalizeInit(getVersion());
    }

    public ScriptingFormEntity getFormForExtending(String name) throws ScriptingErrorLog.SemanticErrorException {
        FormEntity form = findForm(name);
        return new ScriptingFormEntity(this, form);
    }

    public LCP addScriptedDProp(String returnClass, List<String> paramClasses, boolean sessionProp, boolean innerProp, boolean isLocalScope, LocalNestedType nestedType) throws ScriptingErrorLog.SemanticErrorException {
        ValueClass value = findClass(returnClass);
        ValueClass[] params = new ValueClass[paramClasses.size()];
        for (int i = 0; i < paramClasses.size(); i++) {
            params[i] = findClass(paramClasses.get(i));
        }

        if (sessionProp) {
            return addSDProp(LocalizedString.create(""), isLocalScope, value, nestedType, params);
        } else {
            assert nestedType == null;
            if (innerProp) {
                return addDProp(LocalizedString.create(""), value, params);
            } else {
                StoredDataProperty storedProperty = new StoredDataProperty(LocalizedString.create(""), params, value);
                return addProperty(null, new LCP<>(storedProperty));
            }
        }
    }

    public LP<?, ?> addScriptedAbstractProp(CaseUnionProperty.Type type, String returnClass, List<String> paramClasses, boolean isExclusive, boolean isChecked, boolean isLast) throws ScriptingErrorLog.SemanticErrorException {
        ValueClass value = findClass(returnClass);
        ValueClass[] params = new ValueClass[paramClasses.size()];
        for (int i = 0; i < paramClasses.size(); i++) {
            params[i] = findClass(paramClasses.get(i));
        }
        return addAUProp(null, false, isExclusive, isChecked, isLast, type, LocalizedString.create(""), value, params);
    }

    public LP addScriptedAbstractActionProp(ListCaseActionProperty.AbstractType type, List<String> paramClasses, boolean isExclusive, boolean isChecked, boolean isLast) throws ScriptingErrorLog.SemanticErrorException {
        ValueClass[] params = new ValueClass[paramClasses.size()];
        for (int i = 0; i < paramClasses.size(); i++) {
            params[i] = findClass(paramClasses.get(i));
        }
        LAP<?> result;
        if (type == ListCaseActionProperty.AbstractType.LIST) {
            result = addAbstractListAProp(isChecked, isLast, params);
        } else {
            result = addAbstractCaseAProp(type, isExclusive, isChecked, isLast, params);
        }
        return result;
    }

    public void addImplementationToAbstract(PropertyUsage abstractPropUsage, List<TypedParameter> context, LPWithParams implement, LPWithParams when) throws ScriptingErrorLog.SemanticErrorException {
        LP abstractLP = findJoinMainProp(abstractPropUsage, context, true);
        checkParamCount(abstractLP, context.size());
        checkImplementIsNotMain(abstractLP, implement.property);
        
        List<LPWithParams> allProps = new ArrayList<>();
        allProps.add(implement);
        if (when != null) {
            checkCalculationProperty(when.property);
            allProps.add(when);
        }
        List<Object> params = getParamsPlainList(allProps);

        List<ResolveClassSet> signature = getClassesFromTypedParams(context);
        if (abstractLP instanceof LCP) {
            checkCalculationProperty(implement.property);
            addImplementationToAbstractProp(abstractPropUsage.name, (LCP) abstractLP, signature, when != null, params);
        } else {
            checkActionProperty(implement.property);
            addImplementationToAbstractAction(abstractPropUsage.name, (LAP) abstractLP, signature, when != null, params);
        }
    }

    private void addImplementationToAbstractProp(String propName, LCP abstractProp, List<ResolveClassSet> signature, boolean isCase, List<Object> params) throws ScriptingErrorLog.SemanticErrorException {
        checkAbstractProperty(abstractProp, propName);
        CaseUnionProperty.Type type = ((CaseUnionProperty)abstractProp.property).getAbstractType();
        checkAbstractTypes(type == CaseUnionProperty.Type.CASE, isCase);

        try {
            abstractProp.addOperand(isCase, signature, getVersion(), params.toArray());
        } catch (ScriptParsingException e) {
            errLog.emitSimpleError(parser, e.getMessage());
        }
    }

    private void addImplementationToAbstractAction(String actionName, LAP abstractAction, List<ResolveClassSet> signature, boolean isCase, List<Object> params) throws ScriptingErrorLog.SemanticErrorException {
        checkAbstractAction(abstractAction, actionName);
        ListCaseActionProperty.AbstractType type = ((ListCaseActionProperty)abstractAction.property).getAbstractType();
        checkAbstractTypes(type == ListCaseActionProperty.AbstractType.CASE, isCase);

        try {
            abstractAction.addOperand(isCase, signature, getVersion(), params.toArray());
        } catch (ScriptParsingException e) {
            errLog.emitSimpleError(parser, e.getMessage());
        }
    }

    private int indexOf(List<TypedParameter> params, String paramName) {
        if (params == null) return -1;        
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i).paramName.equals(paramName)) {
                return i;    
            }
        }
        return -1;
    }
    
    public List<ResolveClassSet> createClassSetsFromClassNames(List<String> classNames) throws ScriptingErrorLog.SemanticErrorException {
        List<ResolveClassSet> params = new ArrayList<>();
        for (String className : classNames) {
            ValueClass cls = findClass(className);
            params.add(cls.getResolveSet());
        }
        return params;
    }
    
    public int getParamIndex(TypedParameter param, List<TypedParameter> context, boolean dynamic, boolean insideRecursion) throws ScriptingErrorLog.SemanticErrorException {
        String paramName = param.paramName;
        int index = indexOf(context, paramName);
        
        if (index < 0 && paramName.startsWith("$")) {
            if (param.cls != null) {
                errLog.emitParamClassNonDeclarationError(parser, paramName);
            } else if (!insideRecursion) {
                errLog.emitRecursiveParamsOutideRecursionError(parser, paramName);
            } else if (indexOf(context, paramName.substring(1)) < 0) {
                errLog.emitParamNotFoundError(parser, paramName.substring(1));
            }
        }
        
        if (index >= 0 && param.cls != null && context != null) {
            if (context.get(index).cls != null) {
                errLog.emitParamClassRedefinitionError(parser, paramName);
            } else {
                errLog.emitParamClassNonDeclarationError(parser, paramName);
            }
        }
        if (index < 0 && context != null && (dynamic || paramName.startsWith("$") && insideRecursion)) {
            if (paramName.startsWith("$") && insideRecursion) {
                param.cls = context.get(indexOf(context, paramName.substring(1))).cls;            
            }
            index = context.size();
            context.add(param);
        }
        if (index < 0) {
            errLog.emitParamNotFoundError(parser, paramName);
        }
        return index;
    }

    public static class LPWithParams {
        public LP property; // nullable
        public List<Integer> usedParams;

        public LPWithParams(LP property, List<Integer> usedParams) {
            this.property = property;
            this.usedParams = usedParams;
        }

        @Override
        public String toString() {
            return String.format("[%s, %s]", property, usedParams);
        }
    }

    private boolean isTrivialParamList(List<Object> paramList) {
        int index = 1;
        for (Object param : paramList) {
            if (!(param instanceof Integer) || ((Integer)param) != index) return false;
            ++index;
        }
        return true;
    }

    private List<String> getParamNamesFromTypedParams(List<TypedParameter> params) {
        List<String> paramNames = new ArrayList<>();
        for (TypedParameter param : params) {
            paramNames.add(param.paramName);
        }
        return paramNames;        
    }

    public List<ResolveClassSet> getClassesFromTypedParams(List<TypedParameter> params) {
        List<ResolveClassSet> paramClasses = new ArrayList<>();
        for (TypedParameter param : params) {
            if (param.cls == null) {
                paramClasses.add(null);
            } else {
                paramClasses.add(param.cls.getResolveSet());
            }
        }
        return paramClasses;
    }
    
    public LP addSettingsToProperty(LP baseProperty, String name, LocalizedString caption, List<TypedParameter> params, List<ResolveClassSet> signature, 
                                      String groupName, boolean isPersistent, boolean isComplex, boolean noHint, String tableName, BooleanDebug notNull, 
                                      BooleanDebug notNullResolve, Event notNullEvent, String annotation, boolean isLoggable) throws ScriptingErrorLog.SemanticErrorException {
        LP property = baseProperty;
        checkDuplicateProperty(name, signature);

        property.property.annotation = annotation;

        List<String> paramNames = getParamNamesFromTypedParams(params);
        checkDistinctParameters(paramNames);
        checkNamedParams(property, paramNames);
        
        // Если объявление имеет вид f(x, y) = g(x, y), то нужно дополнительно обернуть свойство g в join
        if (property.property.getSID().equals(lastOpimizedJPropSID)) {
            property = addJProp(false, LocalizedString.create(""), (LCP) property, BaseUtils.consecutiveList(property.property.interfaces.size(), 1).toArray());
        }
        
        makePropertyPublic(property, name, signature);
        
        AbstractGroup group = (groupName == null ? null : findGroup(groupName));
        property.property.caption = (caption == null ? LocalizedString.create(name) : caption);
        addPropertyToGroup(property.property, group);

        ImplementTable targetTable = null;
        if (tableName != null) {
            targetTable = findTable(tableName);
            if (!targetTable.equalClasses(((LCP<?>)property).property.getOrderTableInterfaceClasses(ClassType.storedPolicy))) {
                // todo : проверка неправильная - должна быть на ClassWhere
                //errLog.emitWrongClassesForTable(parser, name, tableName);
            }
        }
        if (property.property instanceof StoredDataProperty) {
            ((StoredDataProperty)property.property).markStored(baseLM.tableFactory, targetTable);
        } else if (isPersistent && (property.property instanceof AggregateProperty)) {
            addPersistent((LCP) property, targetTable);
        }

        if(isComplex)
            ((LCP<?>)property).property.complex = true;

        if(noHint)
            ((LCP<?>)property).property.noHint = true;

        if (notNull != null) {
            setNotNull((LCP)property, notNull.debugPoint, notNullEvent, 
                    notNullResolve != null ? ListFact.singleton(new PropertyFollowsDebug(false, true, notNullResolve.debugPoint)) :
                                             ListFact.<PropertyFollowsDebug>EMPTY());
        }

        if (property.property instanceof CalcProperty) {
            
            if (Settings.get().isCheckAlwaysNull()) {
                checkPropertyValue(property);
                if (!alwaysNullProperties.isEmpty()) {
                    showAlwaysNullErrors();
                }
            }

//            if (Settings.get().isCheckClassWhere()) {
//                checkClassWhere((LCP) property, name);
//            }
        }
        makeLoggable(baseProperty, isLoggable);
        return property;
    }

    private void showAlwaysNullErrors() throws ScriptingErrorLog.SemanticErrorException {
        String errorMessage = "";
        for (Property property : alwaysNullProperties.keySet()) {
            if (!errorMessage.isEmpty()) {
                errorMessage += "\n";
            }
            String location = alwaysNullProperties.get(property);
            errorMessage += "[error]:\t" + location + " property '" + property.getName() + "' is always NULL";
        }
        alwaysNullProperties.clear();
        errLog.emitSemanticError(errorMessage, new ScriptingErrorLog.SemanticErrorException(parser.getCurrentParser().input));
    }

    public void addToContextMenuFor(LP onContextAction, LocalizedString contextMenuCaption, PropertyUsage mainPropertyUsage) throws ScriptingErrorLog.SemanticErrorException {
        assert mainPropertyUsage != null;

        checkActionProperty(onContextAction);

        LP<?, ?> mainProperty = findLPByPropertyUsage(mainPropertyUsage);
        LAP onContextLAP = (LAP) onContextAction;
        onContextLAP.addToContextMenuFor(mainProperty, contextMenuCaption);
        ((ActionProperty) onContextLAP.property).checkReadOnly = false;
    }

    public void setAsEditActionFor(LP onEditAction, String editActionSID, PropertyUsage mainPropertyUsage) throws ScriptingErrorLog.SemanticErrorException {
        assert mainPropertyUsage != null;

        checkActionProperty(onEditAction);

        LP<?, ?> mainProperty = findLPByPropertyUsage(mainPropertyUsage);
        LAP onEditLAP = (LAP) onEditAction;
        onEditLAP.setAsEditActionFor(editActionSID, mainProperty);
    }

    public void setForceViewType(LP property, ClassViewType viewType) {
        property.setForceViewType(viewType);
    }

    public void setFixedCharWidth(LP property, Integer fixedCharWidth) {
        if (fixedCharWidth != null && fixedCharWidth > 0)
            property.setFixedCharWidth(fixedCharWidth);
    }

    public void setMinCharWidth(LP property, Integer minCharWidth) {
        if (minCharWidth != null)
            property.setMinimumCharWidth(minCharWidth);
    }

    public void setMaxCharWidth(LP property, Integer maxCharWidth) {
        if (maxCharWidth != null)
            property.setMaximumCharWidth(maxCharWidth);
    }

    public void setPrefCharWidth(LP property, Integer prefCharWidth) {
        if (prefCharWidth != null)
            property.setPreferredCharWidth(prefCharWidth);
    }

    public void setImage(LP property, String path) {
        property.setImage(path);
    }

    public void setEditKey(LP property, String code, Boolean showEditKey) {
        property.setEditKey(KeyStroke.getKeyStroke(code));
        if (showEditKey != null)
            property.setShowEditKey(showEditKey);
    }

    public void setAutoset(LP property, boolean autoset) {
        ((LCP)property).setAutoset(autoset);
    }

    public void setAskConfirm(LP property, boolean askConfirm) {
        property.setAskConfirm(askConfirm);
    }

    public void setRegexp(LP property, String regexp, String regexpMessage) {
        property.setRegexp(regexp);
        if (regexpMessage != null) {
            property.setRegexpMessage(regexpMessage);
        }
    }

    public void makeLoggable(LP property, boolean isLoggable) throws ScriptingErrorLog.SemanticErrorException {
        if (isLoggable && property != null) {
            checkCalculationProperty(property);
            ((LCP) property).makeLoggable(this, BL.systemEventsLM);
        }
    }

    public void setEchoSymbols(LP property) {
        property.setEchoSymbols(true);
    }

    public void setAggProp(LP property) {
        ((CalcProperty)property.property).aggProp = true;
    }

    public void setScriptedEditAction(LP property, String actionType, LPWithParams action) {
        List<Object> params = getParamsPlainList(Collections.singletonList(action));
        ImList<ActionPropertyMapImplement<?, PropertyInterface>> actionImplements = readActionImplements(((LP<PropertyInterface, ?>)property).listInterfaces, params.toArray());
        property.property.setEditAction(actionType, actionImplements.get(0));
    }

    public void setEventId(LP property, String id) {
        property.property.drawOptions.setEventID(id);
    }
    
    public List<ResolveClassSet> getParamClasses(PropertyUsage usage) throws ScriptingErrorLog.SemanticErrorException {
        List<ValueClass> valueClasses = getValueClasses(usage);
        if (valueClasses == null) {
            return null;
        }
        
        List<ResolveClassSet> classes = new ArrayList<>();
        for (ValueClass valueClass : valueClasses) {
            if (valueClass == null) {
                classes.add(null);
            } else {
                classes.add(valueClass.getResolveSet());
            }
        }
        return classes;
    }

    public List<ValueClass> getValueClasses(PropertyUsage usage) throws ScriptingErrorLog.SemanticErrorException {
        if (usage.classNames == null) {
            return null;
        }

        List<ValueClass> classes = new ArrayList<>();
        for (String className : usage.classNames) {
            if (className.equals(PropertyCanonicalNameUtils.UNKNOWNCLASS)) {
                classes.add(null);
            } else {
                ValueClass cls = findClass(className);
                classes.add(cls);
            }
        }
        return classes;
    }
    
    private <T extends PropertyInterface> List<ResolveClassSet> getParamClassesByParamProperties(List<LPWithParams> paramProps, List<TypedParameter> params) {
        List<ResolveClassSet> classes = new ArrayList<>();
        for (LPWithParams paramProp : paramProps) {
            if (paramProp.property != null) {
                LCP<T> lcp = (LCP<T>)paramProp.property;
                List<ResolveClassSet> usedClasses = getUsedClasses(params, paramProp.usedParams);
                classes.add(lcp.getResolveClassSet(usedClasses));
            } else {
                TypedParameter param = params.get(paramProp.usedParams.get(0));
                if (param.cls == null) {
                    classes.add(null);
                } else {
                    classes.add(param.cls.getResolveSet());
                }
            }
        }
        return classes;
    }
    
    public List<ResolveClassSet> getSignatureForGProp(List<LPWithParams> paramProps, List<TypedParameter> params) {
        return getParamClassesByParamProperties(paramProps, params);
    }

    private LP findJoinMainProp(String mainPropName, String sourceName, List<LPWithParams> paramProps, List<TypedParameter> context) throws ScriptingErrorLog.SemanticErrorException {
        List<ResolveClassSet> classes = getParamClassesByParamProperties(paramProps, context);
        return findLPByNameAndClasses(mainPropName, sourceName, classes);
    }
    
    private LP findJoinMainProp(PropertyUsage mainProp, List<LPWithParams> paramProps, List<TypedParameter> context) throws ScriptingErrorLog.SemanticErrorException {
        if (mainProp.classNames != null) {
            return findLPByPropertyUsage(mainProp);
        } else {
            return findJoinMainProp(mainProp.name, mainProp.getSourceName(), paramProps, context);
        }
    }

    private LP findJoinMainProp(PropertyUsage mainProp, List<TypedParameter> params) throws ScriptingErrorLog.SemanticErrorException {
        return findJoinMainProp(mainProp, params, false);
    }
    
    private LP findJoinMainProp(PropertyUsage mainProp, List<TypedParameter> params, boolean onlyAbstract) throws ScriptingErrorLog.SemanticErrorException {
        if (mainProp.classNames != null) {
            return findLPByPropertyUsage(mainProp, onlyAbstract, false); 
        } else {
            return findLPByNameAndClasses(mainProp.name, mainProp.getSourceName(), getClassesFromTypedParams(params), onlyAbstract, true);
        }
    }
    
    public LPWithParams addScriptedJProp(boolean user, PropertyUsage pUsage, List<LPWithParams> paramProps, List<TypedParameter> params) throws ScriptingErrorLog.SemanticErrorException {
        LP mainProp = findJoinMainProp(pUsage, paramProps, params);
        return addScriptedJProp(user, mainProp, paramProps);
    }
    
    public LPWithParams addScriptedJProp(LP mainProp, List<LPWithParams> paramProps) throws ScriptingErrorLog.SemanticErrorException {
        return addScriptedJProp(false, mainProp, paramProps);
    }
    
    public LPWithParams addScriptedJProp(boolean user, LP mainProp, List<LPWithParams> paramProps) throws ScriptingErrorLog.SemanticErrorException {
        checkCalculationProperty(mainProp);
        checkParamCount(mainProp, paramProps.size());
        List<Object> resultParams = getParamsPlainList(paramProps);
        LP prop;
        if (isTrivialParamList(resultParams)) {
            prop = mainProp;
            lastOpimizedJPropSID = mainProp.property.getSID();
        } else {
            prop = addJProp(user, LocalizedString.create(""), (LCP) mainProp, resultParams.toArray());
        }
        return new LPWithParams(prop, mergeAllParams(paramProps));
    }

    private LCP getRelationProp(String op) {
        switch (op) {
            case "==":
                return baseLM.equals2;
            case "!=":
                return baseLM.diff2;
            case ">":
                return baseLM.greater2;
            case "<":
                return baseLM.less2;
            case ">=":
                return baseLM.groeq2;
            case "<=":
                return baseLM.lsoeq2;
        }
        assert false;
        return null;
    }

    private LCP getArithProp(String op) {
        switch (op) {
            case "+":
                return baseLM.sum;
            case "-":
                return baseLM.subtract;
            case "*":
                return baseLM.multiply;
            case "/":
                return baseLM.divide;
        }
        assert false;
        return null;
    }

    public LPWithParams addScriptedEqualityProp(String op, LPWithParams leftProp, LPWithParams rightProp) throws ScriptingErrorLog.SemanticErrorException {
        return addScriptedJProp(getRelationProp(op), asList(leftProp, rightProp));
    }

    public LPWithParams addScriptedRelationalProp(String op, LPWithParams leftProp, LPWithParams rightProp) throws ScriptingErrorLog.SemanticErrorException {
        return addScriptedJProp(getRelationProp(op), asList(leftProp, rightProp));
    }

    public LPWithParams addScriptedOverrideProp(List<LPWithParams> properties, boolean isExclusive) throws ScriptingErrorLog.SemanticErrorException {
        if (isExclusive) {
            return addScriptedUProp(Union.EXCLUSIVE, properties, "EXCLUSIVE");
        } else {
            return addScriptedUProp(Union.OVERRIDE, properties, "OVERRIDE");
        }
    }

    private boolean isLogical(LP property) {
        if(property == null)
            return false;

        Type type = property.property.getType();
        return type != null && type.equals(LogicalClass.instance);
    }

    private LPWithParams toLogical(LPWithParams property) throws ScriptingErrorLog.SemanticErrorException {
        return addScriptedJProp(and(false), Arrays.asList(new LPWithParams(baseLM.vtrue, new ArrayList<Integer>()), property));
    }

    public LPWithParams addScriptedIfProp(List<LPWithParams> properties) throws ScriptingErrorLog.SemanticErrorException {
        LPWithParams curLP = properties.get(0);
        if (properties.size() > 1) {
            boolean[] notsArray = new boolean[properties.size() - 1];
            Arrays.fill(notsArray, false);
            if (properties.get(0).property != null) {
                checkCalculationProperty(properties.get(0).property);
            }
            curLP = addScriptedJProp(and(notsArray), properties);
        }
        return curLP;
    }

    public LPWithParams addScriptedOrProp(List<LPWithParams> properties) throws ScriptingErrorLog.SemanticErrorException {
        LPWithParams res = properties.get(0);
        if (properties.size() > 1) {
            for (LPWithParams prop : properties) {
                if (!isLogical(prop.property)) {
                    prop.property = toLogical(prop).property;
                }
            }
            res = addScriptedUProp(Union.OVERRIDE, properties, "OR");
        }
        return res;
    }

    public LPWithParams addScriptedXorProp(List<LPWithParams> properties) throws ScriptingErrorLog.SemanticErrorException {
        LPWithParams res = properties.get(0);
        if (properties.size() > 1) {
            for (LPWithParams prop : properties) {
                if (!isLogical(prop.property)) {
                    prop.property = toLogical(prop).property;
                }
            }
            res = addScriptedUProp(Union.XOR, properties, "XOR");
        }
        return res;
    }

    public LPWithParams addScriptedAndProp(List<LPWithParams> properties) throws ScriptingErrorLog.SemanticErrorException {
        LPWithParams curLP = properties.get(0);
        if (properties.size() > 1) {
            boolean[] notsArray = new boolean[properties.size() - 1];
            Arrays.fill(notsArray, false);
            if (properties.get(0).property != null) {
                checkCalculationProperty(properties.get(0).property);
            }
            if (!isLogical(properties.get(0).property)) {
                properties.get(0).property = toLogical(properties.get(0)).property;
            }
            curLP = addScriptedJProp(and(notsArray), properties);
        }
        return curLP;
    }

    public LPWithParams addScriptedIfElseUProp(LPWithParams ifProp, LPWithParams thenProp, LPWithParams elseProp) throws ScriptingErrorLog.SemanticErrorException {
        assert !(thenProp.property instanceof LAP) && (elseProp == null || !(elseProp.property instanceof LAP));
        List<LPWithParams> lpParams = new ArrayList<>();
        lpParams.add(addScriptedJProp(and(false), asList(thenProp, ifProp)));
        if (elseProp != null) {
            lpParams.add(addScriptedJProp(and(true), asList(elseProp, ifProp)));
        }
        return addScriptedUProp(Union.EXCLUSIVE, lpParams, "IF");
    }

    public LPWithParams addScriptedCaseUProp(List<LPWithParams> whenProps, List<LPWithParams> thenProps, LPWithParams elseProp, boolean isExclusive) {
        assert whenProps.size() > 0 && whenProps.size() == thenProps.size();

        List<LPWithParams> caseParamProps = new ArrayList<>();
        for (int i = 0; i < whenProps.size(); i++) {
            caseParamProps.add(whenProps.get(i));
            caseParamProps.add(thenProps.get(i));
        }
        if (elseProp != null) {
            caseParamProps.add(elseProp);
        }

        LP caseProp = addCaseUProp(null, false, LocalizedString.create(""), isExclusive, getParamsPlainList(caseParamProps).toArray());
        return new LPWithParams(caseProp, mergeAllParams(caseParamProps));
    }

    public LPWithParams addScriptedMultiProp(List<LPWithParams> properties, boolean isExclusive) throws ScriptingErrorLog.SemanticErrorException {
        if (isExclusive) {
            return addScriptedUProp(Union.CLASS, properties, "MULTI");
        } else {
            return addScriptedUProp(Union.CLASSOVERRIDE, properties, "MULTI");
        }
    }

    public LPWithParams addScriptedFileAProp(FileActionType actionType, LPWithParams property, LPWithParams fileNameProp) {
        LAP<?> res;
        switch (actionType) {
            case LOAD:
                res = addLFAProp((LCP) property.property);
                break;
            case OPEN:
                res = addOFAProp((LCP) property.property);
                break;
            default: // SAVE
                res = addSFAProp((LCP) property.property, fileNameProp != null ? (LCP) fileNameProp.property : null);
        }
        return new LPWithParams(res, property.usedParams);
    }

    public LP addScriptedCustomActionProp(String javaClassName, List<String> classes, boolean allowNullValue) throws ScriptingErrorLog.SemanticErrorException {
        try {
            ActionProperty instance;
            if (classes == null || classes.isEmpty()) {
                instance = (ActionProperty) Class.forName(javaClassName).getConstructor(this.getClass()).newInstance(this);
            } else {
                ValueClass[] classList = new ValueClass[classes.size()];
                for (int i = 0; i < classes.size(); i++) {
                    classList[i] = findClass(classes.get(i));
                }
                instance = (ActionProperty) Class.forName(javaClassName).getConstructor(new Class[] {this.getClass(), ValueClass[].class}).newInstance(this, classList);
            }
            if (instance instanceof ExplicitActionProperty && allowNullValue) {
                ((ExplicitActionProperty) instance).allowNullValue = true;
            }
            return baseLM.addAProp(null, instance);
        } catch (ClassNotFoundException e) {
            errLog.emitClassNotFoundError(parser, javaClassName);
        } catch (Exception e) {
            errLog.emitCreatingClassInstanceError(parser, javaClassName);
        }
        return null;
    }

    public LP addScriptedCustomActionProp(String code, boolean allowNullValue) throws ScriptingErrorLog.SemanticErrorException {
        String script = "";
        try {

            script = code.substring(1, code.length() - 1); //remove brackets

            String javaClass = "import lsfusion.server.data.SQLHandledException;\n" +
                    "import lsfusion.server.logics.property.ClassPropertyInterface;\n" +
                    "import lsfusion.server.logics.property.ExecutionContext;\n" +
                    "import lsfusion.server.logics.scripted.ScriptingActionProperty;\n" +
                    "import lsfusion.server.logics.scripted.ScriptingLogicsModule;\n" +
                    "\n" +
                    "import java.sql.SQLException;\n" +
                    "\n" +
                    "public class ExecuteActionProperty extends ScriptingActionProperty {\n" +
                    "\n" +
                    "    public ExecuteActionProperty(ScriptingLogicsModule LM) {\n" +
                    "        super(LM);\n" +
                    "    }\n" +
                    "\n" +
                    "    @Override\n" +
                    "    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {\n" +
                    "        try {\n" +
                    script +
                    "        } catch (Exception e) {\n" +
                    "            e.printStackTrace();\n" +
                    "        }\n" +
                    "    }\n" +
                    "}";

            SimpleCompiler sc = new SimpleCompiler();
            sc.cook(javaClass);
            Class<?> executeClass = sc.getClassLoader().loadClass("ExecuteActionProperty");

            ActionProperty instance = (ActionProperty) executeClass.getConstructor(ScriptingLogicsModule.class).newInstance(this);
            if (instance instanceof ExplicitActionProperty && allowNullValue) {
                ((ExplicitActionProperty) instance).allowNullValue = true;
            }
            return baseLM.addAProp(null, instance);
        } catch (Exception e) {
            errLog.emitCreatingClassInstanceError(parser, script);
        }
        return null;
    }

    public LPWithParams addScriptedEmailProp(LPWithParams fromProp,
                                             LPWithParams subjProp,
                                             List<Message.RecipientType> recipTypes,
                                             List<LPWithParams> recipProps,
                                             List<String> forms,
                                             List<FormStorageType> formTypes,
                                             List<OrderedMap<String, LPWithParams>> mapObjects,
                                             List<LPWithParams> attachNames,
                                             List<AttachmentFormat> attachFormats,
                                             List<LPWithParams> attachFileNames,
                                             List<LPWithParams> attachFiles,
                                             List<LPWithParams> inlineTexts) throws ScriptingErrorLog.SemanticErrorException {

        List<LPWithParams> allProps = new ArrayList<>();

        if (fromProp != null) {
            allProps.add(fromProp);
        }
        allProps.add(subjProp);
        allProps.addAll(recipProps);

        List<ObjectEntity> formObjects = new ArrayList<>(Collections.<ObjectEntity>nCopies(allProps.size(), null)); 
        for (int i = 0; i < forms.size(); ++i) {
            FormEntity form = findForm(forms.get(i));
            for (Map.Entry<String, LPWithParams> e : mapObjects.get(i).entrySet()) {
                allProps.add(e.getValue());
                formObjects.add(findObjectEntity(form, e.getKey()));
            }
            
            if (formTypes.get(i) == FormStorageType.ATTACH && attachNames.get(i) != null) {
                allProps.add(attachNames.get(i));
                formObjects.add(null);
            }
        }
        
        for (int i = 0; i < attachFileNames.size(); i++) {
            if (attachFileNames.get(i) != null) {
                allProps.add(attachFileNames.get(i));
            }
            allProps.add(attachFiles.get(i));
        }

        for (int i = 0; i < inlineTexts.size(); i++) {
            allProps.add(inlineTexts.get(i));
        }
        
        formObjects.addAll(Collections.<ObjectEntity>nCopies(allProps.size() - formObjects.size(), null));        

        Object[] allParams = getParamsPlainList(allProps).toArray();

        ImOrderSet<PropertyInterface> tempContext = genInterfaces(getIntNum(allParams));
        ValueClass[] eaClasses = CalcProperty.getCommonClasses(tempContext, readCalcImplements(tempContext, allParams).getCol(), formObjects);

        LAP<ClassPropertyInterface> eaPropLP = BL.emailLM.addEAProp(null, LocalizedString.create(""), eaClasses, null, null);
        SendEmailActionProperty eaProp = (SendEmailActionProperty) eaPropLP.property;

        ImList<CalcPropertyInterfaceImplement<ClassPropertyInterface>> allImplements = readCalcImplements(eaPropLP.listInterfaces, allParams);

        int i = 0;
        if (fromProp != null) {
            eaProp.setFromAddressAccount(allImplements.get(i++));
        } else {
            // по умолчанию используем стандартный fromAddressAccount
            eaProp.setFromAddressAccount(new CalcPropertyMapImplement((CalcProperty) BL.emailLM.findProperty("fromAddressDefaultNotificationAccount[]").property));
        }
        eaProp.setSubject(allImplements.get(i++));

        for (Message.RecipientType recipType : recipTypes) {
            eaProp.addRecipient(allImplements.get(i++), recipType);
        }

        for (int j = 0; j < forms.size(); ++j) {
            String formName = forms.get(j);
            FormStorageType formType = formTypes.get(j);
            FormEntity form = findForm(formName);

            Map<ObjectEntity, CalcPropertyInterfaceImplement<ClassPropertyInterface>> objectsImplements = new HashMap<>();
            for (Map.Entry<String, LPWithParams> entry : mapObjects.get(j).entrySet()) {
                objectsImplements.put(findObjectEntity(form, entry.getKey()), allImplements.get(i++));
            }

            if (formType == FormStorageType.ATTACH) {
                CalcPropertyInterfaceImplement<ClassPropertyInterface> attachNameProp = attachNames.get(j) != null ? allImplements.get(i++) : null;
                eaProp.addAttachmentForm(form, attachFormats.get(j), objectsImplements, attachNameProp);
            } else {
                eaProp.addInlineForm(form, objectsImplements);
            }
        }

        for (LPWithParams fileName : attachFileNames) {
            CalcPropertyInterfaceImplement<ClassPropertyInterface> attachFileName = fileName != null ? allImplements.get(i++) : null;
            eaProp.addAttachmentFile(attachFileName, allImplements.get(i++));
        }

        for(int j = 0; j < inlineTexts.size(); j++)
            eaProp.addInlineText(allImplements.get(i++));

        return new LPWithParams(eaPropLP, mergeAllParams(allProps));
    }

    public LPWithParams addScriptedAdditiveOrProp(List<String> operands, List<LPWithParams> properties) {
        assert operands.size() + 1 == properties.size();
        
        LPWithParams res = properties.get(0);
        if (operands.size() > 0) {
            List<Object> resultParams;
            int[] coeffs = new int[properties.size()];
            for (int i = 0; i < coeffs.length; i++) {
                if (i == 0 || operands.get(i-1).equals("(+)")) {
                    coeffs[i] = 1;
                } else {
                    coeffs[i] = -1;
                }
            }
            resultParams = getParamsPlainList(properties);
            res = new LPWithParams(addUProp(null, LocalizedString.create(""), Union.SUM, null, coeffs, resultParams.toArray()), mergeAllParams(properties));
        }
        return res;    
    }
    
    public LPWithParams addScriptedAdditiveProp(List<String> operands, List<LPWithParams> properties) throws ScriptingErrorLog.SemanticErrorException {
        assert operands.size() + 1 == properties.size();

        LPWithParams sumLP = properties.get(0);
        for (int i = 1; i < properties.size(); i++) {
            LPWithParams currLP = properties.get(i);
            sumLP = addScriptedJProp(getArithProp(operands.get(i-1)), asList(sumLP, currLP));
        }
        return sumLP;
    }


    public LPWithParams addScriptedMultiplicativeProp(List<String> operands, List<LPWithParams> properties) throws ScriptingErrorLog.SemanticErrorException {
        assert operands.size() + 1 == properties.size();

        LPWithParams curLP = properties.get(0);
        for (int i = 1; i < properties.size(); i++) {
            String op = operands.get(i-1);
            curLP = addScriptedJProp(getArithProp(op), asList(curLP, properties.get(i)));
        }
        return curLP;
    }

    public LPWithParams addScriptedUnaryMinusProp(LPWithParams prop) throws ScriptingErrorLog.SemanticErrorException {
        return addScriptedJProp(baseLM.minus, Collections.singletonList(prop));
    }

    public LPWithParams addScriptedNotProp(LPWithParams prop) throws ScriptingErrorLog.SemanticErrorException {
        return addScriptedJProp(not(), Collections.singletonList(prop));
    }

    public LPWithParams addScriptedCastProp(String typeName, LPWithParams prop) throws ScriptingErrorLog.SemanticErrorException {
        ValueClass cls = findClass(typeName);

        //cls всегда будет DataClass из-за грамматики
        assert cls instanceof DataClass;

        return addScriptedJProp(addCastProp((DataClass) cls), Collections.singletonList(prop));
    }

    private boolean doesExtendContext(List<LPWithParams> list, List<LPWithParams> orders) {
        Set<Integer> listContext = new HashSet<>();
        for(LPWithParams lp : list)
            if(lp.property != null)
                listContext.addAll(lp.usedParams);
        return !listContext.containsAll(mergeAllParams(orders));
    }

    private List<Integer> mergeAllParams(List<LPWithParams> lpList) {
        Set<Integer> s = new TreeSet<>();
        for (LPWithParams mappedLP : lpList) {
            s.addAll(mappedLP.usedParams);
        }
        return new ArrayList<>(s);
    }

    private List<Integer> mergeIntLists(List<List<Integer>> lists) {
        Set<Integer> s = new TreeSet<>();
        for (List<Integer> list : lists) {
            s.addAll(list);
        }
        return new ArrayList<>(s);
    }

    public LPWithParams addScriptedListAProp(List<LPWithParams> properties, List<LP> localProps) {
        List<Object> resultParams = getParamsPlainList(properties);

        MExclSet<Pair<LP, List<ResolveClassSet>>> mDebugLocals = null;
        if(debugger.isEnabled()) {
            mDebugLocals = SetFact.mExclSet(localProps.size());
        }
        MSet<SessionDataProperty> mLocals = SetFact.mSet();
        for (LP<?, ?> localProp : localProps) {
            List<ResolveClassSet> localSignature = propClasses.remove(localProp);
            removeModuleLP(localProp);
            
            if(mDebugLocals != null)
                mDebugLocals.exclAdd(new Pair<LP, List<ResolveClassSet>>(localProp, localSignature));

            mLocals.add((SessionDataProperty) localProp.property);
        }

        LAP<?> listLP = addListAProp(mLocals.immutable(), resultParams.toArray());

        if(mDebugLocals != null) {
            listLP.property.setDebugLocals(mDebugLocals.immutable());
        }

        List<Integer> usedParams = mergeAllParams(properties);
        return new LPWithParams(listLP, usedParams);
    }

    public LPWithParams addScriptedNewSessionAProp(LPWithParams action, List<PropertyUsage> migrateSessionProps, boolean migrateAllSessionProps,
                                                   boolean isNested, boolean singleApply, boolean newSQL) throws ScriptingErrorLog.SemanticErrorException {
        LAP<?> sessionLP = addNewSessionAProp(null, (LAP) action.property, isNested, singleApply, newSQL, getMigrateProps(migrateSessionProps, migrateAllSessionProps));
        return new LPWithParams(sessionLP, action.usedParams);
    }

    public DataClass getInputDataClass(String paramName, List<TypedParameter> context, String typeId, LPWithParams oldValue, boolean insideRecursion) throws ScriptingErrorLog.SemanticErrorException {
        DataClass requestDataClass;
        if(typeId != null) {
            requestDataClass = ClassCanonicalNameUtils.getScriptedDataClass(typeId);
        } else {
            ValueClass valueClass = oldValue.property.property.getValueClass(ClassType.valuePolicy);
            checkInputDataClass(valueClass);
            requestDataClass = (DataClass) valueClass;
        }

        if(paramName != null)
            getParamIndex(new TypedParameter(requestDataClass, paramName), context, true, insideRecursion);
        return requestDataClass;
    }

    public LPWithParams addScriptedInputAProp(DataClass requestDataClass, LPWithParams oldValue, PropertyUsage targetProp, LPWithParams doAction, LPWithParams elseAction, List<TypedParameter> oldContext, List<TypedParameter> newContext, boolean assign, DebugInfo.DebugPoint assignDebugPoint) throws ScriptingErrorLog.SemanticErrorException {
        LCP tprop = getInputProp(targetProp, requestDataClass, null);

        LAP property = addInputAProp(requestDataClass, (LCP<?>) tprop != null ? ((LCP<?>) tprop).property : null);

        if(oldValue == null)
            oldValue = new LPWithParams(baseLM.vnull, new ArrayList<Integer>());
        LPWithParams inputAction = addScriptedJoinAProp(property, Collections.singletonList(oldValue));

        return proceedDoClause(doAction, elseAction, oldContext, newContext, ListFact.singleton(tprop), inputAction,
                ListFact.singleton(assign ? new Pair<>(oldValue, assignDebugPoint) : null));
    }


    public LPWithParams addScriptedRequestAProp(LPWithParams requestAction, LPWithParams doAction, LPWithParams elseAction) throws ScriptingErrorLog.SemanticErrorException {
        List<LPWithParams> propParams = new ArrayList<>();
        propParams.add(requestAction);
        propParams.add(doAction);
        if(elseAction != null)
            propParams.add(elseAction);

        List<Integer> allParams = mergeAllParams(propParams);
        LP result = addRequestAProp(null, LocalizedString.create(""), getParamsPlainList(propParams).toArray());
        return new LPWithParams(result, allParams);
    }

    public LPWithParams addScriptedActiveFormAProp(String formName) throws ScriptingErrorLog.SemanticErrorException {
        FormEntity form = findForm(formName);
        return new LPWithParams(addAProp(null, new IsActiveFormActionProperty(LocalizedString.create(""), form, baseLM.getIsActiveFormProperty())), new ArrayList<Integer>());
    }

    public LPWithParams addScriptedActivateAProp(String formName, String componentName) throws ScriptingErrorLog.SemanticErrorException {
        FormEntity form = findForm(formName);
        ComponentView component = null;
        if (componentName != null) {
            component = form.getNFRichDesign(getVersion()).getComponentBySID(componentName, getVersion());
            checkComponent(component, componentName); 
        }
        return new LPWithParams(addAProp(null, new ActivateActionProperty(LocalizedString.create(""), form, component)), new ArrayList<Integer>());
    }

    public LCP addLocalDataProperty(String name, String returnClassName, List<String> paramClassNames, LocalNestedType nestedType) throws ScriptingErrorLog.SemanticErrorException {
        List<ResolveClassSet> signature = new ArrayList<>();
        for (String className : paramClassNames) {
            signature.add(findClass(className).getResolveSet());
        }
        checkDuplicateProperty(name, signature);

        LCP res = addScriptedDProp(returnClassName, paramClassNames, true, false, true, nestedType);
        makePropertyPublic(res, name, signature);
        return res;
    }

    public LP addWatchLocalDataProperty(LP lp, List<ResolveClassSet> signature) {
        assert lp.property instanceof SessionDataProperty;
        addModuleLP(lp);
        propClasses.put(lp, signature);
        return lp; 
    }
    
    public LPWithParams addScriptedJoinAProp(PropertyUsage pUsage, List<LPWithParams> properties, List<TypedParameter> params) throws ScriptingErrorLog.SemanticErrorException {
        LP mainProp = findJoinMainProp(pUsage, properties, params);    
        return addScriptedJoinAProp(mainProp, properties);                        
    }
    
    public LPWithParams addScriptedJoinAProp(LP mainProp, List<LPWithParams> properties) throws ScriptingErrorLog.SemanticErrorException {
        checkActionProperty(mainProp);
        checkParamCount(mainProp, properties.size());

        List<Object> resultParams = getParamsPlainList(properties);
        List<Integer> usedParams = mergeAllParams(properties);
        LP prop = addJoinAProp(null, LocalizedString.create(""), (LAP<?>) mainProp, resultParams.toArray());
        return new LPWithParams(prop, usedParams);
    }

    public LP addScriptedAddFormAction(String className, FormSessionScope scope) throws ScriptingErrorLog.SemanticErrorException {
        ValueClass cls = findClass(className);
        checkAddActionsClass(cls);
        return getScriptAddFormAction((CustomClass) cls, scope);
    }

    public LP addScriptedEditFormAction(String className, FormSessionScope scope) throws ScriptingErrorLog.SemanticErrorException {
        ValueClass cls = findClass(className);
        checkAddActionsClass(cls);
        return getScriptEditFormAction((CustomClass) cls, scope);
    }

    public LPWithParams addScriptedConfirmProp(LPWithParams msgProp) {
        List<Object> resultParams = getParamsPlainList(singletonList(msgProp));
        LAP asyncLAP = addConfirmAProp("lsFusion", resultParams.toArray());
        return new LPWithParams(asyncLAP, msgProp.usedParams);
    }
    
    public LPWithParams addScriptedConfirmxProp(LPWithParams msgProp, LPWithParams doAction, LPWithParams elseAction, boolean yesNo, List<TypedParameter> oldContext, List<TypedParameter> newContext) throws ScriptingErrorLog.SemanticErrorException {
        LCP targetProp = null;
        if(yesNo)
            targetProp = getInputProp(null, LogicalClass.instance, null);
        
        List<Object> resultParams = getParamsPlainList(singletonList(msgProp));
        LAP asyncLAP = addConfirmAProp("lsFusion", yesNo, targetProp, resultParams.toArray());
        LPWithParams inputAction = new LPWithParams(asyncLAP, msgProp.usedParams);
                
        return proceedDoClause(doAction, elseAction, oldContext, newContext, yesNo ? ListFact.singleton(targetProp) : ListFact.<LCP>EMPTY(), inputAction, yesNo ? ListFact.<Pair<LPWithParams, DebugInfo.DebugPoint>>singleton(null) : ListFact.<Pair<LPWithParams, DebugInfo.DebugPoint>>EMPTY());
    }

    public LPWithParams addScriptedMessageProp(LPWithParams msgProp, boolean noWait) {
        List<Object> resultParams = getParamsPlainList(singletonList(msgProp));
        LAP asyncLAP = addMAProp("lsFusion", noWait, resultParams.toArray());
        return new LPWithParams(asyncLAP, msgProp.usedParams);
    }

    public LPWithParams addScriptedAsyncUpdateProp(LPWithParams asyncProp) {
        List<Object> resultParams = getParamsPlainList(singletonList(asyncProp));
        LAP asyncLAP = addAsyncUpdateAProp(resultParams.toArray());
        return new LPWithParams(asyncLAP, asyncProp.usedParams);
    }

    private FormEntity getFormFromSeekObjectName(String formObjectName) throws ScriptingErrorLog.SemanticErrorException {
        int pointPos = formObjectName.lastIndexOf('.');
        assert pointPos > 0;

        String formName = formObjectName.substring(0, pointPos);
        return findForm(formName);
    }

    private ObjectEntity getSeekObject(FormEntity form, String formObjectName) throws ScriptingErrorLog.SemanticErrorException {
        return form.getNFObject(getSeekObjectName(formObjectName), getVersion());
    }

    private GroupObjectEntity getSeekGroupObject(FormEntity form, String formObjectName) throws ScriptingErrorLog.SemanticErrorException {
        return form.getNFGroupObject(getSeekObjectName(formObjectName), getVersion());
    }

    private String getSeekObjectName(String formObjectName) {
        int pointPos = formObjectName.lastIndexOf('.');
        assert pointPos > 0;

        return formObjectName.substring(pointPos + 1);
    }

    public LPWithParams addScriptedObjectSeekProp(String name, LPWithParams seekProp, boolean last) throws ScriptingErrorLog.SemanticErrorException {
        FormEntity form = getFormFromSeekObjectName(name);
        ObjectEntity object = getSeekObject(form, name);
        
        if (object != null) {
            List<Object> resultParams = getParamsPlainList(singletonList(seekProp));
            LAP lap = addOSAProp(object, last, resultParams.toArray());
            return new LPWithParams(lap, seekProp.usedParams);
        } else {
            errLog.emitObjectNotFoundError(parser, getSeekObjectName(name));
            return null;
        }
    }

    public LPWithParams addScriptedGroupObjectSeekProp(String name, List<String> objNames, List<LPWithParams> values, boolean last) throws ScriptingErrorLog.SemanticErrorException {
        FormEntity form = getFormFromSeekObjectName(name);
        GroupObjectEntity groupObject = getSeekGroupObject(form, name);
        
        List<ObjectEntity> objects = new ArrayList<>();
        if (objNames != null) {
            for (String objName : objNames) {
                ObjectEntity obj = form.getNFObject(objName, getVersion());
                if (obj == null) {
                    errLog.emitObjectNotFoundError(parser, objName);
                } else if (obj.groupTo != groupObject) {
                    errLog.emitObjectOfGroupObjectError(parser, obj.getSID(), groupObject.getSID());                    
                }
                objects.add(obj);
            }
        }
        
        if (groupObject != null) {
            List<Object> resultParams = getParamsPlainList(values);
            LAP lap = addGOSAProp(groupObject, objects, last, resultParams.toArray());
            return new LPWithParams(lap, mergeAllParams(values));
        } else {
            errLog.emitNotFoundError(parser, "group оbject", getSeekObjectName(name));
            return null;
        }
    }

    public LPWithParams addScriptedEvalActionProp(LPWithParams property) throws ScriptingErrorLog.SemanticErrorException {
        Type exprType = property.property.property.getType();
        if (!(exprType instanceof StringClass)) {
            errLog.emitEvalExpressionError(parser);
        }
        LAP<?> res = addEvalAProp((LCP) property.property);
        return new LPWithParams(res, property.usedParams);
    }

    public LPWithParams addScriptedDrillDownActionProp(LPWithParams property) {
        LAP<?> res = addDrillDownAProp((LCP) property.property);
        return new LPWithParams(res, property.usedParams);
    }

    public LPWithParams addScriptedAssignPropertyAProp(List<TypedParameter> context, PropertyUsage toPropertyUsage, List<LPWithParams> toPropertyMapping, LPWithParams fromProperty, LPWithParams whereProperty, List<TypedParameter> newContext) throws ScriptingErrorLog.SemanticErrorException {
        LP toPropertyLP = findJoinMainProp(toPropertyUsage, toPropertyMapping, newContext);

        LPWithParams toProperty = addScriptedJProp(toPropertyLP, toPropertyMapping);

        return addScriptedAssignAProp(context, fromProperty, whereProperty, toProperty);
    }

    private LPWithParams addScriptedAssignAProp(List<TypedParameter> context, LPWithParams fromProperty, LPWithParams whereProperty, LPWithParams toProperty) throws ScriptingErrorLog.SemanticErrorException {
        checkAssignProperty(fromProperty, toProperty);

        List<Integer> resultInterfaces = getResultInterfaces(context.size(), toProperty, fromProperty, whereProperty);

        List<LPWithParams> paramsList = new ArrayList<>();
        for (int resI : resultInterfaces) {
            paramsList.add(new LPWithParams(null, Collections.singletonList(resI)));
        }
        paramsList.add(toProperty);
        paramsList.add(fromProperty);
        if (whereProperty != null) {
            paramsList.add(whereProperty);
        }
        List<Object> resultParams = getParamsPlainList(paramsList);
        LP result = addSetPropertyAProp(null, LocalizedString.create(""), resultInterfaces.size(), whereProperty != null, resultParams.toArray());
        return new LPWithParams(result, resultInterfaces);
    }

    private void checkAssignProperty(LPWithParams fromProperty, LPWithParams toProperty) throws ScriptingErrorLog.SemanticErrorException {
        if (!(toProperty.property.property instanceof DataProperty || toProperty.property.property instanceof CaseUnionProperty || toProperty.property.property instanceof JoinProperty)) { // joinproperty только с неповторяющимися параметрами
            errLog.emitOnlyDataCasePropertyIsAllowedError(parser, toProperty.property.property.getName()); 
        }

        if (fromProperty.property != null && fromProperty.property.property.getType() != null &&
                toProperty.property.property.getType().getCompatible(fromProperty.property.property.getType()) == null) {
            errLog.emitIncompatibleTypes(parser, "ASSIGN");
        }
    }

    public LPWithParams addScriptedAddObjProp(List<TypedParameter> context, String className, PropertyUsage toPropUsage, List<LPWithParams> toPropMapping, LPWithParams whereProperty, List<TypedParameter> newContext) throws ScriptingErrorLog.SemanticErrorException {
        ValueClass cls = findClass(className);
        checkAddActionsClass(cls);
        checkAddObjTOParams(context.size(), toPropMapping);

        LPWithParams toProperty = null;
        if (toPropUsage != null && toPropMapping != null) {
            toProperty = addScriptedJProp(findJoinMainProp(toPropUsage, toPropMapping, newContext), toPropMapping);
        }

        List<Integer> resultInterfaces = getResultInterfaces(context.size(), toProperty, whereProperty);

        List<LPWithParams> paramsList = new ArrayList<>();
        for (int resI : resultInterfaces) {
            paramsList.add(new LPWithParams(null, Collections.singletonList(resI)));
        }
        if (toProperty != null) {
            paramsList.add(toProperty);
        } else if (whereProperty == null) {
            paramsList.add(new LPWithParams(new LCP(baseLM.getAddedObjectProperty()), new ArrayList<Integer>()));
        }
        if (whereProperty != null) {
            paramsList.add(whereProperty);
        }
        List<Object> resultParams = getParamsPlainList(paramsList);
        LAP result = addAddObjAProp((CustomClass) cls, false, resultInterfaces.size(), whereProperty != null, toProperty != null || whereProperty == null, resultParams.toArray());
        return new LPWithParams(result, resultInterfaces);
    }

    public LPWithParams addScriptedDeleteAProp(int oldContextSize, List<TypedParameter> newContext, LPWithParams param, LPWithParams whereProperty) throws ScriptingErrorLog.SemanticErrorException {
        LPWithParams res = addScriptedChangeClassAProp(oldContextSize, newContext, param, baseClass.unknown, whereProperty);
        setDeleteActionOptions((LAP) res.property);
        return res;
    }

    public LPWithParams addScriptedChangeClassAProp(int oldContextSize, List<TypedParameter> newContext, LPWithParams param, String className, LPWithParams whereProperty) throws ScriptingErrorLog.SemanticErrorException {
        ValueClass cls = findClass(className);
        checkChangeClassActionClass(cls);
        return addScriptedChangeClassAProp(oldContextSize, newContext, param, (ConcreteCustomClass) cls, whereProperty);
    }

    private LPWithParams addScriptedChangeClassAProp(int oldContextSize, List<TypedParameter> newContext, LPWithParams param, ConcreteObjectClass cls, LPWithParams whereProperty) throws ScriptingErrorLog.SemanticErrorException {
        List<LPWithParams> paramList = new ArrayList<>();
        paramList.add(param);
        if (whereProperty != null) {
            paramList.add(whereProperty);
        }
        List<Integer> allParams = mergeAllParams(paramList);
        int changedIndex = allParams.indexOf(param.usedParams.get(0));

        List<Integer> resultInterfaces = new ArrayList<>();
        for (int paramIndex : allParams) {
            if (paramIndex >= oldContextSize) {
                break;
            }
            resultInterfaces.add(paramIndex);
        }
        boolean contextExtended = allParams.size() > resultInterfaces.size();

        checkChangeClassWhere(contextExtended, param, whereProperty, newContext);

        List<LPWithParams> paramsList = new ArrayList<>();
        for (int resI : resultInterfaces) {
            paramsList.add(new LPWithParams(null, Collections.singletonList(resI)));
        }
        if (whereProperty != null) {
            paramsList.add(whereProperty);
        }
        List<Object> resultParams = getParamsPlainList(paramsList);

        LAP<?> res = addChangeClassAProp(cls, resultInterfaces.size(), changedIndex, contextExtended, whereProperty != null, resultParams.toArray());
        return new LPWithParams(res,  resultInterfaces);
    }

    private List<Integer> getResultInterfaces(int contextSize, LPWithParams... params) {
        List<LPWithParams> lpList = new ArrayList<>();
        for (LPWithParams lp : params) {
            if (lp != null) {
                lpList.add(lp);
            }
        }
        List<Integer> allParams = mergeAllParams(lpList);

        //все использованные параметры, которые были в старом контексте, идут на вход результирующего свойства
        List<Integer> resultInterfaces = new ArrayList<>();
        for (int paramIndex : allParams) {
            if (paramIndex >= contextSize) {
                break;
            }
            resultInterfaces.add(paramIndex);
        }
        return resultInterfaces;
    }

    public LPWithParams addScriptedIfAProp(LPWithParams condition, LPWithParams trueAction, LPWithParams falseAction) {
        List<LPWithParams> propParams = toList(condition, trueAction);
        if (falseAction != null) {
            propParams.add(falseAction);
        }
        List<Integer> allParams = mergeAllParams(propParams);
        LP result = addIfAProp(null, LocalizedString.create(""), false, getParamsPlainList(propParams).toArray());
        return new LPWithParams(result, allParams);
    }

    public LPWithParams addScriptedTryAProp(LPWithParams tryAction, LPWithParams finallyAction) {
        List<LPWithParams> propParams = new ArrayList<>();
        propParams.add(tryAction);
        if (finallyAction != null) {
            propParams.add(finallyAction);
        }
        
        List<Integer> allParams = mergeAllParams(propParams);
        LP result = addTryAProp(null, LocalizedString.create(""), getParamsPlainList(propParams).toArray());
        return new LPWithParams(result, allParams);
    }

    public LPWithParams addScriptedCaseAProp(List<LPWithParams> whenProps, List<LPWithParams> thenActions, LPWithParams elseAction, boolean isExclusive) {
        assert whenProps.size() > 0 && whenProps.size() == thenActions.size();

        List<LPWithParams> caseParams = new ArrayList<>();
        for (int i = 0; i < whenProps.size(); i++) {
            caseParams.add(whenProps.get(i));
            caseParams.add(thenActions.get(i));
        }
        if (elseAction != null) {
            caseParams.add(elseAction);
        }

        List<Integer> allParams = mergeAllParams(caseParams);
        LP result = addCaseAProp(isExclusive, getParamsPlainList(caseParams).toArray());
        return new LPWithParams(result, allParams);
    }

    public LPWithParams addScriptedMultiAProp(List<LPWithParams> actions, boolean isExclusive) {
        List<Integer> allParams = mergeAllParams(actions);
        LP result = addMultiAProp(isExclusive, getParamsPlainList(actions).toArray());
        return new LPWithParams(result, allParams);

    }

    public LPWithParams addScriptedApplyAProp(LPWithParams action, boolean singleApply, List<PropertyUsage> keepSessionProps, boolean keepAllSessionProps, boolean serializable) 
            throws ScriptingErrorLog.SemanticErrorException {
        List<LPWithParams> propParams = Collections.singletonList(action);

        LP result = addApplyAProp(null, LocalizedString.create(""), (action != null && action.property instanceof LAP) ? (LAP) action.property : null, singleApply,
                getMigrateProps(keepSessionProps, keepAllSessionProps), serializable);

        return new LPWithParams(result, mergeAllParams(propParams));
    }

    public LPWithParams addScriptedCancelAProp(List<PropertyUsage> keepSessionProps, boolean keepAllSessionProps)
            throws ScriptingErrorLog.SemanticErrorException {
        scriptLogger.info("addScriptedCancelAProp();");

        LP result = addCancelAProp(null, LocalizedString.create(""), getMigrateProps(keepSessionProps, keepAllSessionProps));

        return new LPWithParams(result, new ArrayList<Integer>());
    }

    private FunctionSet<SessionDataProperty> getMigrateProps(List<PropertyUsage> keepSessionProps, boolean keepAllSessionProps) throws ScriptingErrorLog.SemanticErrorException {
        FunctionSet<SessionDataProperty> keepProps;
        if(keepAllSessionProps) {
            keepProps = DataSession.keepAllSessionProperties;
        } else {
            MExclSet<SessionDataProperty> mKeepProps = SetFact.mExclSet(keepSessionProps.size());
            for (PropertyUsage migratePropUsage : keepSessionProps) {
                LP<?, ?> prop = findLPByPropertyUsage(migratePropUsage);
                checkSessionProperty(prop);
                mKeepProps.exclAdd((SessionDataProperty) prop.property);
            }
            keepProps = mKeepProps.immutable();
        }
        return keepProps;
    }

    public LPWithParams addScriptedNewAProp(List<TypedParameter> oldContext, LPWithParams action, Integer addNum, String addClassName, List<TypedParameter> newContext) throws ScriptingErrorLog.SemanticErrorException {
        return addScriptedForAProp(oldContext, null, new ArrayList<LPWithParams>(), action, null, addNum, addClassName, false, false, new ArrayList<LPWithParams>(), false, newContext);
    }
    
    public LPWithParams addScriptedForAProp(List<TypedParameter> oldContext, LPWithParams condition, List<LPWithParams> orders, LPWithParams action, LPWithParams elseAction, Integer addNum, String addClassName, boolean recursive, boolean descending, List<LPWithParams> noInline, boolean forceInline, List<TypedParameter> newContext) throws ScriptingErrorLog.SemanticErrorException {
        boolean ordersNotNull = (condition != null ? doesExtendContext(singletonList(condition), orders) : !orders.isEmpty());

        List<LPWithParams> creationParams = new ArrayList<>();
        if (condition != null) {
            creationParams.add(condition);
        }
        creationParams.addAll(orders);
        if(addNum != null) {
            creationParams.add(new LPWithParams(null, Collections.singletonList(addNum)));
        }
        if (elseAction != null) {
            creationParams.add(elseAction);
        }
        creationParams.add(action);
        List<Integer> allParams = mergeAllParams(creationParams);

        List<Integer> usedParams = new ArrayList<>();
        List<Integer> extParams = new ArrayList<>();
        for (int paramIndex : allParams) {
            if (paramIndex < oldContext.size()) {
                usedParams.add(paramIndex);
            } else {
                extParams.add(paramIndex);
            }
        }
        
        if(ActionPropertyDebugger.watchHack.get() != null && extParams.size() > 1) {
            ActionPropertyDebugger.watchHack.set(true);
        }

        checkForActionPropertyConstraints(recursive, usedParams, allParams);

        List<LPWithParams> allCreationParams = new ArrayList<>();
        for (int usedParam : usedParams) {
            allCreationParams.add(new LPWithParams(null, Collections.singletonList(usedParam)));
        }
        allCreationParams.addAll(creationParams);
        if(noInline==null) { // предполагается надо включить все кроме addNum
            noInline = new ArrayList<>();
            for (int extParam : extParams)
                if(addNum==null || !addNum.equals(extParam)) {
                    noInline.add(new LPWithParams(null, Collections.singletonList(extParam)));
                }
        }
        allCreationParams.addAll(noInline);

        LP result = addForAProp(null, LocalizedString.create(""), !descending, ordersNotNull, recursive, elseAction != null, usedParams.size(),
                                addClassName != null ? (CustomClass) findClass(addClassName) : null, false, condition != null, noInline.size(), forceInline,
                                getParamsPlainList(allCreationParams).toArray());
        return new LPWithParams(result, usedParams);
    }

    public LPWithParams getTerminalFlowActionProperty(boolean isBreak) {
        return new LPWithParams(isBreak ? new LAP(new BreakActionProperty()) : new LAP(new ReturnActionProperty()), new ArrayList<Integer>());
    }

    private List<Object> getCoeffParamsPlainList(List<LPWithParams> mappedPropsList, Integer[] coeffs) {
        List<LP> props = new ArrayList<>();
        List<List<Integer>> usedParams = new ArrayList<>();
        for (LPWithParams mappedProp : mappedPropsList) {
            props.add(mappedProp.property);
            usedParams.add(mappedProp.usedParams);
        }
        return getCoeffParamsPlainList(props, usedParams, coeffs);
    }

    private List<Integer> getParamsAssertList(List<LPWithParams> list) {
        List<Integer> result = new ArrayList<>();
        for(LPWithParams lp : list) {
            assert lp.property == null;
            result.add(BaseUtils.single(lp.usedParams));
        }
        return result;
    }

    @SafeVarargs
    private final List<Object> getParamsPlainList(List<LPWithParams>... mappedPropLists) {
        List<LP> props = new ArrayList<>();
        List<List<Integer>> usedParams = new ArrayList<>();
        for (List<LPWithParams> mappedPropList : mappedPropLists) {
            for (LPWithParams mappedProp : mappedPropList) {
                props.add(mappedProp.property);
                usedParams.add(mappedProp.usedParams);
            }
        }
        return getCoeffParamsPlainList(props, usedParams, null);
    }

    private List<Object> getCoeffParamsPlainList(List<LP> paramProps, List<List<Integer>> usedParams, Integer[] coeffs) {
        assert coeffs == null || paramProps.size() == coeffs.length;
        List<Integer> allUsedParams = mergeIntLists(usedParams);
        List<Object> resultParams = new ArrayList<>();

        for (int i = 0; i < paramProps.size(); i++) {
            LP property = paramProps.get(i);
            if (property != null) {
                if (coeffs != null) {
                    resultParams.add(coeffs[i]);
                }
                resultParams.add(property);
                for (int paramIndex : usedParams.get(i)) {
                    int localParamIndex = allUsedParams.indexOf(paramIndex);
                    assert localParamIndex >= 0;
                    resultParams.add(localParamIndex + 1);
                }
            } else {
                if (coeffs != null) {
                    resultParams.add(coeffs[i]);
                }
                int localParamIndex = allUsedParams.indexOf(usedParams.get(i).get(0));
                assert localParamIndex >= 0;
                resultParams.add(localParamIndex + 1);
            }
        }
        return resultParams;
    }

    public LCP addScriptedGProp(GroupingType type, List<LPWithParams> mainProps, List<LPWithParams> groupProps, List<LPWithParams> orderProps,
                                  boolean ascending, LPWithParams whereProp, List<TypedParameter> innerInterfaces) throws ScriptingErrorLog.SemanticErrorException {
        checkGPropOrderConsistence(type, orderProps.size());
        checkGPropAggregateConsistence(type, mainProps.size());
        checkGPropAggrConstraints(type, mainProps, groupProps);
        checkGPropWhereConsistence(type, whereProp);
        checkGPropSumConstraints(type, mainProps.get(0));

        List<LPWithParams> whereProps = new ArrayList<>();
        if (type == GroupingType.AGGR || type == GroupingType.NAGGR) {
            if (whereProp != null) {
                whereProps.add(whereProp);
            } else {
                whereProps.add(new LPWithParams(null, Collections.singletonList(mainProps.get(0).usedParams.get(0))));
            }
        }
        if (type == GroupingType.LAST) {
            if (whereProp != null) {
                mainProps.add(0, whereProp);
            } else {
                mainProps.add(mainProps.get(0));
            }
        }
        List<Object> resultParams = getParamsPlainList(mainProps, whereProps, orderProps, groupProps);

        boolean ordersNotNull = doesExtendContext(mergeLists(mainProps, groupProps), orderProps);

        int groupPropParamCount = mergeAllParams(mergeLists(mainProps, groupProps, orderProps)).size();
        List<ResolveClassSet> explicitInnerClasses = getClassesFromTypedParams(innerInterfaces);
        assert groupPropParamCount == explicitInnerClasses.size(); // в отличии скажем от Partition, тут внешнего контекста быть
        LocalizedString emptyCaption = LocalizedString.create("");
        LCP resultProp = null;
        if (type == GroupingType.SUM) {
            resultProp = addSGProp(null, false, false, emptyCaption, groupPropParamCount, explicitInnerClasses, resultParams.toArray());
        } else if (type == GroupingType.MAX || type == GroupingType.MIN) {
            resultProp = addMGProp(null, false, emptyCaption, type == GroupingType.MIN, groupPropParamCount, explicitInnerClasses, resultParams.toArray());
        } else if (type == GroupingType.CONCAT) {
            resultProp = addOGProp(null, false, emptyCaption, GroupType.STRING_AGG, orderProps.size(), ordersNotNull, !ascending, groupPropParamCount, explicitInnerClasses, resultParams.toArray());
        } else if (type == GroupingType.AGGR || type == GroupingType.NAGGR) {
            resultProp = addAGProp(null, false, false, emptyCaption, type == GroupingType.NAGGR, groupPropParamCount, explicitInnerClasses, resultParams.toArray());
        } else if (type == GroupingType.EQUAL) {
            resultProp = addCGProp(null, false, false, emptyCaption, null, groupPropParamCount, explicitInnerClasses, resultParams.toArray());
        } else if (type == GroupingType.LAST) {
            resultProp = addOGProp(null, false, emptyCaption, GroupType.LAST, orderProps.size(), ordersNotNull, !ascending, groupPropParamCount, explicitInnerClasses, resultParams.toArray());
        }
        return resultProp;
    }

    public LPWithParams addScriptedMaxProp(List<LPWithParams> paramProps, boolean isMin) throws ScriptingErrorLog.SemanticErrorException {
        if (isMin) {
            return addScriptedUProp(Union.MIN, paramProps, "MIN");
        } else {
            return addScriptedUProp(Union.MAX, paramProps, "MAX");
        }
    }

    private LPWithParams addScriptedUProp(Union unionType, List<LPWithParams> paramProps, String errMsgPropType) throws ScriptingErrorLog.SemanticErrorException {
        checkPropertyTypes(paramProps, errMsgPropType);

        int[] coeffs = null;
        if (unionType == Union.SUM) {
            coeffs = new int[paramProps.size()];
            for (int i = 0; i < coeffs.length; i++) {
                coeffs[i] = 1;
            }
        }
        List<Object> resultParams = getParamsPlainList(paramProps);
        LCP prop = addUProp(null, LocalizedString.create(""), unionType, null, coeffs, resultParams.toArray());
        return new LPWithParams(prop, mergeAllParams(paramProps));
    }

    public LPWithParams addScriptedPartitionProp(PartitionType partitionType, PropertyUsage ungroupPropUsage, boolean strict, int precision, boolean isAscending,
                                                 boolean useLast, int groupPropsCnt, List<LPWithParams> paramProps, List<TypedParameter> context) throws ScriptingErrorLog.SemanticErrorException {
        checkPartitionWindowConsistence(partitionType, useLast);
        LP ungroupProp = ungroupPropUsage != null ? findJoinMainProp(ungroupPropUsage, paramProps.subList(1, groupPropsCnt + 1), context) : null;
        checkPartitionUngroupConsistence(ungroupProp, groupPropsCnt);

        boolean ordersNotNull = doesExtendContext(paramProps.subList(0, groupPropsCnt + 1), paramProps.subList(groupPropsCnt + 1, paramProps.size()));

        List<Object> resultParams = getParamsPlainList(paramProps);
        List<Integer> usedParams = mergeAllParams(paramProps);
        LP prop;
        if (partitionType == PartitionType.SUM || partitionType == PartitionType.PREVIOUS) {
            prop = addOProp(null, false, LocalizedString.create(""), partitionType, isAscending, ordersNotNull, useLast, groupPropsCnt, resultParams.toArray());
        } else if (partitionType == PartitionType.DISTR_CUM_PROPORTION) {
            List<ResolveClassSet> contextClasses = getClassesFromTypedParams(context);// для не script - временный хак
            // может быть внешний context
            List<ResolveClassSet> explicitInnerClasses = new ArrayList<>();
            for(int usedParam : usedParams)
                explicitInnerClasses.add(contextClasses.get(usedParam)); // one-based;
            prop = addPGProp(null, false, precision, strict, LocalizedString.create(""), usedParams.size(), explicitInnerClasses, isAscending, ordersNotNull, (LCP) ungroupProp, resultParams.toArray());
        } else {
            prop = addUGProp(null, false, strict, LocalizedString.create(""), usedParams.size(), isAscending, ordersNotNull, (LCP) ungroupProp, resultParams.toArray());
        }
        return new LPWithParams(prop, usedParams);
    }

    public LPWithParams addScriptedCCProp(List<LPWithParams> params) throws ScriptingErrorLog.SemanticErrorException {
        return addScriptedJProp(addCCProp(params.size()), params);
    }

    public LPWithParams addScriptedConcatProp(String separator, List<LPWithParams> params) throws ScriptingErrorLog.SemanticErrorException {
        return addScriptedJProp(addSFUProp(params.size(), separator), params);
    }

    public LPWithParams addScriptedDCCProp(LPWithParams ccProp, int index) throws ScriptingErrorLog.SemanticErrorException {
        checkDeconcatenateIndex(ccProp, index);
        return addScriptedJProp(addDCCProp(index - 1), Collections.singletonList(ccProp));
    }

    public LCP addScriptedSFProp(String typeName, List<SQLSyntaxType> types, List<String> texts, boolean hasNotNull) throws ScriptingErrorLog.SemanticErrorException {
        assert types.size() == texts.size();
        checkSingleImplementation(types);

        Set<Integer> params = findFormulaParameters(texts.get(0));
        
        for (String text : texts) {
            Set<Integer> formulaParams = findFormulaParameters(text);
            checkFormulaParameters(formulaParams);
            if (formulaParams.size() != params.size()) {
                errLog.emitFormulaDifferentParamCountError(parser);
            }
        }
        
        String defaultFormula = "";
        MExclMap<SQLSyntaxType, String> mSyntaxes = MapFact.mExclMap();
        for (int i = 0; i < types.size(); i++) {
            SQLSyntaxType type = types.get(i);
            String text = transformFormulaText(texts.get(i));
            if (type == null) {
                defaultFormula = text;
            } else {
                mSyntaxes.exclAdd(type, text);
            }
        }
        CustomFormulaSyntax formula = new CustomFormulaSyntax(defaultFormula, mSyntaxes.immutable());
        if (typeName != null) {
            ValueClass cls = findClass(typeName);
            checkFormulaClass(cls);
            return addSFProp(formula, (DataClass) cls, params.size(), hasNotNull);
        } else {
            return addSFProp(formula, params.size(), hasNotNull);
        }
    }

    private Set<Integer> findFormulaParameters(String text) {
        Set<Integer> params = new HashSet<>();
        Pattern pattern = Pattern.compile("\\$\\d+");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String group = matcher.group();
            int paramNumber = Integer.valueOf(group.substring(1));
            params.add(paramNumber);
        }
        return params;
    }

    private String transformFormulaText(String text) {
        return text.replaceAll("\\$(\\d+)", "prm$1");
    }

    public LPWithParams addScriptedRProp(List<TypedParameter> context, LPWithParams zeroStep, LPWithParams nextStep, Cycle cycleType) throws ScriptingErrorLog.SemanticErrorException {
        List<Integer> usedParams = mergeAllParams(asList(zeroStep, nextStep));
        checkRecursionContext(getParamNamesFromTypedParams(context), usedParams);

        MOrderExclSet<Integer> mMainParams = SetFact.mOrderExclSetMax(usedParams.size());
        Map<Integer, Integer> usedToResult = new HashMap<>();
        for (int i = 0; i < usedParams.size(); i++) {
            if (!context.get(usedParams.get(i)).paramName.startsWith("$")) {
                mMainParams.exclAdd(i);
                usedToResult.put(usedParams.get(i), i);
            }
        }
        ImOrderSet<Integer> mainParams = mMainParams.immutableOrder();

        Map<Integer, Integer> mapPrev = new HashMap<>();
        for (int i = 0; i < usedParams.size(); i++) {
            String param = context.get(usedParams.get(i)).paramName; // usedParams и context orderSet / revMap'ы
            if (param.startsWith("$")) {
                int index = 0;
                for (TypedParameter tparam : context)  {
                    if (tparam.paramName.equals(param.substring(1))) {
                        break;
                    }
                    ++index;
                }
                mapPrev.put(i, usedToResult.get(index));
            }
        }

        List<Object> resultParams = getParamsPlainList(Arrays.asList(zeroStep, nextStep));
        LP res = addRProp(null, false, LocalizedString.create(""), cycleType, mainParams, MapFact.fromJavaRevMap(mapPrev), resultParams.toArray());

        List<Integer> resUsedParams = new ArrayList<>();
        for (Integer usedParam : usedParams) {
            if (!context.get(usedParam).paramName.startsWith("$")) {
                resUsedParams.add(usedParam);
            }
        }
        return new LPWithParams(res, resUsedParams);
    }

    private static StringClass getStringConstClass(LocalizedString value) {
        if(value.needToBeLocalized())
            return StringClass.text;
        return StringClass.getv(new ExtInt(value.getSourceString().length()));
    }

    public LCP addConstantProp(ConstType type, Object value) throws ScriptingErrorLog.SemanticErrorException {
        switch (type) {
            case INT: return addCProp(IntegerClass.instance, value);
            case LONG: return addCProp(LongClass.instance, value);
            case NUMERIC: return addNumericConst((String) value);
            case REAL: return addCProp(DoubleClass.instance, value);
            case STRING: return addCProp(getStringConstClass((LocalizedString)value), value);
            case LOGICAL: return addCProp(LogicalClass.instance, value);
            case DATE: return addCProp(DateClass.instance, value);
            case DATETIME: return addCProp(DateTimeClass.instance, value);
            case TIME: return addCProp(TimeClass.instance, value);
            case STATIC: return addStaticClassConst((String) value);
            case COLOR: return addCProp(ColorClass.instance, value);
            case NULL: return baseLM.vnull;
        }
        return null;
    }

    private LCP addNumericConst(String value) {
        return addCProp(NumericClass.get(value.length(), value.length() - value.indexOf('.') - 1), new BigDecimal(value));
    }

    public Color createScriptedColor(int r, int g, int b) throws ScriptingErrorLog.SemanticErrorException {
        if (r > 255 || g > 255 || b > 255) {
            errLog.emitColorComponentValueError(parser);
        }
        return new Color(r, g, b);
    }

    public int createScriptedInteger(String s) throws ScriptingErrorLog.SemanticErrorException {
        int res = 0;
        try {
            res = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            errLog.emitIntegerValueError(parser);
        }
        return res;
    }

    public long createScriptedLong(String s) throws ScriptingErrorLog.SemanticErrorException {
        long res = 0;
        try {
            res = Long.parseLong(s);
        } catch (NumberFormatException e) {
            errLog.emitLongValueError(parser);
        }
        return res;
    }

    public double createScriptedDouble(String s) throws ScriptingErrorLog.SemanticErrorException {
        double res = 0;
        try {
            res = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            errLog.emitDoubleValueError(parser);
        }
        return res;
    }

    private void validateDate(int y, int m, int d) throws ScriptingErrorLog.SemanticErrorException {
        checkRange("year component", y, 1900, 9999);
        checkRange("month component", m, 1, 12);
        checkRange("day component", d, 1, 31);

        final List<Integer> longMonth = Arrays.asList(1, 3, 5, 7, 8, 10, 12);
        if (d == 31 && !longMonth.contains(m) ||
            d == 30 && m == 2 ||
            d == 29 && m == 2 && (y % 4 != 0 || y % 100 == 0 && y % 400 != 0))
        {
            errLog.emitDateDayError(parser, y, m, d);
        }

    }

    private void validateTime(int h, int m) throws ScriptingErrorLog.SemanticErrorException {
        checkRange("hour component", h, 0, 23);
        checkRange("minute component", m, 0, 59);
    }

    private void validateDateTime(int y, int m, int d, int h, int mn) throws ScriptingErrorLog.SemanticErrorException {
        validateDate(y, m, d);
        validateTime(h, mn);
    }

    public java.sql.Date dateLiteralToDate(String text) throws ScriptingErrorLog.SemanticErrorException {
        int y = Integer.parseInt(text.substring(0, 4));
        int m = Integer.parseInt(text.substring(5, 7));
        int d = Integer.parseInt(text.substring(8, 10));
        validateDate(y, m, d);
        return new java.sql.Date(y - 1900, m - 1, d);
    }

    public Timestamp dateTimeLiteralToTimestamp(String text) throws ScriptingErrorLog.SemanticErrorException {
        int y = Integer.parseInt(text.substring(0, 4));
        int m = Integer.parseInt(text.substring(5, 7));
        int d = Integer.parseInt(text.substring(8, 10));
        int h = Integer.parseInt(text.substring(11, 13));
        int mn = Integer.parseInt(text.substring(14, 16));
        validateDateTime(y, m, d, h, mn);
        return new Timestamp(y - 1900, m - 1, d, h, mn, 0, 0);
    }

    public Time timeLiteralToTime(String text) throws ScriptingErrorLog.SemanticErrorException {
        int h = Integer.parseInt(text.substring(0, 2));
        int m = Integer.parseInt(text.substring(3, 5));
        validateTime(h, m);
        return new Time(h, m, 0);
    }

    public <O extends ObjectSelector> LPWithParams addScriptedShowFAProp(MappedForm<O> mapped, List<FormActionProps> allObjectProps,
                                              Boolean syncType, WindowFormType windowType, ManageSessionType manageSession,
                                              boolean checkOnOk, Boolean noCancel, boolean readonly) throws ScriptingErrorLog.SemanticErrorException {
        List<O> objects = new ArrayList<>();
        List<LPWithParams> mapping = new ArrayList<>();
        List<Boolean> nulls = new ArrayList<>();

        List<O> allObjects = mapped.objects;
        for (int i = 0; i < allObjects.size(); i++) {
            O object = allObjects.get(i);
            FormActionProps objectProp = allObjectProps.get(i);
            assert objectProp.in != null;
            objects.add(object);
            mapping.add(objectProp.in);
            nulls.add(objectProp.inNull);
            assert !objectProp.out && !objectProp.constraintFilter;
        }

        if(syncType == null)
            syncType = true;
        if(windowType == null) {
            if(syncType)
                windowType = WindowFormType.FLOAT;
            else
                windowType = WindowFormType.DOCKED;
        }
            
        List<LPWithParams> propParams = new ArrayList<>();
        List<Integer> allParams = mergeAllParams(propParams);

        LAP property = addIFAProp(null, LocalizedString.create(""), mapped.form, objects, nulls,
                                 manageSession, noCancel,
                                 syncType, windowType, checkOnOk,
                                 readonly, getParamsPlainList(propParams).toArray());

        if (mapping.size() > 0) {
            return addScriptedJoinAProp(property, mapping);
        } else {
            return new LPWithParams(property, allParams);
        }
    }

    private LCP<?> getInputProp(PropertyUsage targetProp, ValueClass valueClass, Set<CalcProperty> usedProps) throws ScriptingErrorLog.SemanticErrorException {
        if(targetProp != null) {
            LCP<?> result = (LCP<?>) findLPByPropertyUsage(targetProp);
            usedProps.add(result.property);
            return result;
        }

        if(valueClass instanceof DataClass) {
            LCP<?> requested = baseLM.getRequestedValueProperty().getLCP((DataClass)valueClass);
            if(usedProps == null || usedProps.add(requested.property))
                return requested;
        }
        // уже был или Object - генерим новое
        return new LCP(DerivedProperty.createInputDataProp(valueClass));
    }
    
    public <O extends ObjectSelector> LPWithParams addScriptedDialogFAProp(
                                                MappedForm<O> mapped, List<FormActionProps> allObjectProps,
                                                WindowFormType windowType, ManageSessionType manageSession,
                                                boolean checkOnOk, Boolean noCancel, boolean readonly, LPWithParams doAction, LPWithParams elseAction, List<TypedParameter> oldContext, List<TypedParameter> newContext) throws ScriptingErrorLog.SemanticErrorException {

        List<O> objects = new ArrayList<>();
        List<LPWithParams> mapping = new ArrayList<>();
        List<Boolean> nulls = new ArrayList<>();

        List<O> allObjects = mapped.objects;
        MList<O> mInputObjects = ListFact.mListMax(allObjects.size());
        MList<Boolean> mInputNulls = ListFact.mListMax(allObjects.size());
        MList<LCP> mInputProps = ListFact.mListMax(allObjects.size());
        
        MList<Pair<LPWithParams, DebugInfo.DebugPoint>> mAssignProps = ListFact.mListMax(allObjects.size());

        MList<O> mContextObjects = ListFact.mListMax(allObjects.size() + 1);
        MList<CalcProperty> mContextProps = ListFact.mListMax(allObjects.size() + 1);
        List<LPWithParams> contextLPs = new ArrayList<>();

        Set<CalcProperty> usedProps = new HashSet<>();

        for (int i = 0; i < allObjects.size(); i++) {
            O object = allObjects.get(i);
            FormActionProps objectProp = allObjectProps.get(i);
            if (objectProp.in != null) {
                objects.add(object);
                mapping.add(objectProp.in);
                nulls.add(objectProp.inNull);
            }
            if (objectProp.out) {
                mInputObjects.add(object);
                mInputNulls.add(objectProp.outNull);
                LCP<?> outProp = getInputProp(objectProp.outProp, mapped.form.getBaseClass(object), usedProps);
                mInputProps.add(outProp);

                LPWithParams changeProp = null;
                if(objectProp.constraintFilter || objectProp.assign) {
                    changeProp = objectProp.changeProp;
                    if(changeProp == null)
                        changeProp = objectProp.in;
                    assert changeProp != null;
                }
                if(objectProp.constraintFilter) {
                    mContextObjects.add(object);
                    mContextProps.add((CalcProperty)changeProp.property.property);
                    contextLPs.add(changeProp);
                }

                Pair<LPWithParams, DebugInfo.DebugPoint> assignProp = null;
                if(objectProp.assign)
                    assignProp = new Pair<>(changeProp, objectProp.assignDebugPoint);
                mAssignProps.add(assignProp);
            }
        }
        ImList<O> inputObjects = mInputObjects.immutableList();
        ImList<Boolean> inputNulls = mInputNulls.immutableList();
        ImList<LCP> inputProps = mInputProps.immutableList();

        ImList<Pair<LPWithParams, DebugInfo.DebugPoint>> assignProps = mAssignProps.immutableList();

        ImList<O> contextObjects = mContextObjects.immutableList();
        ImList<CalcProperty> contextProps = mContextProps.immutableList();

        if(windowType == null) {
            if (!inputObjects.isEmpty())
                windowType = WindowFormType.DIALOG;
            else 
                windowType = WindowFormType.FLOAT;
        }

        List<LPWithParams> propParams = new ArrayList<>();
        for(LPWithParams contextLP : contextLPs) {
            propParams.add(contextLP);
            checkCalculationProperty(contextLP.property);
        }
        List<Integer> allParams = mergeAllParams(propParams);

        LAP property = addIFAProp(null, LocalizedString.create(""), mapped.form, objects, nulls,
                                 inputObjects, inputProps, inputNulls,
                                 manageSession, noCancel,
                                 contextObjects, contextProps,
                true, windowType, checkOnOk,
                readonly, getParamsPlainList(propParams).toArray());

        LPWithParams formAction;
        if (mapping.size() > 0) {
            for(LPWithParams contextLP : contextLPs)
                for (int usedParam : contextLP.usedParams) {
                    mapping.add(new LPWithParams(null, singletonList(usedParam)));
                }
            formAction = addScriptedJoinAProp(property, mapping);
        } else {
            formAction = new LPWithParams(property, allParams);
        }

        return proceedDoClause(doAction, elseAction, oldContext, newContext, inputProps, formAction, assignProps);
    }

    private LPWithParams proceedDoClause(LPWithParams doAction, LPWithParams elseAction, List<TypedParameter> oldContext, List<TypedParameter> newContext, ImList<LCP> inputParamProps, LPWithParams inputAction, ImList<Pair<LPWithParams, DebugInfo.DebugPoint>> assignProps) throws ScriptingErrorLog.SemanticErrorException {
        if (doAction != null) {
            doAction = extendDoParams(doAction, newContext, oldContext.size(), inputParamProps, assignProps);
            return addScriptedRequestAProp(inputAction, doAction, elseAction);
        } else {
            return inputAction;
        }
    }

//    private int findOldParam(List<TypedParameter> params, ImList<Integer> inputParams, Result<ImList<LCP>> rInputParamProps) throws ScriptingErrorLog.SemanticErrorException {
//        ImOrderSet<Integer> paramsSet = inputParams.toOrderExclSet();
//        MList<LCP> mInputParamProps = ListFact.mList(inputParams.size());
//        int paramOld = params.size() - inputParams.size();
//        for(int i = params.size()-1; i >= paramOld; i--) {
//            int paramIndex = paramsSet.indexOf(i);
//            if(paramIndex < 0) 
//                errLog.emitExtendParamUsage(parser, params.get(i).paramName);
//            
//            mInputParamProps.add(rInputParamProps.result.get(paramIndex));            
//        }
//        rInputParamProps.set(mInputParamProps.immutableList().reverseList());
//        return paramOld;
//    }
    
    private LPWithParams nullExec(LPWithParams doAction, int param) throws ScriptingErrorLog.SemanticErrorException {
        List<LPWithParams> params = new ArrayList<>();
        boolean found = false;
        for(int usedParam : doAction.usedParams) 
            if(usedParam == param){
                found = true;
                params.add(new LPWithParams(baseLM.vnull, new ArrayList<Integer>()));
            } else
                params.add(new LPWithParams(null, Collections.singletonList(usedParam)));
            
        if(!found) // не было использований
            return null;
        return addScriptedJoinAProp(doAction.property, params);
    }
    
    // recursive
    private LPWithParams extendDoParams(LPWithParams doAction, List<TypedParameter> context, int paramOld, ImList<LCP> resultProps, ImList<Pair<LPWithParams, DebugInfo.DebugPoint>> assignProps) throws ScriptingErrorLog.SemanticErrorException {
        
        List<TypedParameter> currentContext = new ArrayList<>(context);
        int paramNum;
        while((paramNum = currentContext.size() - 1) >= paramOld) {
            // remove'им параметр
            List<TypedParameter> removedContext = new ArrayList<>(currentContext);
            removedContext.remove(paramNum);

            LPWithParams paramLP = new LPWithParams(null, Collections.singletonList(paramNum));
            Pair<LPWithParams, DebugInfo.DebugPoint> assignLP = assignProps.get(paramNum - paramOld);
            if(assignLP != null) {
                LPWithParams assignAction = addScriptedAssignAProp(currentContext, paramLP, null, assignLP.first);
                
                ScriptingLogicsModule.setDebugInfo(null, assignLP.second, ((LAP<?>)assignAction.property).property);
                
                doAction = addScriptedListAProp(BaseUtils.toList(assignAction, doAction), new ArrayList<LP>());
            }

            LPWithParams nullExec = nullExec(doAction, paramNum); // передает NULL в качестве параметра
            if(nullExec != null) { // нет параметра нет проблемы
                modifyContextFlowActionPropertyDefinitionBodyCreated(doAction, currentContext, removedContext, null, false);

                LPWithParams resultLP = new LPWithParams(resultProps.get(paramNum - paramOld), new ArrayList<Integer>());

                doAction = addScriptedForAProp(removedContext, addScriptedEqualityProp("==", paramLP, resultLP), new ArrayList<LPWithParams>(), doAction,
                        nullExec, null, null, false, false, null, false, currentContext);
            }

            currentContext = removedContext;
        }
        
        return doAction;
    }

    public <O extends ObjectSelector> LPWithParams addScriptedPrintFAProp(MappedForm<O> mapped, List<FormActionProps> allObjectProps,
                                           LPWithParams printerProperty, FormPrintType printType, PropertyUsage propUsage,
                                               Boolean syncType, Integer selectTop) throws ScriptingErrorLog.SemanticErrorException {
        List<O> objects = new ArrayList<>();
        List<LPWithParams> mapping = new ArrayList<>();
        List<Boolean> nulls = new ArrayList<>();

        List<O> allObjects = mapped.objects;
        for (int i = 0; i < allObjects.size(); i++) {
            O object = allObjects.get(i);
            FormActionProps objectProp = allObjectProps.get(i);
            assert objectProp.in != null;
            objects.add(object);
            mapping.add(objectProp.in);
            nulls.add(objectProp.inNull);
            assert !objectProp.out && !objectProp.constraintFilter;
        }
        
        if(syncType == null)
            syncType = false;

        List<LPWithParams> propParams = new ArrayList<>();
        if(printerProperty != null) {
            propParams.add(printerProperty);
            checkCalculationProperty(printerProperty.property);
        }
        List<Integer> allParams = mergeAllParams(propParams);

        LCP<?> targetProp = null;
        if(propUsage != null)
            targetProp = (LCP<?>) findLPByPropertyUsage(propUsage);

        LAP property = addPFAProp(null, LocalizedString.create(""), mapped.form, objects, nulls,
                printerProperty != null, printType, syncType, selectTop, targetProp, getParamsPlainList(propParams).toArray());

        if (mapping.size() > 0) {
            return addScriptedJoinAProp(property, mapping);
        } else {
            return new LPWithParams(property, allParams);
        }
    }

    public <O extends ObjectSelector> LPWithParams addScriptedExportFAProp(MappedForm<O> mapped, List<FormActionProps> allObjectProps,
                                               FormExportType exportType, boolean noHeader, String separator, String charset, PropertyUsage propUsage) throws ScriptingErrorLog.SemanticErrorException {
        List<O> objects = new ArrayList<>();
        List<LPWithParams> mapping = new ArrayList<>();
        List<Boolean> nulls = new ArrayList<>();

        List<O> allObjects = mapped.objects;
        for (int i = 0; i < allObjects.size(); i++) {
            O object = allObjects.get(i);
            FormActionProps objectProp = allObjectProps.get(i);
            assert objectProp.in != null;
            objects.add(object);
            mapping.add(objectProp.in);
            nulls.add(objectProp.inNull);
            assert !objectProp.out && !objectProp.constraintFilter;
        }


        List<LPWithParams> propParams = new ArrayList<>();
        List<Integer> allParams = mergeAllParams(propParams);

        LCP<?> targetProp = null;
        if(propUsage != null)
            targetProp = (LCP<?>) findLPByPropertyUsage(propUsage);

        LAP property = addEFAProp(null, LocalizedString.create(""), mapped.form, objects, nulls,
                exportType, noHeader, separator, charset, targetProp, getParamsPlainList(propParams).toArray());

        if (mapping.size() > 0) {
            return addScriptedJoinAProp(property, mapping);
        } else {
            return new LPWithParams(property, allParams);
        }
    }

    public ObjectEntity findObjectEntity(FormEntity form, String objectName) throws ScriptingErrorLog.SemanticErrorException {
        ObjectEntity result = form.getNFObject(objectName, getVersion());
        if (result == null) {
            errLog.emitObjectNotFoundError(parser, objectName);
        }
        return result;
    }

    public void addScriptedMetaCodeFragment(String name, List<String> params, List<String> tokens, String code, int lineNumber) throws ScriptingErrorLog.SemanticErrorException {
        checkDuplicateMetaCodeFragment(name, params.size());
        checkDistinctParameters(params);

        MetaCodeFragment fragment = new MetaCodeFragment(params, tokens, code, getName(), lineNumber);
        addMetaCodeFragment(name, fragment);
    }

    public void runMetaCode(String name, List<String> params, int lineNumber, boolean enabledMeta) throws RecognitionException {
        MetaCodeFragment metaCode = findMetaCodeFragment(name, params.size());
        checkMetaCodeParamCount(metaCode, params.size());

        String code = metaCode.getCode(params);
        parser.runMetaCode(this, code, metaCode, MetaCodeFragment.metaCodeCallString(name, metaCode, params), lineNumber, enabledMeta); 
    }

    public List<String> grabMetaCode(String metaCodeName) throws ScriptingErrorLog.SemanticErrorException {
        return parser.grabMetaCode(metaCodeName);
    }

    public List<String> grabJavaCode() throws ScriptingErrorLog.SemanticErrorException {
        return parser.grabJavaCode();
    }

    private LCP addStaticClassConst(String name) throws ScriptingErrorLog.SemanticErrorException {
        int pointPos = name.lastIndexOf('.');
        assert pointPos > 0;

        String className = name.substring(0, pointPos);
        String instanceName = name.substring(pointPos + 1);
        LCP resultProp = null;

        ValueClass cls = findClass(className);
        if (cls instanceof ConcreteCustomClass) {
            ConcreteCustomClass concreteClass = (ConcreteCustomClass) cls;
            if (concreteClass.hasStaticObject(instanceName)) { //, versionб так как отдельным шагом парсится
                resultProp = addCProp(concreteClass, instanceName);
            } else {
                errLog.emitNotFoundError(parser, "static оbject", instanceName);
            }
        } else {
            errLog.emitAbstractClassInstancesUseError(parser, className, instanceName);
        }
        return resultProp;
    }

    public LCP addScriptedGroupObjectProp(String name, GroupObjectProp prop, List<ResolveClassSet> outClasses) throws ScriptingErrorLog.SemanticErrorException {
        int pointPos = name.lastIndexOf('.');
        assert pointPos > 0;

        String formName = name.substring(0, pointPos);
        String objectName = name.substring(pointPos+1);
        LCP resultProp = null;

        FormEntity form = findForm(formName);

        GroupObjectEntity groupObject = form.getNFGroupObject(objectName, getVersion());
        if (groupObject != null) {
            for (ObjectEntity obj : groupObject.getOrderObjects()) {
                outClasses.add(obj.getResolveClassSet());
            }
            resultProp = addGroupObjectProp(groupObject, prop);
        } else {
            errLog.emitNotFoundError(parser, "group оbject", objectName);
        }
        return resultProp;
    }


    public LCP addScriptedReflectionProperty(ReflectionPropertyType type, PropertyUsage propertyUsage, List<ResolveClassSet> outClasses) throws ScriptingErrorLog.SemanticErrorException {
        switch (type) {
            case CANONICAL_NAME:
            default: return addCanonicalNameProp(propertyUsage);
        }
    }

    public LCP addCanonicalNameProp(PropertyUsage propertyUsage) throws ScriptingErrorLog.SemanticErrorException {
        return new LCP<>(new CanonicalNameProperty(findLPByPropertyUsage(propertyUsage)));
    }

    public LPWithParams addScriptedFocusActionProp(PropertyDrawEntity property) {
        return new LPWithParams(addFocusActionProp(property.getID()), new ArrayList<Integer>());
    }
    
    public LPWithParams addScriptedReadActionProperty(LPWithParams sourcePathProp, PropertyUsage propUsage, LPWithParams movePathProp, boolean delete) throws ScriptingErrorLog.SemanticErrorException {
        ValueClass sourceProp = sourcePathProp.property.property.getValueClass(ClassType.valuePolicy);
        LCP<?> targetProp = (LCP<?>) findLPByPropertyUsage(propUsage);
        ValueClass moveProp = movePathProp == null ? null : movePathProp.property.property.getValueClass(ClassType.valuePolicy);
        return addScriptedJoinAProp(addAProp(new ReadActionProperty(sourceProp, targetProp, moveProp, delete)),
                movePathProp == null ? Collections.singletonList(sourcePathProp) : Lists.newArrayList(sourcePathProp, movePathProp));
    }

    public LPWithParams addScriptedWriteActionProperty(LPWithParams sourcePathProp, LPWithParams sourceProp) throws ScriptingErrorLog.SemanticErrorException {
        checkCalculationProperty(sourceProp.property);
        return addScriptedJoinAProp(addAProp(new WriteActionProperty(sourceProp.property.property.getType(),
                sourcePathProp.property.property.getValueClass(ClassType.valuePolicy),
                sourceProp.property.property.getValueClass(ClassType.valuePolicy))), Arrays.asList(sourcePathProp, sourceProp));
    }

    public LPWithParams addScriptedImportDBFActionProperty(LPWithParams fileProp, LPWithParams whereProp, LPWithParams memoProp, List<String> ids, List<PropertyUsage> propUsages, String charset) throws ScriptingErrorLog.SemanticErrorException {
        List<LCP> props = findLPsForImport(propUsages);
        List<LPWithParams> params = new ArrayList<>();
        params.add(fileProp);
        if(whereProp != null)
            params.add(whereProp);
        if(memoProp != null)
            params.add(memoProp);
        return addScriptedJoinAProp(addAProp(ImportDataActionProperty.createDBFProperty(fileProp.property.property.getValueClass(ClassType.valuePolicy),
                whereProp == null ? null : whereProp.property.property.getValueClass(ClassType.valuePolicy),
                memoProp == null ? null : memoProp.property.property.getValueClass(ClassType.valuePolicy),
                ids, props, charset, baseLM)), params);
    }

    public LPWithParams addScriptedImportActionProperty(ImportSourceFormat format, LPWithParams fileProp, List<String> ids, List<PropertyUsage> propUsages) throws ScriptingErrorLog.SemanticErrorException {
        List<LCP> props = findLPsForImport(propUsages);
        return addScriptedJoinAProp(addAProp(ImportDataActionProperty.createProperty(fileProp.property.property.getValueClass(ClassType.valuePolicy), format, ids, props, baseLM)), Arrays.asList(fileProp));
    }

    public LPWithParams addScriptedNewThreadActionProperty(LPWithParams actionProp, LPWithParams connectionProp, LPWithParams periodProp, LPWithParams delayProp) throws ScriptingErrorLog.SemanticErrorException {
        List<LPWithParams> propParams = toList(actionProp);
        if (periodProp != null) {
            propParams.add(periodProp);
        }
        if (delayProp != null) {
            propParams.add(delayProp);
        }
        if (connectionProp != null) {
            propParams.add(connectionProp);
        }
        List<Integer> allParams = mergeAllParams(propParams);
        LAP<?> property = addNewThreadAProp(null, LocalizedString.create(""), connectionProp != null, periodProp != null, delayProp != null, getParamsPlainList(propParams).toArray());
        return new LPWithParams(property, allParams);
    }

    public LPWithParams addScriptedNewExecutorActionProperty(LPWithParams actionProp, LPWithParams threadsProp) throws ScriptingErrorLog.SemanticErrorException {
        List<LPWithParams> propParams = toList(actionProp, threadsProp);
        List<Integer> allParams = mergeAllParams(propParams);
        LAP<?> property = addNewExecutorAProp(null, LocalizedString.create(""), getParamsPlainList(propParams).toArray());
        return new LPWithParams(property, allParams);
    }

    private List<LCP> findLPsForImport(List<PropertyUsage> propUsages) throws ScriptingErrorLog.SemanticErrorException {
        List<LCP> props = new ArrayList<>();
        for (PropertyUsage propUsage : propUsages) {
            if (propUsage.classNames == null) {
                propUsage.classNames = Collections.singletonList("INTEGER"); // делаем так для лучшего сообщения об ошибке 
            } 
            LCP<?> lcp = (LCP<?>) findLPByPropertyUsage(propUsage);
            ValueClass[] paramClasses = lcp.getInterfaceClasses(ClassType.signaturePolicy);
            if (paramClasses.length != 1 || paramClasses[0].getType() != IntegerClass.instance) {
                errLog.emitPropertyWithParamsExpected(getParser(), propUsage.name, "INTEGER");
            }
            props.add(lcp);
        }
        return props;
    }

    public LPWithParams addScriptedImportExcelActionProperty(ImportSourceFormat format, LPWithParams fileProp, List<String> ids, List<PropertyUsage> propUsages, List<TypedParameter> context, LPWithParams sheetIndex) throws ScriptingErrorLog.SemanticErrorException {
        List<LCP> props = findLPsForImport(propUsages);
        ValueClass sheetIndexValueClass = null;
        if (sheetIndex != null) {
            if (sheetIndex.property == null) {
                TypedParameter param = context.get(sheetIndex.usedParams.get(0));
                sheetIndexValueClass = param.cls;
            } else {
                sheetIndexValueClass = sheetIndex.property.property.getValueClass(ClassType.valuePolicy);
            }
            if (!(sheetIndexValueClass instanceof IntClass)) {
                errLog.emitImportNonIntegralSheetError(parser);
            }
        }
        ValueClass valueClass = fileProp.property.property.getValueClass(ClassType.valuePolicy);
        return addScriptedJoinAProp(addAProp(new ImportXLSDataActionProperty(sheetIndex == null ? new ValueClass[]{valueClass} : new ValueClass[]{valueClass, sheetIndexValueClass},
                        ids, props, baseLM, format)), sheetIndex == null ? Collections.singletonList(fileProp) : Lists.newArrayList(fileProp, sheetIndex));
    }

    public LPWithParams addScriptedImportCSVActionProperty(LPWithParams fileProp, List<String> ids, List<PropertyUsage> propUsages, String separator, boolean noHeader, String charset) throws ScriptingErrorLog.SemanticErrorException {
        List<LCP> props = findLPsForImport(propUsages);
        return addScriptedJoinAProp(addAProp(new ImportCSVDataActionProperty(fileProp.property.property.getValueClass(ClassType.valuePolicy), ids, props, separator, noHeader, charset, baseLM)), Collections.singletonList(fileProp));
    }

    public LPWithParams addScriptedImportXMLActionProperty(LPWithParams fileProp, List<String> ids, List<PropertyUsage> propUsages, boolean attr) throws ScriptingErrorLog.SemanticErrorException {
        List<LCP> props = findLPsForImport(propUsages);
        return addScriptedJoinAProp(addAProp(new ImportXMLDataActionProperty(fileProp.property.property.getValueClass(ClassType.valuePolicy), ids, props, attr, baseLM)), Collections.singletonList(fileProp));
    }

    public LPWithParams addScriptedImportFormCSVActionProperty(FormEntity formEntity, boolean noHeader, String charset, String separator) throws ScriptingErrorLog.SemanticErrorException {
        return addScriptedJoinAProp(addAProp(new ImportFormCSVDataActionProperty(formEntity, noHeader, charset, separator)), Collections.<LPWithParams>emptyList());
    }

    public LPWithParams addScriptedImportFormDBFActionProperty(FormEntity formEntity, String charset) throws ScriptingErrorLog.SemanticErrorException {
        return addScriptedJoinAProp(addAProp(new ImportFormDBFDataActionProperty(formEntity, charset)), Collections.<LPWithParams>emptyList());
    }

    public LPWithParams addScriptedImportFormXMLActionProperty(FormEntity formEntity, boolean attr) throws ScriptingErrorLog.SemanticErrorException {
        return addScriptedJoinAProp(addAProp(new ImportFormXMLDataActionProperty(formEntity, attr)), Collections.<LPWithParams>emptyList());
    }

    public LPWithParams addScriptedImportFormJSONActionProperty(FormEntity formEntity) throws ScriptingErrorLog.SemanticErrorException {
        return addScriptedJoinAProp(addAProp(new ImportFormJSONDataActionProperty(formEntity)), Collections.<LPWithParams>emptyList());
    }

    public LCP addScriptedTypeProp(String className, boolean bIs) throws ScriptingErrorLog.SemanticErrorException {
        if (bIs) {
            return is(findClass(className));
        } else {
            return object(findClass(className));
        }
    }

    public LP addScriptedTypeExprProp(LP mainProp, LPWithParams property) throws ScriptingErrorLog.SemanticErrorException {
        return addScriptedJProp(mainProp, Collections.singletonList(property)).property;
    }

    public void addScriptedConstraint(LP property, Event event, boolean checked, List<PropertyUsage> propUsages, LP messageProperty, DebugInfo.DebugPoint debugPoint) throws ScriptingErrorLog.SemanticErrorException {
        if (!((LCP<?>)property).property.checkAlwaysNull(true)) {
            errLog.emitConstraintPropertyAlwaysNullError(parser);
        }
        ImSet<CalcProperty<?>> checkedProps = null;
        CalcProperty.CheckType type = (checked ? CalcProperty.CheckType.CHECK_ALL : CalcProperty.CheckType.CHECK_NO);
        if (checked && propUsages != null) {
            MSet<CalcProperty<?>> mCheckedProps = SetFact.mSet();
            for (PropertyUsage propUsage : propUsages) {
                mCheckedProps.add((CalcProperty<?>) findLPByPropertyUsage(propUsage).property);
            }
            type = CalcProperty.CheckType.CHECK_SOME;
            checkedProps = mCheckedProps.immutable();
        }
        addConstraint((LCP<?>) property, (LCP<?>) messageProperty, type, checkedProps, event, this, debugPoint);
    }

    private PrevScope prevScope = null;
    public void setPrevScope(Event event) {
        setPrevScope(event.getScope());
    }

    public void dropPrevScope(Event event) {
        dropPrevScope(event.getScope());
    }

    // по сути оптимизация - когда контекст глобального события использовать в операторах изменений PrevScope.DB
    public void setPrevScope(PrevScope scope) {
        assert prevScope == null;
        prevScope = scope;
    }

    public void dropPrevScope(PrevScope scope) {
        assert prevScope.equals(scope);
        prevScope = null;
    }

    public LPWithParams addScriptedSessionProp(IncrementType type, LPWithParams property) {
        LCP newProp;
        PrevScope scope = (type == null ? PrevScope.DB : (prevScope != null ? prevScope : PrevScope.EVENT)); // по сути оптимизация если scope известен использовать его
        if (type == null) {
            newProp = addOldProp((LCP) property.property, scope);
        } else {
            newProp = addCHProp((LCP) property.property, type, scope);
        }
        return new LPWithParams(newProp, property.usedParams);
    }

    public LPWithParams addScriptedSignatureProp(LPWithParams property) throws ScriptingErrorLog.SemanticErrorException {
        checkCalculationProperty(property.property);
        LCP newProp = addClassProp((LCP) property.property);
        return new LPWithParams(newProp, property.usedParams);
    }

    public LPWithParams addScriptedActiveTabProp(String formName, String componentName) throws ScriptingErrorLog.SemanticErrorException {
        FormEntity form = findForm(formName);
        ComponentView component = form.getNFRichDesign(getVersion()).getComponentBySID(componentName, getVersion());
        checkComponent(component, componentName);
        return new LPWithParams(new LCP<>(component.getActiveTab().property), new ArrayList<Integer>());
    }

    public void addScriptedFollows(PropertyUsage mainPropUsage, List<TypedParameter> namedParams, List<PropertyFollowsDebug> resolveOptions, LPWithParams rightProp, Event event, DebugInfo.DebugPoint debugPoint) throws ScriptingErrorLog.SemanticErrorException {
        LCP mainProp = (LCP) findJoinMainProp(mainPropUsage, namedParams);
        checkParamCount(mainProp, namedParams.size());
        checkDistinctParameters(getParamNamesFromTypedParams(namedParams));

        Integer[] params = new Integer[rightProp.usedParams.size()];
        for (int j = 0; j < params.length; j++) {
            params[j] = rightProp.usedParams.get(j) + 1;
        }
        follows(mainProp, debugPoint, ListFact.fromJavaList(resolveOptions), event, (LCP) rightProp.property, params);
    }

    public void addScriptedWriteWhen(PropertyUsage mainPropUsage, List<TypedParameter> namedParams, LPWithParams valueProp, LPWithParams whenProp, boolean action) throws ScriptingErrorLog.SemanticErrorException {
        LP mainProp = findJoinMainProp(mainPropUsage, namedParams);
        if (!(mainProp.property instanceof DataProperty)) {
            errLog.emitOnlyDataPropertyIsAllowedError(parser, mainPropUsage.name);
        }
        checkParamCount(mainProp, namedParams.size());
        checkDistinctParameters(getParamNamesFromTypedParams(namedParams));

        List<Object> params = getParamsPlainList(asList(valueProp, whenProp));
        ((LCP)mainProp).setEventChange(this, action, params.toArray());
    }

    public Set<CalcProperty> findPropsByPropertyUsages(List<PropertyUsage> propUsages) throws ScriptingErrorLog.SemanticErrorException {
        if(propUsages==null)
            return null;

        Set<CalcProperty> props = new HashSet<>(); // функционально из-за exception'а не сделаешь
        for (PropertyUsage usage : propUsages) {
            LP lp = findLPByPropertyUsage(usage);
            checkCalculationProperty(lp);
            props.add(((LCP<?>)lp).property); 
        }
        return props;
    }

    public final static GetValue<CalcProperty, LCP> getProp = new GetValue<CalcProperty, LCP>() {
        public CalcProperty getMapValue(LCP value) {
            return ((LCP<?>)value).property;
        }};

    public void addScriptedEvent(LPWithParams whenProp, LPWithParams event, List<LPWithParams> orders, boolean descending, Event baseEvent, List<LPWithParams> noInline, boolean forceInline, DebugInfo.DebugPoint debugPoint) throws ScriptingErrorLog.SemanticErrorException {
        checkActionProperty(event.property);
        if(noInline==null) {
            noInline = new ArrayList<>();
            for(Integer usedParam : whenProp.usedParams)
                noInline.add(new LPWithParams(null, Collections.singletonList(usedParam)));
        }
        List<Object> params = getParamsPlainList(asList(event, whenProp), orders, noInline);
        addEventAction(baseEvent, descending, false, noInline.size(), forceInline, debugPoint, params.toArray());
    }

    public void addScriptedGlobalEvent(LPWithParams event, Event baseEvent, boolean single, PropertyUsage showDep) throws ScriptingErrorLog.SemanticErrorException {
        checkActionProperty(event.property);
        checkEventNoParameters(event.property);
        ActionProperty action = (ActionProperty) event.property.property;
        if(showDep!=null)
            action.showDep = findLPByPropertyUsage(showDep).property;
        addBaseEvent(action, baseEvent, false, single);
    }

    public void addScriptedShowDep(PropertyUsage property, PropertyUsage propFrom) throws ScriptingErrorLog.SemanticErrorException {
        findLPByPropertyUsage(property).property.showDep = findLPByPropertyUsage(propFrom).property;
    }

    public void addScriptedAspect(PropertyUsage mainPropUsage, List<TypedParameter> mainPropParams, LPWithParams actionProp, boolean before) throws ScriptingErrorLog.SemanticErrorException {
        LP mainProp = findJoinMainProp(mainPropUsage, mainPropParams);
        checkParamCount(mainProp, mainPropParams.size());
        checkDistinctParameters(getParamNamesFromTypedParams(mainPropParams)); 
        checkActionProperty(actionProp.property);
        checkActionProperty(mainProp);

        LAP<PropertyInterface> mainActionLP = (LAP<PropertyInterface>) mainProp;

        List<Object> params = getParamsPlainList(Collections.singletonList(actionProp));
        ImList<ActionPropertyMapImplement<?, PropertyInterface>> actionImplements = readActionImplements(mainActionLP.listInterfaces, params.toArray());
        addAspectEvent((ActionProperty) mainActionLP.property, actionImplements.get(0), before);
    }

    public void addScriptedTable(String name, List<String> classIds, boolean isFull) throws ScriptingErrorLog.SemanticErrorException {
        checkDuplicateTable(name);

        ValueClass[] classes = new ValueClass[classIds.size()];
        for (int i = 0; i < classIds.size(); i++) {
            classes[i] = findClass(classIds.get(i));
        }
        addTable(name, isFull, classes);
    }

    public List<LCP> indexedProperties = new ArrayList<>();
    
    public void addScriptedIndex(LP property) throws ScriptingErrorLog.SemanticErrorException {
        checkCalculationProperty(property);
        indexedProperties.add((LCP) property);        
    }

    public LPWithParams findIndexProp(PropertyUsage toPropertyUsage, List<LPWithParams> toPropertyMapping, List<TypedParameter> context) throws ScriptingErrorLog.SemanticErrorException {
        LP toPropertyLP = findJoinMainProp(toPropertyUsage, toPropertyMapping, context);
        return new LPWithParams(toPropertyLP, getParamsAssertList(toPropertyMapping));
    }
    
    public void addScriptedIndex(List<TypedParameter> params, List<LPWithParams> lps) throws ScriptingErrorLog.SemanticErrorException {
        checkIndexNecessaryProperty(lps);
        checkStoredProperties(lps);
        checkDistinctParametersList(lps);
        checkIndexNumberOfParameters(params.size(), lps);
        ImOrderSet<String> keyNames = ListFact.fromJavaList(params).toOrderExclSet().mapOrderSetValues(new GetValue<String, TypedParameter>() {
            public String getMapValue(TypedParameter value) {
                return value.paramName;
            }});
        addIndex(keyNames, getParamsPlainList(lps).toArray());
    }

    public void addScriptedLoggable(List<PropertyUsage> propUsages) throws ScriptingErrorLog.SemanticErrorException {
        for (PropertyUsage propUsage : propUsages) {
            LCP lp = (LCP) findLPByPropertyUsage(propUsage);
            lp.makeLoggable(this, BL.systemEventsLM);
        }
    }

    public void addScriptedWindow(WindowType type, String name, LocalizedString captionStr, NavigatorWindowOptions options) throws ScriptingErrorLog.SemanticErrorException {
        checkDuplicateWindow(name);

        LocalizedString caption = (captionStr == null ? LocalizedString.create(name) : captionStr);
        NavigatorWindow window = null;
        switch (type) {
            case MENU:
                window = createMenuWindow(name, caption, options);
                break;
            case PANEL:
                window = createPanelWindow(name, caption, options);
                break;
            case TOOLBAR:
                window = createToolbarWindow(name, caption, options);
                break;
            case TREE:
                window = createTreeWindow(caption, options);
                break;
        }

        window.drawRoot = nvl(options.getDrawRoot(), false);
        window.drawScrollBars = nvl(options.getDrawScrollBars(), true);
        window.titleShown = nvl(options.getDrawTitle(), true);

        addWindow(name, window);
    }

    private MenuNavigatorWindow createMenuWindow(String name, LocalizedString caption, NavigatorWindowOptions options) throws ScriptingErrorLog.SemanticErrorException {
        Orientation orientation = options.getOrientation();
        DockPosition dp = options.getDockPosition();
        if (dp == null) {
            errLog.emitWindowPositionNotSpecified(parser, name);
        }

        MenuNavigatorWindow window = new MenuNavigatorWindow(null, caption, dp.x, dp.y, dp.width, dp.height);
        window.orientation = orientation.asMenuOrientation();

        return window;
    }

    private PanelNavigatorWindow createPanelWindow(String name, LocalizedString caption, NavigatorWindowOptions options) {
        Orientation orientation = options.getOrientation();
        DockPosition dockPosition = options.getDockPosition();

        if (orientation == null) {
            orientation = Orientation.VERTICAL;
        }

        PanelNavigatorWindow window = new PanelNavigatorWindow(orientation.asToolbarOrientation(), null, caption);
        if (dockPosition != null) {
            window.setDockPosition(dockPosition.x, dockPosition.y, dockPosition.width, dockPosition.height);
        }
        return window;
    }

    private ToolBarNavigatorWindow createToolbarWindow(String name, LocalizedString caption, NavigatorWindowOptions options) throws ScriptingErrorLog.SemanticErrorException {
        Orientation orientation = options.getOrientation();
        BorderPosition borderPosition = options.getBorderPosition();
        DockPosition dockPosition = options.getDockPosition();

        if (orientation == null) {
            orientation = Orientation.VERTICAL;
        }

        if (borderPosition != null && dockPosition != null) {
            errLog.emitWindowPositionConflict(parser, name);
        }

        ToolBarNavigatorWindow window;
        if (borderPosition != null) {
            window = new ToolBarNavigatorWindow(orientation.asToolbarOrientation(), null, caption, borderPosition.asLayoutConstraint());
        } else if (dockPosition != null) {
            window = new ToolBarNavigatorWindow(orientation.asToolbarOrientation(), null, caption, dockPosition.x, dockPosition.y, dockPosition.width, dockPosition.height);
        } else {
            window = new ToolBarNavigatorWindow(orientation.asToolbarOrientation(), null, caption);
        }

        Alignment hAlign = options.getHAlign();
        Alignment vAlign = options.getVAlign();
        Alignment thAlign = options.getTextHAlign();
        Alignment tvAlign = options.getTextVAlign();
        if (hAlign != null) {
            window.alignmentX = asHorizontalToolbarAlign(hAlign);
        }
        if (vAlign != null) {
            window.alignmentY = asVerticalToolbarAlign(vAlign);
        }
        if (thAlign != null) {
            window.horizontalTextPosition = asHorizontalTextPosition(thAlign);
        }
        if (tvAlign != null) {
            window.verticalTextPosition = asVerticalTextPosition(tvAlign);
        }
        return window;
    }

    private TreeNavigatorWindow createTreeWindow(LocalizedString caption, NavigatorWindowOptions options) {
        TreeNavigatorWindow window = new TreeNavigatorWindow(null, caption);
        DockPosition dp = options.getDockPosition();
        if (dp != null) {
            window.setDockPosition(dp.x, dp.y, dp.width, dp.height);
        }
        return window;
    }


    public void hideWindow(String name) throws ScriptingErrorLog.SemanticErrorException {
        findWindow(name).visible = false;
    }

    public static class NavigatorElementOptions {
        public String imagePath;
        public NavigatorElement anchor;
        public InsertPosition position;
        public String windowName;
    }
    
    public NavigatorElement createScriptedNavigatorElement(String name, LocalizedString caption, DebugInfo.DebugPoint point, NavigatorElement<?> parentElement,
                                                           NavigatorElementOptions options, PropertyUsage actionUsage) throws ScriptingErrorLog.SemanticErrorException {
        checkDuplicateNavigatorElement(name);

        NavigatorElement newElement;

        if (caption == null) {
            caption = LocalizedString.create(name);
        }
        
        if (actionUsage != null) {
            if (actionUsage.classNames == null) {
                actionUsage.classNames = Collections.emptyList(); // делаем так для лучшего сообщения об ошибке
            }
            LP findResult = findLPByPropertyUsage(actionUsage);
            checkNavigatorAction(findResult);
            newElement = addNavigatorAction(name, caption, (LAP<?>)findResult, point.toString());
        } else {
            newElement = addNavigatorElement(name, caption, point.toString());
        }

        setupNavigatorElement(newElement, caption, parentElement, options, true);
        return newElement;
    }

    public void setupNavigatorElement(NavigatorElement<?> element, LocalizedString caption, NavigatorElement<?> parentElement, NavigatorElementOptions options, boolean adding) throws ScriptingErrorLog.SemanticErrorException {
        if (caption != null) {
            element.caption = caption;
        }

        applyNavigatorElementOptions(element, parentElement, options, adding);
    }
    
    public void applyNavigatorElementOptions(NavigatorElement<?> element, NavigatorElement<?> parent, NavigatorElementOptions options, boolean adding) throws ScriptingErrorLog.SemanticErrorException {
        if (options.windowName != null) {
            setNavigatorElementWindow(element, options.windowName);    
        }
        
        if (options.imagePath != null) {
            element.setImage(options.imagePath);
        } else if (element.defaultIcon != null) {
            NavigatorElement root = baseLM.findNavigatorElement("root");
            if (root != null && parent != null && root.equals(parent)) {
                element.setImage(element.defaultIcon == DefaultIcon.ACTION ? "/images/actionTop.png" :
                        element.defaultIcon == DefaultIcon.OPEN ? "/images/openTop.png" : "/images/formTop.png");
            }
            element.defaultIcon = null;
        }
        
        if (parent != null && (adding || options.position != InsertPosition.IN)) {
            moveElement(element, parent, options.position, options.anchor, adding);
        }
    } 

    private void moveElement(NavigatorElement element, NavigatorElement parentElement, InsertPosition pos, NavigatorElement anchorElement, boolean adding) throws ScriptingErrorLog.SemanticErrorException {
        Version version = getVersion();
        
        // если редактирование существующего элемента, и происходит перемещение элемента, то оно должно происходить только внутри своего уровня 
        if (!adding && !parentElement.equals(element.getNFParent(version))) {
            errLog.emitIllegalNavigatorElementMove(parser, element.getCanonicalName(), parentElement.getCanonicalName());
        }
        
        if (anchorElement != null && !parentElement.equals(anchorElement.getNFParent(version))) {
            errLog.emitIllegalInsertBeforeAfterElement(parser, element.getCanonicalName(), parentElement.getCanonicalName(), anchorElement.getCanonicalName());
        }

        if (element.isAncestorOf(parentElement, version)) {
            errLog.emitIllegalAddNavigatorToSubnavigator(parser, element.getCanonicalName(), parentElement.getCanonicalName());
        }

        switch (pos) {
            case IN:
                parentElement.add(element, version);
                break;
            case BEFORE:
                parentElement.addBefore(element, anchorElement, version);
                break;
            case AFTER:
                parentElement.addAfter(element, anchorElement, version);
                break;
            case FIRST:
                parentElement.addFirst(element, version);
                break;
        }
    }

    public void setNavigatorElementWindow(NavigatorElement element, String windowName) throws ScriptingErrorLog.SemanticErrorException {
        assert element != null && windowName != null;

        AbstractWindow window = findWindow(windowName);

        if (window instanceof NavigatorWindow) {
            element.window = (NavigatorWindow) window;
        } else {
            errLog.emitAddToSystemWindowError(parser, windowName);
        }
    }

    public void propertyDefinitionCreated(LP property, DebugInfo.DebugPoint point) {
        if (property != null && property.property instanceof CalcProperty) {
            CalcProperty calcProp = (CalcProperty)property.property; 
            boolean needToCreateDelegate = debugger.isEnabled() && point.needToCreateDelegate() && calcProp instanceof DataProperty;
            if (calcProp.getDebugInfo() == null) { // при использовании в propertyExpression оптимизированных join свойств, не нужно им переустанавливать DebugInfo
                CalcPropertyDebugInfo debugInfo = new CalcPropertyDebugInfo(point, needToCreateDelegate);
                if (needToCreateDelegate) {
                    debugger.addDelegate(debugInfo);
                }
                calcProp.setDebugInfo(debugInfo);
            }
        }
    }

    public void actionPropertyDefinitionBodyCreated(LPWithParams lpWithParams, DebugInfo.DebugPoint startPoint, DebugInfo.DebugPoint endPoint, boolean modifyContext, Boolean needToCreateDelegate) throws ScriptingErrorLog.SemanticErrorException {
        if (lpWithParams.property != null) {
            checkActionProperty(lpWithParams.property);
            setDebugInfo(lpWithParams, startPoint, endPoint, modifyContext, needToCreateDelegate);
        }
    }

    public static void setDebugInfo(LPWithParams lpWithParams, DebugInfo.DebugPoint startPoint, DebugInfo.DebugPoint endPoint, boolean modifyContext, Boolean needToCreateDelegate) {
        //noinspection unchecked
        LAP<PropertyInterface> lAction = (LAP<PropertyInterface>) lpWithParams.property;
        ActionProperty property = (ActionProperty) lAction.property;
        setDebugInfo(needToCreateDelegate, startPoint, endPoint, modifyContext, property);
    }

    public static void setDebugInfo(Boolean needToCreateDelegate, DebugInfo.DebugPoint point, ActionProperty property) {
        setDebugInfo(needToCreateDelegate, point, point, false, property);        
    }

    private static void setDebugInfo(Boolean needToCreateDelegate, DebugInfo.DebugPoint startPoint, DebugInfo.DebugPoint endPoint, boolean modifyContext, ActionProperty property) {
        ActionDelegationType delegationType = property.getDelegationType(modifyContext);

        if(needToCreateDelegate == null)
            needToCreateDelegate = startPoint.needToCreateDelegate();

        if (debugger.isEnabled() && needToCreateDelegate && delegationType != null) {
            DebugInfo.DebugPoint typePoint = delegationType.getDebugPoint(startPoint, endPoint);
            ActionDebugInfo info = new ActionDebugInfo(startPoint, typePoint.line, typePoint.offset, delegationType);
            debugger.addDelegate(info);
            property.setDebugInfo(info);
        } else {
            property.setDebugInfo(new ActionDebugInfo(startPoint, delegationType, false));
        }
    }

    public void topContextActionPropertyDefinitionBodyCreated(LPWithParams lpWithParams) throws ScriptingErrorLog.SemanticErrorException {
        boolean isDebug = debugger.isEnabled();

        if(isDebug) {
            //noinspection unchecked
            LAP<PropertyInterface> lAction = (LAP<PropertyInterface>) lpWithParams.property;

            ActionProperty property = (ActionProperty) lAction.property;

            debugger.setNewDebugStack(property);
        }
    }

    public LPWithParams modifyContextFlowActionPropertyDefinitionBodyCreated(LPWithParams lpWithParams,
                                                    List<TypedParameter> newContext, List<TypedParameter> oldContext,
                                                    List<ResolveClassSet> signature, boolean needFullContext) throws ScriptingErrorLog.SemanticErrorException {
        boolean isDebug = debugger.isEnabled();
        
        if(isDebug || needFullContext) {
            lpWithParams = patchExtendParams(lpWithParams, newContext, oldContext);
        }            
        
        if (isDebug) {

            checkActionProperty(lpWithParams.property);

            //noinspection unchecked
            LAP<PropertyInterface> lAction = (LAP<PropertyInterface>) lpWithParams.property;

            ActionProperty property = (ActionProperty) lAction.property;

            Map<String, PropertyInterface> paramsToInterfaces = new HashMap<>();
            Map<String, String> paramsToClassFQN = new HashMap<>();

            for (int i = 0; i < lpWithParams.usedParams.size(); i++) {
                int usedParam = lpWithParams.usedParams.get(i);
                if(usedParam >= oldContext.size()) { // если новый параметр
                    TypedParameter param = newContext.get(usedParam);

                    paramsToInterfaces.put(param.paramName, lAction.listInterfaces.get(i));
                    paramsToClassFQN.put(param.paramName, param.getParsedName());
                }
            }

            debugger.addParamInfo(property, paramsToInterfaces, paramsToClassFQN);
        }
        
        return lpWithParams;
    }

    // assert'им что newContext "расширяет" oldContext (во всяком случае такое предположение в addScriptedForAProp)
    private LPWithParams patchExtendParams(LPWithParams lpWithParams, List<TypedParameter> newContext, List<TypedParameter> oldContext) {

        if(!lpWithParams.property.listInterfaces.isEmpty() && lpWithParams.usedParams.isEmpty()) {
            return lpWithParams;
        }
        
        Set<Integer> usedExtendParams = new HashSet<>();
        for (int i = 0; i < lpWithParams.usedParams.size(); i++) {
            Integer usedParam = lpWithParams.usedParams.get(i);
            if(usedParam >= oldContext.size()) {
                usedExtendParams.add(usedParam);
            }
        }
        
        if(usedExtendParams.size() == (newContext.size() - oldContext.size())) { // все использованы
            return lpWithParams;
        }

        // по сути этот алгоритм эмулирует создание ListAction, с докидыванием в конец виртуального action'а который использует все extend параметры, однако само действие при этом не создает 
        List<LPWithParams> allCreationParams = new ArrayList<>();
        allCreationParams.add(lpWithParams);        
        for (int i = oldContext.size(); i < newContext.size(); i++) { // докидываем 
            allCreationParams.add(new LPWithParams(null, Collections.singletonList(i)));
        }

        List<Object> resultParams = getParamsPlainList(allCreationParams);
        LAP wrappedLAP = addListAProp(newContext.size() - oldContext.size(), resultParams.toArray());

        List<Integer> wrappedUsed = mergeAllParams(allCreationParams);
        return new LPWithParams(wrappedLAP, wrappedUsed);
    }

    private void checkGroup(AbstractGroup group, String name) throws ScriptingErrorLog.SemanticErrorException {
        if (group == null) {
            errLog.emitGroupNotFoundError(parser, name);
        }
    }

    private void checkClass(ValueClass cls, String name) throws ScriptingErrorLog.SemanticErrorException {
        if (cls == null) {
            errLog.emitClassNotFoundError(parser, name);
        }
    }

    private void checkComponent(ComponentView component, String name) throws ScriptingErrorLog.SemanticErrorException {
        if (component == null) {
            errLog.emitComponentNotFoundError(parser, name);
        }
    }

    private void checkProperty(LP lp, String name) throws ScriptingErrorLog.SemanticErrorException {
        if (lp == null) {
            errLog.emitPropertyNotFoundError(parser, name);
        }
    }

    private void checkModule(LogicsModule module, String name) throws ScriptingErrorLog.SemanticErrorException {
        if (module == null) {
            errLog.emitModuleNotFoundError(parser, name);
        }
    }

    private void checkNamespace(String namespaceName) throws ScriptingErrorLog.SemanticErrorException {
        if (!namespaceToModules.containsKey(namespaceName)) {
            errLog.emitNamespaceNotFoundError(parser, namespaceName);
        }
    }

    private void checkWindow(AbstractWindow window, String name) throws ScriptingErrorLog.SemanticErrorException {
        if (window == null) {
            errLog.emitWindowNotFoundError(parser, name);
        }
    }

    private void checkNavigatorElement(NavigatorElement element, String name) throws ScriptingErrorLog.SemanticErrorException {
        if (element == null) {
            errLog.emitNavigatorElementNotFoundError(parser, name);
        }
    }

    private void checkTable(ImplementTable table, String name) throws ScriptingErrorLog.SemanticErrorException {
        if (table == null) {
            errLog.emitTableNotFoundError(parser, name);
        }
    }

    private void checkForm(NavigatorElement navElement, String name) throws ScriptingErrorLog.SemanticErrorException {
        if (!(navElement instanceof FormEntity)) {
            errLog.emitFormNotFoundError(parser, name);
        }
    }

    private void checkMetaCodeFragment(MetaCodeFragment code, String name) throws ScriptingErrorLog.SemanticErrorException {
        if (code == null) {
            errLog.emitMetaCodeFragmentNotFoundError(parser, name);
        }
    }

    private void checkParamCount(LP mainProp, int paramCount) throws ScriptingErrorLog.SemanticErrorException {
        if (mainProp.property.interfaces.size() != paramCount) {
            errLog.emitParamCountError(parser, mainProp, paramCount);
        }
    }

    public void checkPropertyValue(LP property) {
        if (property.property instanceof CalcProperty && !((CalcProperty)property.property).checkAlwaysNull(false) && !alwaysNullProperties.containsKey(property.property)) {
            String path = parser.getCurrentScriptPath(getName(), parser.getCurrentParserLineNumber(), "\n\t\t\t");
            String location = path + ":" + (parser.getCurrentParser().input.LT(1).getCharPositionInLine() + 1);
            alwaysNullProperties.put(property.property, location);
        }
    }

    private void checkDuplicateClass(String className) throws ScriptingErrorLog.SemanticErrorException {
        LogicsModule module = BL.getModuleContainingClass(getNamespace(), className);
        if (module != null) {
            errLog.emitAlreadyDefinedInModuleError(parser, "class", className, module.getName());
        }
    }

    private void checkDuplicateGroup(String groupName) throws ScriptingErrorLog.SemanticErrorException {
        LogicsModule module = BL.getModuleContainingGroup(getNamespace(), groupName);
        if (module != null) {
            errLog.emitAlreadyDefinedInModuleError(parser, "group", groupName, module.getName());
        }
    }

    private void checkDuplicateProperty(String propName, List<ResolveClassSet> signature) throws ScriptingErrorLog.SemanticErrorException {
        LogicsModule module = BL.getModuleContainingLP(getNamespace(), propName, signature);
        if (module != null) {
            errLog.emitAlreadyDefinedInModuleError(parser, "property", propName, module.getName());
        }
        EqualLPModuleFinder finder = new EqualLPModuleFinder(true);
        if (!finder.resolveInModule(this, propName, signature).isEmpty()) {
            errLog.emitAlreadyDefinedInModuleError(parser, "property", propName, getName());
        }
    }

    private void checkDuplicateWindow(String windowName) throws ScriptingErrorLog.SemanticErrorException {
        LogicsModule module = BL.getModuleContainingWindow(getNamespace(), windowName);
        if (module != null) {
            errLog.emitAlreadyDefinedInModuleError(parser, "window", windowName, module.getName());
        }
    }

    private void checkDuplicateNavigatorElement(String name) throws ScriptingErrorLog.SemanticErrorException {
        LogicsModule module = BL.getModuleContainingNavigatorElement(getNamespace(), name);
        if (module != null) {
            errLog.emitAlreadyDefinedInModuleError(parser, "form or navigator", name, module.getName());
        }
    }

    private void checkDuplicateMetaCodeFragment(String name, int paramCnt) throws ScriptingErrorLog.SemanticErrorException {
        LogicsModule module = BL.getModuleContainingMetaCode(getNamespace(), name, paramCnt);
        if (module != null) {
            errLog.emitAlreadyDefinedInModuleError(parser, "meta code", name, module.getName());
        }
    }

    private void checkDuplicateTable(String name) throws ScriptingErrorLog.SemanticErrorException {
        LogicsModule module = BL.getModuleContainingTable(getNamespace(), name);
        if (module != null) {
            errLog.emitAlreadyDefinedInModuleError(parser, "table", name, module.getName());
        }
    }

    private void checkPropertyTypes(List<LPWithParams> properties, String errMsgPropType) throws ScriptingErrorLog.SemanticErrorException {
        LP lp1 = properties.get(0).property;
        if(lp1 == null)
            return;
        Property prop1 = lp1.property;
        for (int i = 1; i < properties.size(); i++) {
            LP lp2 = properties.get(i).property;
            if(lp2 == null)
                return;            
            Property prop2 = lp2.property;
            if (prop1.getType() != null && prop2.getType() != null && prop1.getType().getCompatible(prop2.getType()) == null) {
                errLog.emitIncompatibleTypes(parser, errMsgPropType);
            }
        }
    }

    private void checkStaticClassConstraints(boolean isAbstract, List<String> instNames, List<LocalizedString> instCaptions) throws ScriptingErrorLog.SemanticErrorException {
        assert instCaptions.size() == instNames.size();
        if (isAbstract && !instNames.isEmpty()) {
            errLog.emitAbstractClassInstancesDefError(parser);
        }

        Set<String> names = new HashSet<>();
        for (String name : instNames) {
            if (names.contains(name)) {
                errLog.emitAlreadyDefinedError(parser, "instance", name);
            }
            names.add(name);
        }
    }

    private void checkClassParents(List<String> parents) throws ScriptingErrorLog.SemanticErrorException {
        Set<ValueClass> parentsSet = new HashSet<>();
        for (String parentName : parents) {
            ValueClass valueClass = findClass(parentName);
            if (!(valueClass instanceof CustomClass)) {
                errLog.emitBuiltInClassAsParentError(parser, parentName);
            }

            if (parentsSet.contains(valueClass)) {
                errLog.emitDuplicateClassParentError(parser, parentName);
            }
            parentsSet.add(valueClass);
        }
    }

    private void checkFormulaClass(ValueClass cls) throws ScriptingErrorLog.SemanticErrorException {
        if (!(cls instanceof DataClass)) {
            errLog.emitFormulaReturnClassError(parser);
        }
    }

    private void checkFormDataClass(ValueClass cls) throws ScriptingErrorLog.SemanticErrorException {
        if (!(cls instanceof DataClass)) {
            errLog.emitFormDataClassError(parser);
        }
    }

    private void checkInputDataClass(ValueClass cls) throws ScriptingErrorLog.SemanticErrorException {
        if (!(cls instanceof DataClass)) {
            errLog.emitInputDataClassError(parser);
        }
    }

    private void checkChangeClassActionClass(ValueClass cls) throws ScriptingErrorLog.SemanticErrorException {
        if (!(cls instanceof ConcreteCustomClass)) {
            errLog.emitChangeClassActionClassError(parser);
        }
    }

    private void checkSingleImplementation(List<SQLSyntaxType> types) throws ScriptingErrorLog.SemanticErrorException {
        Set<SQLSyntaxType> foundTypes = new HashSet<>();
        for (SQLSyntaxType type : types) {
            if (!foundTypes.add(type)) {
                errLog.emitFormulaMultipleImplementationError(parser, type);
            }
        }
    }

    private void checkFormulaParameters(Set<Integer> params) throws ScriptingErrorLog.SemanticErrorException {
        for (int param : params) {
            if (param == 0 || param > params.size()) {
                errLog.emitFormulaParamIndexError(parser, param, params.size());
            }
        }
    }

    private void checkNamedParams(LP property, List<String> namedParams) throws ScriptingErrorLog.SemanticErrorException {
        if (property.property.interfaces.size() != namedParams.size() && !namedParams.isEmpty()) {
            errLog.emitNamedParamsError(parser);
        }
    }

    private <T> void checkDistinctParameters(List<T> params) throws ScriptingErrorLog.SemanticErrorException {
        Set<T> paramsSet = new HashSet<>(params);
        if (paramsSet.size() < params.size()) {
            errLog.emitDistinctParamNamesError(parser);
        }
    }

    private void checkDistinctParametersList(List<LPWithParams> lps) throws ScriptingErrorLog.SemanticErrorException {
        for (LPWithParams lp : lps) {
            checkDistinctParameters(lp.usedParams);
        }
    }    
    
    private void checkMetaCodeParamCount(MetaCodeFragment code, int paramCnt) throws ScriptingErrorLog.SemanticErrorException {
        if (code.parameters.size() != paramCnt) {
            errLog.emitParamCountError(parser, code.parameters.size(), paramCnt);
        }
    }

    private void checkGPropOrderConsistence(GroupingType type, int orderParamsCnt) throws ScriptingErrorLog.SemanticErrorException {
        if (type != GroupingType.CONCAT && type != GroupingType.LAST && orderParamsCnt > 0) {
            errLog.emitRedundantOrderGPropError(parser, type);
        }
    }

    private void checkGPropAggregateConsistence(GroupingType type, int aggrParamsCnt) throws ScriptingErrorLog.SemanticErrorException {
        if (type != GroupingType.CONCAT && aggrParamsCnt > 1) {
            errLog.emitMultipleAggrGPropError(parser, type);
        }
        if (type == GroupingType.CONCAT && aggrParamsCnt != 2) {
            errLog.emitConcatAggrGPropError(parser);
        }
    }

    private void checkGPropSumConstraints(GroupingType type, LPWithParams mainProp) throws ScriptingErrorLog.SemanticErrorException {
        if (type == GroupingType.SUM && mainProp.property != null) {
            if (!(mainProp.property.property.getValueClass(ClassType.valuePolicy).getType() instanceof IntegralClass)) {
                errLog.emitNonIntegralSumArgumentError(parser);
            }
        }
    }
    
    private void checkGPropAggrConstraints(GroupingType type, List<LPWithParams> mainProps, List<LPWithParams> groupProps) throws ScriptingErrorLog.SemanticErrorException {
        if (type == GroupingType.AGGR || type == GroupingType.NAGGR) {
            if (mainProps.get(0).property != null) {
                errLog.emitNonObjectAggrGPropError(parser);
            }
        }
    }

    private void checkGPropWhereConsistence(GroupingType type, LPWithParams where) throws ScriptingErrorLog.SemanticErrorException {
        if (type != GroupingType.AGGR && type != GroupingType.NAGGR && type != GroupingType.LAST && where != null) {
            errLog.emitWhereGPropError(parser, type);
        }
    }

    private void checkActionProperty(LP property) throws ScriptingErrorLog.SemanticErrorException {
        if (!(property instanceof LAP<?>)) {
            errLog.emitNotActionPropertyError(parser);
        }
    }

    private void checkNavigatorAction(LP property) throws ScriptingErrorLog.SemanticErrorException {
        checkActionProperty(property);
        if (property.listInterfaces.size() > 0) {
            errLog.emitWrongNavigatorAction(parser);
        }
    }
    
    private void checkAddActionsClass(ValueClass cls) throws ScriptingErrorLog.SemanticErrorException {
        if (!(cls instanceof CustomClass)) {
            errLog.emitAddActionsClassError(parser);
        }
    }

    private void checkCustomClass(ValueClass cls) throws ScriptingErrorLog.SemanticErrorException {
        if (!(cls instanceof CustomClass)) {
            errLog.emitCustomClassExpextedError(parser);
        }
    }

    private void checkCalculationProperty(LP property) throws ScriptingErrorLog.SemanticErrorException {
        if (!(property instanceof LCP<?>)) {
            errLog.emitNotCalculationPropertyError(parser);
        }
    }

    private void checkSessionProperty(LP property) throws ScriptingErrorLog.SemanticErrorException {
        if (!(property.property instanceof SessionDataProperty)) {
            errLog.emitNotSessionOrLocalPropertyError(parser);
        }
    }

    private void checkForActionPropertyConstraints(boolean isRecursive, List<Integer> oldContext, List<Integer> newContext) throws ScriptingErrorLog.SemanticErrorException {
        if (!isRecursive && oldContext.size() == newContext.size()) {
            errLog.emitForActionSameContextError(parser);
        }
    }

    private void checkRecursionContext(List<String> context, List<Integer> usedParams) throws ScriptingErrorLog.SemanticErrorException {
        for (String param : context) {
            if (param.startsWith("$")) {
                int indexPlain = context.indexOf(param.substring(1));
                if (indexPlain < 0) {
                    errLog.emitParamNotFoundError(parser, param.substring(1));
                }
                if (!usedParams.contains(indexPlain)) {
                    errLog.emitParameterNotUsedInRecursionError(parser, param.substring(1));
                }
            }
        }
    }

    public void checkNecessaryProperty(LPWithParams property) throws ScriptingErrorLog.SemanticErrorException {
        if (property.property == null) {
            errLog.emitNecessaryPropertyError(parser);
        }
    }

    private void checkIndexNecessaryProperty(List<LPWithParams> lps) throws ScriptingErrorLog.SemanticErrorException {
        boolean hasProperty = false;
        for (LPWithParams lp : lps) {
            if (lp.property != null) {
                hasProperty = true;
                break;
            }
        }
        if (!hasProperty) {
            errLog.emitIndexWithoutPropertyError(parser);
        }
    }

    private void checkStoredProperties(List<LPWithParams> lps) throws ScriptingErrorLog.SemanticErrorException {
        ImplementTable table = null;
        String firstPropertyName = null;
        for (LPWithParams lp : lps) {
            if (lp.property != null) {
                checkCalculationProperty(lp.property);
                CalcProperty<?> calcProperty = (CalcProperty<?>) lp.property.property;
                if (!calcProperty.isStored()) {
                    errLog.emitShouldBeStoredError(parser, calcProperty.getName());
                }
                if (table == null) {
                    table = calcProperty.mapTable.table;
                    firstPropertyName = calcProperty.getName();
                } else if (table != calcProperty.mapTable.table) {
                    errLog.emitIndexPropertiesDifferentTablesError(parser, firstPropertyName, calcProperty.getName());
                }
            }
        }
    } 
    
    private void checkIndexNumberOfParameters(int paramsCount, List<LPWithParams> lps) throws ScriptingErrorLog.SemanticErrorException {
        int paramsInProp = -1;
        for (LPWithParams lp : lps) {
            if (lp.property != null) {
                if (paramsInProp == -1) {
                    paramsInProp = lp.usedParams.size();
                } else if (lp.usedParams.size() != paramsInProp){
                    errLog.emitIndexPropertiesNonEqualParamsCountError(parser);    
                }
            }
        }
        if (paramsCount != paramsInProp) {
            errLog.emitIndexParametersError(parser);
        }
    }
    
    private void checkDeconcatenateIndex(LPWithParams property, int index) throws ScriptingErrorLog.SemanticErrorException {
        Type propType = property.property.property.getType();
        if (propType instanceof ConcatenateType) {
            int concatParts = ((ConcatenateType) propType).getPartsCount();
            if (index <= 0 || index > concatParts) {
                errLog.emitDeconcatIndexError(parser, index, concatParts);
            }
        } else {
            errLog.emitDeconcatError(parser);
        }
    }

    private void checkPartitionWindowConsistence(PartitionType partitionType, boolean useLast) throws ScriptingErrorLog.SemanticErrorException {
        if (!useLast && (partitionType != PartitionType.SUM && partitionType != PartitionType.PREVIOUS)) {
            errLog.emitIllegalWindowPartitionError(parser);
        }
    }

    private void checkPartitionUngroupConsistence(LP ungroupProp, int groupPropCnt) throws ScriptingErrorLog.SemanticErrorException {
        if (ungroupProp != null && ungroupProp.property.interfaces.size() != groupPropCnt) {
            errLog.emitUngroupParamsCntPartitionError(parser, groupPropCnt);
        }
    }

//    private void checkClassWhere(LCP<?> property, String name) {
//        ClassWhere<Integer> classWhere = property.getClassWhere(ClassType.signaturePolicy);
//        boolean needWarning = false;
//        if (classWhere.wheres.length > 1) {
//            needWarning = true;
//        } else {
//            AbstractClassWhere.And<Integer> where = classWhere.wheres[0];
//            for (int i = 0; i < where.size(); ++i) {
//                ResolveClassSet acSet = where.getValue(i);
//                if (acSet instanceof UpClassSet && ((UpClassSet)acSet).wheres.length > 1 ||
//                    acSet instanceof OrObjectClassSet && ((OrObjectClassSet)acSet).up.wheres.length > 1) {
//
//                    needWarning = true;
//                    break;
//                }
//            }
//        }
//        if (needWarning) {
//            warningList.add(" Property " + name + " has class where " + classWhere);
//        }
//    }

    private void checkAbstractProperty(LCP property, String propName) throws ScriptingErrorLog.SemanticErrorException {
        if (!(property.property instanceof CaseUnionProperty && ((CaseUnionProperty)property.property).isAbstract())) {
            errLog.emitNotAbstractPropertyError(parser, propName);
        }
    }

    private void checkAbstractAction(LAP action, String actionName) throws ScriptingErrorLog.SemanticErrorException {
        if (!(action.property instanceof ListCaseActionProperty && ((ListCaseActionProperty)action.property).isAbstract())) {
            errLog.emitNotAbstractActionError(parser, actionName);
        }
    }

    private void checkEventNoParameters(LP property) throws ScriptingErrorLog.SemanticErrorException {
        if (property.property.interfaces.size() > 0) {
            errLog.emitEventNoParametersError(parser);
        }
    }

    private void checkChangeClassWhere(boolean contextExtended, LPWithParams param, LPWithParams where, List<TypedParameter> newContext) throws ScriptingErrorLog.SemanticErrorException {
        if (contextExtended && (where == null || !where.usedParams.contains(param.usedParams.get(0)))) {
            errLog.emitChangeClassWhereError(parser, newContext.get(newContext.size() - 1).paramName);
        }
    }

    private void checkAddObjTOParams(int contextSize, List<LPWithParams> toPropMapping) throws ScriptingErrorLog.SemanticErrorException {
        if (toPropMapping != null) {
            for (LPWithParams param : toPropMapping) {
                if (param.usedParams.get(0) < contextSize) {
                    errLog.emitAddObjToPropertyError(parser);
                }
            }
        }
    }

    private void checkAbstractTypes(boolean isCase, boolean implIsCase) throws ScriptingErrorLog.SemanticErrorException {
        if (isCase && !implIsCase) {
            errLog.emitAbstractCaseImplError(parser);
        }
        if (!isCase && implIsCase) {
            errLog.emitAbstractNonCaseImplError(parser);
        }
    }

    private void checkRange(String valueType, int value, int lbound, int rbound) throws ScriptingErrorLog.SemanticErrorException {
        if (value < lbound || value > rbound) {
            errLog.emitOutOfRangeError(parser, valueType, lbound, rbound);
        }
    }

    private void checkImplementIsNotMain(LP mainProp, LP implProp) throws ScriptingErrorLog.SemanticErrorException {
        if (mainProp == implProp) {
            errLog.emitRecursiveImplementError(parser);
        }
    }
    
    public void initModulesAndNamespaces(List<String> requiredModules, List<String> namespacePriority) throws ScriptingErrorLog.SemanticErrorException {
        initNamespacesToModules(this, new HashSet<LogicsModule>());

        if (getNamespace().contains("_")) {
            errLog.emitNamespaceNameError(parser, getNamespace());
        }

        if (namespacePriority.contains(getNamespace())) {
            errLog.emitOwnNamespacePriorityError(parser, getNamespace());
        }

        for (String namespaceName : namespacePriority) {
            checkNamespace(namespaceName);
        }

        for (String moduleName : requiredModules) {
            checkModule(BL.getSysModule(moduleName), moduleName);
        }

        Set<String> prioritySet = new HashSet<>();
        for (String namespaceName : namespacePriority) {
            if (prioritySet.contains(namespaceName)) {
                errLog.emitNonUniquePriorityListError(parser, namespaceName);
            }
            prioritySet.add(namespaceName);
        }
    }

    public void setPropertyScriptInfo(LP property, String script, DebugInfo.DebugPoint point) {
        property.setCreationScript(script);
        property.setCreationPath(point.toString());
    }

    private void parseStep(ScriptParser.State state) throws RecognitionException {
        try {
            parser.initParseStep(this, createStream(), state);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initNamespacesToModules(LogicsModule module, Set<LogicsModule> visitedModules) {
        visitedModules.add(module);
        String namespaceName = module.getNamespace();
        if (!namespaceToModules.containsKey(namespaceName)) {
            namespaceToModules.put(namespaceName, BaseUtils.toList(module));
        } else {
            namespaceToModules.get(namespaceName).add(module);
        }
        for (String requiredModuleName : module.getRequiredModules()) {
            LogicsModule requiredModule = BL.getSysModule(requiredModuleName);
            assert requiredModule != null;
            if (!visitedModules.contains(requiredModule)) {
                initNamespacesToModules(requiredModule, visitedModules);
            }
        }
    }

    @Override
    public void initModuleDependencies() throws RecognitionException {
        parseStep(ScriptParser.State.PRE);
    }

    @Override
    public void initModule() throws RecognitionException {
        parseStep(ScriptParser.State.INIT);
    }

    @Override
    public void initClasses() throws RecognitionException {
        initBaseClassAliases();
        parseStep(ScriptParser.State.CLASS);
    }

    @Override
    public void initTables() throws RecognitionException {
        parseStep(ScriptParser.State.TABLE);
    }

    @Override
    public void initGroups() throws RecognitionException {
        initBaseGroupAliases();
        parseStep(ScriptParser.State.GROUP);
    }

    @Override
    public void initProperties() throws RecognitionException {
        warningList.clear();
        parseStep(ScriptParser.State.PROP);
    }

    @Override
    public void initIndexes() throws RecognitionException {
        parseStep(ScriptParser.State.INDEX);
        for (LCP property : indexedProperties) {
            addIndex(property);
        }
        indexedProperties.clear();
        if (!parser.isInsideMetacode()) {
            showWarnings();
        }
    }

    public void initScriptingModule(String name, String namespace, List<String> requiredModules, List<String> namespacePriority) {
        setModuleName(name);
        setNamespace(namespace == null ? name : namespace);
        setDefaultNamespace(namespace == null);
        if (requiredModules.isEmpty() && !getName().equals("System")) {
            requiredModules.add("System");
        }
        setRequiredModules(new HashSet<>(requiredModules));
        setNamespacePriority(namespacePriority);
    }

    public void initAliases() {
        initBaseGroupAliases();
        initBaseClassAliases();
    }

    private void showWarnings() {
        for (String warningText : warningList) {
            scriptLogger.warn("WARNING!" + warningText);
        }
    }

    @Override
    public String getErrorsDescription() {
        return errLog.toString();
    }

    public static class PropertyUsage {
        public String name;
        public List<String> classNames;
        
        public PropertyUsage(String name) {
            this(name, null);
        }
        
        public PropertyUsage(String name, List<String> classNames) {
            this.name = name;
            this.classNames = classNames;
        }
        
        public String getSourceName() {
            String result = null;
            if (name != null) {
                result = name;
                if (classNames != null) {
                    result += "[";
                    for (String className : classNames) {
                        if (!result.endsWith("[")) {
                            result += ", ";
                        } 
                        result += className;
                    }
                    result += "]";
                }
            }
            return result;
        }
    }
    
    public class TypedParameter {
        public ValueClass cls;
        public String paramName;
        
        public TypedParameter(ValueClass cls, String name) {
            this.cls = cls;
            paramName = name;
        }  
        
        public TypedParameter(String cName, String pName) throws ScriptingErrorLog.SemanticErrorException {
            if (cName != null) {
                cls = findClass(cName);
            } else {
                cls = null;
            }                                
            paramName = pName;
        }
        
        public String getParsedName() {
            if(cls != null)
                return cls.getParsedName();
            return null;
        }
    }

    public static class FormActionProps {
        public final LPWithParams in;
        public final Boolean inNull;

        public final boolean out;
        public final Integer outParamNum; 
        public final Boolean outNull;
        public final PropertyUsage outProp;

        public final LPWithParams changeProp;

        public final boolean assign;
        public final DebugInfo.DebugPoint assignDebugPoint;
        public final boolean constraintFilter;


        public FormActionProps(LPWithParams in, Boolean inNull, boolean out, Integer outParamNum, Boolean outNull, PropertyUsage outProp, boolean constraintFilter, boolean assign, LPWithParams changeProp, DebugInfo.DebugPoint changeDebugPoint) {
            this.in = in;
            this.inNull = inNull;
            this.out = out;
            this.outParamNum = outParamNum;
            this.outNull = outNull;
            this.outProp = outProp;
            this.constraintFilter = constraintFilter;
            this.assign = assign;
            this.changeProp = changeProp;
            this.assignDebugPoint = changeDebugPoint;
        }
    }

    public class CompoundNameResolver<T, P> {
        private ModuleFinder<T, P> finder;

        public CompoundNameResolver(ModuleFinder<T, P> finder) {
            this.finder = finder;
        }

        protected List<FoundItem<T>> findInNamespace(String namespaceName, String name, P param) throws ScriptingErrorLog.SemanticErrorException {
            NamespaceElementFinder<T, P> nsFinder = new NamespaceElementFinder<>(finder, namespaceToModules.get(namespaceName));
            return finalizeNamespaceResult(nsFinder.findInNamespace(namespaceName, name, param), name, param);
        }

        protected List<FoundItem<T>> finalizeNamespaceResult(List<FoundItem<T>> result, String name, P param) throws ScriptingErrorLog.SemanticErrorException {
            FoundItem<T> finalRes = finalizeResult(result, name, param);
            return finalRes.value == null ? new ArrayList<FoundItem<T>>() : Collections.singletonList(finalRes); 
        } 
        
        // реализация по умолчанию, предполагающая, что не может быть более одного подходящего объекта
        protected FoundItem<T> finalizeResult(List<FoundItem<T>> result, String name, P param) throws ScriptingErrorLog.SemanticErrorException {
            if (result.isEmpty()) return new FoundItem<>(null, null);
            if (result.size() > 1) {
                List<LogicsModule> resModules = new ArrayList<>();
                for (FoundItem<T> item : result) {
                    resModules.add(item.module);
                }
                errLog.emitAmbiguousNameError(parser, resModules, name);    
            }
            return result.get(0);
        } 
        
        private T findInRequiredModules(String name, P param, List<String> namespaces) throws ScriptingErrorLog.SemanticErrorException {
            for (String namespaceName : namespaces) {
                List<FoundItem<T>> result = findInNamespace(namespaceName, name, param);
                if (!result.isEmpty()) {
                    return finalizeResult(result, name, param).value;
                }
            }

            List<FoundItem<T>> resultList = new ArrayList<>();
            for (Map.Entry<String, List<LogicsModule>> e : namespaceToModules.entrySet()) {
                for (LogicsModule module : e.getValue()) {
                    List<T> moduleResult = finder.resolveInModule(module, name, param);
                    for (T obj : moduleResult) {
                        resultList.add(new FoundItem<>(obj, module));
                    }
                }
            }
            return finalizeResult(resultList, name, param).value;
        }

        public final T resolve(String name) throws ScriptingErrorLog.SemanticErrorException {
            return resolve(name, null);
        } 
        
        public final T resolve(String name, P param) throws ScriptingErrorLog.SemanticErrorException {
            T result;
            int dotPosition = name.indexOf('.');
            if (dotPosition > 0) {
                String namespaceName = name.substring(0, dotPosition);
                checkNamespace(namespaceName);
                List<FoundItem<T>> foundItems = findInNamespace(namespaceName, name.substring(dotPosition + 1), param);
                return finalizeResult(foundItems, name, param).value;
            } else {
                List<String> namespaces = new ArrayList<>();
                namespaces.add(getNamespace());
                namespaces.addAll(getNamespacePriority());
                result = findInRequiredModules(name, param, namespaces);
            }
            return result;
        }
    }
    
    public class LPResolver extends CompoundNameResolver<LP<?, ?>, List<ResolveClassSet>> {
        private final boolean filter;
        private final boolean prioritizeNotEquals;
        
        class AmbiguousPropertyError extends ScriptingErrorLog.SemanticErrorException {
            public List<FoundItem<LP<?, ?>>> items;
            public AmbiguousPropertyError(IntStream input, List<FoundItem<LP<?, ?>>> items) {
                super(input);
                this.items = items;
            }
        }
        
        public LPResolver(ModuleFinder<LP<?, ?>, List<ResolveClassSet>> finder, boolean filter, boolean prioritizeNotEquals) {
            super(finder);
            this.filter = filter;
            this.prioritizeNotEquals = prioritizeNotEquals;
        }

        @Override
        protected List<FoundItem<LP<?, ?>>> finalizeNamespaceResult(List<FoundItem<LP<?, ?>>> result, String name, List<ResolveClassSet> param) throws ScriptingErrorLog.SemanticErrorException {
            return result;
        }

        @Override
        protected FoundItem<LP<?, ?>> finalizeResult(List<FoundItem<LP<?, ?>>> result, String name, List<ResolveClassSet> param) throws ScriptingErrorLog.SemanticErrorException {
            FoundItem<LP<?, ?>> finalItem = new FoundItem<>(null, null);
            if (!result.isEmpty()) {
                if (filter) {
                    if (prioritizeNotEquals) {
                        result = prioritizeNotEquals(result, param);
                    }
                    result = NamespacePropertyFinder.filterFoundProperties(result);
                }
                if (result.size() > 1) {
                    throw new AmbiguousPropertyError(parser.getCurrentParser().input, result);
                } else if (result.size() == 1) {
                    finalItem = result.get(0);
                }
            } 
            return finalItem;
        }

        private List<FoundItem<LP<?, ?>>> prioritizeNotEquals(List<FoundItem<LP<?, ?>>> result, List<ResolveClassSet> param) {
            assert !result.isEmpty();
            List<FoundItem<LP<?, ?>>> equals = new ArrayList<>();
            List<FoundItem<LP<?, ?>>> notEquals = new ArrayList<>();
            for (FoundItem<LP<?, ?>> item : result) {
                if (!BaseUtils.nullHashEquals(item.module.getParamClasses(item.value), param))
                    notEquals.add(item);
                else
                    equals.add(item);
            }

            if(!notEquals.isEmpty())
                return notEquals;
            else
                return equals;
        }
    }
}
