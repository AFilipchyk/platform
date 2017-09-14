package lsfusion.server.logics;

import lsfusion.base.ExtInt;
import lsfusion.server.classes.*;
import lsfusion.server.classes.link.*;
import lsfusion.server.classes.sets.ResolveClassSet;
import lsfusion.server.classes.sets.ResolveConcatenateClassSet;
import lsfusion.server.classes.sets.ResolveOrObjectClassSet;
import lsfusion.server.classes.sets.ResolveUpClassSet;

import java.util.HashMap;
import java.util.Map;

public final class ClassCanonicalNameUtils {
    public static final String ConcatenateClassNameLBracket = "(";
    public static final String ConcatenateClassNameRBracket = ")";
    public static final String ConcatenateClassNamePrefix = "CONCAT";
    
    public static final String OrObjectClassSetNameLBracket = "{";
    public static final String OrObjectClassSetNameRBracket = "}";
    
    public static final String UpClassSetNameLBracket = "(";
    public static final String UpClassSetNameRBracket = ")";
    
    // CONCAT(CN1, ..., CNk)
    public static String createName(ResolveConcatenateClassSet ccs) {
        ResolveClassSet[] classes = ccs.getClasses();
        String sid = ConcatenateClassNamePrefix + ConcatenateClassNameLBracket; 
        for (ResolveClassSet set : classes) {
            sid += (sid.length() > 1 ? "," : "");
            sid += set.getCanonicalName();
        }
        return sid + ConcatenateClassNameRBracket; 
    }
    
    // {UpCN, SetCN1, ..., SetCNk}
    public static String createName(ResolveOrObjectClassSet cs) {
        if (cs.set.size() == 0) {
            return cs.up.getCanonicalName();
        } else {
            String sid = OrObjectClassSetNameLBracket; 
            sid += cs.up.getCanonicalName();
            for (int i = 0; i < cs.set.size(); i++) {
                sid += ",";
                sid += cs.set.get(i).getCanonicalName();
            }
            return sid + OrObjectClassSetNameRBracket; 
        }
    }
    
    // (CN1, ..., CNk) 
    public static String createName(ResolveUpClassSet up) {
        if (up.wheres.length == 1) {
            return up.wheres[0].getCanonicalName();
        }
        String sid = UpClassSetNameLBracket;
        for (CustomClass cls : up.wheres) {
            sid += (sid.length() > 1 ? "," : "");
            sid += cls.getCanonicalName();
        }
        return sid + UpClassSetNameRBracket;
    }

    public static DataClass defaultStringClassObj = StringClass.text;
    public static DataClass defaultNumericClassObj = NumericClass.get(5, 2);
    
    public static DataClass getCanonicalNameDataClass(String name) {
        return canonicalDataClassNames.get(name); 
    }
    
    private static Map<String, DataClass> canonicalDataClassNames = new HashMap<String, DataClass>() {{
        put("INTEGER", IntegerClass.instance);
        put("DOUBLE", DoubleClass.instance);
        put("LONG", LongClass.instance);
        put("BOOLEAN", LogicalClass.instance);
        put("DATE", DateClass.instance);
        put("DATETIME", DateTimeClass.instance );
        put("TIME", TimeClass.instance);
        put("YEAR", YearClass.instance);
        put("WORDFILE", WordClass.get(false, false));
        put("IMAGEFILE", ImageClass.get(false, false));
        put("PDFFILE", PDFClass.get(false, false));
        put("CUSTOMFILE", DynamicFormatFileClass.get(false, false));
        put("EXCELFILE", ExcelClass.get(false, false));
        put("WORDLINK", WordLinkClass.get(false));
        put("IMAGELINK", ImageLinkClass.get(false));
        put("PDFLINK", PDFLinkClass.get(false));
        put("CUSTOMLINK", DynamicFormatLinkClass.get(false));
        put("EXCELLINK", ExcelLinkClass.get(false));
        put("COLOR", ColorClass.instance);
        put("STRING", defaultStringClassObj);
        put("NUMERIC", defaultNumericClassObj);
    }};

    public static DataClass getScriptedDataClass(String name) {
        assert !name.contains(" ");
        if (scriptedSimpleDataClassNames.containsKey(name)) {
            return scriptedSimpleDataClassNames.get(name);
        } else if (name.matches("^((STRING\\[\\d+\\])|(ISTRING\\[\\d+\\])|(VARSTRING\\[\\d+\\])|(VARISTRING\\[\\d+\\])|(NUMERIC\\[\\d+,\\d+\\]))$")) {
            if (name.startsWith("STRING[")) {
                name = name.substring("STRING[".length(), name.length() - 1);
                return StringClass.get(new ExtInt(Integer.parseInt(name)));
            } else if (name.startsWith("ISTRING[")) {
                name = name.substring("ISTRING[".length(), name.length() - 1);
                return StringClass.geti(new ExtInt(Integer.parseInt(name)));
            } else if (name.startsWith("VARSTRING[")) {
                name = name.substring("VARSTRING[".length(), name.length() - 1);
                return StringClass.getv(new ExtInt(Integer.parseInt(name)));
            } else if (name.startsWith("VARISTRING[")) {
                name = name.substring("VARISTRING[".length(), name.length() - 1);
                return StringClass.getvi(new ExtInt(Integer.parseInt(name)));
            } else if (name.startsWith("NUMERIC[")) {
                String length = name.substring("NUMERIC[".length(), name.indexOf(","));
                String precision = name.substring(name.indexOf(",") + 1, name.length() - 1);
                return NumericClass.get(Integer.parseInt(length), Integer.parseInt(precision));
            }            
        }
        return null;
    }
    
    private static Map<String, DataClass> scriptedSimpleDataClassNames = new HashMap<String, DataClass>() {{
        put("INTEGER", IntegerClass.instance);
        put("DOUBLE", DoubleClass.instance);
        put("LONG", LongClass.instance);
        put("BOOLEAN", LogicalClass.instance);
        put("DATE", DateClass.instance);
        put("DATETIME", DateTimeClass.instance);
        put("TIME", TimeClass.instance);
        put("YEAR", YearClass.instance);
        put("WORDFILE", WordClass.get(false, false));
        put("IMAGEFILE", ImageClass.get(false, false));
        put("PDFFILE", PDFClass.get(false, false));
        put("CUSTOMFILE", DynamicFormatFileClass.get(false, false));
        put("EXCELFILE", ExcelClass.get(false, false));
        put("WORDLINK", WordLinkClass.get(false));
        put("IMAGELINK", ImageLinkClass.get(false));
        put("PDFLINK", PDFLinkClass.get(false));
        put("CUSTOMLINK", DynamicFormatLinkClass.get(false));
        put("EXCELLINK", ExcelLinkClass.get(false));
        put("COLOR", ColorClass.instance);
        put("TEXT", StringClass.text);
        put("ITEXT", StringClass.iText);
        put("RICHTEXT", StringClass.richText);
    }};

}
