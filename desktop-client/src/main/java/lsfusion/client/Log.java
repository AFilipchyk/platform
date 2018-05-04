package lsfusion.client;

import lsfusion.base.ExceptionUtils;
import lsfusion.client.rmi.ConnectionLostManager;
import org.apache.log4j.Logger;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.text.Caret;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import static lsfusion.client.ClientResourceBundle.getString;

public final class Log {
    public static Logger logger = ClientLoggers.clientLogger;

    public static void log(String message) {
        logger.info(message + '\n' + ExceptionUtils.getStackTrace());
    }

    private static String text = "";

    private static WeakReference<LogPanel> logPanelRef = new WeakReference<>(null);

    public static JPanel recreateLogPanel() {
        LogPanel logPanel = new LogPanel();

        logPanelRef = new WeakReference<>(logPanel);
        text = "";

        return logPanel;
    }

    private static LogPanel getLogPanel() {
        LogPanel logPanel = logPanelRef.get();
        // пока таким образом определим есть ли он на экране
        if (logPanel != null && logPanel.getTopLevelAncestor() != null) {
            return logPanel;
        }

        return null;
    }

    private static void print(String itext) {
        text += itext;
        stateChanged();
    }

    private static void println(String itext) {
        print(itext + '\n');
    }

    private static void printmsg(String itext) {
        println(getMsgHeader() + itext + getMsgFooter());
    }

    private static String getMsgHeader() {
        return "--- " + DateFormat.getInstance().format(new Date(System.currentTimeMillis())) + " ---\n";
    }

    private static String getMsgFooter() {
        return "";
    }

    private static void stateChanged() {
        LogPanel logPanel = getLogPanel();
        if (logPanel != null) {
            logPanel.updateText(text);
        }
    }

    private static void provideSuccessFeedback(String message) {
        LogPanel logPanel = getLogPanel();
        if (logPanel != null) {
            logPanel.setTemporaryBackground(Color.green);
        }
    }

    private static void provideErrorFeedback() {
        LogPanel logPanel = getLogPanel();
        if (logPanel != null) {
            logPanel.setTemporaryBackground(Color.red);
            logPanel.provideErrorFeedback();
        }
    }

    public static void message(String message) {
        message(message, true);
    }

    public static void message(String message, boolean successFeedback) {
        printmsg(message);
        logger.info(message);
        if (successFeedback) {
            provideSuccessFeedback(message);
        }
    }

    public static void error(String message) {
        error(message, null, null, false);
    }

    public static void error(String message, Throwable t) {
        error(message, t, false);
    }

    public static void error(String message, String trace) {
        error(message, null, null, trace, false);
    }

    public static void error(String message, List<String> titles, List<List<String>> data, boolean warning) {
        error(message, titles, data, "", warning);
    }

    public static void error(String message, Throwable t, boolean forcedShowError) {
        error(message, null, null, ExceptionUtils.getStackTraceString(t), forcedShowError);
    }

    private static void error(String message, List<String> titles, List<List<String>> data, String trace, boolean warning) {
        error(message, titles, data, trace, false, warning);
    }

