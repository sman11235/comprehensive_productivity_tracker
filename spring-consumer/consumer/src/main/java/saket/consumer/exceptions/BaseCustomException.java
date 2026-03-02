package saket.consumer.exceptions;

public class BaseCustomException extends RuntimeException {
    
    public BaseCustomException() {
        super();
    }

    public BaseCustomException(String message) {
        super(message);
    }
}
