package lsfusion.gwt.client.form.order.user;

import lsfusion.gwt.client.form.object.table.GGridPropertyTable;
import lsfusion.gwt.shared.view.GGroupObject;
import lsfusion.gwt.shared.view.GOrder;
import lsfusion.gwt.shared.view.GPropertyDraw;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class GGridSortableHeaderManager<T> {
    private GGridPropertyTable table;
    private boolean ignoreFirstColumn;
    private LinkedHashMap<T, Boolean> orderDirections = new LinkedHashMap<>();

    public GGridSortableHeaderManager(GGridPropertyTable table, boolean ignoreFirstColumn) {
        this.table = table;
        this.ignoreFirstColumn = ignoreFirstColumn;
    }

    public void headerClicked(int columnIndex, boolean withCtrl) {
        if (columnIndex != -1 && !(ignoreFirstColumn && columnIndex==0)) {
            T columnKey = getColumnKey(columnIndex);
            Boolean sortDir = orderDirections.get(columnKey);
            if (sortDir == null || !sortDir) {
                if (!withCtrl) {
                    changeOrder(columnKey, GOrder.REPLACE);
                } else {
                    changeOrder(columnKey, GOrder.ADD);
                }
            } else {
                if (!withCtrl) {
                    changeOrder(columnKey, GOrder.DIR);
                } else {
                    changeOrder(columnKey, GOrder.REMOVE);
                }
            }
        }
    }

    public final Boolean getSortDirection(int column) {
        if (column < 0 || column >= table.getColumnCount()) {
            return null;
        }

        return orderDirections.get(getColumnKey(column));
    }

    public final void changeOrder(T columnKey, GOrder modiType) {
        if (columnKey == null || noSort(columnKey)) {
            return;
        }

        switch (modiType) {
            case REPLACE:
                orderDirections.clear();
                orderDirections.put(columnKey, true);
                break;
            case ADD:
                orderDirections.put(columnKey, true);
                break;
            case DIR:
                orderDirections.put(columnKey, !orderDirections.get(columnKey));
                break;
            case REMOVE:
                orderDirections.remove(columnKey);
                break;
        }

        orderChanged(columnKey, modiType);
    }

    public void clearOrders(GGroupObject groupObject) {
        orderDirections.clear();
        ordersCleared(groupObject);
    }

    private boolean noSort(T columnKey) {
        if (columnKey instanceof HashMap) {
            for (Object entry : ((HashMap) columnKey).keySet()) {
                if (entry instanceof GPropertyDraw && ((GPropertyDraw) entry).noSort)
                    return true;
            }
        }
        return false;
    }

    public Map<T, Boolean> getOrderDirections() {
        return orderDirections;
    }

    protected abstract void orderChanged(T columnKey, GOrder modiType);

    protected abstract void ordersCleared(GGroupObject groupObject);

    protected abstract T getColumnKey(int column);
}
