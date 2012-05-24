package roman;

import jxl.read.biff.BiffException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import platform.server.classes.CustomStaticFormatFileClass;
import platform.server.classes.DataClass;
import platform.server.integration.ImportInputTable;
import platform.server.integration.SingleSheetImporter;
import platform.server.logics.property.ExecutionContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static roman.InvoicePricatMergeInputTable.ResultField;

public class TopazImportInvoiceActionProperty extends ImportBoxInvoiceActionProperty {
    private final RomanBusinessLogics BL;

    public TopazImportInvoiceActionProperty(RomanBusinessLogics BL) {
        super(BL.RomanLM, BL.RomanLM.topazSupplier);
        this.BL = BL;
    }

    @Override
    protected boolean isSimpleInvoice() {
        return true;
    }

    ;

    @Override
    protected ImportInputTable createTable(ByteArrayInputStream inFile) throws BiffException, IOException, InvalidFormatException {
        TopazInputTable invoiceTable = new TopazInputTable(inFile);
        return new InvoicePricatMergeInputTable(BL, invoiceTable,
                ResultField.ARTICLE, ResultField.BARCODE, ResultField.QUANTITY, ResultField.COMPOSITION,
                ResultField.SIZE, ResultField.COLORCODE, ResultField.COLOR, ResultField.INVOICE, ResultField.BOXNUMBER, ResultField.NUMBERSKU
        );
    }

    @Override
    protected SingleSheetImporter createImporter(ImportInputTable inputTable) {

        return new TopazInvoiceImporter(inputTable,
                barCodeField, sidField, invoiceSIDField, boxNumberField, colorCodeField, colorNameField,
                sizeField, originalNameField, countryField, unitNetWeightField, compositionField,
                unitPriceField, dateInvoiceField, RRPField, unitQuantityField, numberSkuField, customCodeField, customCode6Field,
                genderField, seasonField, themeCodeField, themeNameField, sidDestinationDataSupplierBoxField
        );

    }

    protected DataClass getReadType(ExecutionContext context) {
        return CustomStaticFormatFileClass.getDefinedInstance(true, "Файл Excel (*.xls)", "xls *.*");
    }
}

