package lsfusion.server.logics.scripted;

import lsfusion.server.data.expr.formula.SQLSyntaxType;
import lsfusion.server.logics.LogicsModule;
import lsfusion.server.logics.linear.LP;
import lsfusion.server.logics.resolving.NamespaceElementFinder.FoundItem;
import org.antlr.runtime.BaseRecognizer;
import org.antlr.runtime.IntStream;
import org.antlr.runtime.RecognitionException;

import java.io.StringWriter;
import java.util.List;

import static java.lang.String.format;

public class ScriptingErrorLog {
    public static class SemanticErrorException extends RecognitionException {
        private String msg;

        public SemanticErrorException(IntStream input) {
            super(input);
        }

        public void setMessage(String msg) {
            this.msg = msg;
        }

        @Override
        public String getMessage() { return msg; }
    }

    private final StringWriter errWriter = new StringWriter();
    private String moduleName;

    public ScriptingErrorLog(String moduleName) {
        this.moduleName = moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public void write(String s) {
        errWriter.write(s);
    }

    public String toString() {
        return errWriter.toString();
    }

    public static String getErrorMessage(BaseRecognizer parser, String oldMsg, RecognitionException e) {
        return /*BaseRecognizer.getRuleInvocationStack(e, parser.findClass().getName()) + " " + */ oldMsg;
    }

    public String getRecognitionErrorText(ScriptParser parser, String errorType, String msg, RecognitionException e) {
        String path = parser.getCurrentScriptPath(moduleName, e.line, "\n\t\t\t");
        String hdr = path + ":" + (e.charPositionInLine + 1);
        return "[" + errorType + "]:\t" + hdr + " " + msg;
    }

    public String getSemanticRecognitionErrorText(String msg, ScriptParser parser, RecognitionException e) {
        return getRecognitionErrorText(parser, "error", getErrorMessage(parser.getCurrentParser(), msg, e), e) + "Subsequent errors (if any) could not be found.";
    }

    public void displayRecognitionError(BaseRecognizer parser, ScriptParser scriptParser, String errorType, String[] tokenNames, RecognitionException e) {
        String msg = parser.getErrorMessage(e, tokenNames);
        parser.emitErrorMessage(getRecognitionErrorText(scriptParser,  errorType, msg, e));
    }

    public static void emitSemanticError(String msg, SemanticErrorException e) throws SemanticErrorException {
        e.setMessage(msg);
        throw e;
    }

    public void emitNotFoundError(ScriptParser parser, String objectName, String name) throws SemanticErrorException {
        emitSimpleError(parser, format("%s '%s' not found", objectName, name));
    }

    public void emitClassNotFoundError(ScriptParser parser, String name) throws SemanticErrorException {
        emitNotFoundError(parser, "class", name);
    }

    public void emitGroupNotFoundError(ScriptParser parser, String name) throws SemanticErrorException {
        emitNotFoundError(parser, "group", name);
    }

    public void emitPropertyNotFoundError(ScriptParser parser, String name) throws SemanticErrorException {
        emitNotFoundError(parser, "property", name);
    }

    public void emitActionNotFoundError(ScriptParser parser, String name) throws SemanticErrorException {
        emitNotFoundError(parser, "action", name);
    }

    public void emitModuleNotFoundError(ScriptParser parser, String name) throws SemanticErrorException {
        emitNotFoundError(parser, "module", name);
    }

    public void emitParamNotFoundError(ScriptParser parser, String name) throws SemanticErrorException {
        emitNotFoundError(parser, "parameter", name);
    }

    public void emitFormNotFoundError(ScriptParser parser, String name) throws SemanticErrorException {
        emitNotFoundError(parser, "form", name);
    }

    public void emitMetaCodeFragmentNotFoundError(ScriptParser parser, String name) throws SemanticErrorException {
        emitNotFoundError(parser, "meta code", name);
    }

    public void emitObjectNotFoundError(ScriptParser parser, String name) throws SemanticErrorException {
        emitNotFoundError(parser, "object", name);
    }

    public void emitComponentNotFoundError(ScriptParser parser, String name) throws SemanticErrorException {
        emitNotFoundError(parser, "component", name);
    }

    public void emitNavigatorElementNotFoundError(ScriptParser parser, String name) throws SemanticErrorException {
        emitNotFoundError(parser, "navigator element", name);
    }

    public void emitTableNotFoundError(ScriptParser parser, String name) throws SemanticErrorException {
        emitNotFoundError(parser, "table", name);
    }

    public void emitWindowNotFoundError(ScriptParser parser, String name) throws SemanticErrorException {
        emitNotFoundError(parser, "window", name);
    }

    public void emitFilterGroupNotFoundError(ScriptParser parser, String name) throws SemanticErrorException {
        emitNotFoundError(parser, "filter group", name);
    }

    public void emitIllegalAddNavigatorToSubnavigator(ScriptParser parser, String addedElement, String addedToElement) throws SemanticErrorException {
        emitSimpleError(parser, format("can't add navigator element '%s' to it's subelement '%s'", addedElement, addedToElement));
    }

    public void emitWrongNavigatorAction(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "navigator action should not have arguments");
    }
    
