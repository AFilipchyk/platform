package lsfusion.server.logics.action.session.change;

import lsfusion.base.BaseUtils;
import lsfusion.base.mutability.TwinImmutableObject;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.AddValue;
import lsfusion.base.col.interfaces.mutable.SimpleAddValue;
import lsfusion.server.base.caches.AbstractValuesContext;
import lsfusion.server.base.caches.hash.HashValues;
import lsfusion.server.data.Value;
import lsfusion.server.data.translator.MapValuesTranslate;
import lsfusion.server.logics.action.data.PrereadRows;
import lsfusion.server.logics.property.PropertyInterface;

public class ModifyChange<P extends PropertyInterface> extends AbstractValuesContext<ModifyChange<P>> {
    public final PropertyChange<P> change;
    public final boolean isFinal;
    public final PrereadRows<P> preread;

    public ModifyChange(PropertyChange<P> change, boolean isFinal) {
        this(change, PrereadRows.<P>EMPTY(), isFinal);
    }

    public ModifyChange(PropertyChange<P> change, PrereadRows<P> preread, boolean isFinal) {
        assert change!=null;
        this.change = change;
        this.isFinal = isFinal;
        this.preread = preread;
    }

    public int hash(HashValues hashValues) {
        return 31 * (change.hashValues(hashValues) * 31 + preread.hash(hashValues)) + (isFinal?1:0);
    }

    public ImSet<Value> getValues() {
        return change.getInnerValues().merge(preread.getContextValues());
    }

    @Override
    public ModifyChange<P> translate(MapValuesTranslate mapValues) {
        return new ModifyChange<>(change.translateValues(mapValues), preread.translateValues(mapValues), isFinal);
    }

    @Override
    public boolean calcTwins(TwinImmutableObject o) {
        return change.equals(((ModifyChange)o).change) && isFinal == ((ModifyChange)o).isFinal && preread.equals(((ModifyChange)o).preread);
    }

    public ModifyChange<P> add(ModifyChange<P> modify) {
        if(isFinal)
            return this;
        return new ModifyChange<>(change.add(modify.change), preread.add(modify.preread), modify.isFinal);
    }

    public boolean isEmpty() {
        return change.where.isFalse() && preread.isEmpty();
    }

    public final static AddValue<Object, ModifyChange<PropertyInterface>> addValue = new SimpleAddValue<Object, ModifyChange<PropertyInterface>>() {
        public ModifyChange<PropertyInterface> addValue(Object key, ModifyChange<PropertyInterface> prevValue, ModifyChange<PropertyInterface> newValue) {
            return prevValue.add(newValue);
        }

        public boolean reversed() {
            return false;
        }

        public AddValue<Object, ModifyChange<PropertyInterface>> reverse() {
            throw new UnsupportedOperationException();
        }
    };
    public static <M> AddValue<M, ModifyChange> addValue() {
        return BaseUtils.immutableCast(addValue);
    }

    @Override
    public String toString() {
        return change + ", f:" + isFinal + ", p:" + preread;
    }

    public ChangeType getChangeType() {
        if(isEmpty()) {
            assert isFinal;
            return ChangeType.NOUPDATE;
        }
        Boolean setOrDropped = null;
        if(preread.isEmpty())
            setOrDropped = change.getSetOrDropped();

        return ChangeType.get(isFinal, setOrDropped);
    }
}
