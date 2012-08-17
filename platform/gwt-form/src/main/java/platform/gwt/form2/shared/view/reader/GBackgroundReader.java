package platform.gwt.form2.shared.view.reader;

import platform.gwt.form2.shared.view.changes.GGroupObjectValue;
import platform.gwt.form2.shared.view.logics.GGroupObjectLogicsSupplier;

import java.util.Map;

public class GBackgroundReader implements GPropertyReader {
    public int readerID;
    public int groupObjectID;

    public GBackgroundReader(){}

    public GBackgroundReader(int readerID, int groupObjectID) {
        this.readerID = readerID;
        this.groupObjectID = groupObjectID;
    }

    public void update(GGroupObjectLogicsSupplier controller, Map<GGroupObjectValue, Object> keys) {
        controller.updateBackgroundValues(this, keys);
    }

    @Override
    public int getGroupObjectID() {
        return groupObjectID;
    }
}
