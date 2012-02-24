package platform.server.data.type;

import platform.base.ListCombinations;
import platform.server.classes.BaseClass;
import platform.server.classes.ConcatenateClassSet;
import platform.server.classes.ConcreteClass;
import platform.server.classes.sets.AndClassSet;
import platform.server.data.SQLSession;
import platform.server.data.expr.DeconcatenateExpr;
import platform.server.data.expr.Expr;
import platform.server.data.expr.KeyType;
import platform.server.data.expr.query.Stat;
import platform.server.data.query.Query;
import platform.server.data.sql.SQLSyntax;
import platform.server.form.view.report.ReportDrawField;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.Format;
import java.util.*;

public class ConcatenateType extends AbstractType<byte[]> {

    private Type[] types;

    public ConcatenateType(Type[] types) {
        this.types = types;
    }

    public Type get(int i) {
        return types[i];        
    }

    public String getDB(SQLSyntax syntax) {
        return syntax.getBinaryType(getBinaryLength(syntax.isBinaryString()));
    }
    public int getSQL(SQLSyntax syntax) {
        return syntax.getBinarySQL();
    }

    public boolean isSafeString(Object value) {
        return false;
    }

    public String getString(Object value, SQLSyntax syntax) {
        return value.toString();
    }

    public byte[] read(Object value) {
        if(value instanceof String)
            return ((String)value).getBytes();
        else
            return (byte[])value;
    }
    
    public void writeParam(PreparedStatement statement, int num, Object value, SQLSyntax syntax) throws SQLException {
         if(syntax.isBinaryString())
            statement.setString(num,new String((byte[])value));
         else
            statement.setBytes(num,(byte[])value);
    }

    public Format getReportFormat() {
        throw new RuntimeException("not supported");
    }

    public int getMinimumWidth() {
        throw new RuntimeException("not supported");
    }

    public int getPreferredWidth() {
        throw new RuntimeException("not supported");
    }

    public int getMaximumWidth() {
        throw new RuntimeException("not supported");
    }

    public boolean fillReportDrawField(ReportDrawField reportField) {
        throw new RuntimeException("not supported");
    }

    public boolean isCompatible(Type type) {
        if(!(type instanceof ConcatenateType)) return false;

        ConcatenateType concatenate = (ConcatenateType)type;
        assert concatenate.types.length == types.length;
        for(int i=0;i<types.length;i++)
            if(!(types[i].isCompatible(concatenate.types[i])))
                return false;
        return true;
    }

    private ConcreteClass createConcrete(ConcreteClass[] classes) {
        return new ConcatenateClassSet(classes);
    }

    public ConcreteClass getDataClass(Object value, SQLSession session, BaseClass baseClass) throws SQLException {
        byte[] byteValue = read(value);

        int offset = 0;
        ConcreteClass[] classes = new ConcreteClass[types.length];
        for(int i=0;i<types.length;i++) {
            int blength = types[i].getBinaryLength(session.syntax.isBinaryString());
            byte[] typeValue;
            if(session.syntax.isBinaryString())
                typeValue = new String(byteValue).substring(offset,blength).getBytes();
            else
                typeValue = Arrays.copyOfRange(byteValue,offset,blength);
            classes[i] = types[i].getBinaryClass(typeValue,session,baseClass);
            offset += blength;
        }

        return createConcrete(classes);
    }

    public String getConcatenateSource(List<String> exprs,SQLSyntax syntax) {
        // сначала property и extra объединяем в одну строку
        String source = "";
        for(int i=0;i<types.length;i++)
            source = (source.length() == 0 ? "" : source + syntax.getBinaryConcatenate()) + types[i].getBinaryCast(exprs.get(i), syntax, true);
        return "(" + source + ")";
    }

    public String getDeconcatenateSource(String expr, int part, SQLSyntax syntax) {

        int offset = 0;
        for(int i=0;i<part;i++)
            offset += types[i].getBinaryLength(syntax.isBinaryString());
        return types[part].getCast("SUBSTRING(" + expr + "," + (offset + 1) + "," + types[part].getBinaryLength(syntax.isBinaryString()) + ")", syntax, false);
    }

    public void prepareClassesQuery(Expr expr, Query<?, Object> query, BaseClass baseClass) {
        for(int i=0;i<types.length;i++) {
            Expr partExpr = DeconcatenateExpr.create(expr, i, baseClass);
            partExpr.getReader(query.where).prepareClassesQuery(partExpr,query,baseClass);
        }
    }

    public ConcreteClass readClass(Expr expr, Map<Object, Object> classes, BaseClass baseClass, KeyType keyType) {
        ConcreteClass[] classSets = new ConcreteClass[types.length];
        for(int i=0;i<types.length;i++) {
            Expr partExpr = DeconcatenateExpr.create(expr, i, baseClass);
            classSets[i] = partExpr.getReader(keyType).readClass(partExpr,classes,baseClass, keyType);
        }
        return new ConcatenateClassSet(classSets);
    }

    public List<AndClassSet> getUniversal(BaseClass baseClass) {
        throw new RuntimeException("not supported yet");
    }

    public AndClassSet getBaseClassSet(BaseClass baseClass) {
        AndClassSet[] classSets = new AndClassSet[types.length];
        for(int i=0;i<types.length;i++)
            classSets[i] = types[i].getBaseClassSet(baseClass);
        return new ConcatenateClassSet(classSets);
    }

    public Iterable<List<AndClassSet>> getUniversal(BaseClass baseClass, int part, AndClassSet fix) {
        List<List<AndClassSet>> classSets = new ArrayList<List<AndClassSet>>();
        for(int i=0;i<types.length;i++)
            classSets.add(i==part? Collections.singletonList(fix) : ((Type<?>)types[i]).getUniversal(baseClass));
        return new ListCombinations<AndClassSet>(classSets);
    }

    public int getBinaryLength(boolean charBinary) {
        int length = 0;
        for(Type type : types)
            length += type.getBinaryLength(charBinary);
        return length;
    }

    public ConcreteClass getBinaryClass(byte[] value, SQLSession session, BaseClass baseClass) throws SQLException {
        return getDataClass(value, session, baseClass);
    }

    public Object parseString(String s) throws ParseException {
        throw new RuntimeException("Parsing values from string is not supported");
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ConcatenateType && Arrays.equals(types, ((ConcatenateType) o).types);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(types);
    }

    public Stat getDefaultStat() {
        Stat result = Stat.ONE;
        for(Type type : types)
            result = result.mult(type.getDefaultStat());
        return result;
    }
}
