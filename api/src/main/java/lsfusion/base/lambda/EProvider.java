package lsfusion.base.lambda;

import com.google.common.base.Throwables;

public abstract class EProvider<T> implements InterruptibleProvider<T> {
    @Override
    public T get() {
        try {
            return getExceptionally();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public abstract T getExceptionally() throws Exception;
}
