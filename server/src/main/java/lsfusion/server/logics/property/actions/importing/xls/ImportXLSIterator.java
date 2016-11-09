package lsfusion.server.logics.property.actions.importing.xls;

import com.google.common.base.Throwables;
import lsfusion.server.classes.DateClass;
import lsfusion.server.classes.DateTimeClass;
import lsfusion.server.classes.TimeClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassType;
import lsfusion.server.logics.property.actions.importing.ImportIterator;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class ImportXLSIterator extends ImportIterator {
    private final List<Integer> columns;
    private final List<LCP> properties;
    private int current;
    private HSSFSheet sheet;
    private int lastRow;
    
    public ImportXLSIterator(byte[] file, List<Integer> columns, List<LCP> properties, Integer sheetIndex) throws IOException {
        this.columns = columns;
        this.properties = properties;

        HSSFWorkbook wb = new HSSFWorkbook(new ByteArrayInputStream(file));
        
        sheet = wb.getSheetAt(sheetIndex == null ? 0 : (sheetIndex - 1));
        lastRow = sheet.getLastRowNum();
        lastRow = lastRow == 0 && sheet.getRow(0) == null ? 0 : (lastRow + 1);
    }

    @Override
    public List<String> nextRow(List<List<String>> wheresList) {
        List<String> listRow = new ArrayList<>();
        try {
            HSSFRow hssfRow = sheet.getRow(current);
            if (hssfRow != null) {
                for (Integer column : columns) {
                    ValueClass valueClass = properties.get(columns.indexOf(column)).property.getValueClass(ClassType.valuePolicy);
                    DateFormat dateFormat = null;
                    if (valueClass instanceof DateClass) {
                        dateFormat = DateClass.getDateFormat();
                    } else if (valueClass instanceof TimeClass) {
                        dateFormat = ((TimeClass) valueClass).getDefaultFormat();
                    } else if (valueClass instanceof DateTimeClass) {
                        dateFormat = DateTimeClass.getDateTimeFormat();
                    }
                    listRow.add(getXLSFieldValue(hssfRow, column, dateFormat, null));
                }
            }
        } catch (ParseException e) {
            Throwables.propagate(e);
        }
        current++;
        return current > lastRow ? null : listRow;
    }

    protected String getXLSFieldValue(HSSFRow hssfRow, int cell, DateFormat dateFormat, String defaultValue) throws ParseException {
        if (hssfRow != null) {
            HSSFCell hssfCell = hssfRow.getCell(cell);
            if (hssfCell != null) {
                switch (hssfCell.getCellType()) {
                    case Cell.CELL_TYPE_NUMERIC:
                    case Cell.CELL_TYPE_FORMULA:
                        String result;
                        try {
                            if (dateFormat != null) {
                                result = dateFormat.format(hssfCell.getDateCellValue());
                            } else {
                                result = new DecimalFormat("#.#####").format(hssfCell.getNumericCellValue());
                            }
                        } catch (Exception e) {
                            result = hssfCell.getStringCellValue().isEmpty() ? defaultValue : hssfCell.getStringCellValue();
                        }
                        return result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
                    case Cell.CELL_TYPE_BOOLEAN:
                        return String.valueOf(hssfCell.getBooleanCellValue());
                    case Cell.CELL_TYPE_STRING:
                    default:
                        return (hssfCell.getStringCellValue().isEmpty()) ? defaultValue : hssfCell.getStringCellValue();
                }
            }
        }
        return defaultValue;
    }

    @Override
    protected void release() {
    }
}
