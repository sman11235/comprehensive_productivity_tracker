package saket.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import saket.consumer.domain.EventDTO;
import saket.consumer.domain.EventOp;
import saket.consumer.services.EventDeduplicationService;
import saket.consumer.services.ITypeStrategy;
import saket.consumer.services.KafkaEventConsumer;
import saket.consumer.services.TypeStrategyRegistry;

class KafkaEventConsumerUnitTest {

    @Test
    void duplicateEventIsSkippedBeforeHandlerRuns() {
        TrackingStrategy strategy = new TrackingStrategy();
        KafkaEventConsumer consumer = new KafkaEventConsumer(
            new TypeStrategyRegistry(List.of(strategy)),
            new StubDeduplicationService(false)
        );

        EventDTO event = createEvent("evt-1", EventOp.CREATE);

        consumer.onEvent(event, "saket.location");

        assertEquals(0, strategy.handleCount);
    }

    @Test
    void unsupportedOperationsFailFast() {
        TrackingStrategy strategy = new TrackingStrategy();
        KafkaEventConsumer consumer = new KafkaEventConsumer(
            new TypeStrategyRegistry(List.of(strategy)),
            new StubDeduplicationService(true)
        );

        EventDTO event = createEvent("evt-2", EventOp.DELETE);

        assertThrows(UnsupportedOperationException.class, () -> consumer.onEvent(event, "saket.location"));
        assertEquals(0, strategy.handleCount);
    }

    @Test
    void newCreateEventIsDispatchedToStrategy() {
        TrackingStrategy strategy = new TrackingStrategy();
        KafkaEventConsumer consumer = new KafkaEventConsumer(
            new TypeStrategyRegistry(List.of(strategy)),
            new StubDeduplicationService(true)
        );

        EventDTO event = createEvent("evt-3", EventOp.CREATE);

        consumer.onEvent(event, "saket.location");

        assertEquals(1, strategy.handleCount);
    }

    private EventDTO createEvent(String eventId, EventOp op) {
        return new EventDTO(
            eventId,
            "iphone",
            "ios",
            "saket.location",
            op,
            Instant.now(),
            JsonNodeFactory.instance.objectNode(),
            null
        );
    }

    private static final class TrackingStrategy implements ITypeStrategy {
        private int handleCount;

        @Override
        public String getTopicType() {
            return "saket.location";
        }

        @Override
        public void handle(EventDTO event) {
            handleCount++;
        }
    }

    private static final class StubDeduplicationService extends EventDeduplicationService {
        private final boolean shouldProcess;

        private StubDeduplicationService(boolean shouldProcess) {
            super(null);
            this.shouldProcess = shouldProcess;
        }

        @Override
        public boolean markProcessedIfNew(EventDTO event, String topic) {
            return shouldProcess;
        }
    }
}
