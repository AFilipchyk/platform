package platform.server.data.query;

import platform.base.*;
import platform.interop.Compare;
import platform.server.Message;
import platform.server.caches.*;
import platform.server.caches.hash.HashContext;
import platform.server.classes.BaseClass;
import platform.server.data.QueryEnvironment;
import platform.server.data.SQLSession;
import platform.server.data.Value;
import platform.server.data.expr.BaseExpr;
import platform.server.data.expr.Expr;
import platform.server.data.expr.KeyExpr;
import platform.server.data.expr.where.pull.ExclPullWheres;
import platform.server.data.query.innerjoins.GroupJoinsWhere;
import platform.server.data.sql.SQLSyntax;
import platform.server.data.translator.*;
import platform.server.data.type.Type;
import platform.server.data.where.Where;
import platform.server.data.where.classes.ClassWhere;
import platform.server.logics.DataObject;
import platform.server.logics.ObjectValue;
import platform.server.session.DataSession;

import java.sql.SQLException;
import java.util.*;

import static platform.base.BaseUtils.immutableCast;

// запрос JoinSelect
public class Query<K,V> extends IQuery<K,V> {

    public final Map<K,KeyExpr> mapKeys;
    public Map<V, Expr> properties;
    public Where where;

    public Query(Map<K,KeyExpr> mapKeys) {
        this(mapKeys, Where.TRUE);
    }

    public Query(Collection<K> keys) {
        this(KeyExpr.getMapKeys(keys));
    }

    public Query(Map<K,KeyExpr> mapKeys,Map<V, Expr> properties,Where where) {
        this.mapKeys = mapKeys;
        this.properties = properties;
        this.where = where;
    }

    public <MK,MV> Query(Query<MK, MV> query, Map<K, MK> mapK, Map<V, MV> mapV) {
        this(BaseUtils.join(mapK, query.mapKeys), BaseUtils.join(mapV, query.properties), query.where);
    }

    public Query(Map<K, KeyExpr> mapKeys, Map<V, Expr> properties) {
        this(mapKeys, properties, Expr.getOrWhere(properties.values()));
    }

    public Query(Map<K, KeyExpr> mapKeys, Expr property, V value) {
        this(mapKeys,Collections.singletonMap(value,property));
    }

    public Query(Map<K, KeyExpr> mapKeys, Expr property, V value, Where where) {
        this(mapKeys,Collections.singletonMap(value,property),where);
    }

    public Query(Map<K,KeyExpr> mapKeys,Where where) {
        this(mapKeys, new HashMap<V, Expr>(), where);
    }

    public Query(MapKeysInterface<K> mapInterface) {
        this(mapInterface.getMapKeys());
    }

    public Map<K, KeyExpr> getMapKeys() {
        return mapKeys;
    }

    public Expr getExpr(V property) {
        return properties.get(property);
    }

    public boolean isEmpty() {
        return where.isFalse();
    }

    public Type getKeyType(K key) {
        return mapKeys.get(key).getType(where);
    }

    public Type getPropertyType(V property) {
        return properties.get(property).getType(where);
    }

    public QuickSet<KeyExpr> getKeys() {
        return new QuickSet<KeyExpr>(mapKeys.values());
    }

    public QuickSet<Value> getValues() {
        return AbstractOuterContext.getOuterValues(properties.values()).merge(where.getOuterValues());
    }

    public Where getWhere() {
        return where;
    }

    public Set<V> getProperties() {
        return properties.keySet(); 
    }

    private Join<V> join;
    private Join<V> getJoin() {
        if(join==null) {
            join = new AbstractJoin<V>() {
                public Expr getExpr(V property) {
                    return properties.get(property).and(where);
                }
                public Where getWhere() {
                    return where;
                }
                public Collection<V> getProperties() {
                    return properties.keySet();
                }
                public Join<V> translateRemoveValues(MapValuesTranslate translate) {
                    return ((Query<K,V>)Query.this.translateRemoveValues(translate)).getJoin();
                }
            };
        }
        return join;
    }

    public static <K> Map<K, KeyExpr> getMapKeys(Map<K, ? extends Expr> joinImplement) {
        QuickSet<KeyExpr> checked = new QuickSet<KeyExpr>();
        for(Expr joinExpr : joinImplement.values()) {
            if(!(joinExpr instanceof KeyExpr) || checked.contains((KeyExpr) joinExpr))
                return null;
            checked.add((KeyExpr) joinExpr);
        }
        return BaseUtils.<Map<K, KeyExpr>>immutableCast(joinImplement);
    }
    
