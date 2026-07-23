package loadbalancer;

import java.util.ArrayList;
import java.util.List;

public class LbMain {
    public static void main(String[] args) throws Exception {
        String backendsEnv = System.getenv("BACKENDS");
        if (backendsEnv == null) {
            backendsEnv = "localhost:8081,localhost:8082,localhost:8083";
        }

        List<Backend> backends = new ArrayList<>();
        for (String entry : backendsEnv.split(",")) {
            String[] parts = entry.split(":");
            String address = parts[0];
            int port = Integer.parseInt(parts[1]);
            backends.add(new RemoteBackend(address, port, true));
        }

        String lbPortEnv = System.getenv("PORT");
        int lbPort = (lbPortEnv == null) ? 8080 : Integer.parseInt(lbPortEnv);

        LoadBalancer lb = LoadBalancer.builder()
                .port(lbPort)
                .backends(backends)
                .balancingStrategy(new RandomStrategy())
                .build();

        lb.addHealthListener((b, alive) ->
                System.out.println(b.getAddress() + ":" + b.getPort() + " -> " + alive));

        System.out.println("LoadBalancer started on port " + lbPort + " before backends: " + backendsEnv);
        lb.startHealthCheck();
        lb.start();
    }
}