package loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoundRobinStrategyTest {

    private Backend s1;
    private Backend s2;
    private Backend s3;
    private BalancingStrategy rr;
    private List<Backend> servers;

    @BeforeEach
    void setUp() {
        s1 = new FakeBackend(8081);
        s2 = new FakeBackend(8082);
        s3 = new FakeBackend(8083);

        servers = new ArrayList<>();
        servers.add(s1);
        servers.add(s2);
        servers.add(s3);

        rr = new RoundRobinStrategy();
    }

    @Test
    void returnsServersInRoundRobinOrder() {
        assertEquals(8081, rr.select(servers).getPort());
        assertEquals(8082, rr.select(servers).getPort());
        assertEquals(8083, rr.select(servers).getPort());
        assertEquals(8081, rr.select(servers).getPort());
    }

    @Test
    void returnsServersThatBelongInTheList() {
        servers.remove(1);
        for (int i = 0; i < 10; i++) {
            assertNotEquals(8082, rr.select(servers).getPort());
        }
    }

    private static class FakeBackend implements Backend {
        private final int port;
        private boolean alive = true;

        FakeBackend(int port) { this.port = port; }

        public String getAddress() { return "localhost"; }
        public int getPort() { return port; }
        public boolean isAlive() { return alive; }
        public void setAlive(boolean alive) { this.alive = alive; }
    }
}
