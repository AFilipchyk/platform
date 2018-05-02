package lsfusion.server.logics.property.actions;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.CalcProperty;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;

import java.sql.SQLException;

public class IsActiveFormActionProperty extends SystemExplicitActionProperty {

    private LCP<?> isActiveFormProperty;
    private FormEntity requestedForm;

    public ImMap<CalcProperty, Boolean> aspectChangeExtProps() {
        return getChangeProps(isActiveFormProperty.property);
    }

    public IsActiveFormActionProperty(LocalizedString caption, FormEntity form, LCP isActiveFormProperty) {
        super(caption);
        this.requestedForm = form;
        this.isActiveFormProperty = isActiveFormProperty;
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        FormInstance<?> activeFormInstance = context.getFormInstance(true, false);
        FormEntity activeForm = activeFormInstance == null ? null : activeFormInstance.entity;
        Boolean isActive = activeForm != null && requestedForm != null && activeForm.equals(requestedForm);
        isActiveFormProperty.change(isActive, context);
    }
}