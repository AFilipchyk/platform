package lsfusion.server.data.expr.query;

import lsfusion.base.col.ListFact;
import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.query.compile.CompileOrder;
import lsfusion.server.data.sql.syntax.SQLSyntax;
import lsfusion.server.data.type.ConcatenateType;
import lsfusion.server.data.type.Type;
import lsfusion.server.data.type.exec.TypeEnvironment;
import lsfusion.server.data.type.reader.ClassReader;
import lsfusion.server.data.type.reader.NullReader;
import lsfusion.server.data.where.Where;
import lsfusion.server.logics.classes.data.StringClass;
import lsfusion.server.logics.classes.data.file.FileClass;
import lsfusion.server.logics.classes.data.integral.IntegralClass;
import lsfusion.server.logics.classes.data.link.LinkClass;
import lsfusion.server.logics.property.implement.PropertyInterfaceImplement;
import lsfusion.server.logics.property.oraction.PropertyInterface;
import lsfusion.server.logics.property.set.GroupProperty;
import lsfusion.server.logics.property.set.MaxGroupProperty;
import lsfusion.server.logics.property.set.SumGroupProperty;
import lsfusion.server.physics.admin.Settings;
import lsfusion.server.physics.dev.i18n.LocalizedString;

public enum GroupType implements AggrType {
    SUM, MAX, MIN, ANY, STRING_AGG, AGGAR_SETADD, LAST;
    
    public static GroupType LOGICAL() {
        return ANY;
    }
    
    public static GroupType CHANGE(Type type) {
        return MAXCHECK(type);
    }

    public static GroupType ASSERTSINGLE_CHANGE() {
        return ASSERTSINGLE();
    }
    
    public static GroupType MAXCHECK(Type type) {
        if (type instanceof FileClass || type instanceof LinkClass)  // для File MAX not supported
            return ANY;

        return MAX; 
    }

    public static GroupType ASSERTSINGLE() {
        return ANY;
    }

    public <T extends PropertyInterface> GroupProperty<T> createProperty(LocalizedString caption, ImSet<T> innerInterfaces, PropertyInterfaceImplement<T> property, ImSet<? extends PropertyInterfaceImplement<T>> interfaces) {
        switch (this) {
            case MAX:
                return new MaxGroupProperty<>(caption, innerInterfaces, interfaces, property, false);
            case MIN:
                return new MaxGroupProperty<>(caption, innerInterfaces, interfaces, property, true);
            case SUM:
                return new SumGroupProperty<>(caption, innerInterfaces, interfaces, property);
        }
        throw new RuntimeException("not supported");
    }

    public Expr add(Expr op1, Expr op2) {
        switch (this) {
            case MAX:
                return op1.max(op2);
            case MIN:
                return op1.min(op2);
            case SUM:
                return op1.sum(op2);
            case ANY: // для этого ANY и делается
                return op1.nvl(op2);
        }
        throw new RuntimeException("can not be");
    }

    public boolean isSelect() {
        return this==MAX || this==MIN || this==ANY || this==LAST;
    }

    public boolean canBeNull() {
        return false;
    }

    public boolean isSelectNotInWhere() { // в общем то оптимизационная вещь потом можно убрать
//        assert isSelect();
        return this == LAST;
    }
    public Where getWhere(ImList<Expr> exprs) {
        if(this==LAST) {
            assert exprs.size()==2;
            return exprs.get(0).getWhere();
        }
        return Expr.getWhere(exprs);
    }
    public int getSkipWhereIndex() {
        if(this == LAST)
            return 1;
        return -1;
    }

    public Expr getMainExpr(ImList<Expr> exprs) {
        return exprs.get(getMainIndex());
    }

    public int getMainIndex() {
        if(this==LAST) {
            return 1;
        }
        return 0;
    }
    
