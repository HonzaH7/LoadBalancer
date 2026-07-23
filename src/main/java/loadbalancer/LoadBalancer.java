package loadbalancer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

public class LoadBalancer {
    private final ServerSocket serverSocket;
    private final BalancingStrategy balancingStrategy;
    private final List<Backend> backends;
    private final ExecutorService requestPool;
    private final HealthChecker healthChecker;

    public static class Builder {
        private int port = 0;
        private BalancingStrategy balancingStrategy = new RoundRobinStrategy();
        private List<Backend> listOfServers;
        private int poolSize = 50;
        private int healthCheckInterval = 10000;

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder poolSize(int poolSize) {
            this.poolSize = poolSize;
            return this;
        }

        public Builder balancingStrategy(BalancingStrategy balancingStrategy) {
            this.balancingStrategy = Objects.requireNonNull(balancingStrategy, "strategy must not be null");
            return this;
        }

        public Builder backends(List<Backend> backends) {
            this.listOfServers = List.copyOf(backends);
            return this;
        }

        public Builder healthCheckInterval(int healthCheckInterval) {
            this.healthCheckInterval = healthCheckInterval;
            return this;
        }

        public LoadBalancer build() throws IOException {
            if (listOfServers == null || listOfServers.isEmpty()) {
                throw new IllegalArgumentException("LoadBalancer needs at least one backend");
            }
            return new LoadBalancer(port, listOfServers, balancingStrategy, poolSize, healthCheckInterval);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private LoadBalancer(int port, List<Backend> backends, BalancingStrategy balancingStrategy, int poolSize, int healthCheckInterval) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.backends = backends;
        this.balancingStrategy = balancingStrategy;
        this.requestPool = Executors.newFixedThreadPool(poolSize);
        this.healthChecker = new HealthChecker(backends, healthCheckInterval);
    }

    public void startHealthCheck() {
        healthChecker.startHealthCheck();
    }

    public void addHealthListener(HealthListener listener) {
        healthChecker.addHealthListener(listener);
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void start() {
        while (true) {
            try {
                Socket connection = serverSocket.accept();
                requestPool.submit(() -> handleRequest(connection));
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private void handleRequest(Socket connection) {
        try (connection) {
            BufferedReader clientIn = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            List<String> requestLines = new ArrayList<>();
            String line;
            while ((line = clientIn.readLine()) != null && !line.isEmpty()) {
                requestLines.add(line);
            }

            for (int i = 0; i < backends.size(); i++) {
                List<Backend> healthyServers = healthyServers();
                if (healthyServers.isEmpty()) {
                    break;
                }
                Backend server = balancingStrategy.select(healthyServers);
                try (Socket backendSocket = new Socket(server.getAddress(), server.getPort())) {
                    OutputStream backendOut = backendSocket.getOutputStream();
                    for (String requestLine : requestLines) {
                        backendOut.write((requestLine + "\r\n").getBytes());
                    }
                    backendOut.write("\r\n".getBytes());
                    backendOut.flush();
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
        for (Backend server : backends) {
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
