package platform.server.data.expr.where.extra;

import platform.base.TwinImmutableInterface;
import platform.interop.Compare;
import platform.server.caches.hash.HashContext;
import platform.server.classes.InsensitiveStringClass;
import platform.server.data.expr.BaseExpr;
import platform.server.data.query.CompileSource;
import platform.server.data.where.Where;

public class LikeWhere extends BinaryWhere<LikeWhere> {

    private final boolean isStartWith;

    private LikeWhere(BaseExpr operator1, BaseExpr operator2, boolean startWithType) {
        super(operator1, operator2);
        this.isStartWith = startWithType;
    }

    protected LikeWhere createThis(BaseExpr operator1, BaseExpr operator2) {
        return new LikeWhere(operator1, operator2, isStartWith);
    }

    protected Compare getCompare() {
        return isStartWith ? Compare.START_WITH : Compare.LIKE;
    }

    protected boolean isComplex() {
        return true;
    }
    public int hash(HashContext hashContext) {
        return (operator1.hashOuter(hashContext) * 31 + operator2.hashOuter(hashContext)) * 31 + (isStartWith?1:0);
    }

    @Override
    public boolean twins(TwinImmutableInterface obj) {
        return super.twins(obj) && isStartWith == ((LikeWhere)obj).isStartWith;
    }

    protected String getCompareSource(CompileSource compile) {
        throw new RuntimeException("not supported");
    }

    @Override
    public String getSource(CompileSource compile) {
        String likeString = operator1.getType(compile.keyType) instanceof InsensitiveStringClass
                               ? " " + compile.syntax.getInsensitiveLike() + " " : " LIKE ";

        return operator1.getSource(compile)
               + likeString
               + "(" + operator2.getSource(compile) + (isStartWith ? " || '%'" : "") + ")";
    }

    public static Where create(BaseExpr operator1, BaseExpr operator2, boolean isStartWith) {
        return create(operator1, operator2, new LikeWhere(operator1, operator2, isStartWith));
    }
}
