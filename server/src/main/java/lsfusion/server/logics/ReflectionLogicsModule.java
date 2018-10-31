package lsfusion.server.logics;

import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.LogicalClass;
import lsfusion.server.classes.StringClass;
import lsfusion.server.classes.sets.ResolveClassSet;
import lsfusion.server.logics.linear.LAP;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.CurrentFormFormulaProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.logics.table.ImplementTable;
import org.antlr.runtime.RecognitionException;

import java.io.IOException;
import java.util.ArrayList;

public class ReflectionLogicsModule extends ScriptingLogicsModule {

    public ConcreteCustomClass propertyGroup;
    public ConcreteCustomClass navigatorElement;
    public ConcreteCustomClass navigatorFolder;
    public ConcreteCustomClass navigatorAction;
    public ConcreteCustomClass navigatorForm;
    public ConcreteCustomClass form;
    public ConcreteCustomClass propertyDraw;
    public ConcreteCustomClass propertyDrawShowStatus;
    public ConcreteCustomClass table;
    public ConcreteCustomClass tableKey;
    public ConcreteCustomClass tableColumn;
    public ConcreteCustomClass dropColumn;
    public ConcreteCustomClass property;
    public ConcreteCustomClass action;
    public ConcreteCustomClass groupObject;
    
    public LCP captionPropertyGroup;
    public LCP captionNavigatorElement;
    public LCP parentPropertyGroup;
    public LCP numberPropertyGroup;
    public LCP SIDPropertyGroup;
    public LCP propertyGroupSID;

    public LCP parentProperty;
    public LCP numberProperty;
    public LCP dbNameProperty;
    public LCP canonicalNameActionOrProperty;
    public LCP canonicalNameAction;
    public LCP canonicalNameProperty;
    public LCP loggableProperty;
    public LCP userLoggableProperty;
    public LCP storedProperty;
    public LCP isSetNotNullProperty;
    public LCP signatureProperty;
    public LCP returnProperty;
    public LCP classProperty;
    public LCP complexityProperty;
    public LCP captionProperty;
    public LCP tableSIDProperty;
    public LCP annotationProperty;
    public LCP statsProperty;
    public LCP overStatsProperty;
    public LCP maxStatsProperty;
    public LCP propertyCanonicalName;
    public LCP actionCanonicalName;
    public LCP propertyTableSID;
    public LCP quantityProperty;
    public LCP quantityTopProperty;
    public LCP notNullQuantityProperty;
    public LCP lastRecalculateProperty;
    public LCP hasNotNullQuantity;

    public LCP numberNavigatorElement;
    
    // temporary for migration
    public ImplementTable navigatorElementTable;

    public LCP navigatorElementCanonicalName;
    public LCP canonicalNameNavigatorElement;
    public LCP formCanonicalName;
    public LCP formByCanonicalName;

    public LCP parentNavigatorElement;
    public LCP formNavigatorAction; 
    public LCP actionNavigatorAction; 
    
    public LCP formCaption;
    public LCP isForm;
    public LCP isNavigatorAction;
    public LCP isNavigatorForm;
    public LCP isNavigatorFolder;

    public LCP sidGroupObject;
    public LCP formGroupObject;
    public LCP groupObjectSIDFormNameGroupObject;

    public LCP sidPropertyDraw;
    public LCP captionPropertyDraw;
    public LCP formPropertyDraw;
    public LCP groupObjectPropertyDraw;
    public LCP propertyDrawByFormNameAndPropertyDrawSid;

    public LCP showPropertyDraw;
    public LCP showPropertyDrawCustomUser;
    
    public LCP nameShowPropertyDraw;
    public LCP nameShowPropertyDrawCustomUser;

