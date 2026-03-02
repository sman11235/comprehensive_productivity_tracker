package saket.consumer.domain;

import java.time.Instant;

public record LocationDTO(
    Instant timestamp,
    String deviceId,
    PointDTO loc
) {

}
