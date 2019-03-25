package lsfusion.interop.form.object.table.grid.user.design;

import java.io.Serializable;


public class ColumnUserPreferences implements Serializable {

    public Boolean userHide;
    public String userCaption;
    public String userPattern;
    public Integer userWidth;
    public Integer userOrder;
    public Integer userSort;
    public Boolean userAscendingSort;
    
    public ColumnUserPreferences(ColumnUserPreferences prefs) {
        this(prefs.userHide, prefs.userCaption, prefs.userPattern, prefs.userWidth, prefs.userOrder, prefs.userSort, prefs.userAscendingSort);
    }

    public ColumnUserPreferences(Boolean userHide, String userCaption, String userPattern, Integer width, Integer userOrder,
                                 Integer userSort, Boolean userAscendingSort) {
        this.userHide = userHide;
        this.userCaption = userCaption;
        this.userPattern = userPattern;
        this.userWidth = width;
        this.userOrder = userOrder;
        this.userSort = userSort;
        this.userAscendingSort = userAscendingSort;
    }
}
