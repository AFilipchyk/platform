package platform.server.classes;

import platform.interop.Data;
import platform.server.logics.ServerResourceBundle;

import java.io.DataInputStream;
import java.io.IOException;

public class ExcelClass extends StaticFormatFileClass {

    public final static ExcelClass instance = new ExcelClass(false);
    public final static ExcelClass multipleInstance = new ExcelClass(true);

    protected String getFileSID() {
        return "ExcelClass";
    }

    static {
        DataClass.storeClass(instance, multipleInstance);
    }

    protected ExcelClass(boolean multiple) {
        super(multiple);
    }

    public ExcelClass(DataInputStream inStream) throws IOException {
        super(inStream);
    }

    public String toString() {
        return ServerResourceBundle.getString("classes.excel.file");
    }

    public DataClass getCompatible(DataClass compClass) {
        return compClass instanceof ExcelClass ? this : null;
    }

    public byte getTypeID() {
        return Data.EXCEL;
    }

    public String getOpenExtension() {
        return "xls";
    }
}
