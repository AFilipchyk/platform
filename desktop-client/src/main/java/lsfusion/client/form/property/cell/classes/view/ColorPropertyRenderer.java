package lsfusion.client.form.property.cell.classes.view;

import lsfusion.client.classes.data.ClientColorClass;
import lsfusion.client.form.property.ClientPropertyDraw;
import lsfusion.client.form.property.cell.view.LabelPropertyRenderer;

import java.awt.*;

import static lsfusion.client.form.controller.ClientFormController.colorPreferences;

public class ColorPropertyRenderer extends LabelPropertyRenderer {
    Color value;
    ClientPropertyDraw property;
    public ColorPropertyRenderer(ClientPropertyDraw property) {
        super(property);
        this.property = property;
    }

    @Override
    public void setValue(Object value) {
        this.value = value == null ? ClientColorClass.getDefaultValue() : (Color) value;
        getComponent().setBackground(this.value);
    }

    @Override
    public void drawBackground(boolean isInFocusedRow, boolean hasFocus, Color conditionalBackground) {
        if (hasFocus) {
            getComponent().setBackground(new Color(value.getRGB() & colorPreferences.getFocusedCellBackground().getRGB()));
        }
    }

    @Override
    protected void paintAsSelected() {
        getComponent().setBackground(new Color(value.getRGB() & colorPreferences.getSelectedCellBackground().getRGB()));
    }
}
