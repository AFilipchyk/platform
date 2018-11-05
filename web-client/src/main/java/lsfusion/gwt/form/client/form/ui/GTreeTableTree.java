package lsfusion.gwt.form.client.form.ui;

import lsfusion.client.logics.ClientGroupObject;
import lsfusion.gwt.base.shared.GwtSharedUtils;
import lsfusion.gwt.form.shared.view.GForm;
import lsfusion.gwt.form.shared.view.GGroupObject;
import lsfusion.gwt.form.shared.view.GObject;
import lsfusion.gwt.form.shared.view.GPropertyDraw;
import lsfusion.gwt.form.shared.view.changes.GGroupObjectValue;
import lsfusion.gwt.form.shared.view.changes.GGroupObjectValueBuilder;

import java.util.*;

public class GTreeTableTree {
    private GForm form;

    private final Map<GGroupObject, Set<GTreeTableNode>> groupNodes = new HashMap<>();
    public ArrayList<GPropertyDraw> properties = new ArrayList<>();
    public final List<GPropertyDraw> columnProperties = new ArrayList<>();

    public HashMap<GPropertyDraw, Map<GGroupObjectValue, Object>> values = new HashMap<>();
    public HashMap<GPropertyDraw, Map<GGroupObjectValue, Object>> readOnly = new HashMap<>();
    public HashMap<GGroupObject, List<GPropertyDraw>> groupPropsMap = new HashMap<>();

    public GTreeTableNode root;

    public GTreeTableTree(GForm iForm) {
        form = iForm;
        root = new GTreeTableNode();
    }

    public int addProperty(GGroupObject group, GPropertyDraw property) {

        if (properties.indexOf(property) == -1 && !property.hide) {
            int ins = GwtSharedUtils.relativePosition(property, form.propertyDraws, properties);
            properties.add(ins, property);

            List<GPropertyDraw> groupProperties = groupPropsMap.get(group);
            if (groupProperties == null) {
                groupProperties = new ArrayList<>();
                groupPropsMap.put(group, groupProperties);
            }
            int gins = GwtSharedUtils.relativePosition(property, properties, groupProperties);
            groupProperties.add(gins, property);

            if (group.isLastGroupInTree()) {
                int tins = GwtSharedUtils.relativePosition(property, properties, columnProperties);
                columnProperties.add(tins, property);
                return tins + 1;
            }
        }
        return -1;
    }

    public int removeProperty(GGroupObject group, GPropertyDraw property) {
        values.remove(property);

        properties.remove(property);
        if (groupPropsMap.containsKey(group))
            groupPropsMap.get(group).remove(property);

        int ind = columnProperties.indexOf(property);
        if (ind != -1) {
            columnProperties.remove(property);
        }
        return ind + 1;
    }

    public void setKeys(GGroupObject group, ArrayList<GGroupObjectValue> keys, ArrayList<GGroupObjectValue> parents, Map<GGroupObjectValue, Boolean> expandable) {
        Map<GGroupObjectValue, List<GGroupObjectValue>> childTree = new HashMap<>();

        for (int i = 0; i < keys.size(); i++) {
            GGroupObjectValue key = keys.get(i);

            GGroupObjectValueBuilder parentPathBuilder =
                    new GGroupObjectValueBuilder(key)
                            .removeAll(group.objects)
                            .putAll(parents.get(i));

            GGroupObjectValue parentPath = parentPathBuilder.toGroupObjectValue();

            List<GGroupObjectValue> children = childTree.get(parentPath);
            if (children == null) {
                children = new ArrayList<>();
                childTree.put(parentPath, children);
            }
            children.add(key);
        }

        for (GTreeTableNode groupNode : getGroupNodes(group.getUpTreeGroup())) {
            synchronize(groupNode, group, childTree, expandable);
        }
    }

    void synchronize(GTreeTableNode parent, GGroupObject syncGroup, Map<GGroupObjectValue, List<GGroupObjectValue>> tree, Map<GGroupObjectValue, Boolean> expandables) {
        List<GGroupObjectValue> syncChilds = tree.get(parent.getKey());
        if (syncChilds == null) {
            syncChilds = new ArrayList<>();
        }

        if (hasOnlyExpandningNodeAsChild(parent)) {
            parent.getChild(0).removeFromParent();
        }

        List<GTreeTableNode> allChildren = new ArrayList<>();
        GTreeTableNode[] thisGroupChildren = new GTreeTableNode[syncChilds.size()];

        for (GTreeTableNode child : new ArrayList<>(parent.getChildren())) {

            if (child.getGroup().equals(syncGroup)) {
                int index = syncChilds.indexOf(child.getKey());
                if (index == -1) {
                    child.removeFromParent();
                    removeFromGroupNodes(child);
                } else {
                    thisGroupChildren[index] = child;
                }
            } else {
                allChildren.add(child);
            }
        }

        for (int i = 0; i < syncChilds.size(); ++i) {
            GGroupObjectValue key = syncChilds.get(i);
            GTreeTableNode child = thisGroupChildren[i];

            if (child == null) {
                thisGroupChildren[i] = child = new GTreeTableNode(syncGroup, key);

                parent.addNode(child);

                getGroupNodes(syncGroup).add(child);
            }

            boolean expandable = false;
            if (syncGroup.mayHaveChildren()) {
                Boolean e = expandables.get(key);
                expandable = e == null || e;
            }
            child.setExpandable(expandable);

            synchronize(child, syncGroup, tree, expandables);
        }

        if (parent.getGroup() == syncGroup) {
            allChildren.addAll(0, Arrays.asList(thisGroupChildren));
        } else {
            allChildren.addAll(Arrays.asList(thisGroupChildren));
        }

        removeList(parent.getChildren());

        for (GTreeTableNode child : allChildren) {
            parent.addNode(child);
        }

        if (parent.getChildren().isEmpty() && parent.isExpandable()) {
            parent.addNode(new ExpandingTreeTableNode());
        }
    }

