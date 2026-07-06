package loadbalancer;

import java.util.List;

public interface BalancingStrategy {
    Backend select(List<Backend> healthyServers);
}
