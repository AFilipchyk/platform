package platform.gwt.main.client.form.ui;

import com.smartgwt.client.types.SelectionStyle;
import com.smartgwt.client.widgets.grid.ListGridField;
import com.smartgwt.client.widgets.grid.ListGridRecord;
import com.smartgwt.client.widgets.grid.events.*;
import com.smartgwt.client.widgets.tree.TreeGrid;
import com.smartgwt.client.widgets.tree.TreeNode;
import com.smartgwt.client.widgets.tree.events.*;
import platform.gwt.view.*;
import platform.gwt.view.changes.GGroupObjectValue;

import java.util.*;

public class GTreeTable extends TreeGrid {
    private final GFormController formController;

    private GTreeTableTree tree;

    private HashSet<String> createdFields = new HashSet<String>();

    private GTreeTableNode currentNode;

    private List<GTreeTableNode> expandedNodes;

    private boolean dataUpdated;

    private Map<GPropertyDraw, Map<GGroupObjectValue, Object>> propertyCaptions = new HashMap<GPropertyDraw, Map<GGroupObjectValue, Object>>();
    private Map<GPropertyDraw, Map<GGroupObjectValue, Object>> cellBackgroundValues = new HashMap<GPropertyDraw, Map<GGroupObjectValue, Object>>();
    private Map<GPropertyDraw, Map<GGroupObjectValue, Object>> cellForegroundValues = new HashMap<GPropertyDraw, Map<GGroupObjectValue, Object>>();
    private Map<GGroupObjectValue, Object> rowBackgroundValues = new HashMap<GGroupObjectValue, Object>();
    private Map<GGroupObjectValue, Object> rowForegroundValues = new HashMap<GGroupObjectValue, Object>();

    public GTreeTable(GFormController iformController, GForm iform) {
        this.formController = iformController;

        tree = new GTreeTableTree(iform);

        setCanEdit(false);
        setShowRollOver(false);
        setCanSort(false);
        setCanResizeFields(true);
        setShowAllRecords(true);
        setSelectionType(SelectionStyle.SINGLE);

        setShowConnectors(true);
//        setEmptyCellValue("--");

        setData(tree);

        addFolderOpenedHandler(new FolderOpenedHandler() {
            @Override
            public void onFolderOpened(FolderOpenedEvent folderOpenedEvent) {
                GTreeTableNode node = (GTreeTableNode) folderOpenedEvent.getNode();

                saveVisualState();
                expandedNodes.add(node);
                formController.expandGroupObject(node.group, node.key);

                if (tree.hasOnlyExpandningNodeAsChild(node)) {
                    folderOpenedEvent.cancel();
                }
            }
        });

        addFolderClosedHandler(new FolderClosedHandler() {
            @Override
            public void onFolderClosed(FolderClosedEvent folderClosedEvent) {
                GTreeTableNode node = (GTreeTableNode) folderClosedEvent.getNode();

                saveVisualState();
                expandedNodes.remove(node);
                formController.collapseGroupObject(node.group, node.key);
            }
        });

        addSelectionChangedHandler(new SelectionChangedHandler() {
            @Override
            public void onSelectionChanged(SelectionEvent event) {
                GTreeTableNode node = (GTreeTableNode) event.getSelectedRecord();
                if (node != null && node.group != null && node.key != currentNode.key) {
                    currentNode = node;
                    saveVisualState();
                    formController.changeGroupObject(node.group, node.key);
                }
            }
        });

        ListGridField treeField = getField("nodeTitle");
        if (treeField != null) {
            treeField.setShowValueIconOnly(true);
            treeField.setWidth(110);
            treeField.setName("Дерево");
        }

        currentNode = tree.getRoot();
    }

    public void removeProperty(GGroupObject group, GPropertyDraw property) {
        dataUpdated = true;
        int ind = tree.removeProperty(group, property);
        if (ind > 0)
            hideField(property.sID);
    }

    public void addProperty(GGroupObject group, GPropertyDraw property) {
        dataUpdated = true;

        int ind = tree.addProperty(group, property);

        if (ind > -1) {
            if (createdFields.contains(property.sID)) {
                showField(property.sID);
            } else {
                addField(ind, property.createGridField(formController));
                createdFields.add(property.sID);
            }
        }
    }

    private void addField(int ins, ListGridField newField) {
        ListGridField[] fields = getFields();
        ListGridField[] newFields = new ListGridField[fields.length + 1];

        System.arraycopy(fields, 0, newFields, 0, ins);
        newFields[ins] = newField;
        System.arraycopy(fields, ins, newFields, ins + 1, fields.length - ins);

        setFields(newFields);
    }

