package platform.server.form.instance.filter;

import platform.server.data.expr.Expr;
import platform.server.data.where.Where;
import platform.server.form.instance.FormInstance;
import platform.server.form.instance.ObjectInstance;
import platform.server.form.instance.PropertyObjectInstance;
import platform.server.logics.property.PropertyInterface;
import platform.server.session.Changes;
import platform.server.session.Modifier;

import java.io.DataInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public class NotNullFilterInstance<P extends PropertyInterface> extends PropertyFilterInstance<P> {

    public NotNullFilterInstance(PropertyObjectInstance<P> iProperty) {
        super(iProperty);
    }

    public NotNullFilterInstance(DataInputStream inStream, FormInstance form) throws IOException {
        super(inStream, form);
    }

    public Where getWhere(Map<ObjectInstance, ? extends Expr> mapKeys, Modifier<? extends Changes> modifier) throws SQLException {
        return property.getExpr(mapKeys, modifier).getWhere();
    }
}
