package lsfusion.client;

import com.jhlabs.image.BlurFilter;
import lsfusion.client.dock.ClientFormDockable;
import lsfusion.client.form.ClientFormController;
import lsfusion.client.form.RmiQueue;
import lsfusion.interop.action.ReportPath;
import lsfusion.interop.form.RemoteFormInterface;
import lsfusion.interop.form.ReportGenerationData;
import lsfusion.interop.navigator.RemoteNavigatorInterface;
import org.jdesktop.jxlayer.JXLayer;
import org.jdesktop.jxlayer.plaf.effect.BufferedImageOpEffect;
import org.jdesktop.jxlayer.plaf.ext.LockableUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.*;
import java.util.List;
import java.util.Scanner;

import static lsfusion.client.ClientResourceBundle.getString;
import static lsfusion.client.Main.fusionDir;

public abstract class MainFrame extends JFrame {

    public interface FormCloseListener {
        void formClosed();
    }

    protected File baseDir;
    public RemoteNavigatorInterface remoteNavigator;
    public JLabel statusComponent;
    public JComponent status;

    public static boolean forbidDuplicateForms;

    private LockableUI lockableUI;

    public MainFrame(final RemoteNavigatorInterface remoteNavigator) throws IOException {
        super();

        this.remoteNavigator = remoteNavigator;

        setIconImages(Main.getMainIcons());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        updateUser();

        statusComponent = new JLabel();
        status = new JPanel(new BorderLayout());
        status.add(statusComponent, BorderLayout.CENTER);

        loadLayout();

        initUIHandlers(remoteNavigator);

        installLockableLayer();
    }

    private void installLockableLayer() {
    }

    public void setLocked(boolean locked) {
        lockableUI.setLocked(locked);
    }

    protected void setContent(JComponent content) {
        assert lockableUI == null;

        add(content);

        lockableUI = new LockableUI(new BufferedImageOpEffect(
                new ConvolveOp(new Kernel(1, 1, new float[]{0.5f}), ConvolveOp.EDGE_NO_OP, null), new BlurFilter()));

        JXLayer layer = new JXLayer(getContentPane(), lockableUI);
        layer.setFocusable(false);

        setContentPane(layer);
    }

    private void initUIHandlers(final RemoteNavigatorInterface remoteNavigator) {
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent event) {
                int confirmed = JOptionPane.showConfirmDialog(MainFrame.this,
                                                              getString("quit.do.you.really.want.to.quit"),
                                                              getString("quit.confirmation"),
                                                              JOptionPane.YES_NO_OPTION);
                if (confirmed == JOptionPane.YES_OPTION) {
                    Main.shutdown();
                }
            }

            @Override
            public void windowClosed(WindowEvent e) {
                //windowClosing не срабатывает, если просто вызван dispose,
                //поэтому сохраняем лэйаут в windowClosed
                saveLayout();
            }
        });
    }

    private void loadLayout() {
        if (Main.logicsName != null) {
            baseDir = new File(fusionDir, Main.logicsName);
        } else {
            //по умолчанию
            baseDir = fusionDir;
        }

        try {
            File layoutFile = new File(baseDir, "dimension.txt");
            if (layoutFile.exists()) {
                Scanner in = new Scanner(new FileReader(layoutFile));
                int wWidth = in.nextInt();
                int wHeight = in.nextInt();
                setSize(wWidth, wHeight);
                in.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Dimension size = Toolkit.getDefaultToolkit().getScreenSize().getSize();
            setSize(size.width, size.height - 30);
        }
    }

    private void saveLayout() {
        baseDir.mkdirs();

        try {
            FileWriter out = new FileWriter(new File(baseDir, "dimension.txt"));

            out.write(getWidth() + " " + getHeight() + '\n');

            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateUser() throws IOException {
        LoginAction loginAction = LoginAction.getInstance();
        DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(remoteNavigator.getCurrentUserInfoByteArray()));
        setTitle(Main.getMainTitle() + " - " + inputStream.readUTF() + " (" + loginAction.loginInfo.getServerHost() + ":" + loginAction.loginInfo.getServerPort() + ")");
        forbidDuplicateForms = remoteNavigator.isForbidDuplicateForms();
    }

    public abstract Integer runReport(ClientFormController formController, List<ReportPath> customReportPathList, String formSID, boolean isModal, ReportGenerationData generationData) throws IOException, ClassNotFoundException;

    public Integer runReport(boolean isModal, ReportGenerationData generationData, EditReportInvoker editInvoker) throws IOException, ClassNotFoundException {
        return runReport(isModal, generationData, null, editInvoker);
    }
    public abstract Integer runReport(boolean isModal, ReportGenerationData generationData, String printerName, EditReportInvoker editInvoker) throws IOException, ClassNotFoundException;

    public abstract ClientFormDockable runForm(String canonicalName, String formSID, boolean forbidDuplicate, RemoteFormInterface remoteForm, byte[] firstChanges, FormCloseListener closeListener);

}