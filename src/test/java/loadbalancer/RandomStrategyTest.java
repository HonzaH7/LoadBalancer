package loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class RandomStrategyTest {
    private Backend b1;
    private Backend b2;
    private Backend b3;
    private BalancingStrategy balancingStrategy;
    private List<Backend> servers;

    @BeforeEach
    public void setup() {
        b1 = new FakeBackend(8081);
        b2 = new FakeBackend(8082);
        b3 = new FakeBackend(8083);
        balancingStrategy = new RandomStrategy();
        servers = new ArrayList<>();
        servers.add(b1);
        servers.add(b2);
        servers.add(b3);
    }

    @Test
    void randomlyPickedServers() {
        for (int i = 0; i < 1000; i++) {
            Backend picked = balancingStrategy.select(servers);
            assertTrue(servers.contains(picked));
        }
    }
}
