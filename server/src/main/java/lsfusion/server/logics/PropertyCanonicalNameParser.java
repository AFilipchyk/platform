package lsfusion.server.logics;

import lsfusion.base.col.SetFact;
import lsfusion.base.col.implementations.ArIndexedSet;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.DataClass;
import lsfusion.server.classes.sets.ResolveClassSet;
import lsfusion.server.classes.sets.ResolveConcatenateClassSet;
import lsfusion.server.classes.sets.ResolveOrObjectClassSet;
import lsfusion.server.classes.sets.ResolveUpClassSet;

import java.util.ArrayList;
import java.util.List;

public class PropertyCanonicalNameParser extends AbstractPropertyNameParser {
    static public class CanonicalNameClassFinder implements ClassFinder {
        private BusinessLogics BL;
        public CanonicalNameClassFinder(BusinessLogics BL) {
            this.BL = BL;
        }
            
        @Override
        public CustomClass findCustomClass(String name) {
            return BL.findClass(name);
        }

        @Override
        public DataClass findDataClass(String name) {
            return ClassCanonicalNameUtils.getCanonicalNameDataClass(name);
        }
    }

    private final String CPREFIX = ClassCanonicalNameUtils.ConcatenateClassNamePrefix + ClassCanonicalNameUtils.ConcatenateClassNameLBracket;

    public PropertyCanonicalNameParser(BusinessLogics BL, String canonicalName) {
        this(canonicalName, new CanonicalNameClassFinder(BL));
    }

    public PropertyCanonicalNameParser(String canonicalName, ClassFinder finder) {
        super(canonicalName, finder);
    }

    public String getNamespace() throws ParseException {
        return getNamespace(name);
    }

    public static String getNamespace(String canonicalName) throws ParseException {
        canonicalName = canonicalName.replaceAll(" ", "");
        int pointIndex = canonicalName.indexOf('.');
        if (pointIndex < 0) {
            throw new ParseException("Namespace is missing");
        }
        String namespaceName = canonicalName.substring(0, pointIndex);
        return checkID(namespaceName);
    }
    
    public String getName() throws ParseException {
        return getName(name);
    }

    public static String getName(String canonicalName) throws ParseException {
        canonicalName = canonicalName.replaceAll(" ", "");
        getNamespace(canonicalName); // проверим валидность пространства имен
        int pointIndex = canonicalName.indexOf('.');
        int bracketIndex = canonicalName.indexOf(PropertyCanonicalNameUtils.signatureLBracket);

        String name;
        if (bracketIndex < 0) {
            name = canonicalName.substring(pointIndex + 1);
        } else {
            name = canonicalName.substring(pointIndex + 1, bracketIndex);
        }
        return checkID(name);
    }
    
    public List<ResolveClassSet> getSignature() throws ParseException {
        int bracketPos = name.indexOf(PropertyCanonicalNameUtils.signatureLBracket);
        if (bracketPos >= 0) {
            if (name.lastIndexOf(PropertyCanonicalNameUtils.signatureRBracket) != name.length() - 1) {
                throw new ParseException(String.format("'%s' should be at the end", PropertyCanonicalNameUtils.signatureRBracket));
            }

            parseText = name.substring(bracketPos + 1, name.length() - 1);
            pos = 0;
            len = parseText.length();

            try {
                List<ResolveClassSet> result = parseAndClassSetList(true);
                if (pos < len) {
                    throw new ParseException("Parse error");
                }
                return result;
            } catch (ParseInnerException e) {
                throw new ParseException(e.getMessage());
            }
        }
        return null;
    }

    private List<ResolveClassSet> parseAndClassSetList(boolean isSignature) {
        List<ResolveClassSet> result = new ArrayList<>();
        while (pos < len) {
            if (isSignature && isNext(PropertyCanonicalNameUtils.UNKNOWNCLASS)) {
                checkNext(PropertyCanonicalNameUtils.UNKNOWNCLASS);
                result.add(null);
            } else {
                result.add(parseAndClassSet());
            }

            if (isNext(",")) {
                checkNext(",");
            } else {
                break;
            }
        }
        return result;
    }

    private ResolveClassSet parseAndClassSet() {
        ResolveClassSet result;
        if (isNext(CPREFIX)) {
            result = parseConcatenateClassSet();
        } else if (isNext(ClassCanonicalNameUtils.OrObjectClassSetNameLBracket)) {
            result = parseOrObjectClassSet();
        } else if (isNext(ClassCanonicalNameUtils.UpClassSetNameLBracket)) {
            result = parseUpClassSet();
        } else {
            result = parseSingleClass();
        }
        return result;
    }

    private ResolveConcatenateClassSet parseConcatenateClassSet() {
        checkNext(CPREFIX);
        List<ResolveClassSet> classes = parseAndClassSetList(false);
        checkNext(ClassCanonicalNameUtils.ConcatenateClassNameRBracket);
        return new ResolveConcatenateClassSet(classes.toArray(new ResolveClassSet[classes.size()]));
    }

    private ResolveOrObjectClassSet parseOrObjectClassSet() {
        checkNext(ClassCanonicalNameUtils.OrObjectClassSetNameLBracket);
        ResolveUpClassSet up = parseUpClassSet();
        checkNext(",");
        ImSet<ConcreteCustomClass> customClasses = SetFact.EMPTY();
        if (!isNext(ClassCanonicalNameUtils.OrObjectClassSetNameRBracket)) {
            customClasses = parseCustomClassList();
        }
        ResolveOrObjectClassSet orSet = new ResolveOrObjectClassSet(up, customClasses);
        checkNext(ClassCanonicalNameUtils.OrObjectClassSetNameRBracket);
        return orSet;
    }

    private ImSet<ConcreteCustomClass> parseCustomClassList() {
        List<ConcreteCustomClass> classes = new ArrayList<>();
        while (pos < len) {
            ConcreteCustomClass cls = (ConcreteCustomClass) parseCustomClass();
            classes.add(cls);
            if (!isNext(",")) {
                break;
            }
            checkNext(",");
        }
        return new ArIndexedSet<>(classes.size(), classes.toArray(new ConcreteCustomClass[classes.size()]));
    }

    private ResolveUpClassSet parseUpClassSet() {
        if (isNext(ClassCanonicalNameUtils.UpClassSetNameLBracket)) {
            checkNext(ClassCanonicalNameUtils.UpClassSetNameLBracket);
            List<CustomClass> classes = new ArrayList<>();
            while (!isNext(ClassCanonicalNameUtils.UpClassSetNameRBracket)) {
                classes.add(parseCustomClass());
                if (!isNext(ClassCanonicalNameUtils.UpClassSetNameRBracket)) {
                    checkNext(",");
                }
            }
            checkNext(ClassCanonicalNameUtils.UpClassSetNameRBracket);
            return new ResolveUpClassSet(classes.toArray(new CustomClass[classes.size()]));
        } else {
            CustomClass cls = parseCustomClass();
            return new ResolveUpClassSet(cls);
        }
    }

}
