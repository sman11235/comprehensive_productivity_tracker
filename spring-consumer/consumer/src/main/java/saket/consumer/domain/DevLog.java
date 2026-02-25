package saket.consumer.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * A class that acts as the model for the dev_log table in the database.
 * Tracks developer activity (github commits, leetcode, etc.)
 */
@Entity
@Table(name = "dev_logs")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DevLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String platform;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(nullable = false)
    private String target;

    // Maps JSONB to a Jackson JsonNode (or Map<String, Object>)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode metadata;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id")
    private Visit visit;
}