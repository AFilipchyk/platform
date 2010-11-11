package platform.client.descriptor.editor;

import platform.base.BaseUtils;
import platform.base.OrderedMap;
import platform.client.Main;
import platform.client.descriptor.FormDescriptor;
import platform.client.descriptor.GroupObjectDescriptor;
import platform.client.descriptor.PropertyDrawDescriptor;
import platform.client.descriptor.editor.base.TitledPanel;
import platform.client.descriptor.increment.IncrementDependency;
import platform.client.descriptor.increment.IncrementView;
import platform.client.descriptor.increment.editor.IncrementMultipleListEditor;
import platform.client.descriptor.increment.editor.IncrementMultipleListSelectionModel;
import platform.client.descriptor.increment.editor.IncrementSingleListSelectionModel;
import platform.client.logics.ClientPropertyDraw;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

public class DefaultOrdersEditor extends JPanel implements IncrementView {
    private static final ImageIcon upIcon = new ImageIcon(Main.class.getResource("/platform/client/form/images/arrowup.gif"));
    private static final ImageIcon downIcon = new ImageIcon(Main.class.getResource("/platform/client/form/images/arrowdown.gif"));

    private final FormDescriptor form;
    private DataHolder dataHolder;
    private IncrementDefaultOrdersTable table;

    public DefaultOrdersEditor(FormDescriptor iForm, final GroupObjectDescriptor group) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.form = iForm;

        dataHolder = new DataHolder(form);
        if (group != null) {
            dataHolder.setGroupObject(group);
        }

        IncrementDependency.add(dataHolder, "defaultOrders", this);

        if (group == null) {
            add(new TitledPanel("Группа", new JComboBox(new IncrementSingleListSelectionModel(dataHolder, "groupObject") {

                @Override
                public boolean allowNulls() {
                    return true;
                }

                public List<?> getSingleList() {
                    return form.groupObjects;
                }

                public void fillListDependencies() {
                    IncrementDependency.add(form, "groupObjects", this);
                }
            })));
        }

        JPanel propertiesPanel = new TitledPanel("Свойства для выбора", new JScrollPane(new IncrementMultipleListEditor(new IncrementMultipleListSelectionModel(dataHolder, "pendingProperties") {
            public List<?> getList() {
                return dataHolder.getAvailableProperties();
            }

            public void fillListDependencies() {
                IncrementDependency.add(form, "propertyDraws", this);
                IncrementDependency.add(dataHolder, "defaultOrders", this);
                IncrementDependency.add(dataHolder, "groupObject", this);
            }
        })));

        JButton addBtn = new JButton(new AbstractAction("Добавить") {
            public void actionPerformed(ActionEvent e) {
                dataHolder.addToDefaultOrders(dataHolder.getPendingProperties());
                dataHolder.clearPendingProperties();
            }
        });
        addBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton delBtn = new JButton(new AbstractAction("Удалить") {
            public void actionPerformed(ActionEvent e) {
                dataHolder.removeFromDefaultOrders(table.getSelectedProperties());
            }
        });
        delBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton upBtn = new JButton(new AbstractAction("", upIcon) {
            public void actionPerformed(ActionEvent e) {
                table.moveProperties(true);
            }
        });
        upBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton downBtn = new JButton(new AbstractAction("", downIcon) {
            public void actionPerformed(ActionEvent e) {
                table.moveProperties(false);
            }
        });
        downBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel tablePanel = new TitledPanel("Выбранные свойства", new JScrollPane(table = new IncrementDefaultOrdersTable()));
        tablePanel.setPreferredSize(new Dimension(300, 400));

        JPanel selectPanel = new JPanel();
        selectPanel.setLayout(new BoxLayout(selectPanel, BoxLayout.PAGE_AXIS));
        selectPanel.add(addBtn);
        selectPanel.add(Box.createRigidArea(new Dimension(5, 5)));
        selectPanel.add(delBtn);

        JPanel movePanel = new JPanel();
        movePanel.setLayout(new BoxLayout(movePanel, BoxLayout.PAGE_AXIS));
        movePanel.add(upBtn);
        movePanel.add(Box.createRigidArea(new Dimension(5, 15)));
        movePanel.add(downBtn);

        JPanel newPanel = new JPanel();
        newPanel.setLayout(new BoxLayout(newPanel, BoxLayout.X_AXIS));
        newPanel.add(propertiesPanel);
        newPanel.add(Box.createRigidArea(new Dimension(5, 5)));
        newPanel.add(selectPanel);
        newPanel.add(Box.createRigidArea(new Dimension(5, 5)));
        newPanel.add(tablePanel);
        newPanel.add(Box.createRigidArea(new Dimension(5, 5)));
        newPanel.add(movePanel);
        newPanel.add(Box.createRigidArea(new Dimension(5, 5)));

