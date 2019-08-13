package lsfusion.server.base.version.impl;

import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.base.version.FindIndex;
import lsfusion.server.base.version.Version;
import lsfusion.server.base.version.impl.changes.NFMove;
import lsfusion.server.base.version.impl.changes.NFOrderSetChange;
import lsfusion.server.base.version.impl.changes.NFRemoveAll;
import lsfusion.server.base.version.interfaces.NFOrderSet;

import java.util.List;

public class NFOrderSetImpl<T> extends NFASetImpl<T, NFOrderSetChange<T>, ImOrderSet<T>> implements NFOrderSet<T> {

    public NFOrderSetImpl() {
    }

    public NFOrderSetImpl(boolean allowVersionFinalRead) {
        super(allowVersionFinalRead);
    }

    public NFOrderSetImpl(ImOrderSet<T> changes) {
        super(changes);
    }

    public ImOrderSet<T> getNF(Version version) {
        ImOrderSet<T> result = proceedVersionFinal(version);
        if(result!=null)
            return result;

        final List<T> mSet = SetFact.mAddRemoveOrderSet();
        proceedChanges(change -> change.proceedOrderSet(mSet), version);
        return SetFact.fromJavaOrderSet(mSet);
    }

    public ImOrderSet<T> getNFOrderSet(Version version) {
        return getNF(version);
    }

    @Override
    public int size(Version version) {
        return getNF(version).size();
    }

    public ImSet<T> getSet() {
        return getOrderSet().getSet();
    }

    public ImList<T> getNFList(Version version) {
        return getNF(version);
    }

    public ImList<T> getList() {
        return getFinal();
    }

    public ImOrderSet<T> getOrderSet() {
        return getFinal();
    }

    // множественное наследование

    public void removeAll(Version version) {
        addChange(NFRemoveAll.<T>getInstance(), version);
    }

    public Iterable<T> getListIt() {
        return NFListImpl.getListIt(this);
    }

    public Iterable<T> getNFListIt(Version version) {
        return NFListImpl.getNFListIt(this, version);
    }

    public void add(T element, FindIndex<T> finder, Version version) {
        add(element, version);
        move(element, finder, version);
    }
    
    private static final FindIndex moveFirst = list -> 0;

    public void addFirst(T element, Version version) {
        add(element, moveFirst, version);
    }

    public void move(T element, FindIndex<T> finder, Version version) {
        addChange(new NFMove<>(element, finder), version);
    }

    private static class MoveFinder<T> implements FindIndex<T> {
        private final T element;
        private final boolean isRightNeighbour;
        private final boolean ifNotExistsThenLast;

        private MoveFinder(T element, boolean isRightNeighbour, boolean ifNotExistsThenLast) {
            this.element = element;
            this.isRightNeighbour = isRightNeighbour;
            this.ifNotExistsThenLast = ifNotExistsThenLast;
        }

        public int getIndex(List<T> list) {
            int index = list.indexOf(element);
            if(index < 0) {
                if(ifNotExistsThenLast)
                    return list.size();
                else
                    assert false;
            }
            if(isRightNeighbour)
                ++index;
            return index;
        }
    }
    public void move(T element, final T otherElement, final boolean isRightNeighbour, Version version) {
        move(element, new MoveFinder<>(otherElement, isRightNeighbour, false), version);
    }

    public void addIfNotExistsToThenLast(T element, T to, boolean isRightNeighbour, Version version) {
        add(element, new MoveFinder<>(to, isRightNeighbour, true), version);        
    }

    protected ImSet<T> getFinalSet(ImOrderSet<T> fcol) {
        return fcol.getSet();
    }
}
