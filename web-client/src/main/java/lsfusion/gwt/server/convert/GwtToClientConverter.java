package lsfusion.gwt.server.convert;

import com.google.common.base.Throwables;
import lsfusion.base.DateConverter;
import lsfusion.gwt.client.form.GUpdateMode;
import lsfusion.gwt.client.form.design.GFont;
import lsfusion.gwt.client.form.object.GGroupObjectValue;
import lsfusion.gwt.client.form.object.table.grid.user.design.GColumnUserPreferences;
import lsfusion.gwt.client.form.object.table.grid.user.design.GFormUserPreferences;
import lsfusion.gwt.client.form.object.table.grid.user.design.GGroupObjectUserPreferences;
import lsfusion.gwt.client.form.property.GClassViewType;
import lsfusion.gwt.client.form.property.GPropertyGroupType;
import lsfusion.gwt.client.form.property.cell.classes.ColorDTO;
import lsfusion.gwt.client.form.property.cell.classes.GDateDTO;
import lsfusion.gwt.client.form.property.cell.classes.GFilesDTO;
import lsfusion.gwt.client.form.property.cell.classes.GTimeDTO;
import lsfusion.gwt.client.form.property.cell.view.GUserInputResult;
import lsfusion.gwt.server.FileUtils;
import lsfusion.interop.form.UpdateMode;
import lsfusion.interop.form.design.FontInfo;
import lsfusion.interop.form.object.table.grid.user.design.ColumnUserPreferences;
import lsfusion.interop.form.object.table.grid.user.design.FormUserPreferences;
import lsfusion.interop.form.object.table.grid.user.design.GroupObjectUserPreferences;
import lsfusion.interop.form.property.ClassViewType;
import lsfusion.interop.form.property.PropertyGroupType;
import lsfusion.interop.form.property.cell.UserInputResult;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Time;
import java.util.*;

import static lsfusion.base.BaseUtils.serializeObject;

@SuppressWarnings("UnusedDeclaration")
public class GwtToClientConverter extends ObjectConverter {
    private static final class InstanceHolder {
        private static final GwtToClientConverter instance = new GwtToClientConverter();
    }

    public static GwtToClientConverter getInstance() {
        return InstanceHolder.instance;
    }

    private GwtToClientConverter() {
    }

    @Converter(from = ColorDTO.class)
    public Color convertColorDTO(ColorDTO dto) {
        int c = Integer.parseInt(dto.value, 16);
        return new Color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
    }

    @Converter(from = Date.class)
    public java.sql.Date convertDate(Date date) {
        //TODO: this converter shouldn't be used
        assert false;
        return DateConverter.safeDateToSql(date);
    }

    @Converter(from = GDateDTO.class)
    public java.sql.Date convertDate(GDateDTO dto) {
        GregorianCalendar gc = new GregorianCalendar();
        gc.set(dto.year + 1900, dto.month, dto.day, 0, 0, 0);
        gc.set(Calendar.MILLISECOND, 0);
        return new java.sql.Date(gc.getTimeInMillis());
    }

    @Converter(from = GTimeDTO.class)
    public java.sql.Time convertTime(GTimeDTO dto) {
        GregorianCalendar gc = new GregorianCalendar();
        gc.clear(); // reset to zero epoch
        
        gc.set(Calendar.HOUR_OF_DAY, dto.hour);
        gc.set(Calendar.MINUTE, dto.minute);
        gc.set(Calendar.SECOND, dto.second);
        return new Time(gc.getTimeInMillis());
    }

    @Converter(from = GFont.class)
    public FontInfo convertFont(GFont fontInfo) {
        if (fontInfo == null) {
            return null;
        }
        return new FontInfo(fontInfo.family, fontInfo.size != null ? fontInfo.size : -1, fontInfo.bold, fontInfo.italic);
    }

    @Converter(from = GFilesDTO.class)
    public Object convertFiles(GFilesDTO filesObject) {
        return FileUtils.readFilesAndDelete(filesObject);
    }

    @Converter(from = GUserInputResult.class)
    public UserInputResult convertInputResult(GUserInputResult gInputResult) {
        return new UserInputResult(gInputResult.isCanceled(), convertOrCast(gInputResult.getValue()));
    }

    @Converter(from = GClassViewType.class)
    public ClassViewType convertViewType(GClassViewType gViewType) {
        return ClassViewType.valueOf(gViewType.name());
    }

    @Converter(from = GPropertyGroupType.class)
    public PropertyGroupType convertGroupType(GPropertyGroupType gViewType) {
        return PropertyGroupType.valueOf(gViewType.name());
    }

    @Converter(from = GUpdateMode.class)
    public UpdateMode convertGroupType(GUpdateMode gViewType) {
        return UpdateMode.valueOf(gViewType.name());
    }

    @Converter(from = GGroupObjectValue.class)
    public byte[] convertGroupObjectValue(GGroupObjectValue groupObjectValue) {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        DataOutputStream dataStream = new DataOutputStream(outStream);

        serializeGroupObjectValue(dataStream, groupObjectValue);

        return outStream.toByteArray();
    }

    public static void serializeGroupObjectValue(DataOutputStream dataStream, GGroupObjectValue groupObjectValue) {
        try {
            int size = groupObjectValue.size();
            dataStream.writeInt(size);
            for (int i = 0; i < size; ++i) {
                dataStream.writeInt(groupObjectValue.getKey(i));
                serializeObject(dataStream, groupObjectValue.getValue(i));
            }
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    @Converter(from = GFormUserPreferences.class)
    public FormUserPreferences convertFormUserPreferences(GFormUserPreferences gprefs) {
        java.util.List<GroupObjectUserPreferences> generalPrefs = new ArrayList<>();
        java.util.List<GroupObjectUserPreferences> userPrefs = new ArrayList<>();
        for (GGroupObjectUserPreferences prefs : gprefs.getGroupObjectGeneralPreferencesList()) {
            generalPrefs.add(convertGroupObjectPreferences(prefs));
        }
        for (GGroupObjectUserPreferences prefs : gprefs.getGroupObjectUserPreferencesList()) {
            userPrefs.add(convertGroupObjectPreferences(prefs));
        }
        return new FormUserPreferences(generalPrefs, userPrefs);    
    }
    
    @Converter(from = GGroupObjectUserPreferences.class)
    public GroupObjectUserPreferences convertGroupObjectPreferences(GGroupObjectUserPreferences gprefs) {
        if(gprefs == null)
            return null;

        Map<String, ColumnUserPreferences> columnUPs = new HashMap<>();
        for (Map.Entry<String, GColumnUserPreferences> entry : gprefs.getColumnUserPreferences().entrySet()) {
            columnUPs.put(entry.getKey(), convertColumnPreferences(entry.getValue()));
        }
        return new GroupObjectUserPreferences(columnUPs, gprefs.getGroupObjectSID(), convertFont(gprefs.getFont()), gprefs.getPageSize(), gprefs.getHeaderHeight(), gprefs.hasUserPreferences());
    }
    
    @Converter(from = GColumnUserPreferences.class)
    public ColumnUserPreferences convertColumnPreferences(GColumnUserPreferences gprefs) {
        return new ColumnUserPreferences(gprefs.userHide, gprefs.userCaption, gprefs.userPattern, gprefs.userWidth, gprefs.userOrder, gprefs.userSort, gprefs.userAscendingSort);
    }
}
