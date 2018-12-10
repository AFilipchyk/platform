package lsfusion.gwt.client.grid.renderer;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.ImageElement;
import com.google.gwt.dom.client.Style;
import lsfusion.gwt.client.base.GwtClientUtils;
import lsfusion.gwt.client.cellview.DataGrid;
import lsfusion.gwt.client.cellview.cell.Cell;
import lsfusion.gwt.client.form.form.ui.GGridPropertyTable;
import lsfusion.gwt.shared.view.GPropertyDraw;

import static lsfusion.gwt.client.grid.renderer.FileGridCellRenderer.ICON_EMPTY;

public class ImageGridCellRenderer extends AbstractGridCellRenderer {
    protected GPropertyDraw property;
    
    public ImageGridCellRenderer(GPropertyDraw property) {
        this.property = property;
    }

    @Override
    public void renderDom(Cell.Context context, DataGrid table, DivElement cellElement, Object value) {
        if (table instanceof GGridPropertyTable) {
            cellElement.getStyle().setPosition(Style.Position.RELATIVE);
        }
        
        updateDom(cellElement, table, context, value);
    }

    @Override
    public void updateDom(DivElement cellElement, DataGrid table, Cell.Context context, Object value) {     
        cellElement.removeAllChildren();
        cellElement.setInnerText(null);
        
        if (value == null && property.isEditableNotNull()) {
            cellElement.setInnerText(REQUIRED_VALUE);
            cellElement.setTitle(REQUIRED_VALUE);
            cellElement.addClassName("requiredValueString");
        } else {
            cellElement.removeClassName("requiredValueString");
            cellElement.setInnerText(null);
            cellElement.setTitle("");

            ImageElement img = cellElement.appendChild(Document.get().createImageElement());
            
            Style imgStyle = img.getStyle();
            imgStyle.setVerticalAlign(Style.VerticalAlign.MIDDLE);
            imgStyle.setProperty("maxWidth", "100%");
            imgStyle.setProperty("maxHeight", "100%");
            imgStyle.setProperty("margin", "auto");

            imgStyle.setPosition(Style.Position.ABSOLUTE);
            imgStyle.setTop(0, Style.Unit.PX);
            imgStyle.setLeft(0, Style.Unit.PX);
            imgStyle.setBottom(0, Style.Unit.PX);
            imgStyle.setRight(0, Style.Unit.PX);

            setImageSrc(img, value);
        }
    }

    protected void setImageSrc(ImageElement img, Object value) {
        if (value instanceof String) {
            img.setSrc(imageSrc(value));
        } else {
            img.setSrc(GWT.getModuleBaseURL() + "images/" + ICON_EMPTY);
        }
    }

    private String imageSrc(Object value) {
        return GwtClientUtils.getWebAppBaseURL() + "propertyImage?sid=" + value;
    }
}
