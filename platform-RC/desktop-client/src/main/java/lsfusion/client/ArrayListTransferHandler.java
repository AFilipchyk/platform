package lsfusion.client;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;

public class ArrayListTransferHandler extends TransferHandler {
    DataFlavor localArrayListFlavor, serialArrayListFlavor;

    String localArrayListType = DataFlavor.javaJVMLocalObjectMimeType
            + ";class=java.util.ArrayList";

    JList source = null;
    JList destination = null;

    int[] indices = null;

    int addIndex = -1; //Location where items were added

    int addCount = 0; //Number of items added

    public ArrayListTransferHandler() {
        try {
            localArrayListFlavor = new DataFlavor(localArrayListType);
        } catch (ClassNotFoundException e) {
            System.out.println("ArrayListTransferHandler: unable to create data flavor");
        }
        serialArrayListFlavor = new DataFlavor(ArrayList.class, "ArrayList");
    }

    public boolean importData(JComponent c, Transferable t) {
        JList target;
        ArrayList alist;
        if (!canImport(c, t.getTransferDataFlavors())) {
            return false;
        }
        try {
            target = (JList) c;
            if (hasLocalArrayListFlavor(t.getTransferDataFlavors())) {
                alist = (ArrayList) t.getTransferData(localArrayListFlavor);
            } else if (hasSerialArrayListFlavor(t.getTransferDataFlavors())) {
                alist = (ArrayList) t.getTransferData(serialArrayListFlavor);
            } else {
                return false;
            }
        } catch (UnsupportedFlavorException ufe) {
            System.out.println("importData: unsupported data flavor");
            return false;
        } catch (IOException ioe) {
            System.out.println("importData: I/O exception");
            return false;
        }

        //At this point we use the same code to retrieve the data
        //locally or serially.

        //We'll drop at the current selected index.
        int index = target.getSelectedIndex();

        //Prevent the user from dropping data back on itself.
        //For example, if the user is moving items #4,#5,#6 and #7 and
        //attempts to insert the items after item #5, this would
        //be problematic when removing the original items.
        //This is interpreted as dropping the same data on itself
        //and has no effect.
        if (source.equals(target)) {
            if (indices != null && index >= indices[0]
                    && index <= indices[indices.length - 1]) {
                indices = null;
                return true;
            }
        }

        DefaultListModel listModel = (DefaultListModel) target.getModel();
        int max = listModel.getSize();
        if (index < 0) {
            index = max;
        } else {
            // убрал, чтобы в соседний список вставлялось так же, как в свой, т.е. чтобы можно было вставить первым элементом
//            if (listModel.indexOf(alist.get(0)) < index)
//                index++;
            if (index > max) {
                index = max;
            }
        }
        addIndex = index;
        addCount = alist.size();
        for (Object anAlist : alist) {
            listModel.add(index++, anAlist);
        }

        destination = (JList) c;
        ((JList) c).clearSelection();
        //c.requestFocus();
        //c.getComponents()[index].requestFocus();
        c.setFocusable(false);
        return true;
    }

    protected void exportDone(JComponent c, Transferable data, int action) {
        if ((action == MOVE) && (indices != null)) {
            DefaultListModel model = (DefaultListModel) source.getModel();

            //If we are moving items around in the same list, we
            //need to adjust the indices accordingly since those
            //after the insertion point have moved.
            if ((addCount > 0) && (source.equals(destination))) {
                for (int i = 0; i < indices.length; i++) {
                    if (indices[i] > addIndex) {
                        indices[i] += addCount;
                    }
                }
            }
            for (int i = indices.length - 1; i >= 0; i--)
                model.remove(indices[i]);
        }
        indices = null;
        addIndex = -1;
        addCount = 0;
        ((JList) c).clearSelection();
    }

    private boolean hasLocalArrayListFlavor(DataFlavor[] flavors) {
        if (localArrayListFlavor == null) {
            return false;
        }

        for (DataFlavor flavor : flavors) {
            if (flavor.equals(localArrayListFlavor)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSerialArrayListFlavor(DataFlavor[] flavors) {
        if (serialArrayListFlavor == null) {
            return false;
        }

        for (DataFlavor flavor : flavors) {
            if (flavor.equals(serialArrayListFlavor)) {
                return true;
            }
        }
        return false;
    }

    public boolean canImport(JComponent c, DataFlavor[] flavors) {
        return hasLocalArrayListFlavor(flavors) || hasSerialArrayListFlavor(flavors);
    }

    protected Transferable createTransferable(JComponent c) {
        if (c instanceof JList) {
            source = (JList) c;
            indices = source.getSelectedIndices();
            ArrayList values = (ArrayList) source.getSelectedValuesList();
            if (values == null || values.isEmpty()) {
                return null;
            }
            return new ArrayListTransferable(values);
        }
        return null;
    }

    public int getSourceActions(JComponent c) {
        return MOVE;
    }

    public class ArrayListTransferable implements Transferable {
        ArrayList data;

        public ArrayListTransferable(ArrayList alist) {
            data = alist;
        }

        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return data;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{localArrayListFlavor,
                    serialArrayListFlavor};
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return localArrayListFlavor.equals(flavor) || serialArrayListFlavor.equals(flavor);
        }
    }
}