package loadbalancer;

import java.util.List;
import java.util.Random;

public class RandomStrategy implements BalancingStrategy {
    private final Random random = new Random();
    @Override
    public Backend select(List<Backend> healthyServers) {
        return healthyServers.get(random.nextInt(healthyServers.size()));
    }
}
