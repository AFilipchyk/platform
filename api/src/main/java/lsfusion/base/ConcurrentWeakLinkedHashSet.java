package lsfusion.base;

import java.util.*;

public class ConcurrentWeakLinkedHashSet<L> implements Iterable<L> {

    private int maxIndex = 0;
    private WeakHashMap<L, Integer> map = new WeakHashMap<>();

    public synchronized void add(L item) {
        if(!map.containsKey(item))
            map.put(item, maxIndex++);
    }

    public synchronized Iterator<L> iterator() {
        SortedMap<Integer, L> list = new TreeMap<>();
        for(Map.Entry<L,Integer> entry : map.entrySet())
            list.put(entry.getValue(), entry.getKey());
        return list.values().iterator();
    }
}
