package lsfusion.server.base.version.impl;

import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.base.version.Version;
import lsfusion.server.base.version.impl.changes.NFRemove;
import lsfusion.server.base.version.impl.changes.NFSetChange;
import lsfusion.server.base.version.interfaces.NFSet;

import java.util.Set;

public abstract class NFASetImpl<T, CH extends NFSetChange<T>, R extends Iterable<T>> extends NFColChangeImpl<T, CH, R> implements NFSet<T> {

    protected NFASetImpl() {
    }

    protected NFASetImpl(boolean allowVersionFinalRead) {
        super(allowVersionFinalRead);
    }

    public void remove(T element, Version version) {
        addChange((CH) new NFRemove<>(element), version);
    }

    protected NFASetImpl(R changes) {
        super(changes);
    }

    protected abstract ImSet<T> getFinalSet(R fcol);

    public ImSet<T> getNFSet(Version version) {
        R result = proceedVersionFinal(version);
        if(result!=null)
            return getFinalSet(result);

        final Set<T> mSet = SetFact.mAddRemoveSet(); 
        proceedChanges(new ChangeProcessor<T, CH>() {
            public void proceed(CH change) {
                change.proceedSet(mSet);
            }
        }, version);
        return SetFact.fromJavaSet(mSet);
    }

    public Iterable<T> getNFIt(Version version) {
        return getNFSet(version);
    }

    @Override
    public boolean containsNF(T element, Version version) {
        return getNFSet(version).contains(element);
    }
}
