package platform.client.form;

import platform.client.logics.ClientComponent;
import platform.client.logics.ClientContainer;
import platform.interop.form.layout.InsetTabbedPane;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;

import static platform.client.ClientResourceBundle.getString;

public class ClientFormTabbedPane extends JTabbedPane implements AutoHideableContainer, InsetTabbedPane {

    LayoutManager2 layout;

    private int minHeight;

    private Dimension tabInsets;

    private ClientComponent selectedTab;

    public ClientFormTabbedPane(final ClientContainer key, final ClientFormController form, LayoutManager2 layout) {

        this.layout = layout;

        key.design.designComponent(this);

        // таким вот волшебным способом рассчитывается минимальная высота и отступы использующиеся при отрисовке Tab'ов
        // добавляется тестовый компонент, считаются размеры и все удаляются
        // иначе делать очень тяжело
        Container testContainer = new Container();
        addTab("", testContainer);

        Dimension minSize = getMinimumSize();
        Dimension contSize = testContainer.getMinimumSize();
        minHeight = minSize.height;

        tabInsets = new Dimension(4, minSize.height - contSize.height);

        removeAll();

        if(key.children.size() > 0)
            selectedTab = key.children.iterator().next();

        addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int selected = getSelectedIndex();
                int visible = 0;
                for(ClientComponent child : key.children)
                    if(addedComponents.containsValue(child)) {
                        if(visible++==selected) {
                            if(child!=selectedTab) { // вообще changeListener может вызваться при инициализации, но это проверка в том числе позволяет suppres'ить этот случай
                                try {
                                    form.setTabVisible(key, child);
                                    selectedTab = child;
                                } catch (IOException ex) {
                                    throw new RuntimeException(getString("errors.error.changing.sorting"), ex);
                                }
                            }
                            return;
                        }
                    }
            }
        });
    }

    private Map<Component, Object> addedComponents = new HashMap<Component, Object>();

    @Override
    public void add(Component component, Object constraints) {
        SimplexLayout.showHideableContainers(this);

        layout.addLayoutComponent(component, constraints);
        addedComponents.put(component, constraints); // важно чтобы до, так как listener'у нужно найти компоненту чтобы послать notification на сервер
        addTab(component, constraints);

        adjustMinimumSize();
    }

    @Override
    public void remove(Component component) {
        addedComponents.remove(component);
        super.remove(component);
        layout.removeLayoutComponent(component);
        adjustMinimumSize();
    }

    public void hide(Component component) {
        super.remove(component);
        adjustMinimumSize();
    }

    private void adjustMinimumSize() {
        setMinimumSize(null);
        Dimension minimumSize = getMinimumSize();
        minimumSize.height = minHeight;
        setMinimumSize(minimumSize);
    }

    public void showAllComponents() {
        for (Map.Entry<Component,Object> comp : addedComponents.entrySet())
            if (indexOfComponent(comp.getKey()) == -1)
                addTab(comp.getKey(), comp.getValue());
    }

    private void addTab(Component comp, Object constraints) {

        // вставляем Tab в то место, в котором он идет в container.children
        if (constraints instanceof ClientComponent && ((ClientComponent)constraints).container != null) {
            ClientComponent clientComp = (ClientComponent)constraints;
            ClientContainer clientCont = clientComp.container;

            int tabCount = getTabCount();
            int index;
            for (index = 0; index < tabCount; index++) {
                Component tabComp = getComponentAt(index);
                if (addedComponents.get(tabComp) instanceof ClientComponent) {
                    ClientComponent curComp = (ClientComponent)addedComponents.get(tabComp);
                    if (clientCont.equals(curComp.container) && clientCont.children.indexOf(curComp) > clientCont.children.indexOf(clientComp))
                        break;
                }
            }

            insertTab(clientComp.getCaption(), null, comp, null, index);
        } else
            add("", comp);
    }

    public Dimension getTabInsets() {
        return tabInsets;
    }
}