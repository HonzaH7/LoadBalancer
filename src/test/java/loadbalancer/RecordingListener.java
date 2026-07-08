package loadbalancer;

public class RecordingListener implements HealthListener {
    Backend lastServer;
    boolean lastAlive;
    int callCount = 0;

    public void onHealthChange(Backend server, boolean alive) {
        this.lastServer = server;
        this.lastAlive = alive;
        this.callCount++;
    }
}