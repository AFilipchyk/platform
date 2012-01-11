package platform.server.data.expr.where.cases;

import platform.base.BaseUtils;
import platform.base.QuickSet;
import platform.base.TwinImmutableInterface;
import platform.interop.Compare;
import platform.server.caches.OuterContext;
import platform.server.caches.ParamLazy;
import platform.server.caches.hash.HashContext;
import platform.server.classes.BaseClass;
import platform.server.classes.DataClass;
import platform.server.classes.sets.AndClassSet;
import platform.server.data.expr.*;
import platform.server.data.expr.query.Stat;
import platform.server.data.query.SourceJoin;
import platform.server.data.translator.*;
import platform.server.data.where.MapWhere;
import platform.server.data.query.CompileSource;
import platform.server.data.query.JoinData;
import platform.server.data.sql.SQLSyntax;
import platform.server.data.type.ClassReader;
import platform.server.data.type.NullReader;
import platform.server.data.type.Type;
import platform.server.data.where.Where;

import java.util.*;

public class CaseExpr extends Expr {

    private final ExprCaseList cases;

    // этот конструктор нужен для создания CaseExpr'а в результать mapCase'а
    public CaseExpr(ExprCaseList cases) {
        this.cases = cases;
        assert !(this.cases.size()==1 && this.cases.get(0).where.isTrue());
    }

    // получает список ExprCase'ов
    public ExprCaseList getCases() {
        return cases;
    }

    public String getSource(CompileSource compile) {

        if (compile instanceof ToString) {
            String result = "";
            for (ExprCase exprCase : cases) {
                result = (result.length() == 0 ? "" : result + ",") + exprCase.toString();
            }
            return "CE(" + result + ")";
        }

        if (cases.size() == 0) {
            return SQLSyntax.NULL;
        }

        String source = "CASE";
        boolean hasElse = true;
        for (int i = 0; i < cases.size(); i++) {
            ExprCase exprCase = cases.get(i);
            String caseSource = exprCase.data.getSource(compile);

            if (i == cases.size() - 1 && exprCase.where.isTrue()) {
                source = source + " ELSE " + caseSource;
                hasElse = false;
            } else {
                source = source + " WHEN " + exprCase.where.getSource(compile) + " THEN " + caseSource;
            }
        }
        return source + (hasElse ? " ELSE " + SQLSyntax.NULL : "") + " END";
    }

    public Type getType(KeyType keyType) {
        Type type = null;
        for(ExprCase exprCase : cases) {
            Type caseType = exprCase.data.getType(keyType);
            if(caseType!=null) {
                if(type==null) {
                    if(!(caseType instanceof DataClass))
                        return caseType;
                    type = caseType;
                } else
                    type = ((DataClass)type).getCompatible((DataClass) caseType); // для того чтобы выбрать максимальную по длине
            }
        }
        return type;
    }
    public Stat getTypeStat(Where fullWhere) {
        Stat stat = null;
        for(ExprCase exprCase : cases) {
            Stat caseStat = exprCase.data.getTypeStat(fullWhere.and(exprCase.where));
            if(caseStat!=null)
                return caseStat;
        }
        return stat;
    }

    public ClassReader getReader(KeyType keyType) {
        Type type = getType(keyType);
        if(type==null) return NullReader.instance;
        return type;
    }

    protected CaseExpr translate(MapTranslate translator) {
        ExprCaseList translatedCases = new ExprCaseList();
        for(ExprCase exprCase : cases)
            translatedCases.add(new ExprCase(exprCase.where.translateOuter(translator),exprCase.data.translateOuter(translator)));
        return new CaseExpr(translatedCases);        
    }

    @ParamLazy
    public Expr translateQuery(QueryTranslator translator) {
        ExprCaseList translatedCases = new ExprCaseList();
        for(ExprCase exprCase : cases)
            translatedCases.add(exprCase.where.translateQuery(translator),exprCase.data.translateQuery(translator));
        return translatedCases.getFinal();
    }

    public Expr followFalse(Where where, boolean pack) {
        if(where.isFalse() && !pack) return this;

        return new ExprCaseList(where, cases, pack).getFinal();
    }

