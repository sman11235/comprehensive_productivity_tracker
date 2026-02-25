package saket.consumer.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import saket.consumer.domain.KnownPlace;

import org.locationtech.jts.geom.Point;

@Repository
public interface KnownPlaceRepository extends JpaRepository<KnownPlace, Long> {
    @Query(value = "SELECT * FROM known_places k " +
                   "WHERE ST_DWithin(k.loc, :point, :radiusInMeters)", 
           nativeQuery = true)
    List<KnownPlace> findNearby(@Param("point") Point point, @Param("radiusInMeters") double radiusInMeters);
}
