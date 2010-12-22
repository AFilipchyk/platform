package platform.server.data;

import platform.base.BaseUtils;
import platform.server.data.query.CompiledQuery;
import platform.server.data.query.Query;
import platform.server.data.sql.SQLExecute;
import platform.server.data.sql.SQLSyntax;
import platform.server.data.type.NullReader;
import platform.server.data.where.Where;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ModifyQuery {
    public final Table table;
    private final Query<KeyField, PropertyField> change;
    private final QueryEnvironment env;

    public ModifyQuery(Table table, Query<KeyField, PropertyField> change) {
        this(table, change, QueryEnvironment.empty);
    }

    public ModifyQuery(Table table, Query<KeyField, PropertyField> change, QueryEnvironment env) {
        this.table = table;
        this.change = change;
        this.env = env;
    }

    public SQLExecute getUpdate(SQLSyntax syntax) {

        assert !change.properties.isEmpty();
        
        int updateModel = syntax.updateModel();
        CompiledQuery<KeyField, PropertyField> changeCompile;
        String update;
        String setString;
        Collection<String> whereSelect;

        switch(updateModel) {
            case 2:
                // Oracl'вская модель Update'а
                changeCompile = change.compile(syntax);
                whereSelect = new ArrayList<String>(changeCompile.whereSelect);
                String fromSelect = changeCompile.from;

                for(KeyField key : table.keys)
                    whereSelect.add(table.getName(syntax)+"."+key.name +"="+ changeCompile.keySelect.get(key));

                List<KeyField> keyOrder = new ArrayList<KeyField>();
                List<PropertyField> propertyOrder = new ArrayList<PropertyField>();
                String SelectString = syntax.getSelect(fromSelect, SQLSession.stringExpr(
                        SQLSession.mapNames(changeCompile.keySelect,changeCompile.keyNames,keyOrder),
                        SQLSession.mapNames(changeCompile.propertySelect,changeCompile.propertyNames,propertyOrder)),
                        BaseUtils.toString(whereSelect, " AND "),"","","");

                setString = "";
                for(KeyField field : keyOrder)
                    setString = (setString.length()==0?"":setString+",") + field.name;
                for(PropertyField field : propertyOrder)
                    setString = (setString.length()==0?"":setString+",") + field.name;

                update = "UPDATE " + table.getName(syntax) + " SET ("+setString+") = ("+SelectString+") WHERE EXISTS ("+SelectString+")";
                break;
            case 1:
                // SQL-серверная модель когда она подхватывает первый JoinSelect и старую таблицу уже не вилит
                // построим Query куда переJoin'им все эти поля (оптимизатор уберет все дублирующиеся таблицы) - не получится так как если full join'ы пойдут нарушится инвариант
/*                Query<KeyField, PropertyField> updateQuery = new Query<KeyField, PropertyField>(change);
                updateQuery.and(table.joinAnd(updateQuery.mapKeys).getWhere());
                // надо в compile параметром для какого join'а оставлять alias
                changeCompile = updateQuery.compile(syntax);
                whereSelect = changeCompile.whereSelect;*/
                changeCompile = change.compile(syntax);
                String changeAlias = "ch_upd_al";

                whereSelect = new ArrayList<String>();
                for(KeyField key : table.keys)
                    whereSelect.add(table.getName(syntax)+"."+key.name + "=" + changeAlias + "." + changeCompile.keyNames.get(key));

                setString = "";
                for(Map.Entry<PropertyField,String> setProperty : changeCompile.propertyNames.entrySet())
                    setString = (setString.length()==0?"":setString+",") + setProperty.getKey().name + "=" + changeAlias + "." + setProperty.getValue();
                
                update = "UPDATE " + table.getName(syntax) + " SET " + setString + " FROM " + table.getName(syntax) + " JOIN (" +
                        changeCompile.select + ") " + changeAlias + " ON " + (whereSelect.size()==0? Where.TRUE_STRING:BaseUtils.toString(whereSelect," AND "));
                break;
            case 0:
                // по умолчанию - нормальная
                changeCompile = change.compile(syntax);

                whereSelect = new ArrayList<String>(changeCompile.whereSelect);
                for(KeyField key : table.keys)
                    whereSelect.add(table.getName(syntax)+"."+key.name +"="+ changeCompile.keySelect.get(key));

                setString = "";
                for(Map.Entry<PropertyField,String> setProperty : changeCompile.propertySelect.entrySet())
                    setString = (setString.length()==0?"":setString+",") + setProperty.getKey().name + "=" + setProperty.getValue();

                update = "UPDATE " + syntax.getUpdate(table.getName(syntax)," SET "+setString,changeCompile.from,BaseUtils.clause("WHERE", BaseUtils.toString(whereSelect, " AND ")));
                break;
            default:
                throw new RuntimeException();
        }

        return new SQLExecute(update,changeCompile.getQueryParams(env));
    }

    public SQLExecute getInsertLeftKeys(SQLSyntax syntax) {

        // делаем для этого еще один запрос
        Query<KeyField, PropertyField> leftKeysQuery = new Query<KeyField, PropertyField>(change.mapKeys);
        leftKeysQuery.and(change.where);
        // исключим ключи которые есть
        leftKeysQuery.and(table.joinAnd(leftKeysQuery.mapKeys).getWhere().not());

        return (new ModifyQuery(table,leftKeysQuery,env)).getInsertSelect(syntax);
    }

    private static String getInsertCastSelect(CompiledQuery<KeyField, PropertyField> changeCompile, SQLSyntax syntax) {
        if(changeCompile.unionAll && syntax.nullUnionTrouble()) {
            String alias = "castalias";
            String exprs = "";
            boolean casted = false;
            for(KeyField keyField : changeCompile.keyOrder)
                exprs = (exprs.length()==0?"":exprs+",") + alias + "." + changeCompile.keyNames.get(keyField);
            for(PropertyField propertyField : changeCompile.propertyOrder) {
                String propertyExpr = alias + "." + changeCompile.propertyNames.get(propertyField);
                if(changeCompile.propertyReaders.get(propertyField) instanceof NullReader) { // если null, вставляем явный cast
                    propertyExpr = "CAST(" + propertyExpr + " AS " + propertyField.type.getDB(syntax) + ")";
                    casted = true;
                }
                exprs = (exprs.length()==0?"":exprs+",") + propertyExpr;
            }
            if(casted)
                return "SELECT " + exprs + " FROM (" + changeCompile.select + ") " + alias; 
        }
        return changeCompile.select;
    }

    public SQLExecute getInsertSelect(SQLSyntax syntax) {

        CompiledQuery<KeyField, PropertyField> changeCompile = change.compile(syntax);

        String insertString = "";
        for(KeyField keyField : changeCompile.keyOrder)
            insertString = (insertString.length()==0?"":insertString+",") + keyField.name;
        for(PropertyField propertyField : changeCompile.propertyOrder)
            insertString = (insertString.length()==0?"":insertString+",") + propertyField.name;

        return new SQLExecute("INSERT INTO " + table.getName(syntax) + " (" + insertString + ") " + getInsertCastSelect(changeCompile, syntax),changeCompile.getQueryParams(env));
    }
}
