package lsfusion.server.logics.property.actions.importing.xlsx;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.BaseLogicsModule;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.actions.importing.ImportIterator;
import lsfusion.server.logics.property.actions.importing.xls.ImportXLSDataActionProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.IOException;
import java.util.List;

public class ImportXLSXDataActionProperty extends ImportXLSDataActionProperty {
    public ImportXLSXDataActionProperty(ValueClass[] valueClasses, List<String> ids, List<LCP> properties, BaseLogicsModule baseLM) {
        super(valueClasses, ids, properties, baseLM);
    }

    @Override
    public ImportIterator getIterator(byte[] file) throws IOException {
        return new ImportXLSXIterator(file, getSourceColumns(XLSColumnsMapping), properties, sheetIndex);
    }
}