    public Expr getSingleExpr(ImList<Expr> exprs, Where orderWhere) {
        Expr result = exprs.get(0);
        if(this == LAST)
            result = exprs.get(1).and(result.getWhere());
        return result.and(orderWhere);
    }

    public boolean hasAdd() {
        return this!=STRING_AGG && this!=AGGAR_SETADD && this!=LAST;
    }
    
    public boolean isMaxMin() {
        return this == MAX || this == MIN;
    }

    // если не комутативен и не инвариантен к появляению в выборке null'а
    public boolean nullsNotAllowed() {
        return this == LAST;
    }
    
    public boolean isLastOpt(boolean needValue, ImList<Expr> exprs) {
        if(this == LAST || this == ANY || isMaxMin()) // ANY - LAST без порядка, MAX/MIN - LAST где f(a) =
            return true;
        if(needValue)
            return false;
        if(this == SUM)
            return getMainExpr(exprs).isAlwaysPositiveOrNull();
        return true;
    }

    public boolean splitExprCases() {
        assert hasAdd();
        return isSelect() && Settings.get().isSplitGroupSelectExprcases();
    }

    public boolean splitInnerJoins() {
        assert hasAdd();
        return isSelect() && Settings.get().isSplitSelectGroupInnerJoins();
    }

    public boolean splitInnerCases() {
        assert hasAdd();
        return false;
    }

    public boolean exclusive() {
        assert hasAdd();
        return !isSelect();
    }

    public String getSource(ImList<String> exprs, ImList<ClassReader> exprReaders, ImOrderMap<String, CompileOrder> orders, Type type, SQLSyntax syntax, TypeEnvironment typeEnv) {
        switch (this) {
            case MAX:
                assert exprs.size()==1 && orders.size()==0;
                return (type instanceof ConcatenateType && syntax.hasAggConcProblem() ? "MAXC" : "MAX") + "(" + exprs.get(0) + ")";
            case MIN:
                assert exprs.size()==1 && orders.size()==0;
                return (type instanceof ConcatenateType && syntax.hasAggConcProblem() ? "MINC" : "MIN") + "(" + exprs.get(0) + ")";
            case ANY:
                assert exprs.size()==1 && orders.size()==0;
                return syntax.getAnyValueFunc() + "(" + exprs.get(0) + ")";
            case SUM:
                assert exprs.size()==1 && orders.size()==0;
                String exprSource = exprs.get(0);
                if(exprReaders != null) {
                    ClassReader classReader = exprReaders.get(0);
                    if (classReader instanceof NullReader) // если null cast'им, на самом деле это частично хак, так как может протолкнуться условие, но emptyselect не получится, а empty будет конкретное выражение (возможно тоже самое нужно для partition и т.п.)
                        exprSource = type.getCast(exprSource, syntax, typeEnv);
                }
                return syntax.getNotZero("SUM(" + exprSource + ")", type, typeEnv);
            case STRING_AGG:
                assert exprs.size()==2;
                return type.getCast(syntax.getOrderGroupAgg(this, type, exprs, exprReaders, orders, typeEnv), syntax, typeEnv); // тут точная ширина не нужна главное чтобы не больше
            case AGGAR_SETADD:
                assert exprs.size()==1 && orders.isEmpty();
                return syntax.getArrayAgg(exprs.get(0), exprReaders.get(0), typeEnv);
            case LAST:
                assert exprs.size()==2;
                return syntax.getOrderGroupAgg(this, type, ListFact.singleton(exprs.get(1)), ListFact.singleton(exprReaders.get(1)), orders, typeEnv);
            default:
                throw new RuntimeException("can not be");
        }
    }

    public int numExprs() {
        if(this==STRING_AGG || this==LAST)
            return 2;
        else
            return 1;
    }

    public Type getType(Type exprType) {
        if(this==STRING_AGG)
            return ((StringClass)exprType).extend(10);
        assert this != SUM || exprType instanceof IntegralClass; 
        
        return exprType;
    }
}
