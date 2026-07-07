package loadbalancer;

public class FakeBackend implements Backend {
    private final int port;
    private boolean alive = true;

    FakeBackend(int port) { this.port = port; }

    public String getAddress() { return "localhost"; }
    public int getPort() { return port; }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
}