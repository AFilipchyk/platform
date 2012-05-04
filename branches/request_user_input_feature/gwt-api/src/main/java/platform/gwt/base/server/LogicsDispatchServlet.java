package platform.gwt.base.server;

import net.customware.gwt.dispatch.server.DefaultActionHandlerRegistry;
import net.customware.gwt.dispatch.server.Dispatch;
import net.customware.gwt.dispatch.server.InstanceActionHandlerRegistry;
import net.customware.gwt.dispatch.server.SimpleDispatch;
import net.customware.gwt.dispatch.server.standard.AbstractStandardDispatchServlet;
import net.customware.gwt.dispatch.shared.Action;
import net.customware.gwt.dispatch.shared.DispatchException;
import net.customware.gwt.dispatch.shared.Result;
import org.apache.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import platform.base.ExceptionUtils;
import platform.gwt.base.server.exceptions.RemoteDispatchException;
import platform.gwt.base.server.spring.BusinessLogicsProvider;
import platform.gwt.base.server.spring.NavigatorProvider;
import platform.gwt.base.shared.MessageException;
import platform.interop.RemoteLogicsInterface;
import platform.interop.navigator.RemoteNavigatorInterface;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.rmi.RemoteException;

public abstract class LogicsDispatchServlet<T extends RemoteLogicsInterface> extends AbstractStandardDispatchServlet {
    protected final static Logger logger = Logger.getLogger(LogicsDispatchServlet.class);

    protected Dispatch dispatch;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        InstanceActionHandlerRegistry registry = new DefaultActionHandlerRegistry();
        addHandlers(registry);
        dispatch = new SimpleDispatch(registry);
    }

    @Override
    public Result execute(Action<?> action) throws DispatchException {
        try {
            return super.execute(action);
        } catch (RemoteDispatchException e) {
            String errorMessage;
            if (!ExceptionUtils.isRecoverableRemoteException(e.getRemote())) {
                getLogicsProvider().invalidate();
                errorMessage = "Внутренняя ошибка сервера. Попробуйте перезагрузить страницу.";
            } else {
                errorMessage = ExceptionUtils.getInitialCause(e.getRemote()).getMessage();
            }

            logger.error("Ошибка в LogicsDispatchServlet.execute: ", e);
            e.printStackTrace();
            throw new MessageException(errorMessage);
        } catch (Throwable e) {
            e.printStackTrace();
            logger.error("Ошибка в LogicsDispatchServlet.execute: ", e);
            throw new MessageException("Внутренняя ошибка сервера.");
        }
    }

    @Override
    protected Dispatch getDispatch() {
        return dispatch;
    }

    public T getLogics() {
        return getLogicsProvider().getLogics();
    }

    public RemoteNavigatorInterface getNavigator() throws RemoteException {
        return getNavigatorProvider().getNavigator();
    }

    public BusinessLogicsProvider<T> getLogicsProvider() {
        return getSpringContext().getBean(BusinessLogicsProvider.class);
    }

    public NavigatorProvider<T> getNavigatorProvider() {
        return getSpringContext().getBean(NavigatorProvider.class);
    }

    //todo: это будет не нужно после интеграции сервлетов в Spring
    private WebApplicationContext getSpringContext() {
        return WebApplicationContextUtils.getWebApplicationContext(getServletContext());
    }

    public HttpSession getSession() {
        return getThreadLocalRequest().getSession();
    }

    public HttpServletRequest getRequest() {
        return getThreadLocalRequest();
    }

    public HttpServletResponse getResponse() {
        return getThreadLocalResponse();
    }

    public Authentication getAuthentication() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return securityContext.getAuthentication();
    }

    protected abstract void addHandlers(InstanceActionHandlerRegistry registry);
}