    public Join<V> join(Map<K, ? extends Expr> joinImplement, MapValuesTranslate mapValues) {
        assert joinImplement.size()==mapKeys.size();
        assert mapValues.assertValuesEquals(getInnerValues().getSet()); // все должны быть параметры
        Map<K, KeyExpr> joinKeys = getMapKeys(joinImplement);
        if(joinKeys==null)
            return joinExprs(joinImplement, mapValues);
        else
            return new MapJoin<V>(new MapTranslator(BaseUtils.crossJoin(mapKeys, joinKeys), mapValues), getJoin());
    }

    @ContextTwin
    public Join<V> joinExprs(Map<K, ? extends Expr> joinImplement, MapValuesTranslate mapValues) { // последний параметр = какой есть\какой нужно, joinImplement не translateOuter'ся
        assert joinImplement.size()==mapKeys.size();

        Join<V> join = getJoin();

        // сначала map'им значения
        join = new MapJoin<V>(mapValues, join);

        // затем делаем подстановку
        join = new QueryTranslateJoin<V>(new QueryTranslator(BaseUtils.crossJoin(mapKeys, joinImplement)), join);

        // затем закидываем Where что все implement не null
        join = join.and(Expr.getWhere(joinImplement));

        return join;
    }


    public static <K> String stringOrder(List<K> sources, int offset, OrderedMap<K, Boolean> orders, Set<K> ordersNotNull, SQLSyntax syntax) {
        OrderedMap<String, Boolean> orderSources = new OrderedMap<String, Boolean>();
        Set<String> sourcesNotNull = new HashSet<String>();
        for(Map.Entry<K,Boolean> order : orders.entrySet()) {
            String source = ((Integer) (sources.indexOf(order.getKey()) + offset + 1)).toString();
            orderSources.put(source,order.getValue());
            if(ordersNotNull.contains(order.getKey()))
                sourcesNotNull.add(source);
        }
        return stringOrder(orderSources, sourcesNotNull, syntax);
    }

    public static String stringOrder(OrderedMap<String,Boolean> orders, Set<String> notNull, SQLSyntax syntax) {
        String orderString = "";
        for(Map.Entry<String,Boolean> order : orders.entrySet())
            orderString = (orderString.length()==0?"":orderString+",") + order.getKey() + " " + syntax.getOrderDirection(order.getValue(), notNull.contains(order.getKey()));
        return orderString;
    }

    public void and(Where addWhere) {
        where = where.and(addWhere);
    }

    public void putKeyWhere(Map<K, DataObject> keyValues) {
        for(Map.Entry<K,DataObject> mapKey : keyValues.entrySet())
            and(mapKeys.get(mapKey.getKey()).compare(mapKey.getValue(), Compare.EQUALS));
    }

    public Query(Query<K,V> query, boolean pack) {
        mapKeys = query.mapKeys;

        where = query.where.pack();
        properties = where.followTrue(query.properties, true);
    }

    @ContextTwin
    public IQuery<K,V> calculatePack() {
        return new Query<K,V>(this, true);
    }

    protected long calculateComplexity(boolean outer) {
        return AbstractOuterContext.getComplexity(properties.values(), outer) + where.getComplexity(outer);
    }

    @IdentityLazy
    @Pack
    public <B> ClassWhere<B> getClassWhere(Collection<? extends V> classProps) {
        return (ClassWhere<B>) getClassWhere(where, mapKeys, BaseUtils.filterKeys(properties, classProps));
    }

    private static <B, K extends B, V extends B> ClassWhere<B> getClassWhere(Where where, final Map<K, KeyExpr> mapKeys, Map<V, Expr> mapProps) {
        return new ExclPullWheres<ClassWhere<B>, V, Where>() {
            protected ClassWhere<B> initEmpty() {
                return ClassWhere.STATIC(false);
            }
            protected ClassWhere<B> proceedBase(Where data, Map<V, BaseExpr> map) {
                return (ClassWhere<B>)(ClassWhere<?>)getClassWhereBase(data, mapKeys, map);
            }
            protected ClassWhere<B> add(ClassWhere<B> op1, ClassWhere<B> op2) {
                return op1.or(op2);
            }
        }.proceed(where, mapProps);
    }

