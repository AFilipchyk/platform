package platform.base;

import platform.interop.SerializableImageIconHolder;

import javax.swing.*;
import java.io.*;

public class IOUtils {
    public static final int BUFFER_SIZE = 16384;
    public static final String lineSeparator = System.getProperty("line.separator", "\n");

    public static byte[] readBytesFromStream(InputStream in) throws IOException {
        return readBytesFromStream(in, Integer.MAX_VALUE);
    }

    public static byte[] readBytesFromStream(InputStream in, int maxLength) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte buffer[] = new byte[BUFFER_SIZE];

        int left = maxLength;
        int readCount;
        while (left > 0 && (readCount = in.read(buffer, 0, Math.min(buffer.length, left))) != -1) {
            out.write(buffer, 0, readCount);
            left -= readCount;
        }

        return out.toByteArray();
    }

    public static byte[] getFileBytes(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            return readBytesFromStream(in);
        } finally {
            in.close();
        }
    }

    public static void putFileBytes(File file, byte[] array) throws IOException {
        putFileBytes(file, array, 0, array.length);
    }

    public static void putFileBytes(File file, byte[] array, int off, int len) throws IOException {
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        OutputStream out = new FileOutputStream(file);
        try {
            out.write(array, off, len);
        } finally {
            out.close();
        }
    }

    public static String readStreamToString(InputStream inStream) throws IOException {
        return readStreamToString(inStream, null);
    }

    public static String readStreamToString(InputStream inStream, String charsetName) throws IOException {
        StringBuilder strBuf = new StringBuilder();

        BufferedReader in = new BufferedReader(charsetName == null
                                               ? new InputStreamReader(inStream)
                                               : new InputStreamReader(inStream, charsetName));
        try {
            String line;
            while ((line = in.readLine()) != null) {
                strBuf.append(line).append(lineSeparator);
            }
        } finally {
            inStream.close();
        }

        return strBuf.toString();
    }

    public static void writeImageIcon(DataOutputStream outStream, ImageIcon image) throws IOException {
        new ObjectOutputStream(outStream).writeObject(new SerializableImageIconHolder(image));
    }

    public static ImageIcon readImageIcon(InputStream inStream) throws IOException {
        ObjectInputStream in = new ObjectInputStream(inStream);
        try {
            return ((SerializableImageIconHolder) in.readObject()).getImage();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
