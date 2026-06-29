package loadbalancer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class BackendServer implements Backend {
    private final ServerSocket serverSocket;
    private final String address;
    private volatile boolean isAlive = true;

    public BackendServer(int port, String address) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.address = address;
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public String getAddress() {
        return address;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void setAlive(boolean alive) {
        this.isAlive = alive;
    }

    public void stop() throws IOException {
        serverSocket.close();
    }

    public void start() {
        while(true) {
            try {
                Socket connection = serverSocket.accept();
                new Thread(() -> {
                    try {
                        String response = "HTTP/1.1 200 OK\r\n\r\nHello from port: " + getPort();
                        connection.getOutputStream().write(response.getBytes());
                        connection.close();
                    } catch (IOException e) {
                        System.err.println("Error: " + e.getMessage());
                    }
                }).start();
            } catch (IOException e) {
                return;
            }
        }
    }
}
