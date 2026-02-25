package saket.consumer.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A class that acts as the model for the transaction_log table in the database.
 * Tracks transactions i've made (ie. 50 dollars at starbuck)
 */
@Entity
@Table(name = "transaction_logs")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TransactionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "extern_txn_id", unique = true, nullable = false)
    private String externTxnId;

    @Column(nullable = false)
    private Instant timestamp;

    // Maps to DECIMAL(10, 2)
    private BigDecimal amount;

    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id")
    private Visit visit;
}
