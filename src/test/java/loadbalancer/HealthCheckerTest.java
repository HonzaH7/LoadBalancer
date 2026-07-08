package loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class HealthCheckerTest {
    private Backend b1;
    private List<Backend> servers;
    HealthChecker healthChecker;

    @BeforeEach
    void setup() {
        b1 = new FakeBackend(8081);
        servers = new ArrayList<>();
        servers.add(b1);
        healthChecker = new HealthChecker(servers, 1000);
    }

    @Test
    void notifiesListenerWhenBackendGoesDown() throws IOException {
        RecordingListener listener = new RecordingListener();
        healthChecker.addHealthListener(listener);
        healthChecker.checkHealthOnce();
        assertEquals(b1, listener.lastServer);
        assertFalse(listener.lastAlive);
    }
}
