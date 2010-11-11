package platform.interop;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;

public class ComponentDesign implements Serializable {

    public Font font;
    public Font getFont(JComponent comp) {
        return (font == null ? comp.getFont() : font);
    }

    public Font headerFont;

    public Color background;
    public Color foreground;

    public void designCell(JComponent comp) {
        designComponent(comp, Color.white); // а то по умолчанию background у Label - серый
    }

    public void designComponent(JComponent comp) {
        designComponent(comp, null);
    }

    public void designComponent(JComponent comp, Color defaultBackground) {
        
        if (font != null) {
            comp.setFont(font);
        }

        if (background != null) {
            comp.setBackground(background);
            comp.setOpaque(true);
        }
        else if (defaultBackground != null)
            comp.setBackground(defaultBackground);

        if (foreground != null)
            comp.setForeground(foreground);
    }

    public void designHeader(Component comp) {
        if (headerFont != null)
            comp.setFont(headerFont);
    }
}
