package lsfusion.server.data.type;

import lsfusion.base.ExtInt;
import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.server.classes.BaseClass;
import lsfusion.server.classes.ConcreteClass;
import lsfusion.server.classes.sets.AndClassSet;
import lsfusion.server.data.OperationOwner;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.SQLSession;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.data.query.TypeEnvironment;
import lsfusion.server.data.sql.SQLSyntax;
import lsfusion.server.form.view.report.ReportDrawField;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface Type<T> extends ClassReader<T>, FunctionType {

    boolean useIndexedJoin();

    interface Getter<K> {
        Type getType(K key);
    }

    Object getInfiniteValue(boolean min);

    Object castValue(Object object, Type type, SQLSyntax syntax);
    String getCast(String value, SQLSyntax syntax, TypeEnvironment typeEnv); // как правило нужен, чтобы указать СУБД класс, а не реально прокастить 
    String getCast(String value, SQLSyntax syntax, TypeEnvironment typeEnv, Type typeFrom);
    String getSafeCast(String value, SQLSyntax syntax, TypeEnvironment typeEnv, Type typeFrom);

    String getDB(SQLSyntax syntax, TypeEnvironment typeEnv);
    String getDotNetType(SQLSyntax syntax, TypeEnvironment typeEnv); // for ms sql
    String getDotNetRead(String reader); // for ms sql
    String getDotNetWrite(String writer, String value); // for ms sql
    int getDotNetSize(); // for ms sql
    int getSQL(SQLSyntax syntax);

    boolean isSafeString(Object value);
    boolean isSafeType();
    String getString(Object value, SQLSyntax syntax);

    void writeParam(PreparedStatement statement, SQLSession.ParamNum num, Object value, SQLSyntax syntax) throws SQLException;
    void writeNullParam(PreparedStatement statement, SQLSession.ParamNum num, SQLSyntax syntax) throws SQLException;

    int getMinimumWidth();
    int getPreferredWidth();
    int getMaximumWidth();
    boolean fillReportDrawField(ReportDrawField reportField);
    
    boolean isFlex();

    Type getCompatible(Type type);

    ConcreteClass getDataClass(Object value, SQLSession session, AndClassSet classSet, BaseClass baseClass, OperationOwner owner) throws SQLException, SQLHandledException;

    ImList<AndClassSet> getUniversal(BaseClass baseClass);

    Stat getTypeStat(boolean forJoin);

    ExtInt getCharLength();

    T parseString(String s) throws ParseException;

    Object format(T value); // возвращает String или byte[]
    
    AndClassSet getBaseClassSet(BaseClass baseClass);

    String getSID();
}
