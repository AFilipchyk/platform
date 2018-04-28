package lsfusion.server.session;

import lsfusion.base.*;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.MMap;
import lsfusion.base.col.interfaces.mutable.add.MAddSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.server.Settings;
import lsfusion.server.caches.ManualLazy;
import lsfusion.server.caches.ValuesContext;
import lsfusion.server.classes.BaseClass;
import lsfusion.server.data.OperationOwner;
import lsfusion.server.data.QueryEnvironment;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.SQLSession;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.CalcProperty;
import lsfusion.server.logics.property.OverrideSessionModifier;
import lsfusion.server.logics.property.PropertyInterface;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// поддерживает hint'ы, есть информация о сессии
public abstract class SessionModifier implements Modifier {

    private final String debugInfo;
    
    public SessionModifier(String debugInfo) {
        this.debugInfo = debugInfo;
    }

    private ConcurrentIdentityWeakHashSet<OverrideSessionModifier> views = new ConcurrentIdentityWeakHashSet<>();
    public void registerView(OverrideSessionModifier modifier) throws SQLException, SQLHandledException { // protected
        views.add(modifier);
        modifier.eventDataChanges(getPropertyChanges().getProperties());
    }

    public long getMaxCountUsed(CalcProperty<?> property) {
        long result = 0;
        for(CalcProperty depend : property.getRecDepends()) {
            result = BaseUtils.max(result, getMaxCount(depend));
        }
        return result;
    }
    public abstract long getMaxCount(CalcProperty recDepends);

    public void unregisterView(OverrideSessionModifier modifier) { // protected
        views.remove(modifier);
    }

    protected <P extends CalcProperty> boolean eventChanges(ImSet<P> properties, GetValue<? extends UpdateResult, P> modifyResults) throws SQLException, SQLHandledException {
        boolean dataChanged = false;
        for(P property : properties) {
            UpdateResult result = modifyResults.getMapValue(property);
            if(result.dataChanged())
                dataChanged = true;
            eventChange(property, result.dataChanged(), result.sourceChanged());
        }
        return dataChanged;
    }

    protected <P extends CalcProperty> void eventDataChanges(ImSet<P> properties) throws SQLException, SQLHandledException {
        eventChanges(properties, ModifyResult.DATA_SOURCE.<P>fnGetValue());
    }

