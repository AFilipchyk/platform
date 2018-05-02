package lsfusion.server.classes;

import lsfusion.base.DateConverter;
import lsfusion.base.ExtInt;
import lsfusion.interop.Data;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.data.query.TypeEnvironment;
import lsfusion.server.data.sql.SQLSyntax;
import lsfusion.server.data.type.ParseException;
import lsfusion.server.form.view.report.ReportDrawField;
import lsfusion.server.logics.i18n.LocalizedString;
import net.sf.jasperreports.engine.type.HorizontalAlignEnum;

import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class DateTimeClass extends DataClass<Timestamp> {

    public final static DateTimeClass instance = new DateTimeClass();

    static {
        DataClass.storeClass(instance);
    }

    private DateTimeClass() { super(LocalizedString.create("{classes.date.with.time}")); }

    public int getReportPreferredWidth() {
        return 75;
    }

    public Class getReportJavaClass() {
        return Timestamp.class;
    }

    public boolean fillReportDrawField(ReportDrawField reportField) {
        if (!super.fillReportDrawField(reportField))
            return false;

        reportField.alignment = HorizontalAlignEnum.RIGHT.getValue();
        return true;
    }

    public byte getTypeID() {
        return Data.DATETIME;
    }

    public DataClass getCompatible(DataClass compClass, boolean or) {
        return compClass instanceof DateTimeClass ? this : null;
    }

    public Timestamp getDefaultValue() {
        return new Timestamp(System.currentTimeMillis());
    }

    public String getDB(SQLSyntax syntax, TypeEnvironment typeEnv) {
        return syntax.getDateTimeType();
    }
    public String getDotNetType(SQLSyntax syntax, TypeEnvironment typeEnv) {
        return "SqlDateTime";
    }

    public String getDotNetRead(String reader) {
        return "DateTime.FromBinary("+reader+".ReadInt64())";
    }

    public String getDotNetWrite(String writer, String value) {
        return writer + ".Write(" +value + ".ToBinary());";
    }

    public int getBaseDotNetSize() {
        return 8;
    }

    public int getSQL(SQLSyntax syntax) {
        return syntax.getDateTimeSQL();
    }

    public Timestamp read(Object value) {
        if (value == null) return null;
        return (Timestamp) value;
    }

    @Override
    public Timestamp read(ResultSet set, SQLSyntax syntax, String name) throws SQLException {
        return set.getTimestamp(name);
    }

    public void writeParam(PreparedStatement statement, int num, Object value, SQLSyntax syntax) throws SQLException {
        statement.setTimestamp(num, (Timestamp) value);
    }

    @Override
    public boolean isSafeType() {
        return false;
    }

    @Override
    public ExtInt getCharLength() {
        return new ExtInt(25);
    }

    public boolean isSafeString(Object value) {
        return false;
    }

    public String getString(Object value, SQLSyntax syntax) {
        throw new RuntimeException("not supported");
    }

    public Timestamp parseString(String s) throws ParseException {
        try {
            return DateConverter.dateToStamp(getDateTimeFormat().parse(s));
        } catch (Exception e) {
            try {
                return DateConverter.dateToStamp(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s));
            } catch (Exception e2) {
                throw new ParseException("error parsing datetime", e);
            }
        }
    }

    @Override
    public String formatString(Timestamp value) {
        return value == null ? null : getDateTimeFormat().format(value);
    }

    public static DateFormat getDateTimeFormat() {
        return new SimpleDateFormat("dd.MM.yy HH:mm:ss");
    }

    public static String format(Date date) {
        return getDateTimeFormat().format(date);
    }

    public String getSID() {
        return "DATETIME";
    }

    @Override
    public Object getInfiniteValue(boolean min) {
        return min ? new Timestamp(0) : new Timestamp(Long.MAX_VALUE);
    }

    @Override
    public Stat getTypeStat() {
        return new Stat(Long.MAX_VALUE);
    }

    @Override
    public boolean useIndexedJoin() {
        return true;
    }
}
