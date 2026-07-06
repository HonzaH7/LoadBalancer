package loadbalancer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinStrategy implements BalancingStrategy {
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public Backend select(List<Backend> healthyServers) {
        int serverIndex = Math.floorMod(counter.getAndIncrement(), healthyServers.size());
        return healthyServers.get(serverIndex);
    }
}
