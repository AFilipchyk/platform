package lsfusion.client;

import lsfusion.base.NavigatorInfo;
import lsfusion.base.SystemUtils;
import lsfusion.client.remote.proxy.RemoteBusinessLogicProxy;
import lsfusion.interop.RemoteLogicsInterface;
import lsfusion.interop.RemoteLogicsLoaderInterface;
import lsfusion.interop.exceptions.LockedException;
import lsfusion.interop.exceptions.LoginException;
import lsfusion.interop.exceptions.RemoteInternalException;
import lsfusion.interop.navigator.RemoteNavigatorInterface;

import javax.swing.*;
import java.awt.*;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.CancellationException;

import static lsfusion.client.ClientResourceBundle.getString;
import static lsfusion.client.StartupProperties.*;

public final class LoginAction {
    private static class LoginActionHolder {
        private static LoginAction instance = new LoginAction();
    }

    public static LoginAction getInstance() {
        return LoginActionHolder.instance;
    }

    //login statuses
    final static int OK = 0;
    final static int HOST_NAME_ERROR = 1;
    final static int CONNECT_ERROR = 2;
    final static int SERVER_ERROR = 3;
    final static int PENDING_RESTART_WARNING = 4;
    final static int ERROR = 5;
    final static int CANCELED = 6;
    final static int LOGIN_ERROR = 7;
    final static int LOCKED_ERROR = 8;

    private boolean autoLogin;
    public LoginInfo loginInfo;
    private LoginDialog loginDialog;

    private RemoteLogicsInterface remoteLogics;
    private int computerId;
    private RemoteNavigatorInterface remoteNavigator;

    private LoginAction() {
        autoLogin = Boolean.parseBoolean(getSystemPropertyWithJNLPFallback(LSFUSION_CLIENT_AUTOLOGIN));
        String serverHost = getSystemPropertyWithJNLPFallback(LSFUSION_CLIENT_HOSTNAME);
        String serverPort = getSystemPropertyWithJNLPFallback(LSFUSION_CLIENT_HOSTPORT);
        String serverDB = getSystemPropertyWithJNLPFallback(LSFUSION_CLIENT_EXPORTNAME);
        String userName = getSystemPropertyWithJNLPFallback(LSFUSION_CLIENT_USER);
        String password = getSystemPropertyWithJNLPFallback(LSFUSION_CLIENT_PASSWORD);
        loginInfo = new LoginInfo(serverHost, serverPort, serverDB, userName, password);

        loginDialog = new LoginDialog(loginInfo);
    }

    public String getSystemPropertyWithJNLPFallback(String propertyName) {
        String value = System.getProperty(propertyName);
        return value != null ? value : System.getProperty("jnlp." + propertyName);
    }

    public boolean login() throws MalformedURLException, NotBoundException, RemoteException {
        boolean needData = loginInfo.getServerHost() == null || loginInfo.getServerPort() == null || loginInfo.getServerDB() == null || loginInfo.getUserName() == null || loginInfo.getPassword() == null;
        if (!autoLogin || needData) {
            loginDialog.setAutoLogin(autoLogin);
            loginInfo = loginDialog.login();
        }

        if (loginInfo == null) {
            return false;
        }

        int status = connect();

        while (!(status == OK)) {
            switch (status) {
                case HOST_NAME_ERROR:
                    loginDialog.setWarningMsg(getString("errors.check.server.address"));
                    break;
                case CONNECT_ERROR:
                    loginDialog.setWarningMsg(getString("errors.error.connecting.to.the.server"));
                    break;
                case SERVER_ERROR:
                    loginDialog.setWarningMsg(getString("errors.internal.server.error"));
                    break;
                case PENDING_RESTART_WARNING:
                    loginDialog.setWarningMsg(getString("errors.server.reboots"));
                    break;
                case ERROR:
                    loginDialog.setWarningMsg(getString("errors.error.connecting"));
                    break;
                case CANCELED:
                    loginDialog.setWarningMsg(getString("errors.error.cancel"));
                    break;
                case LOGIN_ERROR:
                    loginDialog.setWarningMsg(getString("errors.check.login.and.password"));
                    break;
                case LOCKED_ERROR:
                    loginDialog.setWarningMsg(getString("errors.locked.user"));
                    break;
            }
            loginDialog.setAutoLogin(false);
            loginInfo = loginDialog.login();
            if (loginInfo == null) {
                return false;
            }
            status = connect();
        }

        return true;
    }

