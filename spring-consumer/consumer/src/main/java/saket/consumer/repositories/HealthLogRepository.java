package saket.consumer.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import saket.consumer.domain.HealthLog;
import saket.consumer.domain.Visit;

@Repository
public interface HealthLogRepository extends JpaRepository<HealthLog, Long> {
    List<HealthLog> findByVisitId(Long visitId);

    @Modifying
    @Query("""
        update HealthLog d
        set d.visit = :visit
        where d.timestamp between :start and :end
    """)
    int assignVisit(@Param("visit") Visit visit,
                    @Param("start") Instant start,
                    @Param("end") Instant end);
}