    protected void eventChange(CalcProperty property, boolean data, boolean source) throws SQLException, SQLHandledException {
        if(source)
            addChange(property, data);
/*        else {
            if(!mChanged.contains(property)) {
                ModifyChange modifyChange = getModifyChange(property);
                if(!BaseUtils.nullEquals(modifyChange, propertyChanges.getModify(property)))
                    modifyChange = modifyChange;
            }
        }*/

        if(data) { // если изменились данные, drop'аем хинты
            try {
                for(CalcProperty<?> incrementProperty : getIncrementProps()) {
                    if(CalcProperty.depends(incrementProperty, property)) {
                        if(increment.contains(incrementProperty))
                            increment.remove(incrementProperty, getSQL(), getOpOwner());
                        preread.remove(incrementProperty);
                        eventChange(incrementProperty, false, true); // так как изначально итерация идет или по increment или по preread, сработает в любом случае
                    }
                }
                MAddSet<CalcProperty> removedNoUpdate = SetFact.mAddSet();
                for(CalcProperty<?> incrementProperty : noUpdate)
                    if(CalcProperty.depends(incrementProperty, property)) // сбрасываем noUpdate, уведомляем остальных об изменении
                        eventNoUpdate(incrementProperty);
                    else
                        removedNoUpdate.add(incrementProperty);
                noUpdate = removedNoUpdate;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        
        for(OverrideSessionModifier view : views)
            view.eventChange(property, data, source);
    }
    
    protected void notifySourceChange(ImMap<CalcProperty, Boolean> changed, boolean forceUpdate) throws SQLException, SQLHandledException {        
    }

    protected void eventNoUpdate(CalcProperty property) throws SQLException, SQLHandledException {
        addChange(property, true);

        for(OverrideSessionModifier view : views)
            view.eventChange(property, true, true); // если сюда зашли, значит гарантировано изменили данные
    }

    protected void eventSourceChanges(Iterable<? extends CalcProperty> properties) throws SQLException, SQLHandledException {
        for(CalcProperty property : properties)
            eventChange(property, false, true); // используется только в случаях когда гарантировано меняется "источник"
    }


    private MMap<CalcProperty, Boolean> mChanged = null;
    private void addChange(CalcProperty property, boolean dataChanged) {
        if(mChanged == null)
            mChanged = MapFact.mMap(MapFact.<CalcProperty>or());
        mChanged.add(property, dataChanged);
    }

    public PropertyChanges getPropertyChanges() throws SQLException, SQLHandledException {
        return getPropertyChanges(false);
    }
    
    private static boolean enableCheckChanges = false; 

    private ImSet<CalcProperty> propertyChangesRecursionGuard = SetFact.EMPTY();
    // по сути protected
    protected PropertyChanges propertyChanges = PropertyChanges.EMPTY;
    @ManualLazy
    public PropertyChanges getPropertyChanges(boolean forceUpdate) throws SQLException, SQLHandledException {
        if(mChanged != null) {
            ImMap<CalcProperty, Boolean> changed = mChanged.immutable();
            assert !changed.isEmpty();
            mChanged = null;
            
            ImMap<CalcProperty, ModifyChange> replace = changed.keys().mapValues(new GetValue<ModifyChange, CalcProperty>() {
                public ModifyChange getMapValue(CalcProperty value) {
                    return getModifyChange(value);
                }});

            if(enableCheckChanges && !forceUpdate && !calculatePropertyChanges().equals(propertyChanges.replace(replace))) // может нарушаться если в calculatePropertyChanges кто-то empty возвращает (а в replace есть filter not empty)
                assert false;

            propertyChanges = propertyChanges.replace(replace);
        
            ImSet<CalcProperty> prevRecursionGuard = propertyChangesRecursionGuard;
            propertyChangesRecursionGuard = propertyChangesRecursionGuard.merge(changed.keys());
            try {
                ImMap<CalcProperty, Boolean> guardedChanged = changed.remove(prevRecursionGuard);
//                if(guardedChanged.size() < changed.size())
//                    ServerLoggers.exinfoLog("GUARDED CHANGES : " + changed + ", " + prevRecursionGuard);
                notifySourceChange(guardedChanged, forceUpdate);

                return getPropertyChanges(forceUpdate); // так как source change мог еще раз изменить
            } finally {
                propertyChangesRecursionGuard = prevRecursionGuard;
            }
        }
        return propertyChanges;
    }

    public void updateSourceChanges() throws SQLException, SQLHandledException {
        getPropertyChanges(true);
        for(OverrideSessionModifier view : views)
            view.updateSourceChanges();        
    }

    public ImSet<CalcProperty> getHintProps() {
        return noUpdate.immutableCopy().merge(getIncrementProps());
    }

    private CalcProperty readProperty;
    public Set<CalcProperty> prereadProps = new HashSet<>();

    // hint'ы хранит
    private TableProps increment = new TableProps();
    private Map<CalcProperty, PrereadRows> preread = new HashMap<>();
    private ImSet<CalcProperty> getPrereadProps() {
        return SetFact.fromJavaSet(preread.keySet());
    }
    private ImSet<CalcProperty> getIncrementProps() {
        return increment.getProperties().merge(getPrereadProps());
    }

    // должно цеплять все views, чтобы не получилось что increment'ы создались до начала транзакции, а удалялись по eventChange (который цепляет все views), тогда rollbacktransaction вернет назад записи в старые таблицы
    public void clearHints(SQLSession session, OperationOwner owner) throws SQLException, SQLHandledException {
        eventSourceChanges(getIncrementProps());
        increment.clear(session, owner);
        preread.clear();
        eventDataChanges(noUpdate.immutableCopy());
        noUpdate = SetFact.mAddSet();

        for(OverrideSessionModifier view : views)
            view.clearHints(session, owner);
    }

    public void clearPrereads() throws SQLException, SQLHandledException {
        eventSourceChanges(getPrereadProps());
        preread.clear();
    }

    public abstract SQLSession getSQL();
    public abstract BaseClass getBaseClass();
    public abstract QueryEnvironment getQueryEnv();
    
    public abstract OperationOwner getOpOwner();

    public boolean allowHintIncrement(CalcProperty property) {
        if (increment.contains(property))
            return false;

        if (readProperty != null && readProperty.equals(property))
            return false;

        if (!property.allowHintIncrement())
            return false;

        return true;
    }

    public boolean forceHintIncrement(CalcProperty property) {
        return false;
    }

    public boolean allowNoUpdate(CalcProperty property) {
        return !noUpdate.contains(property) && !forceDisableNoUpdate(property);
    }

    public boolean forceNoUpdate(CalcProperty property) {
        return false;
    }

    protected <P extends PropertyInterface> boolean allowPropertyPrereadValues(CalcProperty<P> property) {
        if(!property.complex)
            return false;

        if(Settings.get().isDisablePrereadValues())
            return false;

        if (prereadProps.contains(property))
            return false;

        return true;
    }

    public <P extends PropertyInterface> ValuesContext cacheAllowPrereadValues(CalcProperty<P> property) {
        if(!allowPropertyPrereadValues(property))
            return null;

        PrereadRows prereadRows = preread.get(property);
        if(prereadRows==null)
            return PrereadRows.EMPTY();

        return prereadRows;
    }

    // assert что в values только
    // предполагается что должно быть consistent с MapCacheAspect.prereadHintEnabled
    public <P extends PropertyInterface> boolean allowPrereadValues(CalcProperty<P> property, ImMap<P, Expr> values) {
        // assert что values только complex values

        if(!allowPropertyPrereadValues(property))
            return false;

        PrereadRows prereadRows = preread.get(property);

        if(values.size()==property.interfaces.size()) { // если все есть
            if(prereadRows!=null && prereadRows.readValues.containsKey(values))
                return false;
        } else {
            ImMap<P, Expr> complexValues = CalcProperty.onlyComplex(values);
            if(complexValues.isEmpty() || (prereadRows!=null && prereadRows.readParams.keys().containsAll(complexValues.values().toSet())))
                return false;
        }

        return true;
    }

    public boolean forceDisableNoUpdate(CalcProperty property) {
        return true;
    }

    public int getLimitHintIncrementComplexity() {
        return Settings.get().getLimitHintIncrementComplexityCoeff();
    }

    public int getLimitHintIncrementValueComplexity() {
        return Settings.get().getLimitHintIncrementValueComplexityCoeff();
    }

    public double getLimitComplexityGrowthCoeff() {
        return Settings.get().getLimitComplexityGrowthCoeff();
    }

    public long getLimitHintIncrementStat() {
        return Settings.get().getLimitHintIncrementStat();
    }

    public int getLimitHintNoUpdateComplexity() {
        return Settings.get().getLimitHintNoUpdateComplexity();
    }

    public void addHintIncrement(CalcProperty property) throws SQLException, SQLHandledException {
        assert allowHintIncrement(property);

        try {
            readProperty = property;
            final PropertyChangeTableUsage changeTable = property.readChangeTable("htincr", getSQL(), this, getBaseClass(), getQueryEnv());
            increment.add(property, changeTable);
        } catch(Exception e) {
            String message = e.getMessage();
            if(message != null && message.contains("does not exist")) // выводим, что за modifier
                SQLSession.outModifier("DOES NOT EXIST", this);
            throw ExceptionUtils.propagate(e, SQLException.class, SQLHandledException.class);
        } finally {
            readProperty = null;
        }

        eventChange(property, false, true); // используется только в случаях когда гарантировано меняется "источник"
    }

    public <P extends PropertyInterface> void addPrereadValues(CalcProperty<P> property, ImMap<P, Expr> values) throws SQLException, SQLHandledException {
        assert property.complex && allowPrereadValues(property, values);

        try {
            prereadProps.add(property);

            PrereadRows<P> prereadRows = preread.get(property);

            ImSet<Expr> valueSet = values.values().toSet();
            ImMap<Expr, ObjectValue> prereadedParamValues;
            if(prereadRows!=null) {
                prereadedParamValues = prereadRows.readParams.filter(valueSet);
                valueSet = valueSet.remove(prereadedParamValues.keys());
            } else
                prereadedParamValues = MapFact.EMPTY();

            ImMap<Expr, ObjectValue> readedParamValues;
            if(!valueSet.isEmpty())
                readedParamValues = Expr.readObjectValues(getSQL(), getBaseClass(), valueSet.toMap(), getQueryEnv());
            else
                readedParamValues = MapFact.EMPTY();

            ImMap<ImMap<P, Expr>, Pair<ObjectValue, Boolean>> readValues;
            if(values.size() == property.interfaces.size())
                readValues = MapFact.singleton(values, property.readClassesChanged(getSQL(), values.join(prereadedParamValues.addExcl(readedParamValues)), getBaseClass(), this, getQueryEnv()));
            else
                readValues = MapFact.EMPTY();

            PrereadRows<P> readRows = new PrereadRows<>(readedParamValues, readValues);
            if(prereadRows != null)
                readRows = prereadRows.addExcl(readRows);

            preread.put(property, readRows);
        } finally {
            prereadProps.remove(property);
        }

        eventChange(property, false, true); // используется только в случаях когда гарантировано меняется "источник"
    }

    private MAddSet<CalcProperty> noUpdate = SetFact.mAddSet();
    public void addNoUpdate(CalcProperty property) throws SQLException, SQLHandledException {
        assert allowNoUpdate(property);

        noUpdate.add(property);

        eventNoUpdate(property);
    }

    public <P extends PropertyInterface> ModifyChange<P> getModifyChange(CalcProperty<P> property) {
        return getModifyChange(property, PrereadRows.<P>EMPTY(), SetFact.<CalcProperty>EMPTY());
    }

    public <P extends PropertyInterface> ModifyChange<P> getModifyChange(CalcProperty<P> property, PrereadRows<P> preread, FunctionSet<CalcProperty> disableHint) {

        if(!disableHint.contains(property)) {
            PrereadRows<P> rows = this.preread.get(property);
            if(rows!=null)
                preread = preread.add(rows);

            PropertyChange<P> change;
            if(noUpdate.contains(property))
                change = property.getNoChange();
            else
                change = increment.getPropertyChange(property);

            if(change!=null)
                return new ModifyChange<>(change, preread, true);
        }

        return calculateModifyChange(property, preread, disableHint);
    }

    protected abstract <P extends PropertyInterface> ModifyChange<P> calculateModifyChange(CalcProperty<P> property, PrereadRows<P> preread, FunctionSet<CalcProperty> overrided);

    public ImSet<CalcProperty> getProperties() {
        return getHintProps().merge(calculateProperties());
    }

    public abstract ImSet<CalcProperty> calculateProperties();

    public PropertyChanges calculatePropertyChanges() {
        PropertyChanges result = PropertyChanges.EMPTY;
        for(CalcProperty property : getProperties()) {
            ModifyChange modifyChange = getModifyChange(property);
            if(modifyChange!=null)
                result = result.add(new PropertyChanges(property, modifyChange));
        }
        return result;
    }

    public boolean checkPropertyChanges() throws SQLException, SQLHandledException {
        return BaseUtils.hashEquals(getPropertyChanges(), calculatePropertyChanges());
    }

    public void clean(SQLSession sql, OperationOwner opOwner) throws SQLException {
        increment.clear(sql, opOwner);
        preread.clear();
        assert views.isEmpty();
    }
    
    public void cleanViews() { // нужен для того чтобы очистить views раньше и не синхронизировать тогда clean и eventChange
        assert views.isEmpty();
    }

    @Override
    public String toString() {
        return debugInfo;
    }
    
    public String out() {
        return '\n' + debugInfo + "\nincrement : " + BaseUtils.tab(increment.out());
    }
}
