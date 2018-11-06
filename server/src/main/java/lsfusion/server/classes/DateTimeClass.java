package lsfusion.server.classes;

import com.hexiong.jdbf.JDBFException;
import lsfusion.base.DateConverter;
import lsfusion.base.ExtInt;
import lsfusion.interop.Data;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.data.query.TypeEnvironment;
import lsfusion.server.data.sql.SQLSyntax;
import lsfusion.server.data.type.ParseException;
import lsfusion.server.form.view.report.ReportDrawField;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.actions.integration.exporting.plain.dbf.OverJDBField;
import lsfusion.server.logics.property.actions.integration.exporting.plain.xls.ExportXLSWriter;
import lsfusion.server.logics.property.actions.integration.importing.plain.dbf.CustomDbfRecord;
import net.sf.jasperreports.engine.type.HorizontalAlignEnum;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;

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

    public void fillReportDrawField(ReportDrawField reportField) {
        super.fillReportDrawField(reportField);

        reportField.alignment = HorizontalAlignEnum.RIGHT.getValue();
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

    @Override
    public Timestamp parseDBF(CustomDbfRecord dbfRecord, String fieldName, String charset) throws ParseException, java.text.ParseException {
        return readDBF(dbfRecord.getDate(fieldName));
    }
    @Override
    public Timestamp parseXLS(Cell cell, CellValue formulaValue) throws ParseException {
        java.util.Date cellValue;
        try {
            cellValue = cell.getDateCellValue();
        } catch (IllegalStateException e) {
            return super.parseXLS(cell, formulaValue);
        }
        return readXLS(cellValue);
    }

    public Timestamp parseString(String s) throws ParseException {
        try {
            java.util.Date parse = null;
            try {
                parse = getDateTimeFormat().parse(s);
            } catch (java.text.ParseException e) {
                parse = DateConverter.smartParse(s);
            }
            
            return DateConverter.dateToStamp(parse);
        } catch (Exception e) {
            throw new ParseException("error parsing datetime", e);
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
    public OverJDBField formatDBF(String fieldName) throws JDBFException {
        return new OverJDBField(fieldName, 'D', 8, 0);
    }

    @Override
    public void formatXLS(Timestamp object, Cell cell, ExportXLSWriter.Styles styles) {
        cell.setCellValue(object);
        cell.setCellStyle(styles.dateTime);
    }

    @Override
    public boolean useIndexedJoin() {
        return true;
    }
}