    private static <B, K extends B, V extends B> ClassWhere<B> getClassWhereBase(Where where, Map<K, KeyExpr> mapKeys, Map<V, BaseExpr> mapProps) {
        return where.and(Expr.getWhere(mapProps.values())).
                getClassWhere().get(BaseUtils.<B, BaseExpr>forceMerge(mapProps, mapKeys));
    }


    private static <K> void pullValues(Map<K, ? extends Expr> map, Where where, Map<K, Expr> result) {
        Map<BaseExpr, BaseExpr> exprValues = where.getExprValues();
        for(Map.Entry<K, ? extends Expr> entry : map.entrySet()) {
            Expr exprValue = exprValues.get(entry.getValue());
            if(exprValue==null && entry.getValue().isValue())
                exprValue = entry.getValue();
            if(exprValue!=null)
                result.put(entry.getKey(), exprValue);
        }
    }

    // жестковатая эвристика, но не страшно
    @Pack
    public IQuery<K,V> pullValues(Map<K, Expr> pullKeys, Map<V, Expr> pullProps) throws SQLException {
        pullValues(mapKeys, where, pullKeys);
        QueryTranslator keyTranslator = new PartialQueryTranslator(BaseUtils.rightCrossJoin(mapKeys, pullKeys));
        Where transWhere = where.translateQuery(keyTranslator);
        Map<V, Expr> transProps = keyTranslator.translate(properties);
        pullValues(transProps, transWhere, pullProps);
        if(pullKeys.isEmpty() && pullProps.isEmpty())
            return this;
        return new Query<K,V>(BaseUtils.filterNotKeys(mapKeys, pullKeys.keySet()), BaseUtils.filterNotKeys(transProps, pullProps.keySet()), transWhere);
    }

    public CompiledQuery<K,V> compile(SQLSyntax syntax,OrderedMap<V,Boolean> orders,int selectTop) {
        return compile(syntax, orders, selectTop, SubQueryContext.EMPTY);
    }
    public CompiledQuery<K,V> compile(SQLSyntax syntax, SubQueryContext subcontext) {
        return compile(syntax, new OrderedMap<V, Boolean>(), 0, subcontext);
    }
    public CompiledQuery<K,V> compile(SQLSyntax syntax, SubQueryContext subcontext, boolean recursive) {
        return compile(syntax, new OrderedMap<V, Boolean>(), 0, subcontext, recursive);
    }
    public CompiledQuery<K,V> compile(SQLSyntax syntax, OrderedMap<V, Boolean> orders, Integer selectTop, SubQueryContext subcontext) {
        return compile(syntax, orders, selectTop, subcontext, false);
    }
    @SynchronizedLazy
    @Pack
    @Message("message.core.query.compile")
    public CompiledQuery<K,V> compile(SQLSyntax syntax, OrderedMap<V, Boolean> orders, Integer selectTop, SubQueryContext subcontext, boolean noExclusive) {
        return new CompiledQuery<K,V>(this, syntax, orders, selectTop, subcontext, noExclusive);
    }

    public Collection<GroupJoinsWhere> getWhereJoins(boolean tryExclusive, Result<Boolean> isExclusive, List<Expr> orderTop) {
        Pair<Collection<GroupJoinsWhere>,Boolean> whereJoinsExcl = where.getWhereJoins(tryExclusive, getKeys(), orderTop);
        isExclusive.set(whereJoinsExcl.second);
        return whereJoinsExcl.first;
    }

    public static <V> OrderedMap<V,Boolean> reverseOrder(OrderedMap<V,Boolean> orders) {
        OrderedMap<V,Boolean> result = new OrderedMap<V, Boolean>();
        for(Map.Entry<V,Boolean> order : orders.entrySet())
            result.put(order.getKey(),!order.getValue());
        return result;
    }

    public OrderedMap<Map<K, Object>, Map<V, Object>> execute(SQLSession session) throws SQLException {
        return execute(session, QueryEnvironment.empty);
    }

    public OrderedMap<Map<K, Object>, Map<V, Object>> execute(SQLSession session, OrderedMap<V, Boolean> orders) throws SQLException {
        return execute(session, orders, 0, QueryEnvironment.empty);
    }

    public OrderedMap<Map<K, Object>, Map<V, Object>> execute(DataSession session) throws SQLException {
        return execute(session.sql, session.env);
    }