    public void setKeys(GGroupObject group, ArrayList<GGroupObjectValue> keys, ArrayList<GGroupObjectValue> parents) {
        tree.setKeys(group, keys, parents);
        dataUpdated = true;
    }

    public void setValues(GPropertyDraw property, Map<GGroupObjectValue, Object> propValues) {
        if (propValues != null) {
            tree.setValues(property, propValues);
            dataUpdated = true;
        }
    }

    public void update() {
        if (dataUpdated) {
            restoreVisualState();

            tree.updateValues();

            dataUpdated = false;
        }

        boolean needsFieldsRefresh = false;
        for (GPropertyDraw property : tree.properties) {
            Map<GGroupObjectValue, Object> captions = propertyCaptions.get(property);
            if (captions != null) {
                Object value = captions.values().iterator().next();
                getField(property.sID).setTitle(value == null ? null : value.toString().trim());
                needsFieldsRefresh = true;
            }
        }
        if (needsFieldsRefresh)
            refreshFields();
    }

    @Override
    protected String getCellCSSText(ListGridRecord record, int rowNum, int colNum) {
        String cssText = "";

        GPropertyDraw property = null;
        for (GPropertyDraw prop : tree.properties) {
            if (getFieldName(colNum).equals(prop.sID)){
                property = prop;
            }
        }
        if (property != null) {
            Object background = null;
            if (rowBackgroundValues.get(((GTreeTableNode) record).key) != null) {
                background = rowBackgroundValues.get(((GTreeTableNode) record).key);
            } else if (cellBackgroundValues.get(property) != null) {
                background = cellBackgroundValues.get(property).get(((GTreeTableNode) record).key);
            }
            if (background != null) {
                cssText += "background-color:" + background + ";";
            }

            Object foreground = null;
            if (rowForegroundValues.get(((GTreeTableNode) record).key) != null) {
                foreground = rowForegroundValues.get(((GTreeTableNode) record).key);
            } else if (cellForegroundValues.get(property) != null) {
                foreground = cellForegroundValues.get(property).get(((GTreeTableNode) record).key);
            }
            if (foreground != null) {
                cssText += "color:" + foreground + ";";
            }
        }

        return cssText.isEmpty() ? super.getCellCSSText(record, rowNum, colNum) : cssText;
    }

    public void saveVisualState() {
        expandedNodes = new ArrayList<GTreeTableNode>();
        if (tree.isOpen(tree.getRoot())) {
            expandedNodes.add(tree.getRoot());
            expandedNodes.addAll(getExpandedChildren(tree.getRoot()));
        }
    }

    private List<GTreeTableNode> getExpandedChildren(GTreeTableNode node) {
        List<GTreeTableNode> exChildren = new ArrayList<GTreeTableNode>();
        for (TreeNode child : tree.getChildren(node)) {
            if (child instanceof GTreeTableNode && tree.isOpen(child)) {
                exChildren.add((GTreeTableNode) child);
                exChildren.addAll(getExpandedChildren((GTreeTableNode) child));
            }
        }
        return exChildren;
    }

    public void restoreVisualState() {
        expandNode(tree.getRoot());
        selectCurrentNode(currentNode);
    }

    private void selectCurrentNode(GTreeTableNode node) {
        if (!tree.isRoot(node) && !tree.isOpen(tree.getParent(node))) {
            selectCurrentNode((GTreeTableNode) tree.getParent(node));
        } else {
            selectRecord(node);
        }
    }

    private void expandNode(GTreeTableNode node) {
        if (expandedNodes != null && expandedNodes.contains(node) && !tree.hasOnlyExpandningNodeAsChild(node)) {
            tree.openFolder(node);
            for (TreeNode child : tree.getChildren(node)) {
                if (child instanceof GTreeTableNode) {
                    expandNode((GTreeTableNode) child);
                }
            }
        }
    }

    public void updatePropertyCaptions(GPropertyDraw propertyDraw, Map<GGroupObjectValue, Object> values) {
        propertyCaptions.put(propertyDraw, values);
    }

    public void updateCellBackgroundValues(GPropertyDraw propertyDraw, Map<GGroupObjectValue, Object> values) {
        cellBackgroundValues.put(propertyDraw, values);
    }

    public void updateCellForegroundValues(GPropertyDraw propertyDraw, Map<GGroupObjectValue, Object> values) {
        cellForegroundValues.put(propertyDraw, values);
    }

    public void updateRowBackgroundValues(Map<GGroupObjectValue, Object> values) {
        rowBackgroundValues = values;
    }

    public void updateRowForegroundValues(Map<GGroupObjectValue, Object> values) {
        rowForegroundValues = values;
    }
}