    private int connect() {
        RemoteLogicsLoaderInterface remoteLoader;
        RemoteLogicsInterface remoteLogics;
        int computerId;
        RemoteNavigatorInterface remoteNavigator;

        try {
            //Нужно сразу инициализировать Main.remoteLoader, т.к. используется для загрузки классов в ClientRMIClassLoaderSpi
            Main.remoteLoader = remoteLoader = new ReconnectWorker(loginInfo.getServerHost(), loginInfo.getServerPort(), loginInfo.getServerDB()).connect();
            if (remoteLoader == null) {
                return CANCELED;
            }
            RemoteLogicsInterface remote = remoteLoader.getLogics();

            remoteLogics = new RemoteBusinessLogicProxy(remote);
            computerId = remoteLogics.getComputer(SystemUtils.getLocalHostName());

            Object notClassic = Toolkit.getDefaultToolkit().getDesktopProperty("win.xpstyle.themeActive");
            String osVersion = System.getProperty("os.name") + (UIManager.getLookAndFeel().getID().equals("Windows")
                    && (notClassic instanceof Boolean && !(Boolean) notClassic) ? " Classic" : "");
            String processor = System.getenv("PROCESSOR_IDENTIFIER");

            String architecture = System.getProperty("os.arch");
            if (osVersion.startsWith("Windows")) {
                String arch = System.getenv("PROCESSOR_ARCHITECTURE");
                String wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432");
                architecture = arch.endsWith("64") || wow64Arch != null && wow64Arch.endsWith("64") ? "x64" : "x32";
            }

            Integer cores = Runtime.getRuntime().availableProcessors();
            com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean)
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            Integer physicalMemory = (int) (os.getTotalPhysicalMemorySize() / 1048576);
            Integer totalMemory = (int) (Runtime.getRuntime().totalMemory() / 1048576);
            Integer maximumMemory = (int) (Runtime.getRuntime().maxMemory() / 1048576);
            Integer freeMemory = (int) (Runtime.getRuntime().freeMemory() / 1048576);
            String javaVersion = SystemUtils.getJavaVersion() + " " + System.getProperty("sun.arch.data.model") + " bit";
            
            String language = Locale.getDefault().getLanguage();
            String country = Locale.getDefault().getCountry();
            
            String screenSize = null;
            Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
            if(dimension != null) {
                screenSize = (int) dimension.getWidth() + "x" + (int) dimension.getHeight();
            }

            remoteNavigator = remoteLogics.createNavigator(Main.module.isFull(), new NavigatorInfo(loginInfo.getUserName(),
                    loginInfo.getPassword(), computerId, SystemUtils.getLocalHostIP(), osVersion, processor, architecture,
                    cores, physicalMemory, totalMemory, maximumMemory, freeMemory, javaVersion, screenSize, language, country), true);
            if (remoteNavigator == null) {
                Main.remoteLoader = null;
                return PENDING_RESTART_WARNING;
            }
        } catch (CancellationException ce) {
            return CANCELED;
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return HOST_NAME_ERROR;
        } catch (RemoteInternalException e) {
            e.printStackTrace();
            return SERVER_ERROR;
        } catch (LoginException e) {
            e.printStackTrace();
            return LOGIN_ERROR;
        } catch (LockedException e) {
            e.printStackTrace();
            return LOCKED_ERROR;
        } catch (Throwable e) {
            e.printStackTrace();
            return ERROR;
        }

        this.remoteLogics = remoteLogics;
        this.remoteNavigator = remoteNavigator;
        this.computerId = computerId;

        return OK;
    }

    public RemoteLogicsInterface getRemoteLogics() {
        return remoteLogics;
    }

    public int getComputerId() {
        return computerId;
    }

    public RemoteNavigatorInterface getRemoteNavigator() {
        return remoteNavigator;
    }

    public void setAutoLogin(boolean autoLogin) {
        this.autoLogin = autoLogin;
    }

    public boolean needShutdown() {
        try {
            Integer newApiVersion = remoteLogics.getApiVersion();
            boolean needRestart = Main.apiVersion != null && newApiVersion != null && !newApiVersion.equals(Main.apiVersion);
            Main.apiVersion = newApiVersion;
            return needRestart;
        } catch (RemoteException e) {
            return false;
        }
    }
}
