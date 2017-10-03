package lsfusion.server.data.expr;

import lsfusion.base.TwinImmutableObject;
import lsfusion.server.caches.hash.HashContext;
import lsfusion.server.classes.DoubleClass;
import lsfusion.server.data.query.CompileSource;
import lsfusion.server.data.translator.MapTranslate;

public class RandomExpr extends StaticExpr<DoubleClass> {

    public static RandomExpr instance = new RandomExpr(); 
    private RandomExpr() {
        super(DoubleClass.instance);
    }

    protected BaseExpr translate(MapTranslate translator) {
        return this;
    }

    public boolean calcTwins(TwinImmutableObject obj) {
        return true;
    }

    protected int hash(HashContext hashContext) {
        return 3821;
    }
    
    public String getSource(CompileSource compile, boolean needValue) {
        return compile.syntax.getRandom();
    }

}
