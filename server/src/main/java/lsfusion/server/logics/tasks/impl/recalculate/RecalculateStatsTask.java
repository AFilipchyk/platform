package lsfusion.server.logics.tasks.impl.recalculate;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.logics.classes.ConcreteCustomClass;
import lsfusion.server.logics.classes.ObjectValueClassSet;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.expr.ValueExpr;
import lsfusion.server.data.expr.query.GroupExpr;
import lsfusion.server.data.expr.query.GroupType;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.physics.exec.table.ImplementTable;
import lsfusion.server.logics.tasks.GroupPropertiesSingleTask;
import lsfusion.server.session.DataSession;
import org.antlr.runtime.RecognitionException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RecalculateStatsTask extends GroupPropertiesSingleTask<Object> { // ImplementTable, ObjectValueClassSet

    @Override
    public String getTaskCaption(Object element) {
        return "Recalculate Stats";
    }

    @Override
    protected void runInnerTask(Object element, ExecutionStack stack) throws RecognitionException, SQLException, SQLHandledException {
        try (DataSession session = createSession()) {
            if (element instanceof ImplementTable) {
                ((ImplementTable) element).recalculateStat(getBL().reflectionLM, session);
            } else if (element instanceof ObjectValueClassSet) {
                QueryBuilder<Integer, Integer> classes = new QueryBuilder<>(SetFact.singleton(0));

                KeyExpr countKeyExpr = new KeyExpr("count");
                Expr countExpr = GroupExpr.create(MapFact.singleton(0, countKeyExpr.classExpr(getBL().LM.baseClass)),
                        ValueExpr.COUNT, countKeyExpr.isClass((ObjectValueClassSet) element), GroupType.SUM, classes.getMapExprs());

                classes.addProperty(0, countExpr);
                classes.and(countExpr.getWhere());

                ImOrderMap<ImMap<Integer, Object>, ImMap<Integer, Object>> classStats = classes.execute(session);
                ImSet<ConcreteCustomClass> concreteChilds = ((ObjectValueClassSet) element).getSetConcreteChildren();
                for (int i = 0, size = concreteChilds.size(); i < size; i++) {
                    ConcreteCustomClass customClass = concreteChilds.get(i);
                    ImMap<Integer, Object> classStat = classStats.get(MapFact.singleton(0, (Object) customClass.ID));
                    getBL().LM.statCustomObjectClass.change(classStat == null ? 1 : (Integer) classStat.singleValue(), session, customClass.getClassObject());
                }
            }
            session.applyException(getBL(), stack);
        }
    }

    @Override
    protected List<Object> getElements() {
        checkContext();
        List<Object> elements = new ArrayList<>();
        Set<String> notRecalculateStatsTableSet;
        try(DataSession session = createSession()) {
            notRecalculateStatsTableSet = getDbManager().getNotRecalculateStatsTableSet(session);
        } catch (SQLException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }
        elements.addAll(getBL().LM.tableFactory.getImplementTables(notRecalculateStatsTableSet).toJavaSet());
        elements.addAll(getBL().LM.baseClass.getUpObjectClassFields().values().toJavaCol());
        return elements;
    }

    @Override
    protected String getElementCaption(Object element) {
        return element instanceof ImplementTable ? ((ImplementTable) element).getName() :
                element instanceof ObjectValueClassSet ? String.valueOf(element) : null;
    }

    @Override
    protected String getErrorsDescription(Object element) {
        return "";
    }

    @Override
    protected ImSet<Object> getDependElements(Object key) {
        return SetFact.EMPTY();
    }

    @Override
    protected long getTaskComplexity(Object element) {
        if (element instanceof ImplementTable) {
            Stat stat = ((ImplementTable) element).getStatRows();
            return stat == null ? Stat.MIN.getWeight() : stat.getWeight();
        } else if (element instanceof ObjectValueClassSet)
            return ((ObjectValueClassSet) element).getCount();
        else
            return Stat.MIN.getWeight();
    }
}
