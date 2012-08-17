package platform.gwt.form2.shared.view.grid.editor;

import platform.gwt.form2.shared.view.grid.EditManager;

public class IntegerGridEditor extends TextFieldGridEditor {
    public IntegerGridEditor(EditManager editManager, Object oldValue) {
        super(editManager, oldValue);
    }

    @Override
    protected Object tryParseInputText(String inputText) throws ParseException {
        try {
            return inputText.isEmpty() ? null : Integer.parseInt(inputText);
        } catch (NumberFormatException e) {
            throw new ParseException();
        }
    }
}
