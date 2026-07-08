package loadbalancer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class HealthChecker {
    private final List<Backend> backends;
    private final List<HealthListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final int interval;

    public HealthChecker(List<Backend> backends, int interval) {
        this.backends = backends;
        this.interval = interval;
    }

    private void notifyListeners(Backend server, boolean alive) {
        for (HealthListener listener : listeners) {
            listener.onHealthChange(server, alive);
        }
    }

    public void startHealthCheck() {
        scheduler.scheduleAtFixedRate(this::checkHealthOnce, 0, interval, TimeUnit.MILLISECONDS);
    }

    public void addHealthListener(HealthListener listener) {
        listeners.add(listener);
    }

    void checkHealthOnce() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Backend server : backends) {
            futures.add(CompletableFuture.runAsync(() -> checkServer(server)));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void checkServer(Backend server) {
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
