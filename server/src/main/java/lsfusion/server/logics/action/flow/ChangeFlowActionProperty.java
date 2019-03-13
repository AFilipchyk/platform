package lsfusion.server.logics.action.flow;

import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.physics.dev.i18n.LocalizedString;
import lsfusion.server.logics.property.ActionProperty;
import lsfusion.server.logics.property.CalcPropertyMapImplement;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.property.derived.DerivedProperty;

public abstract class ChangeFlowActionProperty extends KeepContextActionProperty {

    protected ChangeFlowActionProperty(LocalizedString caption) {
        super(caption, 0);
    }

    public CalcPropertyMapImplement<?, PropertyInterface> calcWhereProperty() {
        return DerivedProperty.createTrue();
    }

    public ImSet<ActionProperty> getDependActions() {
        return SetFact.EMPTY();
    }

}
