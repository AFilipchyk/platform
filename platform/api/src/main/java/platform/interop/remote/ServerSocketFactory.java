package platform.interop.remote;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;

public class ServerSocketFactory extends RMISocketFactory implements RMIServerSocketFactory, Serializable {

    public Socket createSocket(String host, int port) throws IOException {
        return new CountZipSocket(host, port);
    }

    public ServerSocket createServerSocket(int port) throws IOException {
        return new CountZipServerSocket(port);
    }
}
