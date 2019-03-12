package lsfusion.client.remote.proxy;

import com.google.common.base.Throwables;
import lsfusion.base.lambda.EProvider;
import lsfusion.client.ClientLoggers;
import lsfusion.client.Main;
import lsfusion.client.form.BusyDialogDisplayer;
import lsfusion.client.form.BusyDisplayer;
import lsfusion.interop.PendingMethodInvocation;
import lsfusion.interop.PendingRemoteInterface;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public abstract class RemoteObjectProxy<T extends PendingRemoteInterface> implements PendingRemoteInterface {
    private static Logger logger = ClientLoggers.remoteLogger;

    protected final T target;

    private final Map<Object, Object> properties = new HashMap<>();

    private long startCall = 0;

    public RemoteObjectProxy(T target) {
        this.target = target;
    }

    @Override
    public Object[] createAndExecute(final PendingMethodInvocation creator, final PendingMethodInvocation[] invocations) throws RemoteException {
        logRemoteMethodStartCall("createAndExecute");

        if (logger.isDebugEnabled()) {
            logger.debug("  Creator in createAndExecute: " + creator.toString());
            for (PendingMethodInvocation invocation : invocations) {
                logger.debug("  Invocation in createAndExecute: " + invocation.toString());
            }
        }

        if (!Main.busyDialog) {
            final BusyDisplayer busyDisplayer = new BusyDisplayer(new EProvider<String>() {
                @Override
                public String getExceptionally() throws RemoteException {
                    return target.getRemoteActionMessage();
                }
                @Override
                public void interrupt(boolean cancelable) throws RemoteException {
                    target.interrupt(cancelable);
                }
            });
            busyDisplayer.start();

            Object[] result;
            try {
                result = target.createAndExecute(creator, invocations);
                logRemoteMethodEndCall("createAndExecute", result);
                return result;
            } catch (Exception e) {
                Throwables.propagateIfPossible(e, RemoteException.class);
                throw Throwables.propagate(e);
            } finally {
                busyDisplayer.stop();
            }
        } else {
            final BusyDialogDisplayer busyDisplayer = new BusyDialogDisplayer(new EProvider<List<Object>>() {
                @Override
                public List<Object> getExceptionally() throws RemoteException {
                    return target.getRemoteActionMessageList();
                }
                @Override
                public void interrupt(boolean cancelable) throws RemoteException {
                    target.interrupt(cancelable);
                }
            });
            busyDisplayer.start();

            Object[] result;
            try {
                result = target.createAndExecute(creator, invocations);
                logRemoteMethodEndCall("createAndExecute", result);
                return result;
            } catch (Exception e) {
                Throwables.propagateIfPossible(e, RemoteException.class);
                throw Throwables.propagate(e);
            } finally {
                busyDisplayer.stop();
            }
        }
    }

    @Override
    public String getRemoteActionMessage() throws RemoteException {
        return target.getRemoteActionMessage();
    }

    @Override
    public List<Object> getRemoteActionMessageList() throws RemoteException {
        return target.getRemoteActionMessageList();
    }

    @Override
    public void interrupt(boolean cancelable) throws RemoteException {
        target.interrupt(cancelable);
    }

    public Object getProperty(Object key) {
        return properties.get(key);
    }

    public void setProperty(Object key, Object value) {
        properties.put(key, value);
    }

    public boolean hasProperty(Object key) {
        return properties.containsKey(key);
    }

    protected <T> T callImmutableMethod(String methodName, Callable<T> callable) throws Exception {
        logger.debug("Running immutable method: " + methodName);
        if (hasProperty(methodName)) {
            Object result = getProperty(methodName);
            logger.debug("  Returning cached value: " + result);
            return (T) result;
        }
        logger.debug("  Directly call immutable method:");
        return callable.call();
    }

    protected void logRemoteMethodStartCall(String methodName) {
        if (logger.isInfoEnabled()) {
            startCall = System.currentTimeMillis();
            logger.info("Calling remote method: " + this.getClass().getSimpleName() + "." + methodName);
        }
    }

    protected void logRemoteMethodEndCall(String methodName, Object result) {
        if (logger.isInfoEnabled()) {
            try (ContentLengthOutputStream outStream = new ContentLengthOutputStream(new ByteArrayOutputStream(), 128)) {
                try {
                    new ObjectOutputStream(outStream).writeObject(result);
                } catch (ContentLengthException ignored) {
                    //suppress
                }
                logger.info(
                        String.format("Remote method called (time: %1$d ms.; result size: %2$s): %3$s.%4$s",
                                System.currentTimeMillis() - startCall, outStream.size(), this.getClass().getSimpleName(), methodName));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected void logRemoteMethodStartVoidCall(String methodName) {
        logRemoteMethodStartCall(methodName);
    }

    protected void logRemoteMethodEndVoidCall(String methodName) {
        logRemoteMethodEndCall(methodName, null);
    }

    protected static List<PendingMethodInvocation> getImmutableMethodInvocations(Class clazz) {
        List<PendingMethodInvocation> invocations = new ArrayList<>();
        for (Method method : clazz.getMethods()) {
            if (method.getAnnotation(ImmutableMethod.class) == null) {
                continue;
            }

            // естественно, разрешены только функи без параметров
            assert method.getParameterTypes().length == 0;

            PendingMethodInvocation invocation = new PendingMethodInvocation(method.getName(), new Class[0], new Object[0], method.getReturnType());
            invocations.add(invocation);
        }

        return invocations;
    }
}
