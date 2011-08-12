package platform.base;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class OSUtils {

    public static String getResourcePath(String resource, String path, Class<?> cls, boolean overwrite, boolean appendPath) throws IOException {

        File file = getUserFile(appendPath ? ClassUtils.resolveName(cls, path + resource) : resource);
        if (overwrite || !file.exists()) { // пока сделаем так, хотя это не очень правильно, так как желательно все-таки обновлять dll'ки

            InputStream in = cls.getResourceAsStream(path + resource);

            FileOutputStream out = new FileOutputStream(file);

            byte[] b = new byte[4096];
            int read;
            while ((read = in.read(b)) != -1) {
                out.write(b, 0, read);
            }

            in.close();
            out.close();
        }

        return file.getAbsolutePath();
    }

    public static String getLibraryPath(String libName, String path, Class<?> cls) throws IOException {

        String system = System.getProperty("os.name");
        String libExtension =
           "Linux".equals(system) ? ".so" : ".dll";
        String libPath =
           "Linux".equals(system) ? "ux" : "win";
        libPath += is64Arch() ? "64" : "32";

        return getResourcePath(libPath + '\\' + libName + libExtension, path, cls, false, false); // будем считать, что в library зашифрована вер
    }

    public static boolean is64Arch() {
        String osArch = System.getProperty("os.arch");
        return osArch != null && osArch.contains("64");
    }

    public static File getLocalClassesDir() {
        return getUserDir();
    }

    public static void loadClass(String className, String path, Class<?> cls) throws IOException {
        getResourcePath(className + ".class", path, cls, true, true); // для класса обновляем, поскольку иначе изменения не будут обновляться
    }

    public static void loadLibrary(String libName, String path, Class<?> cls) throws IOException {
        System.load(getLibraryPath(libName, path, cls));
    }

    public static File getUserDir() {

        String userDirPath = System.getProperty("user.home", "") + "/.fusion" ;
        File userDir = new File(userDirPath);
        if (!userDir.exists()) {
            userDir.mkdirs(); // создаем каталог, на всякий случай, чтобы каждому не приходилось его создавать
        }

        return userDir;
    }

    public static File getUserFile(String fileName) {
        return getUserFile(fileName, true);
    }

    public static File getUserFile(String fileName, boolean makeDir) {
        File userFile = new File(getUserDir().getAbsolutePath() + "/" + fileName);
        File userDir = userFile.getParentFile();
        if (makeDir && !userDir.exists())
            userDir.mkdirs();
        return userFile;
    }

    public static String getLocalHostName() {
        String name = null;
        try {
            InetAddress address = InetAddress.getLocalHost();
            name = address.getCanonicalHostName();
        } catch (UnknownHostException uhe) {
        }

        if (name == null || name.trim().isEmpty()) {
            name = "localhost";
        }

        return name;
    }
}
