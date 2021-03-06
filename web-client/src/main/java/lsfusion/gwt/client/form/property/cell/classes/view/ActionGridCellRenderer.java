package lsfusion.gwt.client.form.property.cell.classes.view;

import com.google.gwt.dom.client.*;
import lsfusion.gwt.client.base.GwtClientUtils;
import lsfusion.gwt.client.base.ImageDescription;
import lsfusion.gwt.client.base.view.grid.DataGrid;
import lsfusion.gwt.client.base.view.grid.cell.Cell;
import lsfusion.gwt.client.form.design.GFont;
import lsfusion.gwt.client.form.object.table.view.GGridPropertyTable;
import lsfusion.gwt.client.form.property.GPropertyDraw;
import lsfusion.gwt.client.form.property.cell.view.AbstractGridCellRenderer;

public class ActionGridCellRenderer extends AbstractGridCellRenderer {
    public ActionGridCellRenderer(GPropertyDraw property) {
        this.property = property;
    }

    private GPropertyDraw property;

    @Override
    public void renderDom(Cell.Context context, DataGrid table, DivElement cellElement, Object value) {
        Style divStyle = cellElement.getStyle();
        cellElement.addClassName("gwt-Button");
        divStyle.setWidth(100, Style.Unit.PCT);
        divStyle.setPadding(0, Style.Unit.PX);

        // избавляемся от двух пикселов, добавляемых к 100%-й высоте рамкой
        cellElement.addClassName("boxSized");

        DivElement innerTop = cellElement.appendChild(Document.get().createDivElement());
        innerTop.getStyle().setHeight(50, Style.Unit.PCT);
        innerTop.getStyle().setPosition(Style.Position.RELATIVE);
        innerTop.setAttribute("align", "center");

        DivElement innerBottom = cellElement.appendChild(Document.get().createDivElement());
        innerBottom.getStyle().setHeight(50, Style.Unit.PCT);

        if (property.getImage() != null) {
            ImageElement img = innerTop.appendChild(Document.get().createImageElement());
            img.getStyle().setPosition(Style.Position.ABSOLUTE);
            img.getStyle().setLeft(50, Style.Unit.PCT);
            setImage(img, value);
        } else {
            LabelElement label = innerTop.appendChild(Document.get().createLabelElement());

            GFont font = property.font;
            if (font == null && table instanceof GGridPropertyTable) {
                font = ((GGridPropertyTable) table).font;
            }
            if (font != null) {
                font.apply(label.getStyle());
            }
            
            label.setInnerText("...");
        }
    }

    @Override
    public void updateDom(DivElement cellElement, DataGrid table, Cell.Context context, Object value) {
        if (property.getImage() == null) {
            LabelElement label = cellElement.getFirstChild().getFirstChild().cast();
            GFont font = property.font;
            if (font == null && table instanceof GGridPropertyTable) {
                font = ((GGridPropertyTable) table).font;
            }
            if (font != null) {
                font.apply(label.getStyle());
            }   
        } else {
            ImageElement img = cellElement
                    .getFirstChild()
                    .getFirstChild().cast();
            setImage(img, value);
        }
    }

    private void setImage(ImageElement img, Object value) {
        boolean enabled = value != null && (Boolean) value;
        ImageDescription image = property.getImage(enabled);
        if (image != null) {
            img.setSrc(GwtClientUtils.getAppImagePath(image.url));

            int height = image.height;
            if (height != -1) {
                img.setHeight(height);
                img.getStyle().setBottom(-(double) height / 2, Style.Unit.PX);
            }
            if (image.width != -1) {
                img.setWidth(image.width);
                img.getStyle().setMarginLeft(-(double) image.width / 2, Style.Unit.PX);
            }
        }
    }
}