    private void removeList(List<GTreeTableNode> children) {
        for (GTreeTableNode child : new ArrayList<>(children)) {
            child.removeFromParent();
        }
    }

    public boolean hasOnlyExpandningNodeAsChild(GTreeTableNode node) {
        return node.getChildren().size() == 1 && node.getChild(0) instanceof ExpandingTreeTableNode;
    }

    private void removeFromGroupNodes(GTreeTableNode node) {
        getGroupNodes(node.getGroup()).remove(node);

        for (GTreeTableNode child : node.getChildren()) {
            if (!(child instanceof ExpandingTreeTableNode)) {
                removeFromGroupNodes(child);
            }
        }
    }

    public Set<GTreeTableNode> getGroupNodes(GGroupObject group) {
        if (group == null) {
            return Collections.singleton(root);
        }

        Set<GTreeTableNode> nodes = groupNodes.get(group);
        if (nodes == null) {
            nodes = new HashSet<>();
            groupNodes.put(group, nodes);
        }
        return nodes;
    }


    public void setPropertyValues(GPropertyDraw property, Map<GGroupObjectValue, Object> propValues, boolean updateKeys) {
        GwtSharedUtils.putUpdate(values, property, propValues, updateKeys);
    }

    public void setReadOnlyValues(GPropertyDraw property, Map<GGroupObjectValue, Object> readOnlyValues) {
        GwtSharedUtils.putUpdate(readOnly, property, readOnlyValues, false);
    }

    private int nodeCounter;

    public ArrayList<GTreeGridRecord> getUpdatedRecords() {
        nodeCounter = 0;
        ArrayList<GTreeGridRecord> result = new ArrayList<>();
        if (!hasOnlyExpandningNodeAsChild(root)) {
            result.addAll(getNodeChildrenRecords(root, 0, null));
        }
        return result;
    }

    private List<GTreeGridRecord> getNodeChildrenRecords(GTreeTableNode node, int level, GTreeColumnValue parentValue) {
        List<GTreeGridRecord> result = new ArrayList<>();
        for (GTreeTableNode child : node.getChildren()) {
            HashMap<GPropertyDraw, Object> valueMap = new HashMap<>();
            for (int i = 0; i < columnProperties.size(); i++) {
                GPropertyDraw property = getProperty(child.getGroup(), i);
                if (property != null) {
                    Object value = values.get(property).get(child.getKey());
                    valueMap.put(columnProperties.get(i), value);
                }
            }
            GTreeGridRecord record = new GTreeGridRecord(child.getGroup(), child.getKey(), valueMap);

            GTreeColumnValue treeValue = generateTreeCellValue(child, parentValue, level);
            treeValue.addLastInLevel(level - 1, node.getChildren().indexOf(child) == node.getChildren().size() - 1);
            record.setAttribute("treeColumn", treeValue);
            result.add(record);
            if (child.isOpen()) {
                result.addAll(getNodeChildrenRecords(child, level + 1, treeValue));
            }
        }
        return result;
    }

    private GTreeColumnValue generateTreeCellValue(GTreeTableNode node, GTreeColumnValue parentValue, int level) {
        GTreeColumnValue value = new GTreeColumnValue(level, objectsToString(node.getGroup()) + nodeCounter);
        if (node.isOpen()) {
            value.setOpen(true);
        } else {
            value.setOpen(!node.getChildren().isEmpty() ? false : null);
        }
        if (parentValue != null) {
            value.setLastInLevelMap(parentValue.getLastInLevelMap());
        }
        nodeCounter++;
        return value;
    }

    private String objectsToString(GGroupObject groupObject) {
        String result = "";
        for (GObject object : groupObject.objects) {
            result += object.sID;
        }
        return result;
    }

    public GPropertyDraw getProperty(GGroupObject group, int column) {
        List<GPropertyDraw> groupProperties = groupPropsMap.get(group);
        if (groupProperties == null || column < 0 || column >= groupProperties.size()) {
            return null;
        }

        return groupProperties.get(column);
    }

    public GPropertyDraw getColumnProperty(int column) {
        return column != 0 ? columnProperties.get(column - 1) : null;
    }

    public Object getValue(GGroupObject group, int column, GGroupObjectValue key) {
        GPropertyDraw property = getProperty(group, column);
        if (property == null) {
            return null;
        }
        return values.get(property).get(key);
    }

    public boolean isEditable(GGroupObject group, int column, GGroupObjectValue key) {
        if (column >= 0) {
            GPropertyDraw property = getProperty(group, column);
            if (property != null && !property.isReadOnly()) {
                Map<GGroupObjectValue, Object> propReadOnly = readOnly.get(property);
                return propReadOnly == null || propReadOnly.get(key) == null;
            }
        }
        return false;
    }

    public void putValue(GPropertyDraw property, GGroupObjectValue key, Object value) {
        values.get(property).put(key, value);
    }

    public GTreeTableNode getNodeByRecord(GTreeGridRecord record) {
        if (record != null) {
            for (Map.Entry<GGroupObject, Set<GTreeTableNode>> entry : groupNodes.entrySet()) {
                if (entry.getKey().equals(record.getGroup())) {
                    for (GTreeTableNode node : entry.getValue()) {
                        if (record.getKey().equals(node.getKey())) {
                            return node;
                        }
                    }
                }
            }
        }
        return null;
    }

    public int getPropertyColumnIndex(GPropertyDraw property) {
        return columnProperties.indexOf(property) + 1;
    }

    private class ExpandingTreeTableNode extends GTreeTableNode {
        public ExpandingTreeTableNode() {
            super();
        }
    }
}