    static private <K> void recPullCases(ListIterator<Map.Entry<K, ? extends Expr>> ic, MapCase<K> current, Where currentWhere, MapCaseList<K> result) {

        if(currentWhere.isFalse())
            return;

        if(!ic.hasNext()) {
            result.add(current.where,new HashMap<K, BaseExpr>(current.data));
            return;
        }

        Map.Entry<K,? extends Expr> mapExpr = ic.next();

        for(ExprCase exprCase : mapExpr.getValue().getCases()) {
            Where prevWhere = current.where;
            current.where = current.where.and(exprCase.where);
            current.data.put(mapExpr.getKey(),exprCase.data);
            recPullCases(ic,current,currentWhere.and(exprCase.data.getWhere()),result);
            current.data.remove(mapExpr.getKey());
            current.where = prevWhere;
        }

        ic.previous();
    }

    public static <K> MapCaseList<K> pullCases(Map<K, ? extends Expr> mapExprs) {
        MapCaseList<K> result = new MapCaseList<K>();
        recPullCases(new ArrayList<Map.Entry<K,? extends Expr>>(mapExprs.entrySet()).listIterator(),new MapCase<K>(),Where.TRUE,result);
        return result;
    }

    public QuickSet<OuterContext> calculateOuterDepends() {
        QuickSet<OuterContext> result = new QuickSet<OuterContext>();
        for(ExprCase exprCase : cases) {
            result.addAll(exprCase.where.getOuterDepends());
            result.addAll(exprCase.data.getOuterDepends());
        }
        return result;
    }

    public void fillJoinWheres(MapWhere<JoinData> joins, Where andWhere) {
        // здесь по-хорошему надо andNot(верхних) но будет тормозить
        for(ExprCase exprCase : cases) {
            exprCase.where.fillJoinWheres(joins, andWhere);
            exprCase.data.fillJoinWheres(joins, andWhere.and(exprCase.where));
        }
    }

    public boolean twins(TwinImmutableInterface obj) {
        return cases.equals(((CaseExpr)obj).cases);
    }

    protected boolean isComplex() {
        return true;
    }
    protected int hash(HashContext hashContext) {
        return cases.hashOuter(hashContext) + 5;
    }

    // получение Where'ов

    public Where calculateWhere() {
        return cases.getWhere(new CaseWhereInterface<BaseExpr>(){
            public Where getWhere(BaseExpr cCase) {
                return cCase.getWhere();
            }
        });
    }

    public Where isClass(final AndClassSet set) {
        return cases.getWhere(new CaseWhereInterface<BaseExpr>(){
            public Where getWhere(BaseExpr cCase) {
                return cCase.isClass(set);
            }
        });
    }

    public Where compareBase(final BaseExpr expr, final Compare compareBack) {
        return cases.getWhere(new CaseWhereInterface<BaseExpr>() {
            public Where getWhere(BaseExpr cCase) {
                return cCase.compareBase(expr, compareBack);
            }
        });
    }
    public Where compare(final Expr expr, final Compare compare) {
        return cases.getWhere(new CaseWhereInterface<BaseExpr>(){
            public Where getWhere(BaseExpr cCase) {
                return cCase.compare(expr,compare);
            }
        });
    }

    // получение выражений

/*
    public Expr scale(int coeff) {
        if(coeff==1) return this;
        
        ExprCaseList result = new ExprCaseList();
        for(ExprCase exprCase : cases)
            result.add(exprCase.where, exprCase.props.scale(coeff)); // new ExprCase(exprCase.where,exprCase.props.scale(coeff))
        return result.getExpr();
    }

    public Expr sum(Expr expr) {
        ExprCaseList result = new ExprCaseList();
        for(ExprCase exprCase : cases)
            result.add(exprCase.where,exprCase.props.sum(expr));
        result.add(Where.TRUE,expr); // если null то expr
        return result.getExpr();
    }*/

    public Expr classExpr(BaseClass baseClass) {
        ExprCaseList result = new ExprCaseList();
        for(ExprCase exprCase : cases)
            result.add(exprCase.where,exprCase.data.classExpr(baseClass));
        return result.getFinal();
    }

    public Where getBaseWhere() {
        return BaseUtils.single(cases).where;
    }

    public int getWhereDepth() {
        throw new RuntimeException("not supported");
    }

    public Set<BaseExpr> getBaseExprs() {
        Set<BaseExpr> result = new HashSet<BaseExpr>();
        for(ExprCase exprCase : cases)
            result.add(exprCase.data);
        return result;
    }
}
