package platform.client.form.grid;

import platform.client.form.ClientFormController;

import javax.swing.*;
import java.awt.*;

public class GridView extends JPanel {
    final JScrollPane pane;

    private final GridTable gridTable;
    private final GridController gridController;

    public GridView(GridController igridController, ClientFormController form, boolean tabVertical, boolean verticalScroll) {
        setLayout(new BorderLayout());

        gridController = igridController;

        gridTable = new GridTable(this, form);

        gridTable.setTabVertical(tabVertical);

        pane = new JScrollPane(gridTable) {
            @Override
            public void doLayout() {
                // хак, чтобы не изменялся ряд при изменении размеров таблицы,
                // а вместо этого она скроллировалась к видимой строчке
                gridTable.setLayouting(true);
                super.doLayout();
                gridTable.setLayouting(false);
            }
        };
        int verticalConst = verticalScroll ? ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED : ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER;
        pane.setVerticalScrollBarPolicy(verticalConst);
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        gridTable.setFillsViewportHeight(true);

        gridTable.configureWheelScrolling(pane);

        add(pane, BorderLayout.CENTER);
        add(igridController.getGroupController().getToolbarView(), BorderLayout.SOUTH);
    }

    public GridController getGridController() {
        return gridController;
    }

    public GridTable getTable() {
        return gridTable;
    }
}