    public OrderedMap<Map<K, Object>, Map<V, Object>> execute(SQLSession session, QueryEnvironment env) throws SQLException {
        return execute(session,new OrderedMap<V, Boolean>(),0, env);
    }

    @Message("message.query.execute")
    public OrderedMap<Map<K, Object>, Map<V, Object>> execute(SQLSession session, OrderedMap<V, Boolean> orders, int selectTop, QueryEnvironment env) throws SQLException {
        return compile(session.syntax, orders, selectTop).execute(session, env);
    }

    public OrderedMap<Map<K, DataObject>, Map<V, ObjectValue>> executeClasses(SQLSession session, BaseClass baseClass) throws SQLException {
        return executeClasses(session, new OrderedMap<V, Boolean>(), 0, baseClass, QueryEnvironment.empty);
    }

    public OrderedMap<Map<K, DataObject>, Map<V, ObjectValue>> executeClasses(DataSession session) throws SQLException {
        return executeClasses(session, session.baseClass);
    }

    public OrderedMap<Map<K, DataObject>, Map<V, ObjectValue>> executeClasses(DataSession session, BaseClass baseClass) throws SQLException {
        return executeClasses(session.sql, session.env, baseClass);
    }

    public OrderedMap<Map<K, DataObject>, Map<V, ObjectValue>> executeClasses(SQLSession session, QueryEnvironment env, BaseClass baseClass) throws SQLException {
        return executeClasses(session, new OrderedMap<V, Boolean>(), 0, baseClass, env);
    }

    public OrderedMap<Map<K, DataObject>, Map<V, ObjectValue>> executeClasses(SQLSession session, QueryEnvironment env, BaseClass baseClass, OrderedMap<? extends Expr, Boolean> orders) throws SQLException {
        OrderedMap<Object, Boolean> orderProperties = new OrderedMap<Object, Boolean>();
        Query<K,Object> orderQuery = new Query<K,Object>((Query<K,Object>) this);
        for(Map.Entry<? extends Expr,Boolean> order : orders.entrySet()) {
            Object orderProperty = new Object();
            orderQuery.properties.put(orderProperty, order.getKey());
            orderProperties.put(orderProperty, order.getValue());
        }

        OrderedMap<Map<K, DataObject>, Map<V, ObjectValue>> result = new OrderedMap<Map<K, DataObject>, Map<V, ObjectValue>>();
        for(Map.Entry<Map<K, DataObject>,Map<Object, ObjectValue>> orderRow : orderQuery.executeClasses(session, orderProperties, 0, baseClass, env).entrySet())
            result.put(orderRow.getKey(), BaseUtils.filterKeys(orderRow.getValue(), properties.keySet()));
        return result;
    }
    public OrderedMap<Map<K, DataObject>, Map<V, ObjectValue>> executeClasses(DataSession session, OrderedMap<? extends V, Boolean> orders) throws SQLException {
        return executeClasses(session.sql, orders, 0, session.baseClass, session.env);
    }

    public OrderedMap<Map<K, DataObject>, Map<V, ObjectValue>> executeClasses(SQLSession session, OrderedMap<? extends V, Boolean> orders, int selectTop, BaseClass baseClass, QueryEnvironment env) throws SQLException {
        OrderedMap<Map<K, DataObject>, Map<V, ObjectValue>> result = new OrderedMap<Map<K, DataObject>, Map<V, ObjectValue>>();

        if(where.isFalse()) return result; // иначе типы ключей не узнаем

        // создаем запрос с IsClassExpr'ами
        Query<K,Object> classQuery = new Query<K,Object>((Query<K,Object>) this);

        for(KeyExpr expr : mapKeys.values())
            expr.getReader(classQuery.where).prepareClassesQuery(expr, classQuery, baseClass);
        for(Expr expr : properties.values())
            expr.getReader(classQuery.where).prepareClassesQuery(expr, classQuery, baseClass);

        OrderedMap<Map<K, Object>, Map<Object, Object>> rows = classQuery.execute(session, (OrderedMap<Object,Boolean>) orders,selectTop, env);

        // перемаппим
        for(Map.Entry<Map<K,Object>,Map<Object,Object>> row : rows.entrySet()) {
            Map<K,DataObject> keyResult = new HashMap<K, DataObject>();
            for(Map.Entry<K,KeyExpr> mapKey : mapKeys.entrySet())
                keyResult.put(mapKey.getKey(), new DataObject(row.getKey().get(mapKey.getKey()),mapKey.getValue().getReader(classQuery.where).readClass(mapKey.getValue(), row.getValue(),baseClass,classQuery.where)));

            Map<V,ObjectValue> propResult = new HashMap<V, ObjectValue>();
            for(Map.Entry<V,Expr> property : properties.entrySet())
                propResult.put(property.getKey(),ObjectValue.getValue(row.getValue().get(property.getKey()),property.getValue().getReader(classQuery.where).readClass(property.getValue(), row.getValue(),baseClass,classQuery.where)));
            result.put(keyResult,propResult);
        }
        return result;
    }

