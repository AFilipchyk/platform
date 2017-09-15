package lsfusion.client.form.cell;

import com.google.common.base.Throwables;
import lsfusion.client.SwingUtils;
import lsfusion.client.form.ClientFormController;
import lsfusion.client.form.ClientPropertyTable;
import lsfusion.client.logics.ClientGroupObjectValue;
import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.client.logics.classes.ClientStringClass;

import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static lsfusion.client.form.ClientFormController.PasteData;

public abstract class SingleCellTable extends ClientPropertyTable {

    private final SingleCellTableModel model;

    public SingleCellTable(ClientGroupObjectValue columnKey, ClientFormController form) {
        super(new SingleCellTableModel(columnKey), form);

        model = (SingleCellTableModel) getModel();

        SwingUtils.setupSingleCellTable(this);
    }

    public void setProperty(ClientPropertyDraw property) {
        setName(property.getCaption());
        model.setProperty(property);

        setPreferredSize(new Dimension(property.getBaseValueWidth(this), property.getPreferredValueHeight(this)));
    }

    public void setValue(Object value) {
        model.setValue(value);
        if(getProperty().autoSize && value instanceof String) {
            Dimension size = getSize();
            if (size != null && size.getWidth() > 0) {
                setPreferredSize(new Dimension((int) getPreferredSize().getWidth(), getHeight((String) value, (int) size.getWidth())));
                revalidate();
            }
        }
        repaint();
    }

    private int getHeight(String text, int maxWidth) {
        int rows = 0;
        FontMetrics fm = getFontMetrics(getFont());
        if (text != null) {
            String[] lines = text.split("\n");
            rows += lines.length;
            for(String line : lines) {
                String[] splittedText = line.split(" ");
                String output = "";
                int outputWidth = 0;
                int spaceWidth = fm.charWidth(' ');
                int wordWidth;
                int j = 1;

                for (String word : splittedText) {
                    wordWidth = 0;
                    for (int i = 0; i < word.length(); i++)
                        wordWidth += fm.charWidth(word.charAt(i));
                    if ((outputWidth + spaceWidth + wordWidth) < maxWidth) {
                        output = output.concat(" ").concat(word);
                        outputWidth += spaceWidth + wordWidth;
                    } else {
                        rows++;
                        output = word;
                        outputWidth = wordWidth;
                        j = j + 1;
                    }
                }
            }
        }
        return (rows + 1) * fm.getHeight();
    }

    public void setReadOnly(boolean readOnly) {
        model.setReadOnly(readOnly);
    }

    @Override
    public int getCurrentRow() {
        return 0;
    }

    public ClientGroupObjectValue getColumnKey(int row, int col) {
        return model.getColumnKey();
    }

    public ClientPropertyDraw getProperty() {
        return model.getProperty();
    }

    public ClientPropertyDraw getProperty(int row, int column) {
        return model.getProperty();
    }

    @Override
    public boolean richTextSelected() {
        ClientPropertyDraw property = getProperty();
        return property.baseType instanceof ClientStringClass && ((ClientStringClass) property.baseType).rich;
    }

    public void pasteTable(List<List<String>> table) {
        if (!table.isEmpty() && !table.get(0).isEmpty()) {
            try {
                ClientPropertyDraw property = model.getProperty();
                String value = table.get(0).get(0);
                Object newValue = value == null ? null : property.parseChangeValueOrNull(value);
                if (property.canUsePasteValueForRendering()) {
                    setValue(newValue);
                }

                getForm().pasteMulticellValue(
                        singletonMap(property, new PasteData(newValue, singletonList(model.getColumnKey()), singletonList(model.getValue())))
                );
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }
    }

    public boolean isSelected(int row, int column) {
        return false;
    }

    // приходится делать вот таким извращенным способом, поскольку ComponentListener срабатывает после перерисовки формы
    @Override
    public void setBounds(int x, int y, int width, int height) {
        rowHeight = height;
        super.setBounds(x, y, width, height);
    }

    public abstract ClientFormController getForm();

    @Override
    public Component prepareEditor(TableCellEditor editor, int row, int column) {
        Component editorComp = super.prepareEditor(editor, row, column);
        if (editorComp != null) {
            //вырезаем traversal-кнопки, потому что иначе фокус просто вернётся в таблицу
            editorComp.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, new HashSet<AWTKeyStroke>());
            editorComp.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, new HashSet<AWTKeyStroke>());
        }
        return editorComp;
    }
}
