package lsfusion.server.data.expr;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImCol;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.MMap;
import lsfusion.base.col.interfaces.mutable.mapvalue.ImValueMap;
import lsfusion.interop.Compare;
import lsfusion.server.Settings;
import lsfusion.server.caches.ManualLazy;
import lsfusion.server.caches.OuterContext;
import lsfusion.server.caches.ParamExpr;
import lsfusion.server.classes.sets.AndClassSet;
import lsfusion.server.data.expr.query.PropStat;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.data.expr.query.StatType;
import lsfusion.server.data.expr.where.cases.ExprCase;
import lsfusion.server.data.expr.where.cases.ExprCaseList;
import lsfusion.server.data.expr.where.extra.EqualsWhere;
import lsfusion.server.data.expr.where.extra.GreaterWhere;
import lsfusion.server.data.expr.where.extra.InArrayWhere;
import lsfusion.server.data.expr.where.extra.LikeWhere;
import lsfusion.server.data.query.JoinData;
import lsfusion.server.data.query.stat.BaseJoin;
import lsfusion.server.data.query.stat.InnerBaseJoin;
import lsfusion.server.data.query.stat.KeyStat;
import lsfusion.server.data.translator.MapTranslate;
import lsfusion.server.data.type.ClassReader;
import lsfusion.server.data.where.Where;
import lsfusion.server.data.where.classes.ClassExprWhere;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;

import java.util.Collections;
import java.util.Set;


public abstract class BaseExpr extends Expr {

    public static Expr create(BaseExpr expr) {
        if(expr.getWhere().getClassWhere().isFalse())
            return NULL;
        else
            return expr;
    }

    // получает список ExprCase'ов
    public ExprCaseList getCases() {
        return new ExprCaseList(SetFact.<ExprCase>singleton(new ExprCase(Where.TRUE, this)));
    }

    public ImSet<NullableExprInterface> getExprFollows(boolean includeThis, boolean includeInnerWithoutNotNull, boolean recursive) {
        assert includeThis || recursive; // также предполагается что NullableExpr includeThis отработал
        return getExprFollows(includeInnerWithoutNotNull, recursive);
    }
    private ImSet<NullableExprInterface> exprFollows = null;
    @ManualLazy
    public ImSet<NullableExprInterface> getExprFollows(boolean includeInnerWithoutNotNull, boolean recursive) {
        if(recursive && (!includeInnerWithoutNotNull || !hasExprFollowsWithoutNotNull())) {  // кэшированиие
            if(exprFollows==null)
                exprFollows = getBaseJoin().getExprFollows(includeInnerWithoutNotNull, recursive);
            return exprFollows;
        }
        
        return getBaseJoin().getExprFollows(includeInnerWithoutNotNull, recursive); 
    }
    
    private Boolean hasExprFollowsWithoutNotNull; 
    @ManualLazy
    public boolean hasExprFollowsWithoutNotNull() {
        if(hasExprFollowsWithoutNotNull==null)
            hasExprFollowsWithoutNotNull = getBaseJoin().hasExprFollowsWithoutNotNull();
        return hasExprFollowsWithoutNotNull;        
    }

    public void fillJoinWheres(MMap<JoinData, Where> joins, Where andWhere) {
        fillAndJoinWheres(joins, andWhere.and(getWhere()));
    }

    public ClassReader getReader(KeyType keyType) {
        return getType(keyType); // assert'ится что не null
    }

    protected abstract BaseExpr translate(MapTranslate translator);
    public BaseExpr translateOuter(MapTranslate translator) {
        return (BaseExpr) aspectTranslate(translator);
    }

    public abstract void fillAndJoinWheres(MMap<JoinData, Where> joins, Where andWhere);

    public Expr followFalse(Where where, boolean pack) {
        if(getWhere().means(where))
            return NULL;
        else
            if(pack)
                return packFollowFalse(where);
            else
                return this;
    }

    // для linear'ов делает followFalse, известно что не means(where)
    public Expr packFollowFalse(Where where) {
        return this;
    }

    public abstract ClassExprWhere getClassWhere(AndClassSet classes);

    public Where compareBase(final BaseExpr expr, Compare compareBack) {
        switch(compareBack) {
            case EQUALS:
                return EqualsWhere.create(expr, this);
            case GREATER:
                return GreaterWhere.create(expr, this, false);
            case GREATER_EQUALS:
                if(Settings.get().isUseGreaterEquals())
                    return GreaterWhere.create(expr, this, true);
                else
                    return GreaterWhere.create(expr, this, false).or(EqualsWhere.create(expr, this));
            case LESS:
                return GreaterWhere.create(this, expr, false);
            case LESS_EQUALS:
                if(Settings.get().isUseGreaterEquals())
                    return GreaterWhere.create(this, expr, true);
                else
                    return GreaterWhere.create(this, expr, false).or(EqualsWhere.create(expr, this));
            case NOT_EQUALS: // оба заданы и не равно
                return getWhere().and(expr.getWhere()).and(EqualsWhere.create(expr, this).not());
            case START_WITH:
                return LikeWhere.create(expr, this, 1);
            case LIKE:
                return LikeWhere.create(expr, this, null);
            case INARRAY:
                return InArrayWhere.create(expr, this);
            case CONTAINS:
                return LikeWhere.create(expr, this, 2);
            case ENDS_WITH:
                return LikeWhere.create(expr, this, 3);
        }
        throw new RuntimeException("should not be");
    }
    public Where compare(final Expr expr, final Compare compare) {
        return expr.compareBase(this, compare);
    }

    public boolean hasKey(ParamExpr key) {
        return getOuterKeys().contains(key);
    }

