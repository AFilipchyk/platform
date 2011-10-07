package platform.client.form.renderer;

import platform.client.form.PropertyRendererComponent;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class ActionPropertyRenderer extends JButton
        implements PropertyRendererComponent {

    private static final String defaultCaption = "...";

    private final Color defaultBackground = getBackground();
    private Border defaultBorder = getBorder();
    private Icon defaultIcon;

    public ActionPropertyRenderer() {
    }

    public JComponent getComponent() {
        return this;
    }

    public void setValue(Object value, boolean isSelected, boolean hasFocus) {
        if (defaultIcon == null && getIcon() != null) defaultIcon = getIcon(); // временно так

        setText(defaultIcon != null || value == null ? "" : defaultCaption);
        setIcon(value == null ? null : defaultIcon);

        if (isSelected) {
            if (hasFocus) {
                setBorder(FOCUSED_CELL_BORDER);
                setBackground(FOCUSED_CELL_BACKGROUND);
            }
            else {
                setBorder(SELECTED_ROW_BORDER);
                setBackground(SELECTED_ROW_BACKGROUND);
            }
        } else {
            setBorder(value == null ? BorderFactory.createEmptyBorder() : defaultBorder);
            setBackground(value == null ? Color.white : defaultBackground);
        }
    }

    @Override
    public void rateSelected() {
        setBackground(PropertyRendererComponent.SELECTED_CELL_BACKGROUND);
    }
}
