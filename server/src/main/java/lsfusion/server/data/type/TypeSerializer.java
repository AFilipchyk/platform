package lsfusion.server.data.type;

import lsfusion.base.ExtInt;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.MExclSet;
import lsfusion.interop.Data;
import lsfusion.server.classes.*;
import lsfusion.server.classes.link.*;
import lsfusion.server.logics.BusinessLogics;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class TypeSerializer {
    public static byte[] serializeType(Type type) throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        DataOutputStream dataStream = new DataOutputStream(outStream);
        serializeType(dataStream, type);
        return outStream.toByteArray();
    }

    public static void serializeType(DataOutputStream outStream, Type type) throws IOException {
        if (type instanceof ObjectType)
            outStream.writeByte(0);
        else if (type instanceof DataClass) {
            outStream.writeByte(1);
            ((DataClass) type).serialize(outStream);
        } else if (type instanceof ConcatenateType) {
            outStream.writeByte(2);
            ((ConcatenateType) type).serialize(outStream);
        }
    }

    public static Type deserializeType(DataInputStream inStream) throws IOException {
        switch (inStream.readByte()) {
            case 0:
                return ObjectType.instance;
            case 1:
                return deserializeDataClass(inStream);
            case 2:
        }       return deserializeConcatenateType(inStream);
    }

    public static ConcatenateType deserializeConcatenateType(DataInputStream inStream) throws IOException {
        int typesCount = inStream.readInt();

        Type[] types = new Type[typesCount];

        for (int i = 0; i < typesCount; i++)
            types[i] = TypeSerializer.deserializeType(inStream);

        return ConcatenateType.get(types);
    }

    /**
     * номер последней версии определён в {@link lsfusion.server.logics.DBManager.DBStructure#DBStructure(lsfusion.server.logics.DBManager.DBVersion)}
     */
    public static DataClass deserializeDataClass(DataInputStream inStream) throws IOException {
        byte type = inStream.readByte();

        if (type == Data.INTEGER) return IntegerClass.instance;
        if (type == Data.LONG) return LongClass.instance;
        if (type == Data.DOUBLE) return DoubleClass.instance;
        if (type == Data.NUMERIC) return NumericClass.get(inStream.readInt(), inStream.readInt());
        if (type == Data.LOGICAL) return LogicalClass.instance;
        if (type == Data.DATE) return DateClass.instance;
        if (type == Data.YEAR) return YearClass.instance;
        if (type == Data.DATETIME) return DateTimeClass.instance;
        if (type == Data.TIME) return TimeClass.instance;
        if (type == Data.COLOR) return ColorClass.instance;

        
        if (type == Data.STRING) {
            return StringClass.get(inStream.readBoolean(), inStream.readBoolean(), inStream.readBoolean(), ExtInt.deserialize(inStream));
        }

        if (type == Data.IMAGE) return ImageClass.get(inStream.readBoolean(), inStream.readBoolean());
        if (type == Data.WORD) return WordClass.get(inStream.readBoolean(), inStream.readBoolean());
        if (type == Data.EXCEL) return ExcelClass.get(inStream.readBoolean(), inStream.readBoolean());
        if (type == Data.CSV) return CSVClass.get(inStream.readBoolean(), inStream.readBoolean());
        if (type == Data.HTML) return HTMLClass.get(inStream.readBoolean(), inStream.readBoolean());
        if (type == Data.JSON) return JSONClass.get(inStream.readBoolean(), inStream.readBoolean());
        if (type == Data.XML) return XMLClass.get(inStream.readBoolean(), inStream.readBoolean());
        if (type == Data.CUSTOMSTATICFORMATFILE) {
            boolean multiple = inStream.readBoolean();
            boolean storeName = inStream.readBoolean();
            String filterDescription = inStream.readUTF();
            ImSet<String> filterExtensions;
            int extCount = inStream.readInt();
            if (extCount <= 0) {
                filterExtensions = SetFact.singleton("");
            } else {
                MExclSet<String> mFilterExpressions = SetFact.mExclSet(extCount);
                for (int i = 0; i < extCount; ++i) {
                    mFilterExpressions.exclAdd(inStream.readUTF());
                }
                filterExtensions = mFilterExpressions.immutable();
            }
            return CustomStaticFormatFileClass.get(multiple, storeName, filterDescription, filterExtensions);
        }
        if (type == Data.DYNAMICFORMATFILE) return DynamicFormatFileClass.get(inStream.readBoolean(), inStream.readBoolean());
        if (type == Data.PDF) return PDFClass.get(inStream.readBoolean(), inStream.readBoolean());

        if (type == Data.IMAGELINK) return ImageLinkClass.get(inStream.readBoolean());
        if (type == Data.WORDLINK) return WordLinkClass.get(inStream.readBoolean());
        if (type == Data.EXCELLINK) return ExcelLinkClass.get(inStream.readBoolean());
        if (type == Data.CSVLINK) return CSVLinkClass.get(inStream.readBoolean());
        if (type == Data.HTMLLINK) return HTMLLinkClass.get(inStream.readBoolean());
        if (type == Data.JSONLINK) return JSONLinkClass.get(inStream.readBoolean());
        if (type == Data.XMLLINK) return XMLLinkClass.get(inStream.readBoolean());
        if (type == Data.CUSTOMSTATICFORMATLINK) {
            boolean multiple = inStream.readBoolean();
            String filterDescription = inStream.readUTF();
            ImSet<String> filterExtensions;
            int extCount = inStream.readInt();
            if (extCount <= 0) {
                filterExtensions = SetFact.singleton("");
            } else {
                MExclSet<String> mFilterExtensions = SetFact.mExclSet(extCount);
                for (int i = 0; i < extCount; ++i)
                    mFilterExtensions.exclAdd(inStream.readUTF());
                filterExtensions = mFilterExtensions.immutable();
            }
            return CustomStaticFormatLinkClass.get(multiple, filterDescription, filterExtensions);
        }
        if (type == Data.DYNAMICFORMATLINK) return DynamicFormatLinkClass.get(inStream.readBoolean());
        if (type == Data.PDFLINK) return PDFLinkClass.get(inStream.readBoolean());

        throw new IOException();
    }

    public static ValueClass deserializeValueClass(BusinessLogics context, DataInputStream inStream) throws IOException {
        byte type = inStream.readByte();

        if (type == Data.INTEGER) return IntegerClass.instance;
        if (type == Data.LONG) return LongClass.instance;
        if (type == Data.DOUBLE) return DoubleClass.instance;
        if (type == Data.NUMERIC) return NumericClass.get(inStream.readInt(), inStream.readInt());
        if (type == Data.LOGICAL) return LogicalClass.instance;
        if (type == Data.DATE) return DateClass.instance;
        if (type == Data.STRING) return StringClass.get(inStream.readBoolean(), inStream.readBoolean(), inStream.readBoolean(), ExtInt.deserialize(inStream));
        if (type == Data.YEAR) return YearClass.instance;
        if (type == Data.OBJECT) return context.LM.baseClass.findClassID(inStream.readLong());
        if (type == Data.ACTION) return ActionClass.instance;
        if (type == Data.DATETIME) return DateTimeClass.instance;
        if (type == Data.DYNAMICFORMATFILE) return DynamicFormatFileClass.get(false, false);
        if (type == Data.TIME) return TimeClass.instance;
        if (type == Data.COLOR) return ColorClass.instance;

        if (type == Data.IMAGE) return ImageClass.get(false, false);
        if (type == Data.WORD) return WordClass.get(false, false);
        if (type == Data.EXCEL) return ExcelClass.get(false, false);
        if (type == Data.PDF) return PDFClass.get(false, false);
        if (type == Data.CSV) return CSVClass.get(false, false);
        if (type == Data.HTML) return HTMLClass.get(false, false);
        if (type == Data.JSON) return JSONClass.get(false, false);
        if (type == Data.XML) return XMLClass.get(false, false);
        //todo:!!
        if (type == Data.CUSTOMSTATICFORMATFILE) return CustomStaticFormatFileClass.get(false, false, "", "");

        if (type == Data.IMAGELINK) return ImageLinkClass.get(false);
        if (type == Data.WORDLINK) return WordLinkClass.get(false);
        if (type == Data.EXCELLINK) return ExcelLinkClass.get(false);
        if (type == Data.PDFLINK) return PDFLinkClass.get(false);
        if (type == Data.CSVLINK) return CSVLinkClass.get(false);
        if (type == Data.HTMLLINK) return HTMLLinkClass.get(false);
        if (type == Data.JSONLINK) return JSONLinkClass.get(false);
        if (type == Data.XMLLINK) return XMLLinkClass.get(false);
        //todo:!!
        if (type == Data.CUSTOMSTATICFORMATLINK) return CustomStaticFormatLinkClass.get(false, "", "");

        throw new IOException();
    }
}
