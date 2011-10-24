package platform.client;

import org.apache.log4j.Logger;
import org.aspectj.lang.Aspects;
import platform.base.BaseUtils;
import platform.base.OSUtils;
import platform.client.exceptions.ClientExceptionManager;
import platform.client.exceptions.ExceptionThreadGroup;
import platform.client.form.ClientExternalScreen;
import platform.client.form.SimplexLayout;
import platform.client.logics.classes.ClientObjectClass;
import platform.client.logics.classes.ClientTypeSerializer;
import platform.client.navigator.ClientNavigatorWindow;
import platform.client.remote.PendingExecutionAspect;
import platform.client.remote.proxy.RemoteFormProxy;
import platform.client.rmi.ConnectionLostManager;
import platform.client.rmi.RMITimeoutSocketFactory;
import platform.interop.RemoteLoaderInterface;
import platform.interop.RemoteLogicsInterface;
import platform.interop.ServerInfo;
import platform.interop.event.EventBus;
import platform.interop.event.IDaemonTask;
import platform.interop.form.RemoteFormInterface;
import platform.interop.navigator.RemoteNavigatorInterface;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.rmi.server.RMIClassLoader;
import java.rmi.server.RMIFailureHandler;
import java.rmi.server.RMISocketFactory;
import java.text.DateFormat;
import java.util.*;
import java.util.List;
import java.util.Timer;

import static platform.client.ClientResourceBundle.getString;
import static platform.client.StartupProperties.*;

public class Main {
    private final static Logger logger = Logger.getLogger(Main.class);

    private static PendingExecutionAspect pendingAspect = Aspects.aspectOf(PendingExecutionAspect.class);

    public static final String PLATFORM_TITLE = "LS Fusion";
    private static final String DEFAULT_SPLASH_PATH = "/images/lsfusion.jpg";

    public static ModuleFactory module;

    public static RemoteLoaderInterface remoteLoader;
    public static RemoteLogicsInterface remoteLogics;
    public static RemoteNavigatorInterface remoteNavigator;

    public static int computerId;
    public static TimeZone timeZone;

    public static MainFrame frame;

    private static ThreadGroup bootstrapThreadGroup;
    private static ExceptionThreadGroup mainThreadGroup;
    private static Thread mainThread;
    private static PingThread pingThread;

    private static RMITimeoutSocketFactory socketFactory;

    private static ClientObjectClass baseClass = null;
    public static EventBus eventBus = new EventBus();