        add(newPanel);
    }

    public void update(Object updateObject, String updateField) {
        OrderedMap<ClientPropertyDraw, Boolean> newOrders = new OrderedMap<ClientPropertyDraw, Boolean>();
        for (PropertyDrawDescriptor propertyDraw : dataHolder.defaultOrdersProps) {
            newOrders.put(propertyDraw.client, dataHolder.defaultOrders.get(propertyDraw));
        }
        form.client.setDefaultOrders(newOrders);
    }

    public class IncrementDefaultOrdersTable extends JTable {
        private final String columnNames[] = new String[]{"Свойство", "По возрастанию"};

        public IncrementDefaultOrdersTable() {
            super();
            setModel(new OrdersModel());
            getColumnModel().getColumn(0).setPreferredWidth(50000);
            getColumnModel().getColumn(1).setPreferredWidth(25000);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(50, super.getPreferredSize().height);
        }

        public List<PropertyDrawDescriptor> getSelectedProperties() {
            List<PropertyDrawDescriptor> result = new ArrayList<PropertyDrawDescriptor>();
            for (int rowIndex : getSelectedRows()) {
                result.add(dataHolder.getSelectedProperties().get(rowIndex));
            }
            return result;
        }

        public void moveProperties(boolean up) {
            int[] indices = getSelectedRows();
            int[] newIndices = dataHolder.moveProperties(indices, up);

            for (int ind : newIndices) {
                getSelectionModel().addSelectionInterval(ind, ind);
            }
        }

        private class OrdersModel extends AbstractTableModel implements IncrementView {
            public OrdersModel() {
                IncrementDependency.add(dataHolder, "groupObject", this);
                IncrementDependency.add(dataHolder, "defaultOrders", this);
            }

            public void update(Object updateObject, String updateField) {
                fireTableDataChanged();
            }

            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return columnIndex != 0;
            }

            public int getRowCount() {
                return dataHolder.getSelectedProperties().size();
            }

            public int getColumnCount() {
                return columnNames.length;
            }

            @Override
            public String getColumnName(int column) {
                return columnNames[column];
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 1) {
                    return Boolean.class;
                }
                return Object.class;
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                PropertyDrawDescriptor propertyDraw = dataHolder.getSelectedProperties().get(rowIndex);
                switch (columnIndex) {
                    case 0:
                        return propertyDraw;
                    case 1:
                        return dataHolder.defaultOrders.get(propertyDraw);
                }
                return null;
            }

            @Override
            public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
                if (columnIndex == 1 && aValue instanceof Boolean) {
                    boolean direction = (Boolean) aValue;
                    dataHolder.setOrderDirection(dataHolder.getSelectedProperties().get(rowIndex), direction);
                }
            }
        }
    }

    public static class DataHolder {
        private final FormDescriptor form;

        private GroupObjectDescriptor groupObject;

        private List<PropertyDrawDescriptor> pendingProperties = new ArrayList<PropertyDrawDescriptor>();

        public Map<PropertyDrawDescriptor, Boolean> defaultOrders = new HashMap<PropertyDrawDescriptor, Boolean>();
        public List<PropertyDrawDescriptor> defaultOrdersProps = new ArrayList<PropertyDrawDescriptor>();

        public DataHolder(FormDescriptor form) {
            this.form = form;
            for (Map.Entry<ClientPropertyDraw, Boolean> entry : form.client.defaultOrders.entrySet()) {
                for (PropertyDrawDescriptor propertyDraw : form.propertyDraws) {
                    if (propertyDraw.client == entry.getKey()) {
                        defaultOrders.put(propertyDraw, entry.getValue());
                        defaultOrdersProps.add(propertyDraw);
                        break;
                    }
                }
            }
            IncrementDependency.update(this, "defaultOrders");
        }

        public void setGroupObject(GroupObjectDescriptor groupObject) {
            this.groupObject = groupObject;
            IncrementDependency.update(this, "groupObject");
        }

        public GroupObjectDescriptor getGroupObject() {
            return groupObject;
        }

        public void setPendingProperties(List<PropertyDrawDescriptor> pendingProperties) {
            this.pendingProperties = pendingProperties;
            IncrementDependency.update(this, "pendingProperties");
        }

        public List<PropertyDrawDescriptor> getPendingProperties() {
            return pendingProperties;
        }

        public void clearPendingProperties() {
            pendingProperties.clear();
            IncrementDependency.update(this, "pendingProperties");
        }

        private void addToDefaultOrders(List<PropertyDrawDescriptor> newProperties) {
            for (PropertyDrawDescriptor property : newProperties) {
                defaultOrders.put(property, false);
                defaultOrdersProps.remove(property);
                defaultOrdersProps.add(property);
            }

            IncrementDependency.update(this, "defaultOrders");
        }

        private void removeFromDefaultOrders(List<PropertyDrawDescriptor> properties) {
            defaultOrdersProps.removeAll(properties);
            for (PropertyDrawDescriptor property : properties) {
                defaultOrders.remove(property);
            }

            IncrementDependency.update(this, "defaultOrders");
        }

        public List<PropertyDrawDescriptor> getSelectedProperties() {
            return BaseUtils.filterList(defaultOrdersProps, form.getGroupPropertyDraws(groupObject));
        }

        public List<PropertyDrawDescriptor> getAvailableProperties() {
            return BaseUtils.removeList(form.getGroupPropertyDraws(groupObject), defaultOrdersProps);
        }

        public int[] moveProperties(int[] indices, boolean up) {
            Arrays.sort(indices);
            int[] newIndices = new int[indices.length];

            int begi = up ? 0 : indices.length - 1;
            int endi = up ? indices.length - 1 : 0;
            int di = up ? +1 : -1;
            int firstIndex = up ? 0 : defaultOrdersProps.size() - 1;

            while (begi != endi + di && indices[begi] == firstIndex) {
                newIndices[begi] = indices[begi];
                begi += di;
                firstIndex += di;
            }

            for (int i = begi; i != endi + di; i += di) {
                int index = indices[i];

                PropertyDrawDescriptor property = defaultOrdersProps.get(index);
                defaultOrdersProps.remove(index);
                defaultOrdersProps.add(index - di, property);

                newIndices[i] = index - di;
            }
            IncrementDependency.update(this, "defaultOrders");

            return newIndices;
        }

        public void setOrderDirection(PropertyDrawDescriptor property, boolean isAscending) {
            defaultOrders.put(property, isAscending);
            IncrementDependency.update(this, "defaultOrders");
        }
    }
}
