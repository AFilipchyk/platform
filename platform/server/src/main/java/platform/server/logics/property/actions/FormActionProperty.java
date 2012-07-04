package platform.server.logics.property.actions;

import platform.interop.action.FormClientAction;
import platform.interop.action.MessageClientAction;
import platform.interop.action.ReportClientAction;
import platform.server.classes.ActionClass;
import platform.server.classes.DataClass;
import platform.server.classes.StaticCustomClass;
import platform.server.classes.ValueClass;
import platform.server.form.entity.ActionPropertyObjectEntity;
import platform.server.form.entity.FormEntity;
import platform.server.form.entity.GroupObjectEntity;
import platform.server.form.entity.ObjectEntity;
import platform.server.form.instance.FormCloseType;
import platform.server.form.instance.FormInstance;
import platform.server.form.instance.ObjectInstance;
import platform.server.form.instance.remote.RemoteForm;
import platform.server.logics.DataObject;
import platform.server.logics.ServerResourceBundle;
import platform.server.logics.linear.LCP;
import platform.server.logics.property.AnyValuePropertyHolder;
import platform.server.logics.property.ClassPropertyInterface;
import platform.server.logics.property.ExecutionContext;
import platform.server.session.ExecutionEnvironment;

import java.sql.SQLException;
import java.util.*;

import static platform.base.BaseUtils.join;

// вообще по хорошему надо бы generiть интерфейсы, но тогда с DataChanges (из-за дебилизма generics в современных языках) будут проблемы
public class FormActionProperty extends CustomReadValueActionProperty {

    public final FormEntity<?> form;
    public final Map<ObjectEntity, ClassPropertyInterface> mapObjects;
    private final ActionPropertyObjectEntity<?>[] startProperties;
    public List<ActionPropertyObjectEntity<?>> closeProperties = new ArrayList<ActionPropertyObjectEntity<?>>();
    public Set<ObjectEntity> seekOnOk = new HashSet<ObjectEntity>();
    private DataClass valueClass;
    private final boolean checkOnOk;
    private final boolean newSession;
    private final boolean isModal;

    private final StaticCustomClass formResultClass;
    private final LCP formResultProperty;

    private final AnyValuePropertyHolder chosenValueProperty;

    public static ValueClass[] getValueClasses(ObjectEntity[] objects) {
        ValueClass[] valueClasses = new ValueClass[objects.length];
        for (int i = 0; i < objects.length; i++) {
            valueClasses[i] = objects[i].baseClass;
        }
        return valueClasses;
    }

    //assert objects и startProperties из form
    //assert getProperties и startProperties одинаковой длины
    //startProperties привязаны к созадаваемой форме
    //getProperties привязаны к форме, содержащей свойство...
    public FormActionProperty(String sID, String caption, FormEntity form, ObjectEntity[] objectsToSet, ActionPropertyObjectEntity[] setProperties, DataClass valueClass, boolean newSession, boolean isModal, boolean checkOnOk, StaticCustomClass formResultClass, LCP formResultProperty, AnyValuePropertyHolder chosenValueProperty) {
        super(sID, caption, getValueClasses(objectsToSet));

        this.valueClass = valueClass;
        this.formResultClass = formResultClass;
        this.formResultProperty = formResultProperty;
        this.chosenValueProperty = chosenValueProperty;


        this.isModal = isModal;
        this.checkOnOk = checkOnOk;
        this.newSession = newSession;
        this.startProperties = setProperties;

        int i = 0; // такой же дебилизм и в SessionDataProperty
        mapObjects = new HashMap<ObjectEntity, ClassPropertyInterface>();
        for (ClassPropertyInterface propertyInterface : interfaces) {
            mapObjects.put(objectsToSet[i++], propertyInterface);
        }
        this.form = form;
    }

    protected DataClass getReadType() {
        if(valueClass == null || valueClass.equals(ActionClass.instance))
            return null;
        return valueClass;
    }

    public void executeRead(ExecutionContext<ClassPropertyInterface> context, Object userValue) throws SQLException {

        final FormInstance newFormInstance = context.createFormInstance(form, join(mapObjects, context.getKeys()), context.getSession(), isModal, newSession, checkOnOk, !form.isPrintForm);

        if (form.isPrintForm && !newFormInstance.areObjectsFound()) {
            context.requestUserInteraction(
                    new MessageClientAction(ServerResourceBundle.getString("form.navigator.form.do.not.fit.for.specified.parameters"), form.caption));
        } else {
            for (Map.Entry<ObjectEntity, ClassPropertyInterface> entry : mapObjects.entrySet()) {
                newFormInstance.forceChangeObject(newFormInstance.instanceFactory.getInstance(entry.getKey()), context.getKeyValue(entry.getValue()));
            }

            final FormInstance thisFormInstance = context.getFormInstance();
            if(thisFormInstance!=null) {
                if (form instanceof SelfInstancePostProcessor) {
                    ((SelfInstancePostProcessor) form).postProcessSelfInstance(context.getKeys(), thisFormInstance, newFormInstance);
                }
            }

            for (ActionPropertyObjectEntity<?> startProperty : startProperties)
                newFormInstance.instanceFactory.getInstance(startProperty).execute(newFormInstance);


            RemoteForm newRemoteForm = context.createRemoteForm(newFormInstance);
            if (form.isPrintForm) {
                context.requestUserInteraction(new ReportClientAction(form.getSID(), isModal, newRemoteForm.reportManager.getReportData()));
            } else {
                context.requestUserInteraction(new FormClientAction(isModal, newRemoteForm));
            }

            FormCloseType formResult = newFormInstance.getFormResult();

            if (formResultProperty != null) {
                formResultProperty.change(formResultClass.getID(formResult.asString()), context);
            }

            if (chosenValueProperty != null) {
                for (GroupObjectEntity group : form.groups) {
                    for (ObjectEntity object : group.objects) {
                        chosenValueProperty.write(
                                object.baseClass.getType(), newFormInstance.instanceFactory.getInstance(object).getObjectValue().getValue(), context, new DataObject(object.getSID())
                        );
                    }
                }
            }

            if (formResult == FormCloseType.OK) {
                for (ObjectEntity object : seekOnOk) {
                    try {
                        ObjectInstance objectInstance = newFormInstance.instanceFactory.getInstance(object);
                        // нужна проверка, т.к. в принципе пока FormActionProperty может ссылаться на ObjectEntity из разных FormEntity
                        if (objectInstance != null) {
                            thisFormInstance.expandCurrentGroupObject(object.baseClass);
                            thisFormInstance.forceChangeObject(object.baseClass, objectInstance.getObjectValue());
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            if (formResult == FormCloseType.CLOSE) {
                for (ActionPropertyObjectEntity<?> property : closeProperties) {
                    try {
                        newFormInstance.instanceFactory.getInstance(property).execute(newFormInstance);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            if (thisFormInstance != null) {
                //обновляем текущую форму, чтобы подхватить изменения из вызываемой формы
                thisFormInstance.refreshData();
            }
        }
    }

    public static interface SelfInstancePostProcessor {
        public void postProcessSelfInstance(Map<ClassPropertyInterface, DataObject> keys, FormInstance executeForm, FormInstance selfFormInstance);
    }
}
