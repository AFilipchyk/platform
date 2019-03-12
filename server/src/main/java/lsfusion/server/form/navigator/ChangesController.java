package lsfusion.server.form.navigator;

import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.logics.property.CalcProperty;
import lsfusion.server.session.DataSession;

public interface ChangesController {

    void regChange(ImSet<CalcProperty> changes, DataSession session);

    ImSet<CalcProperty> update(DataSession session, FormInstance form);

    void registerForm(FormInstance form);

    void unregisterForm(FormInstance form);
}
