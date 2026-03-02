package saket.consumer.domain;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A DTO that represents the kafka events that are send to this consumer.
 */
public record EventDTO(
    String eventId,
    String deviceId,
    String source,
    String type,
    EventOp op,
    Instant observedAt,
    JsonNode payload,
    JsonNode attributes
){}

