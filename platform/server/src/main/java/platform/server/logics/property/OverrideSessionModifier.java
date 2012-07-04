package platform.server.logics.property;

import platform.base.*;
import platform.server.classes.BaseClass;
import platform.server.data.QueryEnvironment;
import platform.server.data.SQLSession;
import platform.server.session.*;

import java.sql.SQLException;

import static platform.base.BaseUtils.merge;

public class OverrideSessionModifier extends SessionModifier {

    private final IncrementProps override;
    private final SessionModifier modifier;

    private final FunctionSet<CalcProperty> forceDisableHintIncrement;
    private final FunctionSet<CalcProperty> forceDisableNoUpdate;
    private final FunctionSet<CalcProperty> forceHintIncrement;
    private final FunctionSet<CalcProperty> forceNoUpdate;

    @Override
    public QuickSet<CalcProperty> getHintProps() {
        return super.getHintProps().merge(modifier.getHintProps());
    }

    @Override
    public void clearHints(SQLSession session) throws SQLException {
        super.clearHints(session);
        modifier.clearHints(session);
    }

    @Override
    public boolean allowHintIncrement(CalcProperty property) {
        if(forceDisableHintIncrement.contains(property))
            return false;

        if(pushHint(property))
            return modifier.allowHintIncrement(property);

        return super.allowHintIncrement(property);
    }

    @Override
    public boolean forceHintIncrement(CalcProperty property) {
        return forceHintIncrement.contains(property);
    }

    @Override
    public void addHintIncrement(CalcProperty property) {
        assert allowHintIncrement(property);

        // если не зависит от override'а проталкиваем внутрь
        if(pushHint(property)) {
            modifier.addHintIncrement(property);
            return;
        }

        super.addHintIncrement(property);
    }

    private boolean pushHint(CalcProperty property) {
        return !CalcProperty.depends(property, merge(merge(override.getProperties(), forceHintIncrement), forceNoUpdate));
    }

    @Override
    public boolean forceDisableNoUpdate(CalcProperty property) {
        boolean result = forceDisableNoUpdate.contains(property);
        // если здесь запрещено, то и в modifier'е должно быть запрещено
        assert !result || modifier.forceDisableNoUpdate(property);
        return result;
    }

    @Override
    public boolean forceNoUpdate(CalcProperty property) {
        return forceNoUpdate.contains(property);
    }

    @Override
    public void addNoUpdate(CalcProperty property) {
        assert allowNoUpdate(property);

        if(pushHint(property) && modifier.allowNoUpdate(property)) {
            modifier.addNoUpdate(property);
            return;
        }

        super.addNoUpdate(property);
    }

    // уведомляет что IncrementProps изменился
    public void eventIncrementChange(CalcProperty property) {
        eventChange(property);

        // пробежим по increment'ам modifier, и уведомим что они могли изменится
        for(CalcProperty incrementProperty : modifier.getHintProps())
            if(CalcProperty.depends(incrementProperty, property))
                eventHintIncrement(incrementProperty);
    }

    public void clean() {
        override.unregisterView(this);
        modifier.unregisterView(this);
    }

    public OverrideSessionModifier(IncrementProps override, FunctionSet<CalcProperty> forceDisableHintIncrement, FunctionSet<CalcProperty> forceHintIncrement, FunctionSet<CalcProperty> forceNoUpdate, SessionModifier modifier) { // нужно clean вызывать после такого modifier'а
        this.override = override;
        this.modifier = modifier;
        this.forceDisableHintIncrement = forceDisableHintIncrement;
        this.forceDisableNoUpdate = forceDisableHintIncrement;
        this.forceHintIncrement = forceHintIncrement;
        this.forceNoUpdate = forceNoUpdate;

        // assert что modifier.forceDisableNoUpdate содержит все this.forceDisableNoUpdate
        // assert что forceDisableIncrement содержит все this.forceDisabeNoUpdate

        override.registerView(this);
        modifier.registerView(this);
    }

    public OverrideSessionModifier(IncrementProps override, SessionModifier modifier) { // нужно clean вызывать после такого modifier'а
        this(override, EmptyFunctionSet.<CalcProperty>instance(), EmptyFunctionSet.<CalcProperty>instance(), EmptyFunctionSet.<CalcProperty>instance(), modifier);
    }

    @Override
    protected <P extends PropertyInterface> ModifyChange<P> calculateModifyChange(CalcProperty<P> property, FunctionSet<CalcProperty> overrided) {
        PropertyChange<P> overrideChange = override.getPropertyChange(property);
        if(overrideChange!=null)
            return new ModifyChange<P>(overrideChange, true);
        return modifier.getModifyChange(property, merge(merge(overrided, CalcProperty.getDependsSet(override.getProperties())), forceDisableHintIncrement));
    }

    public QuickSet<CalcProperty> calculateProperties() {
        return modifier.getProperties().merge(override.getProperties());
    }

    public SQLSession getSQL() {
        return modifier.getSQL();
    }

    public BaseClass getBaseClass() {
        return modifier.getBaseClass();
    }

    public QueryEnvironment getQueryEnv() {
        return modifier.getQueryEnv();
    }
}
