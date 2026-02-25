package saket.consumer.domain;

import jakarta.persistence.*;
import lombok.*;

import org.locationtech.jts.geom.Point;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A class that acts as the model for the known_place table in the database.
 * Tracks known discrete locations I have traveled to (Starbucks, library, etc.)
 */
@Entity
@Table(name = "known_places")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class KnownPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String category;

    // Maps to GEOMETRY(POINT, 4326)
    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point loc;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "place", fetch = FetchType.LAZY)
    private List<Visit> visits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KnownPlaceStatus status;

    public void rename(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public void setCategory(String category) {
        this.category = Objects.requireNonNull(category);
    }

    public void setStatus(KnownPlaceStatus s) {
        status = Objects.requireNonNull(s);
    }
}
