package saket.consumer.services;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.kafka.support.KafkaHeaders;
import saket.consumer.domain.EventDTO;
import saket.consumer.domain.EventOp;
import saket.consumer.exceptions.KafkaTopicDoesNotExistError;
import lombok.extern.slf4j.Slf4j;

/**
 * A class that handles all kafka consumption and incoming traffic.
 */
@Slf4j
@Service
public class KafkaEventConsumer {
    private final TypeStrategyRegistry handlerRegistry;
    private final EventDeduplicationService eventDeduplicationService;

    public KafkaEventConsumer(
        TypeStrategyRegistry handlers,
        EventDeduplicationService eventDeduplicationService
    ) {
        handlerRegistry = handlers;
        this.eventDeduplicationService = eventDeduplicationService;
    }

    /**
     * The function that handles all kafka traffic.
     * @param event the kafka event received.
     * @param topic the topic of the kafka event.
     */
    @KafkaListener(
        topicPattern = "saket\\..*",
        groupId = "consumer-app"
    )
    public void onEvent(
        EventDTO event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic
    ) {
        if (event.op() != EventOp.CREATE) {
            throw new UnsupportedOperationException(
                "Unsupported operation %s for topic %s".formatted(event.op(), topic)
            );
        }
        if (!eventDeduplicationService.markProcessedIfNew(event, topic)) {
            log.info("Skipping duplicate event {} on topic {}", event.eventId(), topic);
            return;
        }
        log.info("Processing topic {} event {}", topic, event.eventId());
        ITypeStrategy handler = handlerRegistry.find(topic).orElseThrow(
            () -> new KafkaTopicDoesNotExistError("Kafka Topic " + topic + " does not exist.")
        );
        handler.handle(event);
    }

}
