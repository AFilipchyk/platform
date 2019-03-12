package lsfusion.server.data;

import lsfusion.base.mutability.MutableObject;
import lsfusion.server.ServerLoggers;

import java.sql.SQLException;

// local (not remote) object with SQL resources 
public abstract class MutableClosedObject<O> extends MutableObject {

    private boolean closed;

    protected boolean isClosed() {
        return closed;
    }

    public void explicitClose() throws SQLException {
        ServerLoggers.assertLog(!closed, "ALREADY CLOSED " + this);

        if(closed)
            return;

        onClose(getDefaultCloseOwner());

        closed = true;
    }

    public O getDefaultCloseOwner() {
        return null;
    }


    // явная очистка ресурсов, которые поддерживаются через weak ref'ы
    protected void onClose(O owner) throws SQLException {
    }
}
