package loadbalancer;

import java.io.IOException;
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
    private final List<HealthListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService requestPool;
    private final int healthCheckInterval;

    public static class Builder {
        private int port = 0;
        private BalancingStrategy balancingStrategy = new RoundRobinStrategy();
        private int healthCheckInterval = 10000;
        private List<Backend> listOfServers;
        private int poolSize = 50;

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

        public Builder healthCheckInterval(int healthCheckInterval) {
            this.healthCheckInterval = healthCheckInterval;
            return this;
        }

        public Builder backends(List<Backend> backends) {
            this.listOfServers = List.copyOf(backends);
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
        this.healthCheckInterval = healthCheckInterval;
    }

    public void addHealthListener(HealthListener listener) {
        listeners.add(listener);
    }

    private void notifyListeners(Backend server, boolean alive) {
        for (HealthListener listener : listeners) {
            listener.onHealthChange(server, alive);
        }
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

    public void startHealthCheck() {
        scheduler.scheduleAtFixedRate(this::checkHealthOnce, 0, healthCheckInterval, TimeUnit.MILLISECONDS);
    }

    void checkHealthOnce() {
        for (Backend server : backends) {
            HttpURLConnection connection = null;
            boolean nowAlive;
            try {
                URL url = new URL("http://" + server.getAddress() + ":" + server.getPort());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                int statusCode = connection.getResponseCode();
                nowAlive = (statusCode == 200);
            } catch (IOException e) {
                nowAlive = false;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            boolean wasAlive = server.isAlive();
            if (wasAlive != nowAlive) {
                notifyListeners(server, nowAlive);
            }
            server.setAlive(nowAlive);
        }
    }

    private void handleRequest(Socket connection) {
        try (connection) {
            for (int i = 0; i < backends.size(); i++) {
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
