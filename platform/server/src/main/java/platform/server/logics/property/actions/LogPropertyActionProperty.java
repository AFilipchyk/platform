package platform.server.logics.property.actions;

import platform.base.BaseUtils;
import platform.interop.action.LogMessageClientAction;
import platform.server.classes.ValueClass;
import platform.server.form.entity.ObjectEntity;
import platform.server.form.entity.PropertyFormEntity;
import platform.server.form.instance.FormRow;
import platform.server.form.instance.ObjectInstance;
import platform.server.form.instance.PropertyDrawInstance;
import platform.server.logics.DataObject;
import platform.server.logics.property.CalcProperty;
import platform.server.logics.property.ClassPropertyInterface;
import platform.server.logics.property.ExecutionContext;
import platform.server.logics.property.PropertyInterface;
import platform.server.session.DataSession;

import java.sql.SQLException;
import java.util.*;

public class LogPropertyActionProperty<P extends PropertyInterface> extends CustomActionProperty {

    private final CalcProperty<P> property;

    public LogPropertyActionProperty(CalcProperty<P> property) {
        super("LogProp" + property.getSID(), property.caption, new ValueClass[]{});

        this.property = property;
    }

    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        DataSession session = context.getSession();
        
        String result = property.toString() + '\n';
        for(FormRow formRow : context.createFormInstance(new PropertyFormEntity(property, session.recognizeGroup),
                                                new HashMap<ObjectEntity, DataObject>(), session, false, false, false, false).getFormData(30).rows) {
            String rowResult = "";
            for(Map.Entry<Set<ObjectInstance>, Collection<PropertyDrawInstance>> groupObj : BaseUtils.group(new BaseUtils.Group<Set<ObjectInstance>, PropertyDrawInstance>() {
                                                        public Set<ObjectInstance> group(PropertyDrawInstance property) { // группируем по объектам
                                                            return new HashSet<ObjectInstance>(property.propertyObject.mapping.values());
                                                        } }, formRow.values.keySet()).entrySet()) {
                String groupResult = "";
                for(ObjectInstance objSet : groupObj.getKey())
                    groupResult = (groupResult.length()==0?"":groupResult + ", ") + formRow.keys.get(objSet);
                if(groupResult.length() > 0)
                    groupResult = "id=" + groupResult;
                for(PropertyDrawInstance property : groupObj.getValue())
                    groupResult = (groupResult.length()==0?"":groupResult + ", ") + BaseUtils.toCaption(formRow.values.get(property));
                if(groupResult.length()>0)
                    rowResult += "[" + groupResult + "] ";
            }
            result += "    " + rowResult + '\n';
        }
        context.delayUserInteraction(new LogMessageClientAction(result, true));
    }
}