    public void emitIllegalInsertBeforeAfterElement(ScriptParser parser, String element, String parentElement, String anchorElement) throws SemanticErrorException {
        emitSimpleError(parser, format("can't insert '%s' after or before '%s' in '%s'", element, anchorElement, parentElement));
    }

    public void emitIllegalNavigatorElementMove(ScriptParser parser, String element, String parentElement) throws SemanticErrorException {
        emitSimpleError(parser, format("can't move '%s' because it's not a direct child of '%s'", element, parentElement));
    }

    public void emitIllegalParentNavigatorElement(ScriptParser parser, String parentElement) throws SemanticErrorException {
        emitSimpleError(parser, format("element '%s' can't be a parent element because it's not a navigator folder", parentElement));
    }
    
    public void emitGroupObjectInTreeAfterBeforeError(ScriptParser parser, String groupObject) throws SemanticErrorException {
        emitSimpleError(parser, "'" + groupObject + "' is in tree group - can't use it in AFTER/BEFORE");
    }

    public void emitComponentParentError(ScriptParser parser, String compName) throws SemanticErrorException {
        emitSimpleError(parser, format("component '%s' has no parent", compName));
    }
    
    public void emitComponentMustBeAContainerError(ScriptParser parser, String componentName) throws SemanticErrorException {
        emitSimpleError(parser, format("component '%s' must be a container", componentName));
    }

    public void emitIllegalMoveComponentToSubcomponent(ScriptParser parser, String movingComponent, String movedToComponent) throws SemanticErrorException {
        emitSimpleError(parser, format("can't move component '%s' to it's subcomponent '%s'", movingComponent, movedToComponent));
    }

