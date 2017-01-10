package lsfusion.server.daemons;

import lsfusion.interop.event.AbstractDaemonTask;
import lsfusion.server.ServerLoggers;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.Serializable;

public class DiscountCardDaemonTask extends AbstractDaemonTask implements Serializable, KeyEventDispatcher {

    public static final String SCANNER_SID = "SCANNER";
    //public static final String CARD_SID = "CARD";
    private static boolean recording;
    private static boolean isNew;
    private static String input = "";

    public DiscountCardDaemonTask() {
    }

    @Override
    public void start() {
        install();
    }

    @Override
    public void stop() {
        uninstall();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if(e.getID() == KeyEvent.KEY_PRESSED) {
            if(e.getKeyChar() == ';' || e.getKeyChar() == 'ж' || e.getKeyChar() == '%') {
                if(!recording) {
                    recording = true;
                    isNew = e.getKeyChar() == '%';
                }
                e.consume();
            }
            else if(e.getKeyChar() == '\n') {
                recording = false;
                if(input.length() > 2 && input.charAt(input.length() - 2) == 65535
                        && ((input.charAt(input.length() - 1) == '?') || input.charAt(input.length() - 1) == ',')) {
                    if (isNew) {
                        if (input.startsWith("70833700"))
                            input = "70833700";
                        else if (input.startsWith('\uFFFF' + "Z7083370"))
                            input = "Z7083370";
                        else
                            input = input.substring(0, input.length() - 2);
                    } else {
                        input = input.substring(0, input.length() - 2);
                    }
                    ServerLoggers.systemLogger.info(input);
                    eventBus.fireValueChanged(SCANNER_SID, input);
                    input = "";
                    e.consume();
                }
            } else if(recording) {
                input += e.getKeyChar();
                e.consume();
            }
        }
        return false;
    }

    private void install() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    }

    private void uninstall() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    }

}
