package saket.consumer.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import saket.consumer.domain.Visit;

@Repository
public interface VisitRepository extends JpaRepository<Visit, Long> {
    List<Visit> findByPlaceId(Long placeId);
    List<Visit> findByEntryTimeBetween(Instant start, Instant end);
    @Query("""
        SELECT v
        FROM Visit v
        WHERE v.entryTime <= :time
          AND (v.exitTime > :time OR v.exitTime IS NULL)
    """)
    Optional<Visit> findVisitContainingTime(@Param("time") Instant time);
}
