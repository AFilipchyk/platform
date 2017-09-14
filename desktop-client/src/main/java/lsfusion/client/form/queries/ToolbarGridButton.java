package lsfusion.client.form.queries;

import lsfusion.client.FlatRolloverButton;

import javax.swing.*;
import java.awt.*;

public class ToolbarGridButton extends FlatRolloverButton {
    public final static Dimension DEFAULT_SIZE = new Dimension(20, 20);

    public ToolbarGridButton(String iconPath, String toolTipText) {
        this(new ImageIcon(ToolbarGridButton.class.getResource(iconPath)), toolTipText);
    }

    public ToolbarGridButton(Icon icon, String toolTipText) {
        this(icon, toolTipText, DEFAULT_SIZE);

    }
    public ToolbarGridButton(Icon icon, String toolTipText, Dimension buttonSize) {
        super();
        setIcon(icon);
        setAlignmentY(Component.TOP_ALIGNMENT);
        setMinimumSize(buttonSize);
        setPreferredSize(buttonSize);
        setMaximumSize(buttonSize);
        setFocusable(false);
        if (toolTipText != null) {
            setToolTipText(toolTipText);
        }
        addListener();
    }

    public void addListener() {

    }
}
