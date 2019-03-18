package lsfusion.server.logics.action.session;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.data.OperationOwner;
import lsfusion.server.data.SQLSession;
import lsfusion.server.logics.property.CalcProperty;
import lsfusion.server.logics.property.PropertyInterface;

import java.sql.SQLException;
import java.util.Map;

public class TableProps {

    public TableProps() {
    }

    private Map<CalcProperty, PropertyChangeTableUsage<PropertyInterface>> tables = MapFact.mAddRemoveMap(); // mutable с remove поведение

    public String out() {
        return "\ntables : " + tables.toString();
    }
    
    public String toString() {
        return tables.toString();
    }

    public ImSet<CalcProperty> getProperties() {
        return SetFact.fromJavaSet(tables.keySet());
    }

    public boolean contains(CalcProperty property) {
        return tables.containsKey(property);
    }

    public <P extends PropertyInterface> PropertyChangeTableUsage<P> getTable(CalcProperty<P> property) {
        return (PropertyChangeTableUsage<P>)tables.get(property);
    }

    public <P extends PropertyInterface> PropertyChange<P> getPropertyChange(CalcProperty<P> property) {
        PropertyChangeTableUsage<P> table = getTable(property);
        if(table!=null)
            return PropertyChangeTableUsage.getChange(table);
        return null;
    }

    public <P extends PropertyInterface> void add(CalcProperty<P> property, PropertyChangeTableUsage<P> changeTable) {
        tables.put(property, (PropertyChangeTableUsage<PropertyInterface>) changeTable);
    }

    public <P extends PropertyInterface> void remove(CalcProperty<P> property, SQLSession sql, OperationOwner owner) throws SQLException {
        tables.remove(property).drop(sql, owner);
    }

    public void clear(SQLSession session, OperationOwner owner) throws SQLException {
        for (Map.Entry<CalcProperty, PropertyChangeTableUsage<PropertyInterface>> addTable : tables.entrySet())
            addTable.getValue().drop(session, owner);
        tables.clear();
    }
}
