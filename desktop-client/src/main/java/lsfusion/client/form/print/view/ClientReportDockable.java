package lsfusion.client.form.print.view;

import bibliothek.gui.dock.common.event.CKeyboardListener;
import bibliothek.gui.dock.common.intern.CDockable;
import com.google.common.base.Throwables;
import lsfusion.client.base.view.ClientDockable;
import lsfusion.client.base.view.DockableManager;
import lsfusion.interop.form.stat.report.ReportGenerator;
import lsfusion.interop.form.stat.report.FormPrintType;
import lsfusion.interop.form.stat.report.ReportGenerationData;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRXlsAbstractExporterParameter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.swing.JRViewer;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;

public class ClientReportDockable extends ClientDockable {
    public Integer pageCount;
    public ClientReportDockable(ReportGenerationData generationData, DockableManager dockableManager, String printerName, EditReportInvoker editInvoker) throws ClassNotFoundException, IOException {
        super(null, dockableManager);

        try {
            final JasperPrint print = new ReportGenerator(generationData).createReport(FormPrintType.PRINT);
            print.setProperty(JRXlsAbstractExporterParameter.PROPERTY_DETECT_CELL_TYPE, "true");
            this.pageCount = print.getPages().size();
            final ReportViewer reportViewer = new ReportViewer(print, printerName, editInvoker);
            setContent(print.getName(), prepareViewer(reportViewer));
            addKeyboardListener(new CKeyboardListener() {
                @Override
                public boolean keyPressed(CDockable cDockable, KeyEvent keyEvent) {
                    return false;
                }

                @Override
                public boolean keyReleased(CDockable cDockable, KeyEvent keyEvent) {
                    int KEY_P = 80;
                    boolean ctrlPressed = (keyEvent.getModifiers() & InputEvent.CTRL_MASK) != 0;
                    if(keyEvent.getKeyCode() == KEY_P && ctrlPressed) {
                        reportViewer.clickBtnPrint();
                    }
                    return false;
                }

                @Override
                public boolean keyTyped(CDockable cDockable, KeyEvent keyEvent) {
                    return false;
                }
            });
        } catch (JRException e) {
            Throwables.propagate(e);
        }
    }

    // из файла
    public ClientReportDockable(File file, DockableManager dockableManager) throws JRException {
        super(null, dockableManager);
        setContent(file.getName(), prepareViewer(new JRViewer((JasperPrint) JRLoader.loadObject(file))));
    }

    private JRViewer prepareViewer(final JRViewer viewer) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                viewer.setZoomRatio(1);
            }
        });
        return viewer;
    }

    @Override
    public void onOpened() {
    }
}
