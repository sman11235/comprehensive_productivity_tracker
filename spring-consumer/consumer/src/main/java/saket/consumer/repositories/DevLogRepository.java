package saket.consumer.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import saket.consumer.domain.DevLog;
import saket.consumer.domain.Visit;

@Repository
public interface DevLogRepository extends JpaRepository<DevLog, Long> {
    List<DevLog> findByVisitId(Long visitId);
    
    @Modifying
    @Query("""
        update DevLog d
        set d.visit = :visit
        where d.timestamp between :start and :end
    """)
    int assignVisit(@Param("visit") Visit visit,
                    @Param("start") Instant start,
                    @Param("end") Instant end);
}
