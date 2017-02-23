package lsfusion.gwt.base.server;

import com.google.gwt.user.server.rpc.SerializationPolicy;
import com.google.gwt.user.server.rpc.SerializationPolicyLoader;
import lsfusion.gwt.base.server.dispatch.SecuredAction;
import lsfusion.gwt.base.server.exceptions.RemoteRetryException;
import lsfusion.gwt.base.server.spring.BusinessLogicsProvider;
import lsfusion.gwt.base.server.spring.NavigatorProvider;
import lsfusion.gwt.base.shared.MessageException;
import lsfusion.gwt.base.shared.RetryException;
import lsfusion.gwt.base.shared.actions.RequestAction;
import lsfusion.interop.RemoteLogicsInterface;
import lsfusion.interop.navigator.RemoteNavigatorInterface;
import lsfusion.interop.remote.ClientCallBackInterface;
import net.customware.gwt.dispatch.server.DefaultActionHandlerRegistry;
import net.customware.gwt.dispatch.server.Dispatch;
import net.customware.gwt.dispatch.server.InstanceActionHandlerRegistry;
import net.customware.gwt.dispatch.server.SimpleDispatch;
import net.customware.gwt.dispatch.server.standard.AbstractStandardDispatchServlet;
import net.customware.gwt.dispatch.shared.Action;
import net.customware.gwt.dispatch.shared.DispatchException;
import net.customware.gwt.dispatch.shared.Result;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.HttpRequestHandler;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.text.ParseException;

public abstract class LogicsAwareDispatchServlet<T extends RemoteLogicsInterface> extends AbstractStandardDispatchServlet implements HttpRequestHandler, InitializingBean, BeanNameAware {
    protected final static Logger logger = Logger.getLogger(LogicsAwareDispatchServlet.class);

    @Autowired
    private ServletContext servletContext;

    @Autowired
    private BusinessLogicsProvider<T> blProvider;

    @Autowired
    private NavigatorProvider navigatorProvider;

    private boolean useGETForGwtRPC;

    private String rpcPolicyLocation;

    private String beanName;

    protected Dispatch dispatch;

    private ClientCallBackInterface clientCallBack = null;

    public void setUseGETForGwtRPC(boolean useGETForGwtRPC) {
        this.useGETForGwtRPC = useGETForGwtRPC;
    }

    public void setRpcPolicyLocation(String rpcPolicyLocation) {
        this.rpcPolicyLocation = rpcPolicyLocation;
    }

    public void setBlProvider(BusinessLogicsProvider<T> blProvider) {
        this.blProvider = blProvider;
    }

    @Override
    public void setBeanName(String name) {
        beanName = name;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        InstanceActionHandlerRegistry registry = new DefaultActionHandlerRegistry();
        addHandlers(registry);
        dispatch = new SimpleDispatch(registry);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (useGETForGwtRPC && req.getParameter("payload") != null) {
            doPost(req, resp);
        }
    }

    @Override
    protected String readContent(HttpServletRequest request) throws ServletException, IOException {
        if (request.getMethod().equals("POST")) {
            return super.readContent(request);
        } else {
            return request.getParameter("payload");
        }
    }

    @Override
    protected SerializationPolicy doGetSerializationPolicy(HttpServletRequest request, String moduleBaseURL, String strongName) {
        if (rpcPolicyLocation == null) {
            return super.doGetSerializationPolicy(request, moduleBaseURL, strongName);
        } else {
            String serializationPolicyFilePath = SerializationPolicyLoader.getSerializationPolicyFileName(rpcPolicyLocation + "/" + strongName);
            // Open the RPC resource file and read its contents.
            InputStream is = getServletContext().getResourceAsStream(serializationPolicyFilePath);
            try {
                if (is != null) {
                    try {
                        return SerializationPolicyLoader.loadFromStream(is, null);
                    } catch (ParseException e) {
                        logger.error("ERROR: Failed to parse the policy file '" + serializationPolicyFilePath + "'", e);
                    } catch (IOException e) {
                        logger.error("ERROR: Could not read the policy file '" + serializationPolicyFilePath + "'", e);
                    }
                } else {
                    logger.error("ERROR: The serialization policy file '" + serializationPolicyFilePath + "' was not found.");
                }
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        // Ignore this error
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        service(request, response);
    }

    @Override
    public Result execute(Action<?> action) throws DispatchException {
        try {
            if (action instanceof SecuredAction && !((SecuredAction) action).isAllowed()) {
                throw new MessageException("Access denied.");
            }
            return dispatch.execute(action);
        } catch (RemoteRetryException e) {
            String actionTry = "";
            if(action instanceof RequestAction) {
                actionTry = "\n" + action + " try: " + ((RequestAction) action).requestTry + ", maxTries: " + e.maxTries;
            }
            if (!(action instanceof RequestAction) || ((RequestAction) action).logRemoteException()) {
                logger.error("Ошибка в LogicsAwareDispatchServlet.execute: " + actionTry, e);
            }
            throw new RetryException(e.getMessage(), e.maxTries);
        } catch (MessageException e) {
            logger.error("Ошибка в LogicsAwareDispatchServlet.execute: ", e);
            throw new MessageException("Внутренняя ошибка сервера: " + e.getMessage());
        } catch (Throwable e) {
            logger.error("Ошибка в LogicsAwareDispatchServlet.execute: ", e);
            throw new MessageException("Внутренняя ошибка сервера.", e);
        }
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public String getServletName() {
        return beanName;
    }

    @Override
    protected Dispatch getDispatch() {
        return dispatch;
    }

    public T getLogics() {
        return blProvider.getLogics();
    }

    public BusinessLogicsProvider<T> getBLProvider() {
        return blProvider;
    }

    public RemoteNavigatorInterface getNavigator() throws RemoteException {
        return navigatorProvider.getNavigator();
    }

    public ClientCallBackInterface getClientCallBack() throws RemoteException {
        if(clientCallBack == null)
            clientCallBack = getNavigator().getClientCallBack();
        return clientCallBack;
    }

    public void invalidate() throws RemoteException {
        try {
            blProvider.invalidate();
        } catch (Exception ignored) {}
        try {
            navigatorProvider.getNavigator().close();
        } catch (Exception ignored) {}
        navigatorProvider.invalidate();
        clientCallBack = null;
    }

    public HttpServletRequest getRequest() {
        return getThreadLocalRequest();
    }

    protected abstract void addHandlers(InstanceActionHandlerRegistry registry);

}
