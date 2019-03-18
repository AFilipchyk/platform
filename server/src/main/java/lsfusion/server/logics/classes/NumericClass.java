package lsfusion.server.logics.classes;

import com.hexiong.jdbf.JDBFException;
import lsfusion.base.BaseUtils;
import lsfusion.interop.form.property.ExtInt;
import lsfusion.interop.form.property.DataType;
import lsfusion.server.Settings;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.data.query.TypeEnvironment;
import lsfusion.server.data.sql.SQLSyntax;
import lsfusion.server.data.type.ParseException;
import lsfusion.server.physics.dev.i18n.LocalizedString;
import lsfusion.server.logics.mutables.NFStaticLazy;
import lsfusion.server.logics.form.stat.integration.exporting.plain.dbf.OverJDBField;

import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

public class NumericClass extends IntegralClass<BigDecimal> {
    final byte length;
    final byte precision;

    private NumericClass(byte length, byte precision) {
        super(LocalizedString.create("{classes.numeric}"));
        this.length = length;
        this.precision = precision;
    }

    public Class getReportJavaClass() {
        return BigDecimal.class;
    }

    public byte getTypeID() {
        return DataType.NUMERIC; 
    }

    public void serialize(DataOutputStream outStream) throws IOException {

        super.serialize(outStream);

        outStream.writeInt(length);
        outStream.writeInt(precision);
    }

    public int getWhole() {
        return length-precision;
    }

    public byte getLength() {
        return length;
    }

    public int getPrecision() {
        return precision;
    }

    @Override
    protected boolean isNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0;
    }
    @Override
    public boolean isPositive(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) > 0;
    }

    public String getDB(SQLSyntax syntax, TypeEnvironment typeEnv) {
        return syntax.getNumericType(length,precision);
    }

    public String getDotNetType(SQLSyntax syntax, TypeEnvironment typeEnv) {
        return "SqlDecimal";
    }

    public String getDotNetRead(String reader) {
        return reader + ".ReadDecimal()";
    }
    public String getDotNetWrite(String writer, String value) {
        return writer + ".Write(" + value + ");";
    }

    @Override
    public int getBaseDotNetSize() {
        return 16;
    }

    public int getSQL(SQLSyntax syntax) {
        return syntax.getNumericSQL();
    }

    public BigDecimal read(Object value) {
        if(value==null) return null;
        BigDecimal bigDec;
        if (value instanceof BigDecimal) {
            bigDec = (BigDecimal) value;
        } else {
            bigDec = BigDecimal.valueOf(((Number) value).doubleValue());
        }
        
        if(bigDec.scale()!=precision) // важно, так как у BigDecimal'а очень странный equals
            bigDec = bigDec.setScale(precision, BigDecimal.ROUND_HALF_UP);
        return bigDec;
    }

    @Override
    public BigDecimal read(ResultSet set, SQLSyntax syntax, String name) throws SQLException {
        return read(set.getBigDecimal(name));
    }

    public void writeParam(PreparedStatement statement, int num, Object value, SQLSyntax syntax) throws SQLException {
        statement.setBigDecimal(num, (BigDecimal) value);
    }

    public static NumericClass get(int length, int precision) {
        int maxLength = Settings.get().getMaxLength();
        if(length >= maxLength)
            length = maxLength;
        int maxPrecision = Settings.get().getMaxPrecision();
        if(precision >= maxPrecision)
            precision = maxPrecision;
        return get((byte)length, (byte)precision);
    }

    private final static Collection<NumericClass> instances = new ArrayList<>();

    @NFStaticLazy
    public static NumericClass get(byte length, byte precision) {
        synchronized (instances) {
            for(NumericClass instance : instances)
                if(instance.length == length && instance.precision==precision)
                    return instance;
    
            NumericClass instance = new NumericClass(length,precision);
            instances.add(instance);
            DataClass.storeClass(instance);
            return instance;
        }
    }

    @Override
    public ExtInt getCharLength() {
        return new ExtInt(length);
    }

    public BigDecimal getDefaultValue() {
        return new BigDecimal("0.0");
    }

    public BigDecimal parseString(String s) throws ParseException {
        try {
            return new BigDecimal(s.replace(',','.'));
        } catch (Exception e) {
            throw new ParseException("error parsing numeric", e);
        }
    }

    @Override
    public String getSID() {
        return "NUMERIC" + "_" + length + "_" + precision;
    }
    
    @Override
    public String getCanonicalName() {
        String userSID = getSID();
        return userSID.replaceFirst("_", "[").replaceFirst("_", ",") + "]";
    }

    @Override
    public Number getInfiniteValue(boolean min) {
        return new BigDecimal((min ? "-" : "") + BaseUtils.replicate('9', getWhole()) + "." + BaseUtils.replicate('9', getPrecision()));
    }

    @Override
    public OverJDBField formatDBF(String fieldName) throws JDBFException {
        return new OverJDBField(fieldName, 'N', Math.min(getLength(), 253), getPrecision());
    }

    @Override
    public Stat getTypeStat() {
        return new Stat(10, length);
    }
}