    // может возвращать null, оба метода для ClassExprWhere
    public abstract AndClassSet getAndClassSet(ImMap<VariableSingleClassExpr, AndClassSet> and);
    public abstract boolean addAndClassSet(MMap<VariableSingleClassExpr, AndClassSet> and, AndClassSet add);

    public static <K> ImMap<K, Expr> packFollowFalse(final ImMap<K, BaseExpr> mapExprs, Where falseWhere) {

        ImValueMap<K, Expr> result = mapExprs.mapItValues(); // идет обращение к предыдущим значениям
        for(int i=0,size=mapExprs.size();i<size;i++) {
            Where siblingWhere = Where.TRUE;
            for(int j=0;j<size;j++) {
                if(j!=i) {
                    Expr siblingExpr = result.getMapValue(j);
                    if(siblingExpr==null) siblingExpr = mapExprs.getValue(j);
                    siblingWhere = andExprCheck(siblingWhere, siblingExpr.getWhere());
                }
            }
            result.mapValue(i, mapExprs.getValue(i).packFollowFalse(orExprCheck(falseWhere, siblingWhere.not())));
        }
        return result.immutableValue();
    }

    private static <K> ImMap<K, BaseExpr> pushValues(ImMap<K, BaseExpr> group, Where falseWhere) {
        // нельзя пока использовать getExprValues, потому как этой логиги нет в checkTrue, и таким образом например при (I=5 AND f(i)=1) OR f(i)=2
        // потеряется exclusive условий, что вместе с "перетасовкой" структуры Where, которую делает createMean в If'е и еще ряд механизмов, может в getClassWhere создать ветки где будет не хватать ключей

        return group;
/*        final ImMap<BaseExpr, BaseExpr> exprValues = falseWhere.not().getExprValues();
        return group.mapValues(new GetValue<BaseExpr, BaseExpr>() {
            public BaseExpr getMapValue(BaseExpr value) {
                return BaseUtils.nvl(exprValues.get(value), value);
            }});*/
    }

    public static <K> ImMap<K, Expr> packPushFollowFalse(ImMap<K, BaseExpr> mapExprs, Where falseWhere) {
        return packFollowFalse(pushValues(mapExprs, falseWhere), falseWhere);
    }

    public Where getBaseWhere() {
        return Where.TRUE;
    }

    public int getWhereDepth() {
        return 1;
    }

    public abstract PropStat getStatValue(KeyStat keyStat, StatType type); // должно кэшироваться так как несколько раз используется
    public abstract InnerBaseJoin<?> getBaseJoin();

    public ImSet<OuterContext> calculateOuterDepends() {
        return BaseUtils.immutableCast(getUsed().toSet());
    }

    // используется в FormulaExpr и CalculateJoin (в том числе ExprEquals\OrderTopJoin)
    // forJoin - обозначает что будет использоваться для Join'а, и результат для висячих ключей должен быть достаточно большим чтобы предотвратить их hint'ы и т.п.
    // единственное использование в DataClass.getTypeStat
    public abstract Stat getTypeStat(KeyStat keyStat, boolean forJoin);

    public Stat getTypeStat(Where fullWhere, boolean forJoin) {
        return getTypeStat((KeyStat) fullWhere, forJoin);
    }

    public Set<BaseExpr> getBaseExprs() {
        return Collections.singleton(this);
    }

    public ImCol<BaseExpr> getUsed() {
        return getBaseJoin().getJoins().values();
    }

    public Where calculateWhere() {
        return getOrWhere().and(getNotNullWhere());
    }
    
    private Where orWhere;
    @ManualLazy
    public Where getOrWhere() {
        if(orWhere==null)
            orWhere = calculateOrWhere();
        return orWhere;
    }

    // для всех не TRUE реализаций, должны быть соответствующие проверки в removeJoin
    public Where calculateOrWhere() {
        Where result = Where.TRUE;
        for(BaseExpr baseExpr : ((BaseJoin<?>) getBaseJoin()).getJoins().valueIt())
            result = result.and(baseExpr.getOrWhere());
        return result;
    }
    
    private Where notNullWhere;
    @ManualLazy
    public Where getNotNullWhere() {
        if(notNullWhere==null)
            notNullWhere = calculateNotNullWhere();
        return notNullWhere;
    }
    
    // notNull для использования в binaryWhere
    // проблема в том что мог быть выполнен not и предположение что в условии есть and условия может нарушиться, строго говоря с not'ом просто из-за недетерминированного порядка or могут быть проблемы 
    public Where getBinaryNotNullWhere(boolean source) {
        if(source)
            return getWhere();
        else // не так принципиально так как в худшем случае потеряем следствие, что не перевешивает доп нагрузку по учету or where
            return getNotNullWhere();
    }

    public Where calculateNotNullWhere() {
        return getNotNullWhere(getUsed());
    }

    public static Where getNotNullWhere(ImCol<? extends BaseExpr> exprs) {
        Where result = Where.TRUE;
        for(BaseExpr baseExpr : exprs)
            result = result.and(baseExpr.getNotNullWhere());
        return result;
    }

    // assert InnerExpr или KeyExpr
    public boolean isIndexed() {
        return false;
    }

    public boolean hasALotOfNulls() {
        assert isIndexed();
        return false;
    }

    public final static int STATICEQUALCLASSES = 3; // количество "классов сравнения"
    // определят "класс сравнения", то есть класс в рамках которого если выражения не equals, то и их значения гарантировано не equals
    public int getStaticEqualClass() {
        return -1;
    }

    public static <K> ImMap<K, BaseExpr> onlyBaseExprs(ImMap<K, ? extends Expr> map) {
        for(int i=0,size=map.size();i<size;i++)
            if(!(map.getValue(i) instanceof BaseExpr))
                return null;
        return BaseUtils.immutableCast(map);
    }
}
