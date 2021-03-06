package lsfusion.interop.form.event;

import java.io.Serializable;
import java.util.Map;
import java.util.WeakHashMap;

import static lsfusion.base.BaseUtils.nullEquals;

public class EventBus implements Serializable {

    private WeakHashMap<ValueEventListener, String> listeners = new WeakHashMap<>();

    public synchronized void addListener(ValueEventListener listener, String eventID) {
        listeners.put(listener, eventID);
    }

    public void fireValueChanged(String eventID, Object value) {
        fireEvent(new ValueEvent(eventID, value));
    }

    public synchronized void fireEvent(ValueEvent event) {
        for (Map.Entry<ValueEventListener, String> entry : listeners.entrySet()) {
            if (nullEquals(entry.getValue(), event.getSID())) {
                entry.getKey().actionPerfomed(event);
            }
        }
    }

    public synchronized void invalidate() {
        listeners.clear();
    }
}
