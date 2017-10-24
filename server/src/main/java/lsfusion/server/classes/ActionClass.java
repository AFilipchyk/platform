package lsfusion.server.classes;

import lsfusion.interop.Data;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.data.query.TypeEnvironment;
import lsfusion.server.data.sql.SQLSyntax;
import lsfusion.server.data.type.ParseException;
import lsfusion.server.form.view.report.ReportDrawField;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.i18n.LocalizedString;

import java.sql.PreparedStatement;
import java.sql.SQLException;

// по умолчанию будем считать, что у ActionClass'а данные как у LogicalClass
public class ActionClass extends DataClass<Object> {

    public static final ActionClass instance = new ActionClass();

    public static final ObjectValue TRUE = ObjectValue.getValue(true, instance);

    private final static String sid = "ActionClass";
    static {
        DataClass.storeClass(instance);
    }

    private ActionClass() { super(LocalizedString.create("{classes.action}")); }

    public DataClass getCompatible(DataClass compClass, boolean or) {
        return compClass instanceof ActionClass ? this : null;
    }

    public Object getDefaultValue() {
        return true;
//        throw new RuntimeException("Неправильный вызов интерфейса");
    }

    @Override
    public byte getTypeID() {
        return Data.ACTION;
    }

    protected Class getReportJavaClass() {
        return Boolean.class;
    }

    public String getDB(SQLSyntax syntax, TypeEnvironment typeEnv) {
        return syntax.getBitType();
    }
    public String getDotNetType(SQLSyntax syntax, TypeEnvironment typeEnv) {
        throw new UnsupportedOperationException();
    }
    public String getDotNetRead(String reader) {
        throw new UnsupportedOperationException();
    }
    public String getDotNetWrite(String writer, String value) {
        throw new UnsupportedOperationException();
    }
    public int getBaseDotNetSize() {
        throw new UnsupportedOperationException();
    }

    public int getSQL(SQLSyntax syntax) {
        return syntax.getBitSQL();
    }

    public boolean isSafeString(Object value) {
        return true;
    }

    public String getString(Object value, SQLSyntax syntax) {
        assert (Boolean)value;
        return syntax.getBitString(true);
    }

    public void writeParam(PreparedStatement statement, int num, Object value, SQLSyntax syntax) throws SQLException {
        assert (Boolean)value;
        statement.setByte(num, (byte)1);
    }

    public Object read(Object value) {
        if(value!=null) return true;
        return null;
    }

    @Override
    public boolean fillReportDrawField(ReportDrawField reportField) {
        return false;
    }

    public Object parseString(String s) throws ParseException {
        throw new RuntimeException("not supported");
    }

    public String getSID() {
        return sid;
    }

    @Override
    public String getParsedName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stat getTypeStat() {
        return Stat.ONE;
    }

    @Override
    public boolean isFlex() {
        return false;
    }
}
