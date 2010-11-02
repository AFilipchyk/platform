package platform.client.descriptor.editor;

import platform.base.BaseUtils;
import platform.client.descriptor.editor.base.FlatButton;
import platform.client.descriptor.editor.base.NamedContainer;
import platform.client.descriptor.increment.IncrementDependency;
import platform.client.descriptor.increment.IncrementView;
import platform.client.form.classes.ClassDialog;
import platform.client.logics.classes.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ValueClassEditor extends JPanel {

    final JComboBox typeClasses;

    private abstract class Numeric extends JPanel implements ActionListener {

        final JTextField fieldLength;
        final JTextField precisionLength;

        private Numeric(int length, int precision) {
            this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

            fieldLength = new JTextField();
            add(new NamedContainer("Ширина", false, fieldLength));
            fieldLength.setText(Integer.toString(length));

            add(Box.createRigidArea(new Dimension(5, 5)));

            precisionLength = new JTextField();
            add(new NamedContainer("После запятой", false, precisionLength));
            precisionLength.setText(Integer.toString(precision));
        }
    }

    JComponent currentComponent;
    boolean updated;

    private final IncrementView typeVisible;

    public ValueClassEditor(final Object object, final String property) {
        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        typeClasses = new JComboBox();
        for(ClientTypeClass typeClass : ClientClass.getEnumTypeClasses())
            typeClasses.addItem(typeClass);
        typeClasses.setSelectedItem(null);

        typeClasses.addActionListener(new ActionListener() {
            
            public void actionPerformed(ActionEvent e) {
                if(!updated)
                    BaseUtils.invokeCheckSetter(object, property, ((ClientTypeClass) typeClasses.getSelectedItem()).getDefaultClass());
            }
        });

        add(new NamedContainer("Тип", false, typeClasses));
        
        add(Box.createRigidArea(new Dimension(5, 5)));

        typeVisible = new IncrementView() {
            public void update(Object updateObject, String updateField) {
                final ClientClass clientClass = (ClientClass) BaseUtils.invokeGetter(object, property);

                if(clientClass == null) {
//                    BaseUtils.invokeCheckSetter(object, property, ClientIntegerClass.instance);
                    return;
                }

                ClientTypeClass typeClass = clientClass.getType().getTypeClass();

                updated = true;
                typeClasses.setSelectedItem(typeClass);
                updated = false;

                // remove'аем старый компонент,
                if(currentComponent !=null) {
                    remove(currentComponent);
                    currentComponent = null;
                }

                // полиморфно для TypeClass'а, добавляем новый компонент, заполняем его текущими значения
                if(typeClass.equals(ClientStringClass.type)) {
                    final JTextField fieldLength = new JTextField();
                    fieldLength.setText(Integer.toString(((ClientStringClass) clientClass).length));
                    fieldLength.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            BaseUtils.invokeCheckSetter(object, property, new ClientStringClass(Integer.parseInt(fieldLength.getText())));
                        }
                    });
                    currentComponent = new NamedContainer("Длина", false, fieldLength);
                } else
                if(typeClass.equals(ClientNumericClass.type)) {
                    currentComponent = new Numeric(((ClientNumericClass) clientClass).length, ((ClientNumericClass) clientClass).precision) {
                        public void actionPerformed(ActionEvent e) {
                            BaseUtils.invokeCheckSetter(object, property, new ClientNumericClass(Integer.parseInt(fieldLength.getText()), Integer.parseInt(precisionLength.getText())));
                        }
                    };
                } else
                if(typeClass.equals(ClientObjectClass.type)) {
                    currentComponent = new NamedContainer("Класс", false, new FlatButton(clientClass.toString()) {
                        protected void onClick() {
                            ClientObjectClass selectedClass = ClassDialog.dialogObjectClass(this, ClientObjectType.baseClass, (ClientObjectClass) clientClass, false);
                            if(selectedClass!=null)
                                BaseUtils.invokeCheckSetter(object, property, selectedClass);
                        }
                    });
                }

                if(currentComponent!=null)
                    add(currentComponent);

                validate();
            }
        };
        IncrementDependency.add(object, property, typeVisible);
    }
}
