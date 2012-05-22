package platform.server.logics.property.actions.flow;

import platform.server.classes.LogicalClass;
import platform.server.logics.property.*;
import platform.server.logics.property.derived.DerivedProperty;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static platform.base.BaseUtils.reverse;
import static platform.base.BaseUtils.toListNoNull;

public abstract class AroundAspectActionProperty extends KeepContextActionProperty {
    private final ActionPropertyMapImplement<?, PropertyInterface> aspectActionImplement;

    public <P extends PropertyInterface, I extends PropertyInterface> AroundAspectActionProperty(String sID, String caption, List<I> innerInterfaces, ActionPropertyMapImplement<P, I> action) {
        super(sID, caption, innerInterfaces.size());

        this.aspectActionImplement = action.map(reverse(getMapInterfaces(innerInterfaces)));
    }

    public Set<ActionProperty> getDependActions() {
        return Collections.singleton((ActionProperty)aspectActionImplement.property);
    }

    public final FlowResult execute(ExecutionContext<PropertyInterface> context) throws SQLException {
        return aroundAspect(context);
    }

    protected FlowResult aroundAspect(ExecutionContext<PropertyInterface> context) throws SQLException {
        ExecutionContext<PropertyInterface> innerContext = beforeAspect(context);

        FlowResult result = proceed(innerContext);

        afterAspect(result, context, innerContext);

        return result;
    }

    protected ExecutionContext<PropertyInterface> beforeAspect(ExecutionContext<PropertyInterface> context) throws SQLException {
        return context;
    }

    protected FlowResult proceed(ExecutionContext<PropertyInterface> innerContext) throws SQLException {
        return execute(innerContext, aspectActionImplement);
    }

    protected void afterAspect(FlowResult result, ExecutionContext<PropertyInterface> context, ExecutionContext<PropertyInterface> innerContext) throws SQLException {
    }

    public CalcPropertyMapImplement<?, PropertyInterface> getWhereProperty() {
        return aspectActionImplement.mapWhereProperty();
    }
}