    public void emitRemoveMainContainerError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "can't remove main container");
    }

    public void emitUnableToSetPropertyError(ScriptParser parser, String propertyName, String cause) throws SemanticErrorException {
        emitSimpleError(parser, "unable to set property '" + propertyName + "'. Cause: " + cause);
    }

    public void emitWrongKeyStrokeFormat(ScriptParser parser, String ksLiteral) throws SemanticErrorException {
        emitSimpleError(parser, "can't create keystroke from string '" + ksLiteral + "'");
    }

    public void emitWindowPositionNotSpecified(ScriptParser parser, String sid) throws SemanticErrorException {
        emitSimpleError(parser, "position ( POSITION(x, y, width, height) ) isn't specified for window '" + sid + "'");
    }

    public void emitWindowPositionConflict(ScriptParser parser, String sid) throws SemanticErrorException {
        emitSimpleError(parser, "both border position (LEFT, RIGHT, TOP or BOTTOM) and dock position (POSITION(x, y, widht, height)) are specified for window '" + sid + "', " +
                                "only one of those should be used");
    }

    public void emitAddToSystemWindowError(ScriptParser parser, String sid) throws SemanticErrorException {
        emitSimpleError(parser, "it's illegal to add navigator element to system window '" + sid + "'");
    }

    public void emitFormulaParamIndexError(ScriptParser parser, int paramIndex, int paramCount) throws SemanticErrorException {
        String errText = "wrong parameter index $" + String.valueOf(paramIndex);
        if (paramIndex < 1) {
            errText += ", first parameter is $1";
        } else {
            errText += ", last parameter is $" + String.valueOf(paramCount);
        }
        emitSimpleError(parser, errText);
    }

    public void emitFormulaMultipleImplementationError(ScriptParser parser, SQLSyntaxType type) throws SemanticErrorException {
        emitSimpleError(parser, "two implementations for syntax " + (type == null ? "DEFAULT" : type));
    }

    public void emitFormulaDifferentParamCountError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "implementations have different number of parameters");
    }
    
    public void emitParamClassRedefinitionError(ScriptParser parser, String paramName) throws SemanticErrorException {
        emitSimpleError(parser, format("class of parameter '%s' was already defined", paramName));
    }

    public void emitParamClassNonDeclarationError(ScriptParser parser, String paramName) throws SemanticErrorException {
        emitSimpleError(parser, format("class of parameter '%s' should be defined at first usage", paramName));
    }

    public void emitBuiltInClassAsParentError(ScriptParser parser, String className) throws SemanticErrorException {
        emitSimpleError(parser, format("built-in class '%s' cannot be inherited", className));
    }

    public void emitBuiltInClassFormSetupError(ScriptParser parser, String className) throws SemanticErrorException {
        emitSimpleError(parser, "can't set custom form for built-in class '" + className + "'");
    }

    public void emitCustomClassExpectedError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "custom class expected");
    }

    public void emitCustomClassExpectedError(ScriptParser parser, String propertyName) throws SemanticErrorException {
        emitSimpleError(parser, format("custom class parameter expected for property '%s'", propertyName));
    }

    public void emitAbstractClassInstancesDefError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "abstract class cannot be instantiated");
    }

    public void emitAbstractClassInstancesUseError(ScriptParser parser, String className, String objectName) throws SemanticErrorException {
        emitSimpleError(parser, format("static object '%s' not found (class '%s' is abstract)", objectName, className));
    }

    public void emitParamCountError(ScriptParser parser, LP property, int paramCount) throws SemanticErrorException {
        int interfacesCount = property.property.interfaces.size();
        emitParamCountError(parser, interfacesCount, paramCount);
    }

    public void emitParamCountError(ScriptParser parser, int interfacesCount, int paramCount) throws SemanticErrorException {
        emitElementCountError(parser, "parameter(s)", interfacesCount, paramCount);
    }
    
    public void emitElementCountError(ScriptParser parser, String elementName, int expected, int provided) throws SemanticErrorException {
        emitSimpleError(parser, format("%d %s expected, %d provided", expected, elementName, provided));    
    }

    public void emitConstraintPropertyAlwaysNullError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "constrained property is always NULL");
    }

    public void emitAlreadyDefinedError(ScriptParser parser, String type, String name) throws SemanticErrorException {
        emitSimpleError(parser, format("%s '%s' was already defined", type, name));
    }

    public <T> void emitAlreadyDefinedError(ScriptParser parser, String type, String name, List<FoundItem<T>> items) throws SemanticErrorException {
        assert !items.isEmpty();
        StringBuilder formatStringBuilder = new StringBuilder(format("%s '%s' was already defined in modules:", type, name));
        for (FoundItem<T> item : items) {
            formatStringBuilder.append("\n\t\t");
            formatStringBuilder.append(item.toString());
        }
        emitSimpleError(parser, formatStringBuilder.toString());
    }

    public void emitAlreadyDefinedPropertyDraw(ScriptParser parser, String formName, String propertyDrawName, String oldPosition) throws SemanticErrorException {
        emitSimpleError(parser, format("property '%s' in form '%s' was already defined at %s", propertyDrawName, formName, oldPosition));
    }

    public void emitNamedParamsError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "number of named parameters should be equal to actual number of parameters");
    }

    public void emitFormulaReturnClassError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "formula return class must be a built-in class");
    }

    public void emitCIInExpr(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "BY clause in GROUP operator cannot be used in expressions");
    }

    public void emitFormDataClassError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "form class must be a built-in class");
    }

    public void emitInputDataClassError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "Input class must be a built-in class");
    }

    public void emitIncompatibleTypes(ScriptParser parser, String propType) throws SemanticErrorException {
        emitSimpleError(parser, format("%s's arguments' types don't match", propType));
    }

    public void emitMetaCodeNotEndedError(ScriptParser parser, String name) throws SemanticErrorException {
        emitSimpleError(parser, format("meta code '%s' does not end with END keyword", name));
    }

    public void emitJavaCodeNotEndedError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "java code does not end with END keyword");
    }

    public void emitDistinctParamNamesError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "names of parameters should be distinct");
    }

    public void emitRedundantOrderGPropError(ScriptParser parser, ScriptingLogicsModule.GroupingType groupType) throws SemanticErrorException {
        emitSimpleError(parser, format("ORDER clause is forbidden with '%s' grouping type", groupType));
    }

    public void emitMultipleAggrGPropError(ScriptParser parser, ScriptingLogicsModule.GroupingType groupType) throws SemanticErrorException {
        emitSimpleError(parser, format("multiple aggregate properties are forbidden with '%s' grouping type", groupType));
    }

    public void emitConcatAggrGPropError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "GROUP CONCAT property should have two aggregate properties exactly (second is a separator)");
    }

    public void emitNonIntegralSumArgumentError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "GROUP SUM main property should have integral class as return value");
    }
    
    public void emitNonObjectAggrGPropError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "GROUP AGGR should have simple parameter as aggregate function");
    }

    public void emitWhereGPropError(ScriptParser parser, ScriptingLogicsModule.GroupingType groupType) throws SemanticErrorException {
        emitSimpleError(parser, format("WHERE clause is forbidden with '%s' grouping type", groupType));
    }

    public void emitDifferentObjsNPropsQuantity(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "number of properties specified after PARENT should be equal to number of objects");
    }

    public void emitCreatingClassInstanceError(ScriptParser parser, String exceptionMessage, String className) throws SemanticErrorException {
        emitSimpleError(parser, String.format("error '%s' occurred during creation of %s instance", exceptionMessage, className));
    }

    public void emitNotActionPropertyError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "should be an action here");
    }

    public void emitNotCalculationPropertyError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "should be a property here");
    }

    public void emitNotSessionOrLocalPropertyError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "should be a session or local property here");
    }

    public void emitExtendActionContextError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "action parameters must be defined explicitly");
    }

    public void emitForActionSameContextError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "FOR action statement must introduce new parameters, use IF or WHILE instead");
    }

    public void emitNestedRecursionError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "RECURSION property inside another recursive step is forbidden");
    }

    public void emitRecursiveParamsOutideRecursionError(ScriptParser parser, String paramName) throws SemanticErrorException {
        emitSimpleError(parser, format("recursive parameter '%s' outside recursive step is forbidden", paramName));
    }

    public void emitParameterNotUsedInRecursionError(ScriptParser parser, String paramName) throws SemanticErrorException {
        emitSimpleError(parser, format("there is no '%s' inside RECURSION", paramName));
    }

    public void emitAddActionsClassError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "built-in class cannot be used in NEW/ADDFORM/EDITFORM actions");
    }

    public void emitNecessaryPropertyError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "single parameter is forbidden in this context");
    }

    public void emitDeconcatIndexError(ScriptParser parser, int index, int size) throws SemanticErrorException {
        if (index == 0) {
            emitSimpleError(parser, "indices are one-based");
        } else if (index > size) {
            emitSimpleError(parser, format("wrong index, should be at most %d", size));
        }
    }

    public void emitDeconcatError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "expression is not a list");
    }

    public void emitIllegalWindowPartitionError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "WINDOW allowed only in SUM and PREV types of PARTITION");
    }

    public void emitUngroupParamsCntPartitionError(ScriptParser parser, int groupPropCnt) throws SemanticErrorException {
        emitSimpleError(parser, format("UNGROUP property should have %d parameter(s)", groupPropCnt));
    }

    public void emitChangeClassActionClassError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "class cannot be built-in or abstract");
    }

    public void emitWrongClassesForTable(ScriptParser parser, String property, String table) throws SemanticErrorException {
        emitSimpleError(parser, format("property '%s' can't be included into '%s' table: wrong classes", property, table));
    }

    public void emitNotAbstractPropertyError(ScriptParser parser, String propName) throws SemanticErrorException {
        emitSimpleError(parser, format("property '%s' is not ABSTRACT", propName));
    }

    public void emitNotAbstractActionError(ScriptParser parser, String propName) throws SemanticErrorException {
        emitSimpleError(parser, format("action '%s' is not ABSTRACT", propName));
    }

    public void emitRequestUserInputDataTypeError(ScriptParser parser, String typeName) throws SemanticErrorException {
        emitSimpleError(parser, format("type '%s' cannot be used with INPUT option", typeName));
    }

    public void emitOwnNamespacePriorityError(ScriptParser parser, String namespaceName) throws SemanticErrorException {
        emitSimpleError(parser, format("namespace '%s' has maximum priority level and should be deleted from the PRIORITY list", namespaceName));
    }

    public void emitNamespaceNotFoundError(ScriptParser parser, String namespaceName) throws SemanticErrorException {
        emitSimpleError(parser, format("namespace '%s' was not found in required modules", namespaceName));
    }

    public void emitNonUniquePriorityListError(ScriptParser parser, String namespaceName) throws SemanticErrorException {
        emitSimpleError(parser, format("priority list contains namespace '%s' more than once", namespaceName));
    }

    public void emitEventNoParametersError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "event should have no parameters");
    }

    public void emitAmbiguousNameError(ScriptParser parser, List<LogicsModule> modules, String name) throws SemanticErrorException {
        String msg = String.format("ambiguous name '%s', list of modules:", name);
        for (int i = 0; i < modules.size(); i++) {
            if (i > 0) {
                msg = msg + ", ";
            }
            msg = msg + " " + modules.get(i).getName() + " (namespace " + modules.get(i).getNamespace() + ")";
        }
        emitSimpleError(parser, msg);
    }

    public void emitAmbiguousPropertyNameError(ScriptParser parser, List<FoundItem<LP<?, ?>>> foundItems, String name) throws SemanticErrorException {
        StringBuilder msg = new StringBuilder(String.format("ambiguous name '%s', was found in modules:", name));
        for (FoundItem<LP<?, ?>> item : foundItems) {
            msg.append("\n\t").append(item.toString());                
        }
        emitSimpleError(parser, msg.toString());
    }

    public void emitNeighbourPropertyError(ScriptParser parser, String name1, String name2) throws SemanticErrorException {
        emitSimpleError(parser, format("properties '%s' and '%s' should be in one group", name1, name2));
    }

    public void emitMetacodeInsideMetacodeError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "metacode cannot be defined inside another metacode");
    }

    public void emitNamespaceNameError(ScriptParser parser, String namespaceName) throws SemanticErrorException {
        emitSimpleError(parser, format("namespace name '%s' contains underscore character", namespaceName));
    }

    public void emitDuplicateClassParentError(ScriptParser parser, String className) throws SemanticErrorException {
        emitSimpleError(parser, format("class '%s' is a parent already", className));
    }

    public void emitEvalExpressionError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "ACTION EVAL expression should be string");
    }

    public void emitChangeClassWhereError(ScriptParser parser, String paramName) throws SemanticErrorException {
        emitSimpleError(parser, format("local param '%s' must be used in WHERE clause", paramName));
    }

    public void emitAddObjToPropertyError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "TO clause should use only local parameters introduced in WHERE clause");
    }

    public void emitWrongPropertyParametersError(ScriptParser parser, String propertyName) throws SemanticErrorException {
        emitSimpleError(parser, format("wrong parameters are passed to the property '%s'", propertyName));
    }

    public void emitWrongPropertyParameterError(ScriptParser parser, String paramName, String paramClass, String actualParamClass) throws SemanticErrorException {
        emitSimpleError(parser, format("parameter '%s' of class %s has actual class %s", paramName, paramClass, actualParamClass));
    }
    
    public void emitOnlyDataCasePropertyIsAllowedError(ScriptParser parser, String propertyName) throws SemanticErrorException {
        emitSimpleError(parser, format("'%s' is only allowed to be DATA/MULTI/CASE property", propertyName));
    }

    public void emitOnlyDataPropertyIsAllowedError(ScriptParser parser, String propertyName) throws SemanticErrorException {
        emitSimpleError(parser, format("'%s' is only allowed to be DATA property", propertyName));
    }

    public void emitStrLiteralEscapeSequenceError(ScriptParser parser, char ch) throws SemanticErrorException {
        emitSimpleError(parser, format("wrong escape sequence: '\\%c'", ch));
    }

    public void emitColorComponentValueError(ScriptParser parser) throws SemanticErrorException {
        emitOutOfRangeError(parser, "color component", 0, 255);
    }

    public void emitOutOfRangeError(ScriptParser parser, String valueType, int lbound, int rbound) throws SemanticErrorException {
        emitSimpleError(parser, format("%s is out of range (%d-%d)", valueType, lbound, rbound));
    }

    public void emitIntegerValueError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "absolute value of INTEGER constant should be less than 2147483648 (2^31), use LONG or NUMERIC instead");
    }

    public void emitLongValueError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "absolute value of LONG constant should be less than 2^63, use NUMERIC instead");
    }

    public void emitDoubleValueError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "double constant is out of range");
    }

    public void emitDateDayError(ScriptParser parser, int y, int m, int d) throws SemanticErrorException {
        emitSimpleError(parser, format("wrong date %04d-%02d-%02d", y, m, d));
    }

    public void emitAbstractCaseImplError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "abstract CASE implementation needs WHEN ... THEN block");
    }

    public void emitAbstractNonCaseImplError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "WHEN ... THEN block should be used only with CASE abstract");
    }
    
    public void emitIndexWithoutPropertyError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "index should contain at least one property");
    }
    
    public void emitIndexPropertiesNonEqualParamsCountError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "properties in INDEX statement should have the same number of parameters");    
    }

    public void emitIndexParametersError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "all parameters that can be found in INDEX statement should be passed to every property in INDEX statement");
    }
     
    public void emitShouldBeStoredError(ScriptParser parser, String propertyName) throws SemanticErrorException {
        emitSimpleError(parser, format("property '%s' should be persistent", propertyName));
    }

    public void emitIndexPropertiesDifferentTablesError(ScriptParser parser, String firstPropName, String secondPropName) throws SemanticErrorException {
        emitSimpleError(parser, format("properties '%s' and '%s' should be in one table to create index", firstPropName, secondPropName));
    }
    
    public void emitObjectOfGroupObjectError(ScriptParser parser, String objName, String groupObjName) throws SemanticErrorException {
        emitSimpleError(parser, format("group object '%s' does not contain object '%s'", groupObjName, objName));
    }
    
    public void emitImportNonIntegralSheetError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "Sheet index should have INTEGER or LONG value");
    } 
    
    public void emitNavigatorElementFolderNameError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "Navigator folder name should be defined");
    }

    public void emitImportFromWrongClassError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "FROM expression should return FILE value");
    }

    public void emitPropertyWithParamsExpected(ScriptParser parser, String property, String paramClasses) throws SemanticErrorException {
        emitSimpleError(parser, format("expected property with (%s) param classes: %s", paramClasses, property));
    }

    public void emitRecursiveImplementError(ScriptParser parser) throws SemanticErrorException {
        emitSimpleError(parser, "recursive implement");
    }    
    
    public void emitSimpleError(ScriptParser parser, String message) throws SemanticErrorException {
        if (parser.getCurrentParser() != null) {
            SemanticErrorException e = new SemanticErrorException(parser.getCurrentParser().input);
            String msg = getSemanticRecognitionErrorText(message + "\n", parser, e);
            emitSemanticError(msg, e);
        } else {
            throw new RuntimeException(message);
        }
    }
}
