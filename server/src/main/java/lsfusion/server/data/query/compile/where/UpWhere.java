package lsfusion.server.data.query.compile.where;

import lsfusion.server.data.translate.JoinExprTranslator;
import lsfusion.server.data.where.Where;

public interface UpWhere {

    UpWhere or(UpWhere upWhere);

    UpWhere and(UpWhere upWhere);

    UpWhere not();

    Where getWhere(JoinExprTranslator translator);

}
