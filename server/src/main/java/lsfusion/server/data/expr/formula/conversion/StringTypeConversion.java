package lsfusion.server.data.expr.formula.conversion;

import lsfusion.interop.form.property.ExtInt;
import lsfusion.server.data.type.Type;
import lsfusion.server.logics.classes.data.StringClass;

import static lsfusion.server.logics.classes.data.StringClass.get;

public class StringTypeConversion implements TypeConversion {
    public final static StringTypeConversion instance = new StringTypeConversion();

    @Override
    public Type getType(Type type1, Type type2) {
        if (type1 instanceof StringClass || type2 instanceof StringClass) {
            ExtInt length1 = type1 == null ? ExtInt.ZERO : type1.getCharLength();
            ExtInt length2 = type2 == null ? ExtInt.ZERO : type2.getCharLength();

            boolean caseInsensitive =
                    (type1 instanceof StringClass && ((StringClass) type1).caseInsensitive) ||
                            (type2 instanceof StringClass && ((StringClass) type2).caseInsensitive);

            boolean rich =
                    (type1 instanceof StringClass && ((StringClass) type1).rich) ||
                            (type2 instanceof StringClass && ((StringClass) type2).rich);

            boolean blankPadded =
                    (type1 instanceof StringClass && ((StringClass) type1).blankPadded) &&
                            (type2 instanceof StringClass && ((StringClass) type2).blankPadded);

            return get(blankPadded, caseInsensitive, rich, length1.sum(length2));
        }
        return null;
    }
}
