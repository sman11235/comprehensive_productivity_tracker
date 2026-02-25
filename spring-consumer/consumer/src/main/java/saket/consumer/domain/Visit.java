package saket.consumer.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A class that acts as the model for the visits table in the database.
 * Tracks instances when i've been to a certain location for a while and done actions there.
 * This is where most of the correlation logic will be acting on.
 * Ex: 30 dollars spent at Starbucks while there for 40 mins
 */
@Entity
@Table(name = "visits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private KnownPlace place;

    @Column(name = "entry_time", nullable = false)
    private Instant entryTime;

    @Column(name = "exit_time")
    private Instant exitTime;

    // Bidirectional mappings for logs (Optional, but useful)
    @OneToMany(mappedBy = "visit", fetch = FetchType.LAZY)
    private List<LocationLog> locationLogs;

    @OneToMany(mappedBy = "visit", fetch = FetchType.LAZY)
    private List<TransactionLog> transactionLogs;

    public void linkToPlace(KnownPlace p) {
        place = p;
    }

    public void startAt(Instant start) {
        entryTime = Objects.requireNonNull(start);
    }

    public void endAt(Instant end) {
        exitTime = Objects.requireNonNull(end);
    }
}
