package lsfusion.gwt.shared.form.view.filter;

import lsfusion.gwt.shared.form.changes.dto.GFilterValueDTO;

import java.io.Serializable;

public abstract class GFilterValue implements Serializable {
    public abstract GFilterValueDTO getDTO();
}
