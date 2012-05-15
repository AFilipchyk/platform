package skolkovo.actions;

import platform.base.BaseUtils;
import platform.base.OrderedMap;
import platform.interop.Compare;
import platform.interop.action.ClientAction;
import platform.server.classes.ConcreteCustomClass;
import platform.server.classes.ValueClass;
import platform.server.data.expr.KeyExpr;
import platform.server.data.query.Query;
import platform.server.form.instance.PropertyObjectInterfaceInstance;
import platform.server.form.instance.remote.RemoteForm;
import platform.server.logics.DataObject;
import platform.server.logics.ObjectValue;
import platform.server.logics.linear.LCP;
import platform.server.logics.property.ClassPropertyInterface;
import platform.server.logics.property.ExecutionContext;
import platform.server.logics.property.actions.CustomActionProperty;
import skolkovo.SkolkovoLogicsModule;

import java.sql.*;
import java.util.*;

public class CopyProjectActionProperty extends CustomActionProperty {

    private SkolkovoLogicsModule LM;
    private final ClassPropertyInterface projectInterface;

    public CopyProjectActionProperty(String caption, SkolkovoLogicsModule LM, ValueClass project) {
        super(LM.genSID(), caption, new ValueClass[]{project});
        this.LM = LM;

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        projectInterface = i.next();
    }

    public void execute(Map<ClassPropertyInterface, DataObject> keys, ObjectValue value, List<ClientAction> actions, RemoteForm executeForm, Map<ClassPropertyInterface, PropertyObjectInterfaceInstance> mapObjects) throws SQLException {
        throw new RuntimeException("no need");
    }