    public LCP columnCaptionPropertyDrawCustomUser;
    public LCP columnCaptionPropertyDraw;
    public LCP columnPatternPropertyDrawCustomUser;
    public LCP columnPatternPropertyDraw;
    public LCP columnWidthPropertyDrawCustomUser;
    public LCP columnWidthPropertyDraw;
    public LCP columnOrderPropertyDrawCustomUser;
    public LCP columnOrderPropertyDraw;
    public LCP columnSortPropertyDrawCustomUser;
    public LCP columnSortPropertyDraw;
    public LCP columnAscendingSortPropertyDrawCustomUser;
    public LCP columnAscendingSortPropertyDraw;
    public LCP hasUserPreferencesGroupObject;
    public LCP hasUserPreferencesGroupObjectCustomUser;
    public LCP hasUserPreferencesOverrideGroupObjectCustomUser;
    public LCP fontSizeGroupObject;
    public LCP fontSizeGroupObjectCustomUser;
    public LCP isFontBoldGroupObject;
    public LCP isFontBoldGroupObjectCustomUser;
    public LCP isFontItalicGroupObject;
    public LCP isFontItalicGroupObjectCustomUser;
    public LCP pageSizeGroupObject;
    public LCP pageSizeGroupObjectCustomUser;
    public LCP headerHeightGroupObject;
    public LCP headerHeightGroupObjectCustomUser;
    
    public LCP nameFormGrouping;
    public LCP itemQuantityFormGrouping;
    public LCP groupObjectFormGrouping;
    public LCP formGroupingNameFormGroupingGroupObject;
    public LCP groupOrderFormGroupingPropertyDraw;
    public LCP sumFormGroupingPropertyDraw;
    public LCP maxFormGroupingPropertyDraw;
    public LCP pivotFormGroupingPropertyDraw;
    
    public LCP sidTable;
    public LCP tableSID;
    public LCP rowsTable;
    public LCP tableTableKey;
    public LCP sidTableKey;
    public LCP tableKeySID;
    public LCP classTableKey;
    public LCP classSIDTableKey;
    public LCP nameTableKey;
    public LCP quantityTableKey;
    public LCP quantityTopTableKey;
    public LCP overQuantityTableKey;
    public LCP tableTableColumn;
    public LCP propertyTableColumn;
    public LCP sidTableColumn;
    public LCP longSIDTableColumn;
    public LCP tableColumnLongSID;
    public LCP tableColumnSID;

    public LCP overQuantityTableColumn;
    public LCP notNullQuantityTableColumn;
    public LCP notRecalculateTableColumn;
    public LAP recalculateAggregationTableColumn;

    public LCP notRecalculateSID;
    public LCP notRecalculateStatsSID;

    public LCP<?> sidTableDropColumn;
    public LCP<?> sidDropColumn;
    public LCP dropColumnSID;

    public LCP timeDropColumn;
    public LCP revisionDropColumn;
    public LAP dropDropColumn;

    public final StringClass navigatorElementSIDClass = StringClass.get(50);
    public final StringClass navigatorElementCanonicalNameClass = StringClass.getv(100);
    public final StringClass navigatorElementCaptionClass = StringClass.get(250);
    public final StringClass formCanonicalNameClass = StringClass.getv(100);
    public final StringClass actionCanonicalNameClass = StringClass.get(512);
    public final StringClass formCaptionClass = StringClass.getv(250);
    public final StringClass propertySIDValueClass = StringClass.get(100);
    public final StringClass propertyCanonicalNameValueClass = StringClass.get(512);
    public final StringClass propertyCaptionValueClass = StringClass.get(250);
    public final StringClass propertyClassValueClass = StringClass.get(100);
    public final StringClass propertyTableValueClass = StringClass.get(100);
    public final StringClass propertyDrawSIDClass = StringClass.get(100);
    public final LogicalClass propertyLoggableValueClass = LogicalClass.instance;
    public final LogicalClass propertyStoredValueClass = LogicalClass.instance;
    public final LogicalClass propertyIsSetNotNullValueClass = LogicalClass.instance;

    public LCP currentForm;

    public ReflectionLogicsModule(BusinessLogics BL, BaseLogicsModule baseLM) throws IOException {
        super(ReflectionLogicsModule.class.getResourceAsStream("/system/Reflection.lsf"), "/system/Reflection.lsf", baseLM, BL);
    }

