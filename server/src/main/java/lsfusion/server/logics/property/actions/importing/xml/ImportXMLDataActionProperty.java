package lsfusion.server.logics.property.actions.importing.xml;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.BaseLogicsModule;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.actions.importing.ImportDataActionProperty;
import lsfusion.server.logics.property.actions.importing.ImportIterator;
import org.jdom.JDOMException;
import org.xBaseJ.xBaseJException;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

public class ImportXMLDataActionProperty extends ImportDataActionProperty {
    boolean attr;
    public ImportXMLDataActionProperty(ValueClass valueClass, List<String> ids, List<LCP> properties, boolean attr, BaseLogicsModule baseLM) {
        super(new ValueClass[] {valueClass}, ids, properties, baseLM);
        this.attr = attr;
    }

    @Override
    public ImportIterator getIterator(byte[] file) throws IOException, ParseException, xBaseJException, JDOMException, ClassNotFoundException {
        return new ImportXMLIterator(file, attr) {
            @Override
            public List<Integer> getColumns(Map<String, Integer> mapping) {
                return getSourceColumns(mapping);
            }
        };
    }
}
