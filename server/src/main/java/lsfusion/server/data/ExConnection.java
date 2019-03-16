package lsfusion.server.data;

import lsfusion.base.BaseUtils;
import lsfusion.server.physics.admin.logging.ServerLoggers;
import lsfusion.server.physics.admin.Settings;
import lsfusion.server.data.sql.SQLSyntax;

import java.sql.Connection;
import java.sql.SQLException;

public class ExConnection {
    public Connection sql;
    public SQLTemporaryPool temporary;

    public ExConnection(Connection sql, SQLTemporaryPool temporary) {
        this.sql = sql;
        this.temporary = temporary;
        long currentTime = System.currentTimeMillis();
        this.timeStarted = currentTime;
        this.lastTempTablesActivity = currentTime;
    }
    
    private Integer lastLogLevel; // оптимизация
    public synchronized void updateLogLevel(SQLSyntax syntax) {
        int logLevel = Settings.get().getLogLevelJDBC();
        if(lastLogLevel == null || !lastLogLevel.equals(logLevel)) {
            syntax.setLogLevel(logLevel);
            lastLogLevel = logLevel;
        }
    }

    public void checkClosed() throws SQLException {
        ServerLoggers.assertLog(!sql.isClosed(), "CONNECTION IS ALREADY CLOSED " + sql);
    }

    public double lengthScore;
    public double timeScore;
    public long timeStarted;
    public int maxTotalSessionTablesCount;
    public long lastTempTablesActivity;

    public void registerExecute(int length, long runTime) {
        Settings settings = Settings.get();
        int degree = settings.getQueryExecuteDegree();
        // тут по хорошему надо было бы использовать DoubleAdder но он только в 8-й java
        lengthScore += BaseUtils.pow(((double)length/((double)settings.getQueryLengthAverageMax())), degree);
        timeScore += BaseUtils.pow(((double)runTime/((double)settings.getQueryTimeAverageMax())), degree);
    }
}