    @Override
    public void initMetaGroupsAndClasses() throws RecognitionException {
        super.initMetaGroupsAndClasses();
        propertyGroup = (ConcreteCustomClass) findClass("PropertyGroup");
        navigatorElement = (ConcreteCustomClass) findClass("NavigatorElement");
        navigatorFolder = (ConcreteCustomClass) findClass("NavigatorFolder");
        navigatorAction = (ConcreteCustomClass) findClass("NavigatorAction");
        navigatorForm = (ConcreteCustomClass) findClass("NavigatorForm"); 
        form = (ConcreteCustomClass) findClass("Form");
        propertyDraw = (ConcreteCustomClass) findClass("PropertyDraw");
        propertyDrawShowStatus = (ConcreteCustomClass) findClass("PropertyDrawShowStatus");
        table = (ConcreteCustomClass) findClass("Table");
        tableKey = (ConcreteCustomClass) findClass("TableKey");
        tableColumn = (ConcreteCustomClass) findClass("TableColumn");
        dropColumn = (ConcreteCustomClass) findClass("DropColumn");
        property = (ConcreteCustomClass) findClass("Property");
        action = (ConcreteCustomClass) findClass("Action");
        groupObject = (ConcreteCustomClass) findClass("GroupObject");
    }

    @Override
    public void initProperties() throws RecognitionException {
        currentForm = addProperty(null, new LCP<>(new CurrentFormFormulaProperty(form)));
        makePropertyPublic(currentForm, "currentForm", new ArrayList<ResolveClassSet>());

        super.initProperties();

        // ------- Доменная логика --------- //

        // Группы свойства
        captionPropertyGroup = findProperty("caption[PropertyGroup]");
        parentPropertyGroup = findProperty("parent[PropertyGroup]");
        numberPropertyGroup = findProperty("number[PropertyGroup]");
        SIDPropertyGroup = findProperty("SID[PropertyGroup]");
        propertyGroupSID = findProperty("propertyGroup[VARSTRING[100]]");

        // Свойства
        parentProperty = findProperty("parent[ActionOrProperty]");
        tableSIDProperty = findProperty("tableSID[Property]");
        annotationProperty = findProperty("annotation[Property]");
        statsProperty = findProperty("stats[Property]");
        overStatsProperty = findProperty("overStats[Property]");
        maxStatsProperty = findProperty("maxStatsProperty[]");
        numberProperty = findProperty("number[ActionOrProperty]");
        dbNameProperty = findProperty("dbName[Property]");
        canonicalNameActionOrProperty = findProperty("canonicalName[ActionOrProperty]");
        canonicalNameAction = findProperty("canonicalName[Action]");
        canonicalNameProperty = findProperty("canonicalName[Property]");
        loggableProperty = findProperty("loggable[Property]");
        userLoggableProperty = findProperty("userLoggable[Property]");
        storedProperty = findProperty("stored[Property]");
        isSetNotNullProperty = findProperty("isSetNotNull[Property]");
        returnProperty = findProperty("return[Property]");
        classProperty = findProperty("class[Property]");
        complexityProperty = findProperty("complexity[Property]");
        captionProperty = findProperty("caption[Property]");
        propertyCanonicalName = findProperty("propertyCanonicalName[VARSTRING[512]]");
        actionCanonicalName = findProperty("actionCanonicalName[VARSTRING[512]]");
        propertyTableSID = findProperty("propertyTable[VARSTRING[100],VARSTRING[100]]");
        quantityProperty = findProperty("quantity[Property]");
        quantityTopProperty = findProperty("quantityTop[Property]");
        notNullQuantityProperty = findProperty("notNullQuantity[Property]");
        lastRecalculateProperty = findProperty("lastRecalculate[Property]");
        hasNotNullQuantity = findProperty("hasNotNullQuantity[]");

        // ------- Логика представлений --------- //

        // Навигатор
        numberNavigatorElement = findProperty("number[NavigatorElement]");
        navigatorElementCanonicalName = findProperty("navigatorElementCanonicalName[VARSTRING[100]]");
        canonicalNameNavigatorElement = findProperty("canonicalName[NavigatorElement]");
        captionNavigatorElement = findProperty("caption[NavigatorElement]");
        parentNavigatorElement = findProperty("parent[NavigatorElement]");
        
        isNavigatorFolder = findProperty("isNavigatorFolder[?]");
        isNavigatorAction = findProperty("isNavigatorAction[?]");
        isNavigatorForm = findProperty("isNavigatorForm[?]");

        navigatorElementTable = findTable("navigatorElement");
        
        // ----- Формы ---- //
        formCanonicalName = findProperty("canonicalName[Form]");
        formByCanonicalName = findProperty("form[VARSTRING[100]]");
        formCaption = findProperty("caption[Form]");
        isForm = findProperty("is[Form]");

        formNavigatorAction = findProperty("form[NavigatorAction]");
        actionNavigatorAction = findProperty("action[NavigatorAction]");
        
        // Группа объектов
        sidGroupObject = findProperty("sid[GroupObject]");
        formGroupObject = findProperty("form[GroupObject]");
        groupObjectSIDFormNameGroupObject = findProperty("groupSIDFormGroupObject[VARSTRING[100],VARSTRING[100]]");


        // PropertyDraw
        sidPropertyDraw = findProperty("sid[PropertyDraw]");
        captionPropertyDraw = findProperty("caption[PropertyDraw]");
        formPropertyDraw = findProperty("form[PropertyDraw]");
        groupObjectPropertyDraw = findProperty("groupObject[PropertyDraw]");
        // todo : это свойство должно быть для форм, а не навигаторов
        propertyDrawByFormNameAndPropertyDrawSid = findProperty("propertyDrawByFormNameAndPropertyDrawSid[VARSTRING[100],VARSTRING[100]]");

        // UserPreferences
        showPropertyDraw = findProperty("show[PropertyDraw]");
        showPropertyDrawCustomUser = findProperty("show[PropertyDraw,CustomUser]");

        nameShowPropertyDraw = findProperty("nameShow[PropertyDraw]");
        nameShowPropertyDrawCustomUser = findProperty("nameShow[PropertyDraw,CustomUser]");

        columnCaptionPropertyDrawCustomUser = findProperty("columnCaption[PropertyDraw,CustomUser]");
        columnCaptionPropertyDraw = findProperty("columnCaption[PropertyDraw]");

        columnPatternPropertyDrawCustomUser = findProperty("columnPattern[PropertyDraw,CustomUser]");
        columnPatternPropertyDraw = findProperty("columnPattern[PropertyDraw]");
        
        columnWidthPropertyDrawCustomUser = findProperty("columnWidth[PropertyDraw,CustomUser]");
        columnWidthPropertyDraw = findProperty("columnWidth[PropertyDraw]");

        columnOrderPropertyDrawCustomUser = findProperty("columnOrder[PropertyDraw,CustomUser]");
        columnOrderPropertyDraw = findProperty("columnOrder[PropertyDraw]");

        columnSortPropertyDrawCustomUser = findProperty("columnSort[PropertyDraw,CustomUser]");
        columnSortPropertyDraw = findProperty("columnSort[PropertyDraw]");

        columnAscendingSortPropertyDrawCustomUser = findProperty("columnAscendingSort[PropertyDraw,CustomUser]");
        columnAscendingSortPropertyDraw = findProperty("columnAscendingSort[PropertyDraw]");

        hasUserPreferencesGroupObjectCustomUser = findProperty("hasUserPreferences[GroupObject,CustomUser]");
        hasUserPreferencesGroupObject = findProperty("hasUserPreferences[GroupObject]");
        hasUserPreferencesOverrideGroupObjectCustomUser = findProperty("hasUserPreferencesOverride[GroupObject,CustomUser]");

        fontSizeGroupObjectCustomUser = findProperty("fontSize[GroupObject,CustomUser]");
        fontSizeGroupObject = findProperty("fontSize[GroupObject]");

        isFontBoldGroupObjectCustomUser = findProperty("isFontBold[GroupObject,CustomUser]");
        isFontBoldGroupObject = findProperty("isFontBold[GroupObject]");

        isFontItalicGroupObjectCustomUser = findProperty("isFontItalic[GroupObject,CustomUser]");
        isFontItalicGroupObject = findProperty("isFontItalic[GroupObject]");

        pageSizeGroupObjectCustomUser = findProperty("pageSize[GroupObject,CustomUser]");
        pageSizeGroupObject = findProperty("pageSize[GroupObject]");

        headerHeightGroupObjectCustomUser = findProperty("headerHeight[GroupObject,CustomUser]");
        headerHeightGroupObject = findProperty("headerHeight[GroupObject]");

        // группировки
        nameFormGrouping = findProperty("name[FormGrouping]");
        itemQuantityFormGrouping = findProperty("itemQuantity[FormGrouping]");
        groupObjectFormGrouping = findProperty("groupObject[FormGrouping]");
        formGroupingNameFormGroupingGroupObject = findProperty("formGrouping[VARSTRING[100],GroupObject]");
        groupOrderFormGroupingPropertyDraw = findProperty("groupOrder[FormGrouping,PropertyDraw]");
        sumFormGroupingPropertyDraw = findProperty("sum[FormGrouping,PropertyDraw]");
        maxFormGroupingPropertyDraw = findProperty("max[FormGrouping,PropertyDraw]");
        pivotFormGroupingPropertyDraw = findProperty("pivot[FormGrouping,PropertyDraw]");
        // ------------------------------------------------- Физическая модель ------------------------------------ //

        // Таблицы
        sidTable = findProperty("sid[Table]");
        tableSID = findProperty("table[VARISTRING[100]]");

        rowsTable = findProperty("rows[Table]");

        // Ключи таблиц
        tableTableKey = findProperty("table[TableKey]");

        sidTableKey = findProperty("sid[TableKey]");
        tableKeySID = findProperty("tableKey[VARISTRING[100]]");

        classTableKey = findProperty("class[TableKey]");
        classSIDTableKey = findProperty("classSID[TableKey]");
        nameTableKey = findProperty("name[TableKey]");

        quantityTableKey = findProperty("quantity[TableKey]");
        quantityTopTableKey = findProperty("quantityTop[TableKey]");
        overQuantityTableKey = findProperty("overQuantity[TableKey]");

        // Колонки таблиц
        tableTableColumn = findProperty("table[TableColumn]");
        propertyTableColumn = findProperty("property[TableColumn]");

        sidTableColumn = findProperty("sid[TableColumn]");
        longSIDTableColumn = findProperty("longSID[TableColumn]");
        tableColumnLongSID = findProperty("tableColumnLong[VARISTRING[100]]");
        tableColumnSID = findProperty("tableColumnSID[VARISTRING[100]]");

        overQuantityTableColumn = findProperty("overQuantity[TableColumn]");
        notNullQuantityTableColumn = findProperty("notNullQuantity[TableColumn]");

        notRecalculateTableColumn = findProperty("notRecalculate[TableColumn]");
        notRecalculateSID = findProperty("notRecalculate[VARISTRING[100]]");
        recalculateAggregationTableColumn = findAction("recalculateAggregation[TableColumn]");

        notRecalculateStatsSID = findProperty("notRecalculateStats[VARISTRING[100]]");

        // Удаленные колонки
        sidTableDropColumn = findProperty("sidTable[DropColumn]");

        sidDropColumn = findProperty("sid[DropColumn]");
        dropColumnSID = findProperty("dropColumn[VARSTRING[100]]");

        timeDropColumn = findProperty("time[DropColumn]");
        revisionDropColumn = findProperty("revision[DropColumn]");

        dropDropColumn = findAction("drop[DropColumn]");
        //dropDropColumn.setEventAction(this, IncrementType.DROP, false, is(dropColumn), 1); // event, который при удалении колонки из системы удаляет ее из базы
    }
}

