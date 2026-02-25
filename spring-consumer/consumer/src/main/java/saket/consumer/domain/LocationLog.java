package saket.consumer.domain;

import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;
import java.time.Instant;

/**
 * A class that acts as the model for the location_log table in the database.
 * Tracks the continuous location I have been to (lat log coords like (12.3, 124.5))
 */
@Entity
@Table(name = "location_logs")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LocationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(columnDefinition = "GEOGRAPHY(Point, 4326)")
    private Point loc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id")
    private Visit visit;

    @Override
    public String toString() {
        return "{id: " + id
            + "; timestamp: " + timestamp 
            + "; deviceId: " + deviceId 
            + "; loc: " + loc 
            + "; visit: " + (visit == null ? "null" : visit.getId()) 
            + " }";
    }
}
