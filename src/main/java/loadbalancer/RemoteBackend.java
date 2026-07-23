package loadbalancer;

public class RemoteBackend implements Backend {
    private final String address;
    private final int port;
    private volatile boolean isAlive;

    public RemoteBackend(String address, int port, boolean isAlive) {
        this.address = address;
        this.port = port;
        this.isAlive = isAlive;
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public boolean isAlive() {
        return isAlive;
    }

    @Override
    public void setAlive(boolean alive) {
        this.isAlive = alive;
    }
}