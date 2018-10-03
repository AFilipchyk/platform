package lsfusion.server.logics.property.actions.integration.hierarchy;

import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.logics.property.group.AbstractGroup;

public class PropertyGroupParseNode extends GroupParseNode {
    private final AbstractGroup group;

    protected String getKey() {
        return group.getIntegrationSID();
    }

    public PropertyGroupParseNode(ImSet<ParseNode> children, AbstractGroup group) {
        super(children);
        this.group = group;
    }

    @Override
    public <T extends Node<T>> void importNode(T node, ImMap<ObjectEntity, Object> upValues, ImportData importData) {
        importChildrenNodes(node.getNode(getKey()), upValues, importData);
    }

    @Override
    public <T extends Node<T>> void exportNode(T node, ImMap<ObjectEntity, Object> upValues, ExportData exportData) {
        T newNode = node.createNode();
        exportChildrenNodes(newNode, upValues, exportData);
        node.addNode(node, getKey(), newNode);            
    }
}
