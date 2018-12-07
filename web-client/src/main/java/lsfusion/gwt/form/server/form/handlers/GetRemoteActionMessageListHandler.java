package lsfusion.gwt.form.server.form.handlers;

import lsfusion.base.ProgressBar;
import lsfusion.gwt.base.shared.actions.ListResult;
import lsfusion.gwt.form.server.spring.LSFusionDispatchServlet;
import lsfusion.gwt.form.server.form.spring.FormSessionObject;
import lsfusion.gwt.form.server.convert.ClientActionToGwtConverter;
import lsfusion.gwt.form.server.form.FormActionHandler;
import lsfusion.gwt.form.shared.actions.form.GetRemoteActionMessageList;
import net.customware.gwt.dispatch.server.ExecutionContext;
import net.customware.gwt.dispatch.shared.DispatchException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GetRemoteActionMessageListHandler extends FormActionHandler<GetRemoteActionMessageList, ListResult> {
    private static ClientActionToGwtConverter clientActionConverter = ClientActionToGwtConverter.getInstance();

    public GetRemoteActionMessageListHandler(LSFusionDispatchServlet servlet) {
        super(servlet);
    }

    @Override
    protected String getActionDetails(GetRemoteActionMessageList action) {
        return null; // too many logs
    }

    @Override
    public ListResult executeEx(GetRemoteActionMessageList action, ExecutionContext context) throws DispatchException, IOException {
        FormSessionObject form = getFormSessionObject(action.formSessionID);
        List<Object> result = new ArrayList<>();
        if (form != null) {

            for (Object object : form.remoteForm.getRemoteActionMessageList()) {
                if (object instanceof ProgressBar)
                    result.add(clientActionConverter.convertProgressBar((lsfusion.base.ProgressBar) object));
                else
                    result.add(object);
            }
        }
        return new ListResult((ArrayList) result);
    }
}