    public void outSelect(SQLSession session) throws SQLException {
        outSelect(session, QueryEnvironment.empty);
    }
    public void outSelect(SQLSession session, QueryEnvironment env) throws SQLException {
        compile(session.syntax).outSelect(session, env);
    }

    public String toString() {
        return "JQ";
    }

    // конструктор копирования
    public Query(Query<K, V> query) {
        this(query.mapKeys, new HashMap<V, Expr>(query.properties), query.where);
    }

    protected boolean isComplex() {
        return true;
    }
    public static class MultiParamsContext<K,V> extends AbstractInnerContext<MultiParamsContext<?,?>> {

        private final Query<K,V> thisObj;
        public MultiParamsContext(Query<K, V> thisObj) {
            this.thisObj = thisObj;
        }

        protected QuickSet<KeyExpr> getKeys() {
            return thisObj.getInnerKeys();
        }
        public QuickSet<Value> getValues() {
            return thisObj.getInnerValues();
        }
        protected MultiParamsContext translate(MapTranslate translator) {
            return thisObj.translateInner(translator).getQuery().getMultiParamsContext();
        }
        @Override
        public MultiParamsContext<?, ?> translateRemoveValues(MapValuesTranslate translate) {
            return thisObj.translateRemoveValues(translate).getQuery().getMultiParamsContext();
        }

        public Query<K,V> getQuery() {
            return thisObj;
        }
        protected boolean isComplex() {
            return true;
        }

        protected int hash(HashContext hash) {
            return thisObj.where.hashOuter(hash) * 31 + AbstractSourceJoin.hashOuter(thisObj.properties.values(), hash);
        }
        public boolean equalsInner(MultiParamsContext object) {
            return BaseUtils.hashEquals(thisObj.where,object.getQuery().where) && BaseUtils.hashEquals(BaseUtils.multiSet(thisObj.properties.values()),BaseUtils.multiSet(object.getQuery().properties.values()));
        }
    }
    private MultiParamsContext<K,V> multiParamsContext;
    public MultiParamsContext<K,V> getMultiParamsContext() {
        if(multiParamsContext==null)
            multiParamsContext = new MultiParamsContext<K,V>(this);
        return multiParamsContext;
    }

    public int hash(HashContext hashContext) {
        return 31 * (where.hashOuter(hashContext) * 31 + AbstractSourceJoin.hashOuter(properties, hashContext)) + AbstractSourceJoin.hashOuter(mapKeys, hashContext);
    }

    public MapQuery<K, V, ?, ?> translateMap(MapValuesTranslate translate) {
        return new MapQuery<K,V,K,V>(this, BaseUtils.toMap(properties.keySet()), BaseUtils.toMap(mapKeys.keySet()), translate);
    }
    public Query<K, V> translateQuery(MapTranslate translate) {
        return new Query<K,V>(translate.translateKey(mapKeys), translate.translate(properties), where.translateOuter(translate));
    }

    public boolean equalsInner(Query<K, V> object) { // нужно проверить что совпадут
        return BaseUtils.hashEquals(where, object.where) && BaseUtils.hashEquals(properties, object.properties) && BaseUtils.hashEquals(mapKeys, object.mapKeys);
    }

    public boolean equalsInner(IQuery<K, V> object) { // нужно проверить что совпадут
        return equalsInner(object.getQuery());
    }

    public Query<K, V> getQuery() {
        return this;
    }

    public <RMK, RMV> IQuery<RMK, RMV> map(Map<RMK, K> remapKeys, Map<RMV, V> remapProps, MapValuesTranslate translate) {
        return new MapQuery<RMK, RMV, K, V>(this, remapProps, remapKeys, translate);
    }
}