    @Override
    public void executeCustom(ExecutionContext context) throws SQLException {

        DataObject projectObject = context.getKeyValue(projectInterface);
        DataObject projectCopy = context.addObject(LM.project);

        List<LCP> propertiesToCopy = BaseUtils.toList(
                LM.nameNativeProject, LM.nameNativeManagerProject, LM.nameNativeGenitiveManagerProject,
                LM.nameNativeDativusManagerProject, LM.nameNativeAblateManagerProject, LM.nativeProblemProject,
                LM.nativeInnovativeProject, LM.nativeSubstantiationProjectType, LM.fileNativeSummaryProject,
                LM.fileNativeTechnicalDescriptionProject, LM.fileNativeRoadMapProject, LM.nameNativeClaimerProject,
                LM.nameForeignProject, LM.nameForeignManagerProject, LM.foreignProblemProject,
                LM.foreignInnovativeProject, LM.foreignSubstantiationProjectType, LM.fileForeignSummaryProject,
                LM.fileForeignTechnicalDescriptionProject, LM.fileForeignRoadMapProject, LM.fillNativeProject,
                LM.fillForeignProject, LM.isConsultingCenterQuestionProject, LM.isConsultingCenterCommentProject,
                LM.consultingCenterCommentProject, LM.isOwnedEquipmentProject, LM.isAvailableEquipmentProject,
                LM.isTransferEquipmentProject, LM.descriptionTransferEquipmentProject, LM.ownerEquipmentProject,
                LM.isPlanningEquipmentProject, LM.specificationEquipmentProject, LM.isSeekEquipmentProject,
                LM.descriptionEquipmentProject, LM.isOtherEquipmentProject, LM.commentEquipmentProject,
                LM.isReturnInvestmentsProject, LM.nameReturnInvestorProject, LM.amountReturnFundsProject,
                LM.isNonReturnInvestmentsProject, LM.nameNonReturnInvestorProject, LM.amountNonReturnFundsProject,
                LM.commentOtherNonReturnInvestmentsProject, LM.isCapitalInvestmentProject, LM.isPropertyInvestmentProject,
                LM.isGrantsProject, LM.isOtherNonReturnInvestmentsProject, LM.isOwnFundsProject,
                LM.amountOwnFundsProject, LM.isPlanningSearchSourceProject, LM.amountFundsProject,
                LM.isOtherSoursesProject, LM.commentOtherSoursesProject, LM.updateDateProject,
                LM.projectTypeProject, LM.projectActionProject, LM.emailClaimerProject,
                LM.dateProject, LM.nameProjectActionProject, LM.autoGenerateProject,
                LM.inactiveProject, LM.statementClaimerProject, LM.constituentClaimerProject,
                LM.extractClaimerProject, LM.isOtherClusterProject, LM.nativeSubstantiationOtherClusterProject,
                LM.foreignSubstantiationOtherClusterProject, LM.claimerProject//, LM.nameStatusProject
        );
        for (LCP prop : propertiesToCopy)
            copyProperty(prop, context, projectObject, projectCopy);

        propertiesToCopy = BaseUtils.toList(
                LM.nativeNumberPatent, LM.priorityDatePatent,
                LM.isOwned, LM.ownerPatent, LM.ownerTypePatent,
                LM.isValuated, LM.valuatorPatent, LM.fileIntentionOwnerPatent,
                LM.fileActValuationPatent, LM.nativeTypePatent, LM.foreignTypePatent,
                LM.foreignNumberPatent);
        copyObject(LM.patent, propertiesToCopy, context, LM.projectPatent, projectObject, projectCopy);

        propertiesToCopy = BaseUtils.toList(
                LM.fullNameAcademic, LM.institutionAcademic,
                LM.titleAcademic, LM.fileDocumentConfirmingAcademic, LM.fileDocumentEmploymentAcademic
        );
        copyObject(LM.academic, propertiesToCopy, context, LM.projectAcademic, projectObject, projectCopy);

        propertiesToCopy = BaseUtils.toList(
                LM.fullNameNonRussianSpecialist, LM.organizationNonRussianSpecialist,
                LM.titleNonRussianSpecialist, LM.fileNativeResumeNonRussianSpecialist, LM.fileForeignResumeNonRussianSpecialist,
                LM.filePassportNonRussianSpecialist, LM.fileStatementNonRussianSpecialist
        );
        copyObject(LM.nonRussianSpecialist, propertiesToCopy, context, LM.projectNonRussianSpecialist, projectObject, projectCopy);


        List<LCP> propertiesClusterProjectToCopy = BaseUtils.toList(
                LM.nativeSubstantiationProjectCluster, LM.foreignSubstantiationProjectCluster, LM.inProjectCluster);
        List<LCP> propertiesClusterToCopy = BaseUtils.toList(
                LM.nameNativeCluster, LM.nameForeign);

        KeyExpr clusterExpr = new KeyExpr("cluster");
        KeyExpr projectExpr = new KeyExpr("project");
        Map<Object, KeyExpr> newKeys = new HashMap<Object, KeyExpr>();
        newKeys.put("cluster", clusterExpr);
        newKeys.put("project", projectExpr);

        Query<Object, Object> query = new Query<Object, Object>(newKeys);
        for (LCP prop : propertiesClusterProjectToCopy)
            query.properties.put(prop.toString(), prop.getExpr(context.getModifier(), projectExpr, clusterExpr));
        for (LCP prop : propertiesClusterToCopy)
            query.properties.put(prop.toString(), prop.getExpr(context.getModifier(), clusterExpr));
        query.and(LM.inProjectCluster.getExpr(context.getModifier(), projectExpr, clusterExpr).getWhere());
        query.and(projectExpr.compare(projectObject, Compare.EQUALS));
        OrderedMap<Map<Object, Object>, Map<Object, Object>> result = query.execute(context.getSession().sql);

        for (Map.Entry<Map<Object, Object>, Map<Object, Object>> rows : result.entrySet()) {
            DataObject clusterObject = new DataObject(rows.getKey().get("cluster"), LM.cluster);
            for (LCP prop : propertiesClusterProjectToCopy)
                prop.change(rows.getValue().get(prop.toString()), context, projectCopy, clusterObject);
            for (LCP prop : propertiesClusterToCopy)
                prop.change(rows.getValue().get(prop.toString()), context, clusterObject);
        }
    }

    private void copyProperty(LCP prop, ExecutionContext context, DataObject projectObject, DataObject objectCopy) throws SQLException {
        prop.change(prop.read(context, projectObject), context, objectCopy);
    }

    private void copyObject(ConcreteCustomClass copyingCustomClass, List<LCP> propertiesToCopy, ExecutionContext context, LCP projectCopyingCustomClass, DataObject projectObject, DataObject projectCopy) throws SQLException {
        LCP isCopyingCustomClass = LM.is(copyingCustomClass);
        Map<Object, KeyExpr> keys = isCopyingCustomClass.getMapKeys();
        KeyExpr key = BaseUtils.singleValue(keys);
        Query<Object, Object> query = new Query<Object, Object>(keys);
        for (LCP prop : propertiesToCopy)
            query.properties.put(prop.toString(), prop.getExpr(context.getModifier(), key));
        query.and(isCopyingCustomClass.getExpr(key).getWhere());
        query.and(projectCopyingCustomClass.getExpr(context.getModifier(), key).compare(projectObject.getExpr(), Compare.EQUALS));
        OrderedMap<Map<Object, Object>, Map<Object, Object>> result = query.execute(context.getSession().sql);

        for (Map<Object, Object> values : result.values()) {
            DataObject copy = context.addObject(copyingCustomClass);
            projectCopyingCustomClass.change(projectCopy.getValue(), context, copy);
            for (LCP prop : propertiesToCopy)
                prop.change(values.get(prop.toString()), context, copy);
        }
    }
}