    private static void error(String message, List<String> titles, List<List<String>> data, String trace, boolean forcedShowError, boolean warning) {
        if (!forcedShowError && ConnectionLostManager.isConnectionLost()) {
            return;
        }

        SwingUtils.assertDispatchThread();

        printmsg(message);
        logger.error(message);

        provideErrorFeedback();

        if (Main.frame == null) {
            return;
        }
        
        JPanel labelPanel = new JPanel();
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));
        JLabel titlePanel = new JLabel(toHtml(message));
        double screenWidth = Main.frame.getRootPane().getWidth() * 0.9;
        double titleWidth = titlePanel.getPreferredSize().getWidth();
        double titleHeight = titlePanel.getPreferredSize().getHeight();
        titlePanel.setPreferredSize(new Dimension((int) Math.min(screenWidth, titleWidth), (int) (titleHeight * Math.ceil(titleWidth / screenWidth))));
        labelPanel.add(titlePanel);
        labelPanel.add(Box.createHorizontalGlue());
        
        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        messagePanel.add(labelPanel);
        if (data != null) {
            messagePanel.add(new JScrollPane(createDataTable(titles, data)));
        }

        JTextArea taErrorText = new JTextArea(trace, 7, 60);
        taErrorText.setFont(new Font("Tahoma", Font.PLAIN, 12));
        taErrorText.setForeground(Color.RED);

        JPanel textWithLine = new JPanel();
        textWithLine.setLayout(new BorderLayout(10, 10));
        textWithLine.add(new JSeparator(), BorderLayout.NORTH);
        textWithLine.add(new JScrollPane(taErrorText));

        final JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setVisible(false);
        south.add(Box.createVerticalStrut(10));

        south.add(textWithLine);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(messagePanel);
        mainPanel.add(south);

        String opt[];
        if (trace.length() > 0) {
            opt = new String[]{"OK", getString("client.more")};
        } else {
            opt = new String[]{"OK"};
        }
        final JOptionPane optionPane = new JOptionPane(mainPanel, warning ? JOptionPane.WARNING_MESSAGE : JOptionPane.ERROR_MESSAGE,
                                     JOptionPane.YES_NO_OPTION,
                                     null,
                                     opt,
                                     "OK");

        final JDialog dialog = new JDialog(Main.frame, Main.getMainTitle(), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(optionPane);
        dialog.setMinimumSize(dialog.getPreferredSize());
        dialog.pack();

        optionPane.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                Object value = optionPane.getValue();
                if (dialog.isVisible() && (value.equals("OK") || value.equals(-1))) {
                    dialog.dispose();
                } else if (value.equals(getString("client.more"))) {
                    boolean southWasVisible = south.isVisible();
                    south.setVisible(!southWasVisible);
                    optionPane.setValue(JOptionPane.UNINITIALIZED_VALUE);
                    dialog.setMinimumSize(dialog.getPreferredSize());
                    if (southWasVisible) {
                        dialog.pack();
                    }
                }
            }
        });


        //центрируем на экране
        dialog.setLocationRelativeTo(dialog.getOwner());

        dialog.setVisible(true);
    }

    private static Component createDataTable(List<String> titles, List<List<String>> data) {
        int size = data.size();
        Object columnNames[] = titles.toArray();
        Object dataArray[][] = new Object[size][];
        int i = 0;
        for (List<String> dataRow : data) {
            dataArray[i] = dataRow.toArray();
            i++;
        }
        JTable table = new JTable(dataArray, columnNames);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setPreferredScrollableViewportSize(
                new Dimension(
                        table.getPreferredScrollableViewportSize().width,
                        Math.min(table.getPreferredScrollableViewportSize().height, dataArray.length * table.getRowHeight())
                ));

        Caret caret = new DefaultCaret()
        {
            public void focusGained(FocusEvent e)
            {
                setVisible(true);
                setSelectionVisible(true);
            }
        };
        caret.setBlinkRate( UIManager.getInt("TextField.caretBlinkRate") );

        JTextField textField = new JTextField();
        textField.setEditable(false);
        textField.setCaret(caret);
        textField.setBorder(new LineBorder(Color.BLACK));

        DefaultCellEditor dce = new DefaultCellEditor(textField);
        for(int j = 0; j < table.getColumnModel().getColumnCount(); j++) {
            table.getColumnModel().getColumn(j).setCellEditor(dce); 
        }
       
        table.setFocusable(true);
        return table;
    }

    private static String toHtml(String message) {
        StringBuilder htmlMessage = new StringBuilder("<html><font size=+1>");
        for (int i = 0; i < message.length(); i++) {
            char ch = message.charAt(i);
            if (ch == '\n') {
                htmlMessage.append("<br>");
            } else {
                htmlMessage.append(ch);
            }
        }
        htmlMessage.append("</font></html>");
        return htmlMessage.toString();
    }
}
