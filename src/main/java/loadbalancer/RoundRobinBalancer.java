package loadbalancer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinBalancer {
    private final List<Backend> servers;
    private final AtomicInteger counter = new AtomicInteger(0);

    public RoundRobinBalancer(List<Backend> servers) {
        this.servers = servers;
    }
    public Backend nextServer() {
        int size = servers.size();
        for (int i = 0; i < size; i++) {
            int serverIndex = Math.floorMod(counter.getAndIncrement(), size);
            Backend server = servers.get(serverIndex);
            if (server.isAlive()) {
                return server;
            }
        }
        throw new NoHealthyServerException("None of the servers are alive!");
    }
}
