package lsfusion.server.caches;

import lsfusion.base.BaseUtils;
import lsfusion.base.comb.map.GlobalObject;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.add.MAddSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.base.col.interfaces.mutable.mapvalue.ImRevValueMap;
import lsfusion.base.col.lru.LRUWVWSMap;
import lsfusion.server.caches.hash.HashCodeValues;
import lsfusion.server.caches.hash.HashMapValues;
import lsfusion.server.caches.hash.HashValues;
import lsfusion.server.data.Value;
import lsfusion.server.data.translator.MapValuesTranslate;

public abstract class AbstractValuesContext<U extends ValuesContext<U>> extends AbstractTranslateContext<U, MapValuesTranslate, HashValues> implements ValuesContext<U> {

    public static ImRevMap<Value, Value> getBigValues(ImSet<Value> values) {
        MAddSet<Value> usedValues = SetFact.mAddSet(values);

        boolean removed = false;

        ImRevValueMap<Value, Value> result = values.mapItRevValues(); // есть промежуточная коллекция
        for(int i=0,size=values.size();i<size;i++) {
            Value value = values.get(i);
            Value removeValue = value.removeBig(usedValues);
            if(removeValue!=null) {
                removed = true;
                result.mapValue(i, removeValue);
                usedValues.add(removeValue);
            } else
                result.mapValue(i, value);
        }
        if(!removed)
            return null;
        return result.immutableValueRev();
    }

    protected U aspectContextTranslate(MapValuesTranslate translator) {
        ImSet<Value> values = aspectGetValues();

        ImSet<Value> transValues = translator.translateValues(values);
        if(transValues.equals(values))
            return (U) this;
        else {
            AbstractValuesContext result = (AbstractValuesContext) translate(translator);
            result.values = transValues;
            return (U) result;
        }
    }

    protected HashValues aspectContextHash(HashValues hash) {
        return hash.filterValues(aspectGetValues());
    }

    public U translateValues(MapValuesTranslate translate) {
        return aspectTranslate(translate);
    }

    public U translateRemoveValues(MapValuesTranslate translate) {
        return translateValues(translate);
    }

    public int hashValues(HashValues hashValues) {
        return aspectHash(hashValues);
    }

    public ImSet<Value> getContextValues() {
        return aspectGetValues();
    }

    public int immutableHashCode() {
        return hashValues(HashCodeValues.instance);
    }

    private final static GetValue<GlobalObject, Value> paramClasses = new GetValue<GlobalObject, Value>() {
        public GlobalObject getMapValue(Value value) {
            return value.getValueClass();
        }
    };
    public static ImMap<Value, GlobalObject> getParamClasses(ImSet<Value> values) {
        return values.mapValues(paramClasses);
    }

    // множественные наследование
    public static BaseUtils.HashComponents<Value> getComponents(final ValuesContext<?> valuesContext) {
        return BaseUtils.getComponents(new BaseUtils.HashInterface<Value, GlobalObject>() {
            public ImMap<Value, GlobalObject> getParams() {
                return getParamClasses(valuesContext.getContextValues());
            }

            public int hashParams(ImMap<Value, ? extends GlobalObject> map) {
                return valuesContext.hashValues(HashMapValues.create(map));
            }
        });
    }

    private BaseUtils.HashComponents<Value> valueComponents;
    @ManualLazy
    public BaseUtils.HashComponents<Value> getValueComponents() {
        if(valueComponents==null)
            valueComponents = aspectGetValueComponents();
        return valueComponents;
    }
    private static BaseUtils.HashComponents<Value> translate(BaseUtils.HashComponents<Value> components, MapValuesTranslate translator) {
        return new BaseUtils.HashComponents<>(translator.translateValuesMapKeys(components.map), components.hash);
    }
    private BaseUtils.HashComponents<Value> aspectGetValueComponents() {
//        U from = getFrom();
//        MapValuesTranslate translator = getTranslator();
//        if(from!=null && translator!=null) // объект не ушел
//            return translate(from.getValueComponents(), translator);
        LRUWVWSMap.Value<MapValuesTranslate, U> fromPair = getFromValue();
        MapValuesTranslate translator = fromPair.getLRUKey();
        if(translator!=null)
            return translate(fromPair.getLRUValue().getValueComponents(), translator);

        return calculateValueComponents();
    }
    public BaseUtils.HashComponents<Value> calculateValueComponents() {
        return getComponents(this);
    }

    protected HashValues reverseTranslate(HashValues hash, MapValuesTranslate translator) {
        return hash.reverseTranslate(translator, aspectGetValues());
    }
}
