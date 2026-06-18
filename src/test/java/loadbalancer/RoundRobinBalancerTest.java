package loadbalancer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FakeBackend {

    private Backend s1;
    private Backend s2;
    private Backend s3;
    private RoundRobinBalancer rr;

    @BeforeEach
    void setUp() {
        s1 = new FakeBackend(8081);
        s2 = new FakeBackend(8082);
        s3 = new FakeBackend(8083);

        List<Backend> servers = new ArrayList<>();
        servers.add(s1);
        servers.add(s2);
        servers.add(s3);

        rr = new RoundRobinBalancer(servers);
    }

    @Test
    void returnsServersInRoundRobinOrder() {
        assertEquals(8081, rr.nextServer().getPort());
        assertEquals(8082, rr.nextServer().getPort());
        assertEquals(8083, rr.nextServer().getPort());
        assertEquals(8081, rr.nextServer().getPort());
    }

    @Test
    void skipsDeadServers() {
        s2.setAlive(false);
        for (int i = 0; i < 10; i++) {
            assertNotEquals(8082, rr.nextServer().getPort());
        }
    }

    @Test
    void throwsWhenAllServersAreDead() {
        s1.setAlive(false);
        s2.setAlive(false);
        s3.setAlive(false);
        assertThrows(NoHealthyServerException.class, () -> rr.nextServer());
    }
}
