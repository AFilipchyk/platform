package lsfusion.interop.form.user;

import lsfusion.interop.form.design.FontInfo;

import java.io.Serializable;
import java.util.Map;

public class GroupObjectUserPreferences implements Serializable {
    private Map<String, ColumnUserPreferences> columnUserPreferences;
    public String groupObjectSID;
    public FontInfo fontInfo;
    public Integer pageSize;
    public Integer headerHeight;
    public boolean hasUserPreferences;

    public GroupObjectUserPreferences(Map<String, ColumnUserPreferences> columnUserPreferences, String groupObjectSID,
                                      FontInfo fontInfo, Integer pageSize, Integer headerHeight, boolean hasUserPreferences) {
        this.columnUserPreferences = columnUserPreferences;
        this.groupObjectSID = groupObjectSID;
        this.fontInfo = fontInfo;
        this.pageSize = pageSize;
        this.headerHeight = headerHeight;
        this.hasUserPreferences = hasUserPreferences;
    }

    public Map<String, ColumnUserPreferences> getColumnUserPreferences() {
        return columnUserPreferences;
    }
}
