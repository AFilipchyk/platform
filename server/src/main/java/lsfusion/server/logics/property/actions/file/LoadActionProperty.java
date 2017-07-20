package lsfusion.server.logics.property.actions.file;

import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.server.classes.DataClass;
import lsfusion.server.classes.FileClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.type.Type;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.*;
import lsfusion.server.logics.property.actions.SystemExplicitActionProperty;

import java.sql.SQLException;

public class LoadActionProperty extends FileActionProperty {

    public LoadActionProperty(LocalizedString caption, LCP fileProperty) {
        super(caption, fileProperty);

        drawOptions.setImage("load.png");
    }

    protected FileClass getReadType() {
        return (FileClass) fileProperty.property.getType();
    }

    @Override // сам выполняет request поэтому на inRequest не смотрим
    public Type getSimpleRequestInputType(boolean optimistic, boolean inRequest) {
        return getReadType();
    }

    @Override
    public ImMap<CalcProperty, Boolean> aspectChangeExtProps() {
        return getChangeProps(fileProperty.property);
    }

    @Override
    protected boolean allowNulls() {
        return false;
    }

    @Override
    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        FileClass readType = getReadType();
        ObjectValue objectValue = context.requestUserData(readType, null);
        if (objectValue == null)
            return;

        DataObject[] objects = new DataObject[context.getKeyCount()];
        int i = 0; // здесь опять учитываем, что порядок тот же
        for (ClassPropertyInterface classInterface : interfaces)
            objects[i++] = context.getDataKeyValue(classInterface);
        fileProperty.change(objectValue, context, objects);
    }
}
