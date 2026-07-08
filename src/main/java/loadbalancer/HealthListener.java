package loadbalancer;

public interface HealthListener {
    void onHealthChange(Backend server, boolean alive);
}
