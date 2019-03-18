package lsfusion.server.logics.classes.user.set;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.base.mutability.TwinImmutableObject;
import lsfusion.server.data.type.ObjectType;
import lsfusion.server.data.type.Type;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.physics.dev.id.name.ClassCanonicalNameUtils;

// по большому счету только для class data props
public class ResolveOrObjectClassSet extends TwinImmutableObject implements ResolveClassSet {

    public final ResolveUpClassSet up;
    public final ImSet<ConcreteCustomClass> set; 
    
    public ResolveOrObjectClassSet(ResolveUpClassSet up, ImSet<ConcreteCustomClass> customClasses) {
        this.up = up;
        this.set = customClasses;
    }

    private ResolveOrObjectClassSet(ResolveUpClassSet up) {
        this(up, SetFact.<ConcreteCustomClass>EMPTY());
    }

    public static ResolveOrObjectClassSet fromSetConcreteChildren(ImSet<ConcreteCustomClass> set) {
        return new ResolveOrObjectClassSet(ResolveUpClassSet.FALSE, set);
    }

    @Override
    public boolean containsAll(ResolveClassSet set, boolean implicitCast) {
        if(this.set.isEmpty())
            return up.containsAll(set, implicitCast);
        throw new UnsupportedOperationException();
//        return toAnd().containsAll(set.toAnd(), implicitCast); // жестко, но пока используется только для check
    }

    // добавляет отфильтровывая up'ы
    private static void addAll(MSet<ConcreteCustomClass> mTo, ImSet<ConcreteCustomClass> set, ResolveUpClassSet up) {
        for(int i=0,size=set.size();i<size;i++) {
            ConcreteCustomClass nodeSet = set.get(i);
            if(up.has(nodeSet))
                mTo.add(nodeSet);
        }
    }

    @Override
    public ResolveClassSet and(ResolveClassSet resolveSet) {
        if(resolveSet instanceof ResolveOrObjectClassSet)
            return and((ResolveOrObjectClassSet)resolveSet);

        if(!(resolveSet instanceof ResolveUpClassSet))
            return ResolveUpClassSet.FALSE;
        return and(new ResolveOrObjectClassSet((ResolveUpClassSet)resolveSet));
    }

    public ResolveOrObjectClassSet and(ResolveOrObjectClassSet node) {
        // or'им Up'ы, or'им Set'ы после чего вырезаем из Set'а все кто есть в Up'ах

        MSet<ConcreteCustomClass> mAndSet = SetFact.mSet(set.filter(node.set));
        addAll(mAndSet, set, node.up);
        addAll(mAndSet, node.set, up);
        return new ResolveOrObjectClassSet(up.and(node.up), mAndSet.immutable());
    }

    @Override
    public ResolveClassSet or(ResolveClassSet resolveSet) {
        if(resolveSet instanceof ResolveOrObjectClassSet)
            return or((ResolveOrObjectClassSet)resolveSet);
        return or(new ResolveOrObjectClassSet((ResolveUpClassSet)resolveSet));
    }

    public ResolveOrObjectClassSet or(ResolveOrObjectClassSet node) {
        // or'им Up'ы, or'им Set'ы после чего вырезаем из Set'а все кто есть в Up'ах
        return new ResolveOrObjectClassSet(up.or(node.up), set.merge(node.set));
    }


    @Override
    public boolean isEmpty() {
        return up.isEmpty() && set.isEmpty();
    }

    @Override
    public Type getType() {
        return ObjectType.instance;
    }

    @Override
    public ValueClass getCommonClass() {
        return OrObjectClassSet.getCommonClass(SetFact.toExclSet(up.getCommonClasses()).addExcl(set));
    }

    @Override
    protected boolean calcTwins(TwinImmutableObject o) {
        return up.equals(((ResolveOrObjectClassSet)o).up) && set.equals(((ResolveOrObjectClassSet)o).set);
    }

    @Override
    public int immutableHashCode() {
        return 31 * up.hashCode() + set.hashCode();
    }

    @Override
    public AndClassSet toAnd() {
        return new OrObjectClassSet(up.toAnd(), set);
    }

    @Override
    public String getCanonicalName() {
        return ClassCanonicalNameUtils.createName(this);
    }

    @Override
    public boolean equalsCompatible(ResolveClassSet set) {
        return BaseUtils.hashEquals(this, set);
    }
}
