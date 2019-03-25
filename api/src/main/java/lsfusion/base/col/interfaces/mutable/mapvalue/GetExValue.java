package lsfusion.base.col.interfaces.mutable.mapvalue;

public interface GetExValue<M, V, E1 extends Exception, E2 extends Exception> {

    M getMapValue(V value) throws E1, E2;
}
