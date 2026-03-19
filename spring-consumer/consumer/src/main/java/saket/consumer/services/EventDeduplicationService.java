package saket.consumer.services;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import saket.consumer.domain.EventDTO;
import saket.consumer.domain.ProcessedEvent;
import saket.consumer.repositories.ProcessedEventRepository;

/**
 * Prevents duplicate processing of externally supplied event ids.
 */
@Service
public class EventDeduplicationService {
    private final ProcessedEventRepository processedEventRepository;

    public EventDeduplicationService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public boolean markProcessedIfNew(EventDTO event, String topic) {
        String eventId = event.eventId();
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Incoming events must include a non-empty eventId.");
        }
        if (processedEventRepository.existsByEventId(eventId)) {
            return false;
        }

        processedEventRepository.save(ProcessedEvent.builder()
            .eventId(eventId)
            .topic(topic)
            .observedAt(event.observedAt())
            .processedAt(Instant.now())
            .build());
        return true;
    }
}
