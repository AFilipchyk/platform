package lsfusion.server.data;

import java.sql.SQLException;

public class SQLTooLongQueryException extends SQLHandledException {
    
    private final String select;

    public SQLTooLongQueryException(String select) {
        this.select = select;
    }

    public boolean repeatApply(SQLSession sql, OperationOwner owner, int attempts) throws SQLException {
        return false;
    }

    public String toString() {
        return "TOO LONG QUERY ROWS, LENGTH : " + select.length() + ", QUERY : " + select;
    }

    @Override
    public boolean willDefinitelyBeHandled() {
        return false;
    }

    @Override
    public String getDescription(boolean wholeTransaction) {
        return "ln";
    }
}
