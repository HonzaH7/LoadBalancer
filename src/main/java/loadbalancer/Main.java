package loadbalancer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        BackendServer server1 = new BackendServer(8081, "localhost");
        BackendServer server2 = new BackendServer(8082, "localhost");
        BackendServer server3 = new BackendServer(8083, "localhost");
        List<Backend> listOfServers = new ArrayList<>();
        listOfServers.add(server1);
        listOfServers.add(server2);
        listOfServers.add(server3);
        LoadBalancer loadBalancer = new LoadBalancer(8080, listOfServers);
        new Thread(server1::start).start();
        new Thread(server2::start).start();
        new Thread(server3::start).start();
        int interval = args.length > 0 ? Integer.parseInt(args[0]) : 10000;
        new Thread(() -> loadBalancer.startHealthCheck(interval)).start();
        loadBalancer.start();
    }
}

// http://localhost:8080/
