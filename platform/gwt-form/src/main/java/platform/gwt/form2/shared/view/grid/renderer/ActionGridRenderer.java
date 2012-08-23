package platform.gwt.form2.shared.view.grid.renderer;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.safehtml.shared.UriUtils;
import platform.gwt.form2.shared.view.GPropertyDraw;

public class ActionGridRenderer implements GridCellRenderer {
    interface Template extends SafeHtmlTemplates {
        @Template("<div><center><div style=\"border: 1px solid blue; width: 48px; height: 16px;\"><center><img src=\"{0}\"/><center></div></center></div>")
        SafeHtml img(SafeUri url);

        @Template("<div style=\"border: 1px solid blue; width: 48px; height: 16px;\"><center>{0}<center></div>")
        SafeHtml div(String text);
    }

    private static Template template;

    public ActionGridRenderer(GPropertyDraw property) {
        if (template == null) {
            template = GWT.create(Template.class);
        }
        this.property = property;
    }

    private GPropertyDraw property;

    @Override
    public void render(Cell.Context context, Object value, SafeHtmlBuilder sb) {
        boolean disabled = value == null || !(Boolean) value;
        String iconPath = property.getIconPath(disabled);
        if (iconPath != null) {
            sb.append(template.img(UriUtils.fromString(GWT.getModuleBaseURL() + "images/" + iconPath)));
        } else {
            sb.append(template.div("..."));
        }
    }
}
