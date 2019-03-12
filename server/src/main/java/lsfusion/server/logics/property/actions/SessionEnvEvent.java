package lsfusion.server.logics.property.actions;

import lsfusion.base.BaseUtils;
import lsfusion.base.mutability.TwinImmutableObject;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.AddValue;
import lsfusion.base.col.interfaces.mutable.SimpleAddValue;
import lsfusion.base.col.interfaces.mutable.SymmAddValue;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.session.DataSession;

public class SessionEnvEvent extends TwinImmutableObject {

    public final static SessionEnvEvent ALWAYS = new SessionEnvEvent(null);

    public final static SimpleAddValue<Object, SessionEnvEvent> mergeEnv = new SymmAddValue<Object, SessionEnvEvent>() {
        public SessionEnvEvent addValue(Object key, SessionEnvEvent prevValue, SessionEnvEvent newValue) {
            return prevValue.merge(newValue);
        }
    };

    public static <T> AddValue<T, SessionEnvEvent> mergeSessionEnv() {
        return BaseUtils.immutableCast(mergeEnv);
    }

    public SessionEnvEvent merge(SessionEnvEvent env) {
        if(forms==null)
            return this;
        if(env.forms==null)
            return env;

        return new SessionEnvEvent(forms.merge(env.forms));
    }

    private final ImSet<FormEntity> forms;
    public SessionEnvEvent(ImSet<FormEntity> forms) {
        this.forms = forms;
    }

    public boolean contains(DataSession element) {
        if(forms==null) { // вообще говоря не только оптимизация, так как activeForms может быть пустым
            assert this==ALWAYS;
            return true;
        }

        for(FormInstance form : element.getAllActiveForms())
            if(forms.contains(form.entity))
                return true;
        if(element.hasSessionEventActiveForms(forms))
            return true;

        return false;
    }

    public boolean isEmpty() {
        return false;
    }

    public boolean isFull() {
        return false;
    }

    public boolean calcTwins(TwinImmutableObject o) {
        return BaseUtils.nullHashEquals(forms, ((SessionEnvEvent)o).forms);
    }

    public int immutableHashCode() {
        return forms == null ? 34432 : forms.hashCode();
    }
}
