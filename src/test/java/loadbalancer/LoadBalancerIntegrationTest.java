package loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoadBalancerIntegrationTest {
    private BackendServer b1;
    private BackendServer b2;
    private LoadBalancer loadBalancer;
    private BalancingStrategy balancingStrategy;

    @BeforeEach
    void setup() throws IOException {
        b1 = startBackend();
        b2 = startBackend();
        List<Backend> backends = new ArrayList<>();
        backends.add(b1);
        backends.add(b2);
        balancingStrategy = new RoundRobinStrategy();
        loadBalancer = startLoadBalancer(backends, balancingStrategy);
    }

    @Test
    void returns200WhenBackendsAreHealthy() throws IOException {
        assertEquals(200, sendGet(loadBalancer.getPort()));
    }

    @Test
    void retriesToHealthyBackendWhenOneIsDead() throws IOException {
        b1.stop();

        for (int i = 0; i < 4; i++) {
            assertEquals(200, sendGet(loadBalancer.getPort()));
        }
    }

    @Test
    void returns502WhenAllBackendsAreDead() throws IOException {
        b1.setAlive(false);
        b2.setAlive(false);
        assertEquals(502, sendGet(loadBalancer.getPort()));
    }

    private BackendServer startBackend() throws IOException {
        BackendServer backend = new BackendServer(0, "localhost");
        Thread t = new Thread(backend::start);
        t.setDaemon(true);
        t.start();
        return backend;
    }

    private LoadBalancer startLoadBalancer(List<Backend> backends, BalancingStrategy balancingStrategy) throws IOException {
        LoadBalancer lb = LoadBalancer.builder()
                            .port(0)
                            .backends(backends)
                            .balancingStrategy(balancingStrategy)
                            .build();
        Thread t = new Thread(lb::start);
        t.setDaemon(true);
        t.start();
        return lb;
    }

    private int sendGet(int port) throws IOException {
        URL url = new URL("http://localhost:" + port + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        conn.disconnect();
        return code;
    }
}
