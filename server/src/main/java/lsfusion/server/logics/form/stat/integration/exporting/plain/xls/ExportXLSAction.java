package lsfusion.server.logics.form.stat.integration.exporting.plain.xls;

import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.server.data.type.Type;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.form.open.FormSelector;
import lsfusion.server.logics.form.open.ObjectSelector;
import lsfusion.server.logics.form.stat.integration.FormIntegrationType;
import lsfusion.server.logics.form.stat.integration.exporting.plain.ExportPlainAction;
import lsfusion.server.logics.form.stat.integration.exporting.plain.ExportPlainWriter;
import lsfusion.server.logics.form.struct.object.GroupObjectEntity;
import lsfusion.server.physics.dev.i18n.LocalizedString;

import java.io.IOException;

public class ExportXLSAction<O extends ObjectSelector> extends ExportPlainAction<O> {
    private boolean xlsx;
    private boolean noHeader;

    public ExportXLSAction(LocalizedString caption, FormSelector<O> form, ImList<O> objectsToSet, ImList<Boolean> nulls, FormIntegrationType staticType, ImMap<GroupObjectEntity, LP> exportFiles, String charset, boolean xlsx, boolean noHeader) {
        super(caption, form, objectsToSet, nulls, staticType, exportFiles, charset);
        this.xlsx = xlsx;
        this.noHeader = noHeader;
    }

    @Override
    protected ExportPlainWriter getWriter(ImOrderMap<String, Type> fieldTypes, boolean singleRow) throws IOException {
        return new ExportXLSWriter(fieldTypes, xlsx, noHeader);
    }
}