package platform.server.data.expr;

import platform.base.QuickMap;
import platform.server.caches.IdentityLazy;
import platform.server.caches.hash.HashContext;
import platform.server.classes.BaseClass;
import platform.server.classes.ConcatenateClassSet;
import platform.server.classes.sets.AndClassSet;
import platform.server.data.expr.cases.ExprCase;
import platform.server.data.expr.cases.ExprCaseList;
import platform.server.data.expr.where.MapWhere;
import platform.server.data.query.AbstractSourceJoin;
import platform.server.data.query.CompileSource;
import platform.server.data.query.ContextEnumerator;
import platform.server.data.query.JoinData;
import platform.server.data.translator.MapTranslate;
import platform.server.data.translator.QueryTranslator;
import platform.server.data.type.ConcatenateType;
import platform.server.data.type.Type;
import platform.server.data.where.Where;
import platform.server.data.where.classes.ClassExprWhere;

import java.util.List;

public class DeconcatenateExpr extends SingleClassExpr {

    BaseExpr expr;
    int part;

    BaseClass baseClass;

    public DeconcatenateExpr(BaseExpr expr, int part, BaseClass baseClass) {
        assert !(expr instanceof ConcatenateExpr);

        this.expr = expr;
        this.part = part;

        this.baseClass = baseClass;
    }

    private static Expr createBase(BaseExpr expr, int part, BaseClass baseClass) {
        if(expr instanceof ConcatenateExpr)
            return ((ConcatenateExpr)expr).deconcatenate(part);
        else
            return BaseExpr.create(new DeconcatenateExpr(expr, part, baseClass));
    }

    public static Expr create(Expr expr, int part, BaseClass baseClass) {
        ExprCaseList result = new ExprCaseList();
        for(ExprCase exprCase : expr.getCases())
            result.add(exprCase.where, createBase(exprCase.data, part, baseClass));
        return result.getExpr();
    }


    public DeconcatenateExpr translateOuter(MapTranslate translator) {
        return new DeconcatenateExpr(expr.translateOuter(translator), part, baseClass);
    }

    public VariableExprSet calculateExprFollows() {
        return expr.getExprFollows();
    }

    public void fillAndJoinWheres(MapWhere<JoinData> joins, Where andWhere) {
        expr.fillJoinWheres(joins, andWhere);
    }

    public Type getType(KeyType keyType) {
        return ((ConcatenateType)expr.getType(keyType)).get(part);
    }
    
    public AndClassSet getAndClassSet(QuickMap<VariableClassExpr, AndClassSet> and) {
        return ((ConcatenateClassSet)expr.getAndClassSet(and)).get(part);
    }

    public Where calculateWhere() {
        return expr.getWhere();
    }

    public Expr translateQuery(QueryTranslator translator) {
        return create(expr.translateQuery(translator),part,baseClass);
    }

    public boolean twins(AbstractSourceJoin obj) {
        return expr.equals(((DeconcatenateExpr)obj).expr) && part == ((DeconcatenateExpr)obj).part && baseClass.equals(((DeconcatenateExpr)obj).baseClass);  
    }

    @IdentityLazy
    public int hashOuter(HashContext hashContext) {
        return expr.hashOuter(hashContext) * 31 + part;
    }

    public void enumerate(ContextEnumerator enumerator) {
        expr.enumerate(enumerator);
    }

    public ClassExprWhere getClassWhere(AndClassSet classes) {
        ClassExprWhere result = ClassExprWhere.FALSE;

        ConcatenateType type = (ConcatenateType) expr.getSelfType();
        for(List<AndClassSet> list : type.getUniversal(baseClass,part,classes))
            result = result.or(expr.getClassWhere(new ConcatenateClassSet(list.toArray(new AndClassSet[1]))));

        return result;
    }

    public boolean addAndClassSet(QuickMap<VariableClassExpr, AndClassSet> and, AndClassSet add) {
        throw new RuntimeException("not supported");
    }

    public String getSource(CompileSource compile) {
        return ((ConcatenateType) expr.getType(compile.keyType)).getDeconcatenateSource(expr.getSource(compile), part, compile.syntax);
    }

    public long calculateComplexity() {
        return expr.getComplexity()+1;
    }
}
