package lsfusion.server.data.caches.hash;

import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.comb.map.GlobalObject;
import lsfusion.server.data.translator.MapValuesTranslate;
import lsfusion.server.data.value.Value;
import lsfusion.server.physics.admin.Settings;

public class HashMapValues extends HashValues {

    public final ImMap<Value, ? extends GlobalObject> hashValues;
    public static HashValues create(ImMap<Value, ? extends GlobalObject> hashValues) {
        if(hashValues.isEmpty())
            return HashCodeValues.instance;
        return new HashMapValues(hashValues);
    }
    private HashMapValues(ImMap<Value, ? extends GlobalObject> hashValues) {
        this.hashValues = hashValues;
    }

    public int hash(Value expr) {
        return hashValues.get(expr).hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof HashMapValues && hashValues.equals(((HashMapValues) o).hashValues);
    }

    @Override
    public int hashCode() {
        return hashValues.hashCode();
    }

    public boolean isGlobal() {
        return Settings.get().isCacheInnerHashes();
    }

    public HashValues filterValues(ImSet<Value> values) {
        return create(hashValues.filterIncl(values));
    }

    public HashValues reverseTranslate(MapValuesTranslate translator, ImSet<Value> values) {
        return create(translator.translateMapValues(values.toMap()).join(hashValues));
    }
}
