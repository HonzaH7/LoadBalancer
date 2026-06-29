package loadbalancer;

public interface Backend {
    String getAddress();
    int getPort();
    boolean isAlive();
    void setAlive(boolean alive);
}
