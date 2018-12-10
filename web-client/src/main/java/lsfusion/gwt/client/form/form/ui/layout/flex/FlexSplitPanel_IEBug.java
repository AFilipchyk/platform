package lsfusion.gwt.client.form.form.ui.layout.flex;

import com.google.gwt.user.client.ui.Widget;
import lsfusion.gwt.client.base.ui.FlexPanel;
import lsfusion.gwt.client.base.ui.GFlexAlignment;
import lsfusion.gwt.client.form.form.ui.layout.SplitPanelBase;
import lsfusion.gwt.shared.form.view.GComponent;

// почему то это решение приводит к странному багу в IE при ресайзе сплита
// дерево компонентов, начиная с формы, на мгновение рендерится в (0, 0), а затем возращается в правильное состояние - что приводит к миганию...
public class FlexSplitPanel_IEBug extends SplitPanelBase<FlexPanel> {

    public FlexSplitPanel_IEBug(boolean vertical) {
        super(vertical, new FlexPanel(vertical));
    }

    @Override
    protected void addSplitterImpl(Splitter splitter) {
        panel.add(splitter, GFlexAlignment.STRETCH);
    }

    @Override
    protected void addFirstWidgetImpl(GComponent child, Widget widget) {
        panel.add(firstWidget, 0, GFlexAlignment.STRETCH, flex1, 0);
    }

    @Override
    protected void addSecondWidgetImpl(GComponent child, Widget secondWidget) {
        int index = firstWidget == null ? 1 : 2;
        panel.add(secondWidget, index, GFlexAlignment.STRETCH, flex2, 0);
    }

    @Override
    protected void setSplitSize(double ratio, double flexSum, boolean recheck) {
        double f1 = flexSum * ratio;
        if (firstWidget != null) {
            panel.setChildFlex(firstWidget, f1, 0);
        }
        if (secondWidget != null) {
            double f2 = flexSum - f1;
            panel.setChildFlex(secondWidget, f2, 0);
        }
    }
}
