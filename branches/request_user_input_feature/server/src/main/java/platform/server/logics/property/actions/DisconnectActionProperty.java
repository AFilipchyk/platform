package platform.server.logics.property.actions;

import platform.base.Pair;
import platform.server.classes.ValueClass;
import platform.server.form.entity.PropertyDrawEntity;
import platform.server.form.view.DefaultFormView;
import platform.server.form.view.PropertyDrawView;
import platform.server.logics.BaseLogicsModule;
import platform.server.logics.BusinessLogics;
import platform.server.logics.DataObject;
import platform.server.logics.property.ActionProperty;
import platform.server.logics.property.ClassPropertyInterface;
import platform.server.logics.property.ExecutionContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static platform.server.logics.ServerResourceBundle.getString;

public class DisconnectActionProperty extends CustomActionProperty {
    BaseLogicsModule LM;
    BusinessLogics BL;

    public DisconnectActionProperty(BusinessLogics BL, BaseLogicsModule LM, ValueClass dictionary) {
        super("disconnectConnection", getString("logics.connection.disconnect"), new ValueClass[] {dictionary});
        this.BL = BL;
        this.LM = LM;
    }

    public void execute(ExecutionContext context) throws SQLException {
        List<ClassPropertyInterface> interfacesList = new ArrayList<ClassPropertyInterface>(interfaces);
        DataObject connection = context.getKeyValue(interfacesList.remove(0));

        String login = ((String) LM.userNameConnection.read(context, connection)).trim();
        Integer computer = (Integer) LM.connectionComputer.read(context, connection);
        Pair<String, Integer> key = new Pair<String, Integer>(login, computer);
        BL.cutOffConnection(key);
    }

    @Override
    public void proceedDefaultDesign(PropertyDrawView propertyView, DefaultFormView view) {
        super.proceedDefaultDesign(propertyView, view);
        propertyView.design.setIconPath("disconnect.png");
    }
}
