package saket.consumer.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * A class that acts as the model for the health_log table in the database.
 * Tracks health data (step, miles traveled, etc.)
 */
@Entity
@Table(name = "health_logs")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class HealthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "metric_type", nullable = false)
    private String metricType;

    @Column(nullable = false)
    private Double val;

    @Column(nullable = false)
    private String unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id")
    private Visit visit;
}
