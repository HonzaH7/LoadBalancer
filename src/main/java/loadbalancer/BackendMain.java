package loadbalancer;

import java.io.IOException;

public class BackendMain {
    public static void main(String[] args) throws IOException {
        String portFromEnv = System.getenv("PORT");

        int port;
        if (portFromEnv == null) {
            port = 8080;
        } else {
            port = Integer.parseInt(portFromEnv);
        }

        BackendServer server = new BackendServer(port, "0.0.0.0");
        System.out.println("BackendServer started on port " + port);
        server.start();
    }
}
