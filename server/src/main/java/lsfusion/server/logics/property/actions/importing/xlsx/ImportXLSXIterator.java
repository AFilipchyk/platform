package lsfusion.server.logics.property.actions.importing.xlsx;

import com.google.common.base.Throwables;
import lsfusion.server.classes.DateClass;
import lsfusion.server.classes.DateTimeClass;
import lsfusion.server.classes.TimeClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassType;
import lsfusion.server.logics.property.actions.importing.ImportIterator;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ImportXLSXIterator extends ImportIterator {
    private final List<Integer> columns;
    private final List<LCP> properties;
    private int current;
    private XSSFSheet sheet;
    private int lastRow;

    public ImportXLSXIterator(byte[] file, List<Integer> columns, List<LCP> properties, Integer sheetIndex) throws IOException {
        this.columns = columns;
        this.properties = properties;

        XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(file));
        sheet = wb.getSheetAt(sheetIndex == null ? 0 : (sheetIndex - 1));
        lastRow = sheet.getLastRowNum();
        lastRow = lastRow == 0 && sheet.getRow(0) == null ? 0 : (lastRow + 1);
    }

    @Override
    public List<String> nextRow() {
        List<String> listRow = new ArrayList<>();
        try {
            XSSFRow xssfRow = sheet.getRow(current);
            if (xssfRow != null) {
                for (Integer column : columns) {
                    try {
                        ValueClass valueClass = properties.get(columns.indexOf(column)).property.getValueClass(ClassType.valuePolicy);
                        DateFormat dateFormat = null;
                        if (valueClass instanceof DateClass) {
                            dateFormat = DateClass.getDateFormat();
                        } else if (valueClass instanceof TimeClass) {
                            dateFormat = ((TimeClass) valueClass).getDefaultFormat();
                        } else if (valueClass instanceof DateTimeClass) {
                            dateFormat = DateTimeClass.getDateTimeFormat();
                        }
                        listRow.add(getXLSXFieldValue(xssfRow, column, dateFormat, null));
                    } catch (Exception e) {
                        throw new RuntimeException(String.format("Error parsing row %s, column %s", current, column), e);
                    }
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        current++;
        return current > lastRow ? null : listRow;
    }

    protected String getXLSXFieldValue(XSSFRow xssfRow, Integer cell, DateFormat dateFormat, String defaultValue) throws ParseException {
        String result = defaultValue;
        if (cell != null && xssfRow != null) {
            XSSFCell xssfCell = xssfRow.getCell(cell);
            if (xssfCell != null) {
                switch (xssfCell.getCellType()) {
                    case Cell.CELL_TYPE_NUMERIC:
                        if (dateFormat != null) {
                            Date value = getDateValue(xssfCell);
                            if(value != null)
                                result = dateFormat.format(value);
                        } else {
                            result = new DecimalFormat("#.#####").format(xssfCell.getNumericCellValue());
                            result = result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
                        }
                        break;
                    case Cell.CELL_TYPE_FORMULA:
                        try {
                            if (dateFormat != null) {
                                String formulaCellValue = getFormulaCellValue(xssfCell);
                                result = formulaCellValue.isEmpty() ? defaultValue : formulaCellValue.trim();
                                if(result != null)
                                    result = parseFormatDate(dateFormat, result);
                            } else {
                                result = new DecimalFormat("#.#####").format(xssfCell.getNumericCellValue());
                            }
                        } catch (Exception e) {
                            String formulaCellValue = getFormulaCellValue(xssfCell);
                            result = formulaCellValue.isEmpty() ? defaultValue : formulaCellValue;
                        }
                        result = result != null && result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
                        break;
                    case Cell.CELL_TYPE_ERROR:
                        result = null;
                        break;
                    case Cell.CELL_TYPE_BOOLEAN:
                        result = String.valueOf(xssfCell.getBooleanCellValue());
                        break;
                    case Cell.CELL_TYPE_STRING:
                    default:
                        if(dateFormat != null) {
                            result = (xssfCell.getStringCellValue().isEmpty()) ? defaultValue : xssfCell.getStringCellValue().trim();
                            if(result != null)
                                result = parseFormatDate(dateFormat, result);
                        } else {
                            result = (xssfCell.getStringCellValue().isEmpty()) ? defaultValue : xssfCell.getStringCellValue().trim();
                        }
                }
            }
        }
        return result;
    }

    private String getFormulaCellValue(XSSFCell xssfCell) {
        String result;
        switch (xssfCell.getCachedFormulaResultType()) {
            case 0:
                result = String.valueOf(xssfCell.getNumericCellValue());
                break;
            default:
                result = xssfCell.getStringCellValue();
                break;
        }
        return result;
    }

    private String parseFormatDate(DateFormat dateFormat, String value) {
        String result = null;
        try {
            if (value != null && !value.isEmpty() && !value.replace(".", "").trim().isEmpty()) {
                Date date = DateUtils.parseDate(value, "dd/MM/yyyy", "dd.MM.yyyy", "dd.MM.yyyy HH:mm:ss");
                if(date != null)
                    result = dateFormat.format(date);
            }
        } catch (ParseException ignored) {
        }
        return result;
    }

    @Override
    protected void release() {
    }

    private Date getDateValue(XSSFCell xssfCell) {
        try {
            return xssfCell.getDateCellValue();
        } catch (Exception ignored) {
            return null;
        }
    }
}
