package platform.server.data.expr.query;

import platform.base.BaseUtils;
import platform.base.OrderedMap;
import platform.server.data.expr.order.PartitionCalc;
import platform.server.data.expr.order.PartitionParam;
import platform.server.data.expr.order.PartitionToken;

import java.util.*;

public enum PartitionType implements AggrType {
    SUM, DISTR_CUM_PROPORTION, DISTR_RESTRICT, DISTR_RESTRICT_OVER, PREVIOUS;

    public static <K> Set<K> getSet(List<K> exprs, OrderedMap<K, Boolean> orders, Collection<K> partitions) {
        Set<K> result = new HashSet<K>();
        result.addAll(exprs);
        result.addAll(orders.keySet());
        result.addAll(partitions);
        return result;
    }

    // вообще первый параметр PartitionParam, но не хочется с generics'ами играться
    public PartitionCalc createAggr(Map<PartitionToken, String> tokens, List<String> sourceExprs, OrderedMap<String, Boolean> sourceOrders, Set<String> sourcePartitions) {
        Map<String, PartitionParam> params = new HashMap<String, PartitionParam>();
        for(String expr : getSet(sourceExprs, sourceOrders, sourcePartitions))
            params.put(expr, new PartitionParam());
        tokens.putAll(BaseUtils.reverse(params));

        List<PartitionToken> exprs = BaseUtils.<String, PartitionToken>mapList(sourceExprs, params);
        OrderedMap<PartitionToken, Boolean> orders = BaseUtils.<String, Boolean, PartitionToken>mapOrder(sourceOrders, params);
        Set<PartitionToken> partitions = new HashSet<PartitionToken>(BaseUtils.filterKeys(params, sourcePartitions).values());

        switch (this) {
            case SUM:
            case PREVIOUS:
                return new PartitionCalc(new PartitionCalc.Aggr(this==PREVIOUS?"lag":toString(), exprs, orders, partitions));
            case DISTR_CUM_PROPORTION: // exprs : 1-й - пропорционально чему, 2-й что
                // 1-й пробег высчитываем огругленную и часть, и первую запись на которую ра
                PartitionCalc.Aggr part = new PartitionCalc.Aggr("SUM", Collections.singletonList(exprs.get(0)), partitions);
                PartitionCalc round = new PartitionCalc("ROUND(CAST((prm1*prm2/prm3) AS numeric),0)", part, exprs.get(0), exprs.get(1));
                PartitionCalc number = new PartitionCalc(new PartitionCalc.Aggr("ROW_NUMBER", orders, partitions));
                // 2-й пробег - результат
                PartitionCalc.Aggr totRound = new PartitionCalc.Aggr("SUM", Collections.<PartitionToken>singletonList(round), partitions);
                return new PartitionCalc("prm1 + (CASE WHEN prm2=1 THEN (prm3-prm4) ELSE 0 END)", totRound, round, number, exprs.get(1));
        }
        throw new RuntimeException("not supported");
    }

    public boolean isSelect() {
        return this==PREVIOUS;
    }

    public boolean canBeNull() { // может возвращать null если само выражение не null
        return this==PREVIOUS;
    }
}
