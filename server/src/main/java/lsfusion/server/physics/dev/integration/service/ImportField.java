package lsfusion.server.physics.dev.integration.service;

import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.type.Type;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.session.change.modifier.Modifier;
import lsfusion.server.logics.action.session.table.SinglePropertyTableUsage;
import lsfusion.server.logics.classes.data.DataClass;
import lsfusion.server.logics.property.Property;

public class ImportField implements ImportFieldInterface, ImportKeyInterface {
    private DataClass fieldClass;

    public static final Type.Getter<ImportField> typeGetter = ImportField::getFieldClass;

    public ImportField(DataClass fieldClass) {
        this.fieldClass = fieldClass;
    }

    public ImportField(LP property) {
        this((Property<?>) property.property);
    }

    public ImportField(Property<?> property) {
        this.fieldClass = (DataClass) property.getType();
    }

    public DataClass getFieldClass() {
        return fieldClass;
    }

    public DataObject getDataObject(ImportTable.Row row) {
        if (row.getValue(this) != null) {
            return new DataObject(row.getValue(this), getFieldClass());
        } else {
            return null;
        }
    }

    public Expr getExpr(ImMap<ImportField, ? extends Expr> importKeys) {
        return importKeys.get(this);
    }

    public Expr getExpr(ImMap<ImportField, ? extends Expr> importKeys, ImMap<ImportKey<?>, SinglePropertyTableUsage<?>> addedKeys, Modifier modifier) {
        return getExpr(importKeys);
    }

    public Type getType() {
        return fieldClass;
    }
}
