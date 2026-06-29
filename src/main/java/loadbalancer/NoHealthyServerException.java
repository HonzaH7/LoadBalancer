package loadbalancer;

public class NoHealthyServerException extends RuntimeException {
    public NoHealthyServerException(String message) {
        super(message);
    }
}
