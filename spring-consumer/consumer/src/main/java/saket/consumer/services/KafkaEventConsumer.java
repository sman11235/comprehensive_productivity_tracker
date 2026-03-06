package saket.consumer.services;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.kafka.support.KafkaHeaders;
import saket.consumer.domain.EventDTO;
import saket.consumer.exceptions.KafkaTopicDoesNotExistError;

@Service
public class KafkaEventConsumer {
    private final TypeStrategyRegistry handlerRegistry;

    public KafkaEventConsumer(TypeStrategyRegistry handlers) {
        handlerRegistry = handlers;
    }

    @KafkaListener(
        topicPattern = "saket\\..*",
        groupId = "consumer-app"
    )
    public void onEvent(
        EventDTO event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic
    ) {
        System.out.println("Topic: " + topic + "; event: " + event);
        ITypeStrategy handler = handlerRegistry.find(topic).orElseThrow(
            () -> new KafkaTopicDoesNotExistError("Kafka Topic " + topic + " does not exist.")
        );
        handler.handle(event);
    }

}
