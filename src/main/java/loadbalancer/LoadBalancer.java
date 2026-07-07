package loadbalancer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class LoadBalancer {
    private final ServerSocket serverSocket;
    private final BalancingStrategy balancingStrategy;
    private final List<Backend> listOfServers;

    public LoadBalancer(int port, List<Backend> listOfServers, BalancingStrategy balancingStrategy) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.listOfServers = listOfServers;
        this.balancingStrategy = balancingStrategy;
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void start() {
        while (true) {
            try {
                Socket connection = serverSocket.accept();
                new Thread(() -> handleRequest(connection)).start();
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    public void startHealthCheck(int interval) {
        while(true) {
            for (Backend server : listOfServers) {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL("http://" + server.getAddress() + ":" + server.getPort());
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    int statusCode = connection.getResponseCode();
                    server.setAlive(statusCode == 200);
                } catch (IOException e) {
                    server.setAlive(false);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void handleRequest(Socket connection) {
        try (connection) {
            for (int i = 0; i < listOfServers.size(); i++) {
                List<Backend> healthyServers = healthyServers();

                if (healthyServers.isEmpty()) {
                    break;
                }

                Backend server = balancingStrategy.select(healthyServers);
                try (Socket backendSocket = new Socket(server.getAddress(), server.getPort())) {
                    backendSocket.getInputStream().transferTo(connection.getOutputStream());
                    return;
                } catch (IOException e) {
                    server.setAlive(false);
                }
            }
            sendBadGateway(connection);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private List<Backend> healthyServers() {
        List<Backend> healthyServers = new ArrayList<>();
        for (Backend server : listOfServers) {
            if (server.isAlive()) {
                healthyServers.add(server);
            }
        }
        return healthyServers;
    }

    private void sendBadGateway(Socket connection) throws IOException {
        String response = "HTTP/1.1 502 Bad Gateway\r\n\r\nNo backend available";
        connection.getOutputStream().write(response.getBytes());
    }
}
