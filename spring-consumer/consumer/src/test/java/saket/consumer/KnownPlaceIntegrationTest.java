package saket.consumer;
import saket.consumer.domain.KnownPlace;
import saket.consumer.domain.KnownPlaceStatus;
import saket.consumer.repositories.KnownPlaceRepository;
import saket.consumer.services.PointUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
class KnownPlaceIntegrationTest extends BaseContainerTest {

    @Autowired
    private KnownPlaceRepository knownPlaceRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final double TECH_SQUARE_LAT = 33.7766;
    private static final double TECH_SQUARE_LON = -84.3886;
    private static final int NUM_OF_POINTS = 4;

    @BeforeEach
    void cleanAndInit() {
        jdbcTemplate.execute("""
            TRUNCATE TABLE location_logs, visits, known_places
            RESTART IDENTITY CASCADE
        """);
        knownPlaceRepo.deleteAll();  // parent table next
        KnownPlace techSquare = new KnownPlace(
            null, 
            "Tech Square", 
            "Work", 
            PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON), 
            Instant.now(), 
            null, 
            KnownPlaceStatus.ESTABLISHED
        );
        KnownPlace timesSquare = new KnownPlace(
            null, 
            "Times Square", 
            "Tourism", 
            PointUtil.wgs84FromLatLon(40.7580, -30.9855), 
            Instant.now(), 
            null, 
            KnownPlaceStatus.ESTABLISHED
        );
        KnownPlace biggsSquare = new KnownPlace(
            null, 
            "Biggs Square", 
            "Tourism", 
            PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT + 0.25, TECH_SQUARE_LON), 
            Instant.now(), 
            null, 
            KnownPlaceStatus.ESTABLISHED
        );
        KnownPlace techSquarePizza = new KnownPlace(
            null, 
            "Tech Square Pizza", 
            "Food", 
            PointUtil.wgs84FromLatLon(33.776870, -84.388600), 
            Instant.now(), 
            null, 
            KnownPlaceStatus.ESTABLISHED
        );
        knownPlaceRepo.saveAndFlush(techSquare);
        knownPlaceRepo.saveAndFlush(timesSquare);
        //~27735 meters from tech square
        knownPlaceRepo.saveAndFlush(biggsSquare);
        //30 meters from tech square.
        knownPlaceRepo.saveAndFlush(techSquarePizza);
    }

    @Test
    void testSpatialQuery() {
        Point point = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);
        List<KnownPlace> nearby = knownPlaceRepo.findNearby(point, 30000);

        assertThat(nearby).hasSize(NUM_OF_POINTS - 1);
        assertThat(nearby.get(0).getName()).isEqualTo("Tech Square");
    }

    @Test
    void testCloseSpatialQuery() {

        // 3. Search within 500 meters of Tech Square
        Point point = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);
        List<KnownPlace> nearby = knownPlaceRepo.findNearby(point, 30);

        assertThat(nearby).hasSize(NUM_OF_POINTS - 2);
        assertThat(nearby.get(0).getName()).isEqualTo("Tech Square");
        assertThat(nearby.get(1).getName()).isEqualTo("Tech Square Pizza");
    }

    @Test
    void testBorderSpatialQuery() {

        // 3. Search within 500 meters of Tech Square
        //on the border between biggs and tech square.
        Point point = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);
        List<KnownPlace> nearby = knownPlaceRepo.findNearby(point, 27725); 

        // 4. Verify we found Tech Square but NOT Times Square
        for (KnownPlace k : nearby) {
            System.out.println(k.getName() + ": " + k.getLoc());
        }
        assertThat(nearby).hasSize(NUM_OF_POINTS - 2);

        assertThat(nearby.get(0).getName()).isEqualTo("Tech Square");
        assertThat(nearby.get(1).getName()).isEqualTo("Tech Square Pizza");    
    }

}