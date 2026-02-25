package saket.consumer.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import saket.consumer.domain.LocationLog;
import saket.consumer.domain.Visit;

import org.locationtech.jts.geom.Point;

@Repository
public interface LocationLogRepository extends JpaRepository<LocationLog, Long> {
    List<LocationLog> findByVisitId(Long visitId);
    @Query(value = "select * from location_logs l where ST_DWithin(l.loc, :point, :radius)", 
        nativeQuery = true)
    List<LocationLog> findNearByLocations(@Param("point") Point point, @Param("radius") double radius);
    @Query("""
        select l
        from LocationLog l
        where l.timestamp >= :start
          and l.timestamp <= :end
        order by l.timestamp asc
    """)
    List<LocationLog> findByTimeRange(@Param("start") Instant start, @Param("end") Instant end);
    
    @Modifying
    @Query("""
        update LocationLog d
        set d.visit = :visit
        where d.timestamp between :start and :end
    """)
    int assignVisit(@Param("visit") Visit visit,
                    @Param("start") Instant start,
                    @Param("end") Instant end);
}