    public static void start(final String[] args, ModuleFactory startModule) {
        bootstrapThreadGroup = Thread.currentThread().getThreadGroup();

        module = startModule;

        System.setProperty("sun.awt.exception.handler", ClientExceptionManager.class.getName());

//        Это нужно, чтобы пофиксать баг, когда форма не собирается GC...
//        http://stackoverflow.com/questions/2808582/memory-leak-with-swing-drag-and-drop/2860372#2860372
        System.setProperty("swing.bufferPerWindow", "false");

        // делаем, чтобы сборщик мусора срабатывал каждую минуту - для удаления ненужных connection'ов
        System.setProperty("sun.rmi.dgc.client.gcInterval", "60000");

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                ClientExceptionManager.handle(e);
            }
        });

        try {
            initRmiLogging();

            loadLibraries();

            initRmiClassLoader();

            initRMISocketFactory();

            initSwing();
        } catch (Exception e) {
            logger.error("Error during startup: ", e);
            e.printStackTrace();
            System.exit(1);
        }

        startWorkingThreads();
    }

    private static void startWorkingThreads() {
        startWorkingThreads(false);
    }

    private static void startWorkingThreads(boolean reconnect) {
        mainThreadGroup = new ExceptionThreadGroup();
        mainThread = new Thread(mainThreadGroup, "Init thread") {
            public void run() {
                try {
                    //UIManager.setLookAndFeel("com.jgoodies.looks.plastic.PlasticXPLookAndFeel");
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

                    LoginAction loginAction = LoginAction.getInstance();
                    if (!loginAction.login()) {
                        return;
                    }

                    remoteLogics = loginAction.getRemoteLogics();
                    remoteNavigator = loginAction.getRemoteNavigator();
                    computerId = loginAction.getComputerId();

                    timeZone = remoteLogics.getTimeZone();

                    startSplashScreen();

                    logger.info("Before init frame");
                    frame = module.initFrame(remoteNavigator);
                    logger.info("After init frame");

                    pingThread = new PingThread(remoteNavigator.getClientCallBack());
                    pingThread.start();

                    frame.addWindowListener(
                            new WindowAdapter() {
                                public void windowOpened(WindowEvent e) {
                                    closeSplashScreen();
                                }

                                public void windowClosing(WindowEvent e) {
                                    try {
                                        remoteLogics.endSession(OSUtils.getLocalHostName() + " " + computerId);
                                    } catch (Exception ex) {
                                        throw new RuntimeException(ex);
                                    }
                                }
                            }
                    );

                    frame.setExtendedState(Frame.MAXIMIZED_BOTH);
                    logger.info("After setExtendedState");

                    ConnectionLostManager.install(frame);

                    frame.setVisible(true);

                    ArrayList<IDaemonTask> tasks = remoteNavigator.getDaemonTasks(Main.computerId);
                    Timer timer = new Timer();
                    for (IDaemonTask task : tasks) {
                        task.setEventBus(eventBus);
                        timer.schedule(new DaemonTask(task), task.getDelay(), task.getPeriod());
                    }
                    // todo : где-то обязательно надо уведомлять DaemonTask о том, что пора сворачиваться, чтобы они освобождали порты
                } catch (Exception e) {
                    closeSplashScreen();
                    logger.error(getString("client.error.application.initialization"), e);
                    throw new RuntimeException(getString("client.error.application.initialization"), e);
                }
            }
        };
        mainThread.start();
    }

    private static void initRmiLogging() {
        boolean turnOnRmiLogging = Boolean.getBoolean(PLATFORM_CLIENT_LOG_RMI);
        if (turnOnRmiLogging) {
            String logBaseDir = System.getProperty(PLATFORM_CLIENT_LOG_BASEDIR);
            if (logBaseDir != null) {
                ClientLoggingManager.turnOnRmiLogging(logBaseDir);
            } else {
                ClientLoggingManager.turnOnRmiLogging();
            }
        }
    }

    // будет загружать все не кросс-платформенные библиотеки
    private static void loadLibraries() throws IOException {
        SimplexLayout.loadLibraries();
        ComBridge.loadJacobLibraries();
        ComBridge.loadJsscLibraries();
    }

    private static void initRmiClassLoader() throws IllegalAccessException, NoSuchFieldException {
        // приходится извращаться, так как RMIClassLoader использует для загрузки Spi Class.forname,
        // а это работает некорректно, поскольку JWS использует свой user-class loader,
        // а сами jar-файлы не добавляются в java.class.path
        // необходимо, чтобы ClientRMIClassLoaderSpi запускался с родным ClassLoader JWS

        Field field = RMIClassLoader.class.getDeclaredField("provider");
        field.setAccessible(true);

        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

        field.set(null, new ClientRMIClassLoaderSpi());

        // сбрасываем SecurityManager, который устанавливает JavaWS,
        // поскольку он не дает ничего делать классу ClientRMIClassLoaderSpi,
        // так как он load'ится из временного директория
        System.setSecurityManager(null);
    }

    private static void initRMISocketFactory() throws IOException {
        String timeout = System.getProperty(PLATFORM_CLIENT_CONNECTION_LOST_TIMEOUT, "7200000");

        if (RMISocketFactory.getSocketFactory() != null) {
            System.out.println(RMISocketFactory.getSocketFactory());
        }

        socketFactory = new RMITimeoutSocketFactory(Integer.valueOf(timeout));

        RMISocketFactory.setFailureHandler(new RMIFailureHandler() {
            public boolean failure(Exception ex) {
                return true;
            }
        });

        try {
            Field field = RMISocketFactory.class.getDeclaredField("factory");
            field.setAccessible(true);
            field.set(null, socketFactory);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void initSwing() throws ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException {
        //хак для решения проблемы со сканированием...
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
            @Override
            protected void enqueueKeyEvents(long after, Component untilFocused) {
                super.enqueueKeyEvents(0, untilFocused);
            }
        });

        ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
    }

    public static ClientObjectClass getBaseClass() {
        if (baseClass == null) {
            try {
                baseClass = (ClientObjectClass) ClientTypeSerializer.deserializeClientClass(
                        new DataInputStream(new ByteArrayInputStream(
                                remoteLogics.getBaseClassByteArray())));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return baseClass;
    }

    public static void clientExceptionLog(String info) throws RemoteException {
        if (remoteNavigator != null) {
            remoteNavigator.clientExceptionLog(info);
        }
    }

    public static void setStatusText(String msg) {
        if (frame != null) {
            frame.statusComponent.setText(msg);
        }
    }

    public static long getBytesSent() {
        return socketFactory.outSum;
    }

    public static long getBytesReceived() {
        return socketFactory.inSum;
    }

    private static void startSplashScreen() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                SplashScreen.start(getLogo());
            }
        });
    }

    private static void closeSplashScreen() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                SplashScreen.close();
            }
        });
    }

    public static int generateNewID() {
        try {
            return remoteLogics.generateNewID();
        } catch (RemoteException e) {
            throw new RuntimeException(getString("client.error.on.id.generation"));
        }
    }

    public static ImageIcon getMainIcon() {
        byte[] iconData = null;
        if (remoteLogics != null) {
            try {
                iconData = remoteLogics.getMainIcon();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        return loadResource(iconData, PLATFORM_CLIENT_LOGO, DEFAULT_SPLASH_PATH);
    }

    public static ImageIcon getLogo() {
        byte[] logoData = null;
        if (remoteLogics != null) {
            try {
                logoData = remoteLogics.getLogo();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        return loadResource(logoData, PLATFORM_CLIENT_LOGO, DEFAULT_SPLASH_PATH);
    }

    private static ImageIcon loadResource(byte[] resourceData, String defaultUrlSystemPropName, String defaultResourcePath) {
        ImageIcon resource = resourceData != null ? new ImageIcon(resourceData) : null;
        if (resource == null || resource.getImageLoadStatus() != MediaTracker.COMPLETE) {
            String splashUrlString = System.getProperty(defaultUrlSystemPropName);
            URL splashUrl = null;
            if (splashUrlString != null) {
                try {
                    splashUrl = new URL(splashUrlString);
                } catch (MalformedURLException ignored) {
                }
            }
            if (splashUrl != null) {
                resource = new ImageIcon(splashUrl);
            }

            if (resource == null || resource.getImageLoadStatus() != MediaTracker.COMPLETE) {
                resource = new ImageIcon(SplashScreen.class.getResource(defaultResourcePath));
            }
        }
        return resource;
    }

    public static String getMainTitle() {
        return BaseUtils.nvl(getDisplayName(), PLATFORM_TITLE);
    }

    public static String getDisplayName() {
        String title = null;
        if (remoteLogics != null) {
            try {
                title = remoteLogics.getDisplayName();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        return title;
    }

    public static String formatDate(Object date) {
        DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
        if (timeZone != null) {
            df.setTimeZone(Main.timeZone);
        }
        return df.format((Date) date);
    }

    public static void shutdown() {
        final Thread closer = new Thread(bootstrapThreadGroup, new Runnable() {
            @Override
            public void run() {
                clean();

                // закрываемся в EDT, чтобы обработались текущие события (в частности WINDOW_CLOSING)
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        System.exit(0);
                    }
                });
            }
        }, "Restarting thread...");
        closer.setDaemon(false);
        closer.start();
    }

    public static void restart() {
        restart(false);
    }

    public static void reconnect() {
        restart(true);
    }

    public static void restart(final boolean reconnect) {
        LoginAction.getInstance().setAutoLogin(reconnect);
        final Thread restarter = new Thread(bootstrapThreadGroup, new Runnable() {
            @Override
            public void run() {
                clean();

                startWorkingThreads(reconnect);
            }
        }, "Restarting thread...");
        restarter.setDaemon(false);
        restarter.start();
    }

    private static void clean() {
        pendingAspect.startRestarting();

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ConnectionLostManager.invalidate();

                    SplashScreen.close();

                    if (frame != null) {
                        frame.setVisible(false);
                        frame.dispose();
                    }
                }
            });
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        mainThreadGroup.interrupt();

        RemoteFormProxy.dropCaches();
        ClientExternalScreen.dropCaches();
        ClientNavigatorWindow.dropCaches();

        eventBus.invalidate();

        computerId = -1;
        timeZone = null;
        baseClass = null;
        frame = null;
        remoteLoader = null;
        remoteLogics = null;
        remoteNavigator = null;

        System.gc();

        pendingAspect.stopRestarting();
    }

    static class DaemonTask extends TimerTask {
        IDaemonTask task;

        public DaemonTask(IDaemonTask task) {
            this.task = task;
        }

        @Override
        public void run() {
            task.run();
        }
    }

    public interface ModuleFactory {
        MainFrame initFrame(RemoteNavigatorInterface remoteNavigator) throws ClassNotFoundException, IOException;

        void runExcel(RemoteFormInterface remoteForm);

        boolean isFull();

        SwingWorker<List<ServerInfo>, ServerInfo> getServerHostEnumerator(MutableComboBoxModel serverHostModel, String waitMessage);
    }

    public static void main(final String[] args) {
        start(args, new ModuleFactory() {
            public MainFrame initFrame(RemoteNavigatorInterface remoteNavigator) throws ClassNotFoundException, IOException {

                String forms = System.getProperty(PLATFORM_CLIENT_FORMS);
                if (forms == null) {
                    String formSet = System.getProperty(PLATFORM_CLIENT_FORMSET);
                    if (formSet == null) {
                        throw new RuntimeException(getString("client.property.not.set"));
                    }
                    forms = remoteNavigator.getForms(formSet);
                    if (forms == null) {
                        throw new RuntimeException(getString("client.forms.not.found", formSet));
                    }
                }

                return new SimpleMainFrame(remoteNavigator, forms);
            }

            public void runExcel(RemoteFormInterface remoteForm) {
                // not supported
            }

            public boolean isFull() {
                return false;
            }

            public SwingWorker<List<ServerInfo>, ServerInfo> getServerHostEnumerator(MutableComboBoxModel serverHostModel, String waitMessage) {
                return null;
            }
        });
    }
}