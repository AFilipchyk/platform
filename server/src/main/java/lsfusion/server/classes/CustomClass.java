package lsfusion.server.classes;

import lsfusion.base.BaseUtils;
import lsfusion.base.ImmutableObject;
import lsfusion.base.SFunctionSet;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.MExclSet;
import lsfusion.base.col.interfaces.mutable.MMap;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.base.col.lru.LRUUtil;
import lsfusion.base.col.lru.LRUWSVSMap;
import lsfusion.interop.Data;
import lsfusion.server.caches.IdentityLazy;
import lsfusion.server.caches.IdentityStrongLazy;
import lsfusion.server.caches.ManualLazy;
import lsfusion.server.classes.sets.OrObjectClassSet;
import lsfusion.server.classes.sets.ResolveClassSet;
import lsfusion.server.classes.sets.ResolveUpClassSet;
import lsfusion.server.classes.sets.UpClassSet;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.data.type.ObjectType;
import lsfusion.server.data.type.Type;
import lsfusion.server.form.entity.*;
import lsfusion.server.form.instance.CustomObjectInstance;
import lsfusion.server.form.instance.ObjectInstance;
import lsfusion.server.logics.BaseLogicsModule;
import lsfusion.server.logics.debug.ClassDebugInfo;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.mutables.NFFact;
import lsfusion.server.logics.mutables.Version;
import lsfusion.server.logics.mutables.interfaces.NFDefault;
import lsfusion.server.logics.mutables.interfaces.NFOrderSet;
import lsfusion.server.logics.mutables.interfaces.NFProperty;
import lsfusion.server.logics.property.*;
import lsfusion.server.logics.property.actions.ChangeClassActionProperty;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class CustomClass extends ImmutableObject implements ObjectClass, ValueClass {

    private ClassDebugInfo debugInfo;

    public Type getType() {
        return ObjectType.instance;
    }
    public Stat getTypeStat(boolean forJoin) {
        return Stat.ALOT;
    }

    public boolean dialogReadOnly = false;

    public final NFOrderSet<CustomClass> parents = NFFact.orderSet();
    public Iterable<CustomClass> getParentsIt() { // есть риск нарваться на NF, хотя может и нет так как классы первым прогоном собираются
        return parents.getIt();
    }
    public Iterable<CustomClass> getParentsListIt() {
        return parents.getListIt();
    }        
    public boolean containsNFParents(CustomClass customClass, Version version) {
        return parents.containsNF(customClass, version);
    }
    public Iterable<CustomClass> getNFParentsIt(Version version) {
        return parents.getNFIt(version);
    }

    public final NFOrderSet<CustomClass> children = NFFact.orderSet();
    public ImSet<CustomClass> getChildren() {
        return children.getSet();
    }
    public Iterable<CustomClass> getChildrenIt() {
        return children.getIt();
    }

    public String toString() {
        return ThreadLocalContext.localize(LocalizedString.concat(caption, " (" + getCanonicalName() + ")"));
    }

    public Integer ID;
    protected String sID;

    public String getSID() {
        return sID;
    }
    
    public String getCanonicalName() {
        return getSID().replaceFirst("_", ".");
    }

    public String getParsedName() {
        return getCanonicalName();
    }

    public LocalizedString caption;
    public CustomClass(String sID, LocalizedString caption, Version version, CustomClass... parents) {
        this.sID = sID;
        this.caption = caption;

        for (CustomClass parent : parents) {
            addParentClass(parent, version);
        }
    }

    public final void addParentClass(CustomClass parent, Version version) {
        this.parents.add(parent, version);
        parent.children.add(this, version);
        assert checkParentChildsCaches(parent, version);
    }

    private boolean checkParentChildsCaches(CustomClass parent, Version version) {
        assert parent.allChildren == null;
        for(CustomClass recParent : parent.getNFParentsIt(version))
            checkParentChildsCaches(recParent, version);
        return true;
    }

    @Override
    public Object getDefaultValue() {
        return null;
    }

    @Override
    public LocalizedString getCaption() {
        return caption;
    } 

    public boolean hasChildren() {
        return !getChildren().isEmpty();
    }

    public UpClassSet getUpSet() {
        return new UpClassSet(this);
    }

    @Override
    public ResolveClassSet getResolveSet() {
        return new ResolveUpClassSet(this);
    }

    public BaseClass getBaseClass() {
        return getParentsIt().iterator().next().getBaseClass();
    }

    public boolean isChild(CustomClass parentClass) {
        return parentClass.getAllChildren().contains(this);
    }

    public boolean isCompatibleParent(ValueClass remoteClass) {
        return remoteClass instanceof CustomClass && ((CustomClass)remoteClass).isChild(this);
    }

    public CustomClass findClassID(int idClass) {
        if(ID!=null && ID == idClass) return this; // проверка чисто для fillIDs

        for(CustomClass child : getChildrenIt()) {
            CustomClass findClass = child.findClassID(idClass);
            if(findClass!=null) return findClass;
        }

        return null;
    }

    public ConcreteCustomClass findConcreteClassID(int idClass) {
        CustomClass cls = findClassID(idClass);
        if (cls == null)
            throw new RuntimeException(ThreadLocalContext.localize("{classes.there.is.an.object.of.not.existing.class.in.the.database} : " + idClass + ")"));
        if (! (cls instanceof ConcreteCustomClass))
            throw new RuntimeException(ThreadLocalContext.localize("{classes.there.is.an.object.of.abstract.class.in.the.database} : " + idClass + ")"));
        return (ConcreteCustomClass) cls;
    }

    private final static LRUWSVSMap<CustomClass, CustomClass, ImSet<CustomClass>> cacheChilds = new LRUWSVSMap<>(LRUUtil.G2);

    public void fillParents(MSet<CustomClass> parentSet) {
        if (parentSet.add(this)) return;

        for(CustomClass parent : getParentsIt())
            parent.fillParents(parentSet);
    }

    // заполняет список классов
    public void fillChilds(MSet<CustomClass> classSet) {
        classSet.add(this);

        for(CustomClass child : getChildrenIt())
            child.fillChilds(classSet);
    }

    private ImSet<CustomClass> allChildren = null;
    @ManualLazy
    public ImSet<CustomClass> getAllChildren() {
        if(allChildren ==null) {
            allChildren = BaseUtils.getAllChildren(this, getChildren);
        }
        return allChildren;
    }

    private ImSet<CustomClass> allParents = null;
    @ManualLazy
    public ImSet<CustomClass> getAllParents() {
        if(allParents==null) {
            MSet<CustomClass> mParents = SetFact.mSet();
            fillParents(mParents);
            allParents = mParents.immutable();
        }
        return allParents;
    }

    // заполняет список классов
    public void fillConcreteChilds(MSet<ConcreteCustomClass> classSet) {
        if(this instanceof ConcreteCustomClass) {
            ConcreteCustomClass concreteThis = (ConcreteCustomClass) this;
            if(classSet.add(concreteThis)) return;
        }

        for(CustomClass child : getChildrenIt())
            child.fillConcreteChilds(classSet);
    }

    // заполняет все нижние классы имплементации
    public abstract void fillNextConcreteChilds(MSet<ConcreteCustomClass> mClassSet);

    private static BaseUtils.ExChildrenInterface<CustomClass> getChildren = new BaseUtils.ExChildrenInterface<CustomClass>() {
        public ImSet<CustomClass> getAllChildren(CustomClass element) {
            return element.getAllChildren();
        }

        public Iterable<CustomClass> getChildrenIt(CustomClass element) {
            return element.getChildrenIt();
        }
    };

    protected static BaseUtils.ExChildrenInterface<CustomClass> getParents = new BaseUtils.ExChildrenInterface<CustomClass>() {
        public ImSet<CustomClass> getAllChildren(CustomClass element) {
            return element.getAllParents();
        }

        public Iterable<CustomClass> getChildrenIt(CustomClass element) {
            return element.getParentsIt();
        }
    };

    // получает классы у которого есть оба интерфейса
    public ImSet<CustomClass> commonChilds(CustomClass toCommon) {
        ImSet<CustomClass> result;

        result = cacheChilds.get(this, toCommon);
        if(result!=null) return result;
        
        result = BaseUtils.commonChildren(this, toCommon, getChildren);
        cacheChilds.put(this, toCommon, result);
        return result;
    }

    public void serialize(DataOutputStream outStream) throws IOException {
        outStream.writeByte(Data.OBJECT);
        outStream.writeBoolean(this instanceof ConcreteCustomClass);
        outStream.writeUTF(caption.getSourceString());
        outStream.writeInt(ID);
        outStream.writeUTF(getSID());

        ImSet<CustomClass> children = getChildren();
        outStream.writeInt(children.size());
        for (CustomClass cls : children)
            cls.serialize(outStream);
    }

    private FormEntity baseClassForm = null;

    @ManualLazy
    public FormEntity getBaseClassForm(BaseLogicsModule LM) {
        if (baseClassForm == null) {
            Version version = LM.getVersion();

            baseClassForm = new ListFormEntity(LM, this);
            List<FormEntity> childrenList = new ArrayList<>();
            for (CustomClass child : getChildrenIt()) {
                FormEntity childForm = child.getBaseClassForm(LM);
                if (childForm.getNFParent(version) == null)
                    childrenList.add(childForm);
            }

            Collections.sort(childrenList, new FormEntityComparator());

            for (FormEntity childForm : childrenList) {
                baseClassForm.add(childForm, version);
            }
        }
        return baseClassForm;
    }

    public ClassDebugInfo getDebugInfo() {
        return debugInfo;
    }

    public void setDebugInfo(ClassDebugInfo debugInfo) {
        this.debugInfo = debugInfo;
    }

    static class FormEntityComparator implements Comparator<FormEntity> {
        public int compare(FormEntity f1, FormEntity f2) {
            if (f1.caption == null && f2.caption == null)
                return 0;
            if (f1.caption == null)
                return -1;
            if (f2.caption == null)
                return 1;
            return f1.caption.getSourceString().compareTo(f2.caption.getSourceString());
        }
    }

    // проверяет находятся ли он и все верхние в OrObjectClassSet'е
    public boolean upInSet(UpClassSet upSet, ImSet<ConcreteCustomClass> set) {
        if(upSet.has(this))
            return true; // по child'ам уже не идем они явно все тоже есть
        if(this instanceof ConcreteCustomClass && !set.contains((ConcreteCustomClass) this))
            return false;
        for(CustomClass child : getChildrenIt())
            if(!child.upInSet(upSet, set))
                return false;
        return true;
    }
    public boolean upInSet(CustomClass[] wheres, int numWheres, CustomClass[] proceeded, int numProceeded, CustomClass check) {
        if(isChild(check)) return true;
        for(int i=0;i<numWheres;i++) if(wheres[i]!=null && isChild(wheres[i])) return true;
        for(int i=0;i<numProceeded;i++) if(isChild(proceeded[i])) return true;

        if(this instanceof ConcreteCustomClass) return false;
        for(CustomClass child : getChildrenIt())
            if(!child.upInSet(wheres,numWheres, proceeded, numProceeded, check)) return false;
        return true;
    }

    public abstract ConcreteCustomClass getSingleClass();

    public ObjectInstance newInstance(ObjectEntity entity) {
        return new CustomObjectInstance(entity, this);
    }

    private abstract class ClassFormHolder {
        private NFProperty<ClassFormEntity> form = NFFact.property();

        public ClassFormEntity getForm(final BaseLogicsModule LM) {
            return form.getDefault(new NFDefault<ClassFormEntity>() {
                public ClassFormEntity create() {
                    return addDefaultForm(LM);
                }
            });
        }

        public ClassFormEntity getNFForm(BaseLogicsModule LM, Version version) {
            ClassFormEntity classForm = form.getNF(version);
            if (classForm != null) {
                return classForm;
            }

            classForm = addDefaultForm(LM);
            
            form.set(classForm, version);

            return classForm;
        }
        
        @IdentityStrongLazy
        private ClassFormEntity addDefaultForm(BaseLogicsModule LM) {
            ClassFormEntity classForm = createDefaultForm(LM);
            LM.addFormEntity(classForm.form);
            return classForm;
        }

        public void setForm(ClassFormEntity form, Version version) {
            this.form.set(form, version);
        }

        protected abstract ClassFormEntity createDefaultForm(BaseLogicsModule LM);
    }

    private ClassFormHolder dialogFormHolder = new ClassFormHolder() {
        @Override
        protected ClassFormEntity createDefaultForm(BaseLogicsModule LM) {
            DialogFormEntity dialogFormEntity = new DialogFormEntity(LM, CustomClass.this);
            return new ClassFormEntity(dialogFormEntity, dialogFormEntity.object);
        }
    };

    private ClassFormHolder editFormHolder = new ClassFormHolder() {
        @Override
        protected ClassFormEntity createDefaultForm(BaseLogicsModule LM) {
            EditFormEntity editFormEntity = new EditFormEntity(LM, CustomClass.this);
            return new ClassFormEntity(editFormEntity, editFormEntity.object);
        }
    };

    /**
     * используются при редактировании свойства даного класса из диалога, т.е. фактически для выбора объекта данного класса
     * @param LM
     */
    public ClassFormEntity getDialogForm(BaseLogicsModule LM) {
        return dialogFormHolder.getForm(LM);
    }
    public ClassFormEntity getDialogForm(BaseLogicsModule LM, Version version) {
        return dialogFormHolder.getNFForm(LM, version);
    }

    public void setDialogForm(FormEntity form, ObjectEntity object, Version version) {
        dialogFormHolder.setForm(new ClassFormEntity(form, object), version);
    }

    /**
     * используется для редактирования конкретного объекта данного класса
     * @param LM
     */
    public ClassFormEntity getEditForm(BaseLogicsModule LM) {
        return editFormHolder.getForm(LM);
    }
    public ClassFormEntity getEditForm(BaseLogicsModule LM, Version version) {
        return editFormHolder.getNFForm(LM, version);
    }

    public void setEditForm(FormEntity form, ObjectEntity object, Version version) {
        editFormHolder.setForm(new ClassFormEntity(form, object), version);
    }

    public static IsClassProperty getProperty(ValueClass valueClass) {
        return new IsClassProperty(LocalizedString.concat(valueClass.getCaption(), LocalizedString.create("{logics.pr}")), valueClass);
    }

    private IsClassProperty property;
    @ManualLazy
    public IsClassProperty getProperty() {
        if(property==null)
            property = getProperty(this);
        return property;
    }

    // использование в aspectChangeExtProps у ADDOBJ, ChangeClass и т.п.
    public void fillChangedProps(MExclSet<CalcProperty> mSet, IncrementType type) {
        getProperty().fillChangedProps(mSet, type);
    }
    public ImSet<CalcProperty> getDataProps() { // используется не везде, так как в явную ClassDataProperty не используется, только чтобы readStored был после всех изменений
        return getUpObjectClassFields().keys().mapSetValues(new GetValue<CalcProperty, ObjectClassField>() {
            public CalcProperty getMapValue(ObjectClassField value) {
                return value.getProperty();
            }});
    }

    public static ImSet<IsClassProperty> getProperties(ImSet<? extends ValueClass> classes) {
        return ((ImSet<ValueClass>)classes).mapSetValues(new GetValue<IsClassProperty, ValueClass>() {
            public IsClassProperty getMapValue(ValueClass value) {
                return value.getProperty();
            }});
    }

    public static ImSet<ClassDataProperty> getDataProperties(ImSet<ConcreteCustomClass> classes) {
        return classes.mapMergeSetValues(new GetValue<ClassDataProperty, ConcreteCustomClass>() {
            public ClassDataProperty getMapValue(ConcreteCustomClass value) {
                return value.dataProperty;
            }});
    }

    public ImSet<CalcProperty> getChildProps() {
        ImSet<CustomClass> childs = getAllChildren();
        MExclSet<CalcProperty> mResult = SetFact.mExclSet();
        for(CustomClass customClass : childs) {
            customClass.fillChangedProps(mResult, IncrementType.SET);
            customClass.fillChangedProps(mResult, IncrementType.DROP);
        }
        return mResult.immutable();
    }

//    public ImSet<CalcProperty> getChildDropProps(final ConcreteObjectClass toClass) {
//        MExclSet<CalcProperty> mResult = SetFact.mExclSet();
//        for(CustomClass child : getAllChildren())
//            if(!(toClass instanceof CustomClass && ((CustomClass) toClass).isChild(child)))
//                child.fillChangedProps(mResult, IncrementType.DROP);
//        return mResult.immutable();
//    }

    public ImSet<CalcProperty> getParentSetProps() {
        MExclSet<CalcProperty> mResult = SetFact.mExclSet();
        for(CustomClass parent : getAllParents())
            parent.fillChangedProps(mResult, IncrementType.SET);
        return mResult.immutable();
    }

    public static ImSet<CalcProperty> getChangeProperties(ImSet<CustomClass> addClasses, ImSet<CustomClass> removeClasses) {
        MExclSet<CalcProperty> mResult = SetFact.mExclSet();
        for(CustomClass addClass : addClasses)
            addClass.fillChangedProps(mResult, IncrementType.SET);
        for(CustomClass removeClass : removeClasses)
            removeClass.fillChangedProps(mResult, IncrementType.DROP);
        return mResult.immutable();
    }

    public static ImSet<CalcProperty<ClassPropertyInterface>> getProperties(ImSet<CustomClass> addClasses, ImSet<CustomClass> removeClasses, ImSet<ConcreteObjectClass> oldClasses, ImSet<ConcreteObjectClass> newClasses) {
        return SetFact.addExclSet(getProperties(addClasses.merge(removeClasses)), getDataProperties(BaseUtils.<ImSet<ConcreteCustomClass>>immutableCast(oldClasses.merge(newClasses).filterFn(new SFunctionSet<ConcreteObjectClass>() {
            public boolean contains(ConcreteObjectClass element) {
                return element instanceof ConcreteCustomClass;
            }
        }))));
    }

    public static ActionProperty getChangeClassAction(ObjectClass cls) {
        return ChangeClassActionProperty.create(cls, false, cls.getBaseClass());
    }

    @IdentityStrongLazy // для ID
    public ActionProperty getChangeClassAction() {
        return getChangeClassAction(this);
    }

    public ImMap<ObjectClassField,ObjectValueClassSet> getUpObjectClassFields() {
        return BaseUtils.immutableCast(getUpClassFields(true));
    }

    public ImMap<IsClassField,ObjectValueClassSet> getUpIsClassFields() {
        return getUpClassFields(false);
    }

        // в отличии от getMapTables, для того чтобы ходить "вверх" было удобно
    private IsClassField isClassField;

    public void setIsClassField(IsClassField isClassField) {
        this.isClassField = isClassField;
    }

    @IdentityLazy
    public ImMap<IsClassField,ObjectValueClassSet> getUpClassFields(boolean onlyObjectClassFields) {
        if(isClassField != null && (!onlyObjectClassFields || isClassField instanceof ObjectClassField)) // последняя проверка оптимизационная
            return MapFact.<IsClassField, ObjectValueClassSet>singleton(isClassField, getUpSet());

        MMap<IsClassField, ObjectValueClassSet> mMap = MapFact.mMap(OrObjectClassSet.<IsClassField>objectValueSetAdd());
        for(CustomClass customClass : getChildrenIt())
            mMap.addAll(customClass.getUpClassFields(onlyObjectClassFields));
        if(this instanceof ConcreteCustomClass) // глуповато конечно,
            mMap.add(((ConcreteCustomClass)this).dataProperty, (ConcreteCustomClass)this);
        return pack(mMap.immutable().toRevExclMap(), onlyObjectClassFields, getUpSet());
    }


    public static ImRevMap<IsClassField, ObjectValueClassSet> pack(ImRevMap<IsClassField, ObjectValueClassSet> map, boolean onlyObjectClassFields, ObjectValueClassSet baseClassSet) {
        // паковка по идее должна включать в себя случай, когда есть ClassField который полностью покрывает одну из таблиц, то эффективнее ее долить в ClassField, аналогичная оптимизация на количества ClassValueWhere
        // но пока из способа определения ClassField'а и методологии определения FULL, это большая редкость

        // во многом оптимизация, но важно что дает детерменированность что для Abstract не вернется Concrete, а для Concrete - Abstract что приведет к бесконечной паковке, например в GroupExpr.followFalse
        if(map.size() == 1) {
            assert map.singleValue().containsAll(baseClassSet, false) && baseClassSet.containsAll(map.singleValue(), false);
            return MapFact.singletonRev(map.singleKey(), baseClassSet);
        }

        return map;
    }

    public boolean isComplex;

    @IdentityLazy
    public boolean hasComplex(boolean up) {
        if(isComplex)
            return true;

        for(CustomClass customClass : (up ? getParentsIt() : getChildrenIt()))
            if(customClass.hasComplex(up))
                return true;
        return false;
    }

    public boolean hasComplex() {
        return hasComplex(true) || hasComplex(false);
    }
}
