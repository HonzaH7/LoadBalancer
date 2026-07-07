package loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoundRobinStrategyTest {

    private Backend b1;
    private Backend b2;
    private Backend b3;
    private BalancingStrategy balancingStrategy;
    private List<Backend> servers;

    @BeforeEach
    void setup() {
        b1 = new FakeBackend(8081);
        b2 = new FakeBackend(8082);
        b3 = new FakeBackend(8083);

        servers = new ArrayList<>();
        servers.add(b1);
        servers.add(b2);
        servers.add(b3);

        balancingStrategy = new RoundRobinStrategy();
    }

    @Test
    void returnsServersInRoundRobinOrder() {
        assertEquals(8081, balancingStrategy.select(servers).getPort());
        assertEquals(8082, balancingStrategy.select(servers).getPort());
        assertEquals(8083, balancingStrategy.select(servers).getPort());
        assertEquals(8081, balancingStrategy.select(servers).getPort());
    }

    @Test
    void returnsServersThatBelongInTheList() {
        servers.remove(1);
        for (int i = 0; i < 10; i++) {
            assertNotEquals(8082, balancingStrategy.select(servers).getPort());
        }
    }
}
