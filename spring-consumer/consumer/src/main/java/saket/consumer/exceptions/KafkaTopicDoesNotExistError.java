package saket.consumer.exceptions;

public class KafkaTopicDoesNotExistError extends BaseCustomException {
    public KafkaTopicDoesNotExistError(String m) {
        super(m);
    }
}
