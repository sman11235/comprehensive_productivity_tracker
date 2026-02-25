package saket.consumer.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import saket.consumer.domain.TransactionLog;
import saket.consumer.domain.Visit;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {
    List<TransactionLog> findByVisitId(Long visitId);
    TransactionLog findByExternTxnId(String externTxnId);
    
    @Modifying
    @Query("""
        update TransactionLog d
        set d.visit = :visit
        where d.timestamp between :start and :end
    """)
    int assignVisit(@Param("visit") Visit visit,
                    @Param("start") Instant start,
                    @Param("end") Instant end);
}
