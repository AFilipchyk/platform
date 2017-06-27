package lsfusion.server.form.view;

import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.interop.form.layout.AbstractContainer;
import lsfusion.interop.form.layout.Alignment;
import lsfusion.interop.form.layout.ContainerAdder;
import lsfusion.interop.form.layout.ContainerType;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.form.entity.CalcPropertyObjectEntity;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.mutables.NFFact;
import lsfusion.server.logics.mutables.Version;
import lsfusion.server.logics.mutables.interfaces.NFOrderSet;
import lsfusion.server.serialization.ServerSerializationPool;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ContainerView extends ComponentView implements AbstractContainer<ComponentView, LocalizedString> {

    public NFOrderSet<ComponentView> children = NFFact.orderSet();

    public LocalizedString caption;
    public LocalizedString description;

    private ContainerType type = ContainerType.CONTAINERV;

    public Alignment childrenAlignment = Alignment.LEADING;

    public int columns = 4;
    
    public int columnLabelsWidth = 0;

    public CalcPropertyObjectEntity<?> showIf;

    public ContainerView() {
    }

    public ContainerView(int ID) {
        this(ID, false);
    }

    public boolean main;
    public ContainerView(int ID, boolean main) {
        super(ID);
        this.main = main;
    }

    public void setCaption(LocalizedString caption) {
        this.caption = caption;
    }

    public void setDescription(LocalizedString description) {
        this.description = description;
    }

    public boolean isTabbedPane() {
        return getType() == ContainerType.TABBED_PANE;
    }
    public boolean isScroll() {
        return getType() == ContainerType.SCROLL;
    }
    public boolean isSplit() {
        ContainerType type = getType();
        return type == ContainerType.HORIZONTAL_SPLIT_PANE || type == ContainerType.VERTICAL_SPLIT_PANE;
    }
    public ContainerType getType() {
        return type;
    }

    public void setType(ContainerType type) {
        this.type = type;
    }

    public void setChildrenAlignment(Alignment childrenAlignment) {
        this.childrenAlignment = childrenAlignment;
    }

    public void setColumns(int columns) {
        this.columns = columns;
    }

    public CalcPropertyObjectEntity<?> getShowIf() {
        return showIf;
    }

    public void setShowIf(CalcPropertyObjectEntity<?> showIf) {
        this.showIf = showIf;
    }

    @Override
    public ComponentView findById(int id) {
        ComponentView result = super.findById(id);
        if(result!=null) return result;
        
        for(ComponentView child : getChildrenIt()) {
            result = child.findById(id);
            if(result!=null) return result;
        }

        return null;
    }

    private void changeContainer(ComponentView comp, Version version) {
        ContainerView container = comp.getNFContainer(version);
        if (container != null)
            container.remove(comp, version);

        comp.setContainer(this, version);
    }

    public static class VersionContainerAdder extends ContainerAdder<ContainerView, ComponentView, LocalizedString> {
        private final Version version;

        public VersionContainerAdder(Version version) {
            this.version = version;
        }

        public void add(ContainerView container, ComponentView component) {
            container.add(component, version); 
        }
    } 

    public void add(ComponentView comp) {
        add(comp, Version.DESCRIPTOR);
    }
    
    public void add(ComponentView comp, Version version) {
        changeContainer(comp, version);
        children.add(comp, version);
    }

    public void addFirst(ComponentView comp, Version version) {
        changeContainer(comp, version);
        children.addFirst(comp, version);
    }

    public void addBefore(ComponentView comp, ComponentView compBefore, Version version) {
        changeContainer(comp, version);
        children.addIfNotExistsToThenLast(comp, compBefore, false, version);
    }

    public void addAfter(ComponentView comp, ComponentView compAfter, Version version) {
        changeContainer(comp, version);
        children.addIfNotExistsToThenLast(comp, compAfter, true, version);
    }

    public boolean remove(ComponentView comp, Version version) {
        if (children.containsNF(comp, version)) {
            children.remove(comp, version);
            comp.setContainer(null, version);
            return true;
        } else {
            return false;
        }
    }

    public boolean isAncestorOf(ComponentView container) {
        return container != null && (super.isAncestorOf(container) || isAncestorOf(container.getContainer()));
    }

    public boolean isNFAncestorOf(ComponentView container, Version version) {
        return container != null && (super.isNFAncestorOf(container, version) || isNFAncestorOf(container.getNFContainer(version), version));
    }

    public Iterable<ComponentView> getChildrenIt() {
        return children.getIt();
    }
    public ImList<ComponentView> getChildrenList() {
        return children.getList();
    }
    public Iterable<ComponentView> getNFChildrenIt(Version version) {
        return children.getNFIt(version);
    }

    @Override
    public void customSerialize(ServerSerializationPool pool, DataOutputStream outStream, String serializationType) throws IOException {
        super.customSerialize(pool, outStream, serializationType);

        pool.serializeCollection(outStream, getChildrenList(), serializationType);

        pool.writeString(outStream, ThreadLocalContext.localize(caption));
        pool.writeString(outStream, ThreadLocalContext.localize(description));

//        pool.writeObject(outStream, main);

        pool.writeObject(outStream, type);

        pool.writeObject(outStream, childrenAlignment);

        outStream.writeInt(columns);
        outStream.writeInt(columnLabelsWidth);
    }

    @Override
    public void customDeserialize(ServerSerializationPool pool, DataInputStream inStream) throws IOException {
        super.customDeserialize(pool, inStream);

        children = NFFact.finalOrderSet(pool.<ComponentView>deserializeList(inStream));

        caption = LocalizedString.create(pool.readString(inStream));
        description = LocalizedString.create(pool.readString(inStream));

//        main = pool.readBoolean(inStream); // пока не будем делать, так как надо клиента обновлять

        type = pool.readObject(inStream);

        childrenAlignment = pool.readObject(inStream);

        columns = inStream.readInt();
        columnLabelsWidth = inStream.readInt();
    }

    @Override
    public void finalizeAroundInit() {
        super.finalizeAroundInit();
        
        for(ComponentView child : getChildrenIt())
            child.finalizeAroundInit();
    }

    @Override
    public String toString() {
        return ThreadLocalContext.localize(caption);
    }
}
