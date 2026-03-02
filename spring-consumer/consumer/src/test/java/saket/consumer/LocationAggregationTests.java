package saket.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import saket.consumer.domain.KnownPlace;
import saket.consumer.domain.KnownPlaceStatus;
import saket.consumer.domain.LocationLog;
import saket.consumer.domain.userFSM.UserLocationContext;
import saket.consumer.repositories.KnownPlaceRepository;
import saket.consumer.repositories.LocationLogRepository;
import saket.consumer.services.Constants;
import saket.consumer.services.LocationAggregationService;
import saket.consumer.services.PointUtil;

@SuppressWarnings("unchecked")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
class LocationAggregationTests extends BaseContainerTest {

    @Autowired
    private LocationAggregationService locationAggregationService;

    @Autowired
    private LocationLogRepository locationLogRepository;

    @Autowired
    private KnownPlaceRepository knownPlaceRepository;

    private static final String DEVICE_ID = "device-1";
    private static final double TECH_SQUARE_LAT = 33.7766;
    private static final double TECH_SQUARE_LON = -84.3886;

    @BeforeEach
    void setup() {
        // Clean tables (order matters if FK exists)
        locationLogRepository.deleteAll();
        knownPlaceRepository.deleteAll();
    }

    @Test
    void aggregateLocationInfo_stationary_true_and_matchesClosestKnownPlace() {
        // Arrange: one known place near Tech Square
        Point techSquare = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);
        KnownPlace kp = new KnownPlace(
            null,
            "Tech Square",
            "Work",
            techSquare,
            Instant.now(),
            null,
            KnownPlaceStatus.ESTABLISHED
        );
        knownPlaceRepository.saveAndFlush(kp);

        // Arrange: a tight cluster of logs within stationary radius, within the time window
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // Small offsets ~ a few meters (safe for "stationary")
        Point p1 = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);
        Point p2 = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT + 0.00002, TECH_SQUARE_LON);
        Point p3 = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON + 0.00002);

        locationLogRepository.saveAll(List.of(
            new LocationLog(null, now.minusSeconds(30), DEVICE_ID, p1, null),
            new LocationLog(null, now.minusSeconds(20), DEVICE_ID, p2, null),
            new LocationLog(null, now.minusSeconds(10), DEVICE_ID, p3, null)
        ));
        locationLogRepository.flush();

        // Act
        UserLocationContext ctx = locationAggregationService.aggregateLocationInfo(now, DEVICE_ID);

        // Assert
        assertThat(ctx).isNotNull();
        assertThat(ctx.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(ctx.timestamp()).isEqualTo(now);
        assertThat(ctx.centroid()).isNotNull();
        assertThat(ctx.stationary()).isTrue();

        assertThat(ctx.nearestKnownPlaceInRadius()).isNotNull();
        assertThat(ctx.nearestKnownPlaceInRadius().getName()).isEqualTo("Tech Square");
        System.out.println(ctx);

        Instant expectedOldest = now.minusSeconds(30);
        assertThat(ctx.oldestTimestampInWindow()).isEqualTo(expectedOldest);
    }

    @Test
    void aggregateLocationInfo_stationary_false_whenPointsSpreadOut() {
        // Arrange: known place near Tech Square
        Point techSquare = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);
        KnownPlace kp = new KnownPlace(
            null,
            "Tech Square",
            "Work",
            techSquare,
            Instant.now(),
            null,
            KnownPlaceStatus.ESTABLISHED
        );
        knownPlaceRepository.saveAndFlush(kp);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // One point far enough to exceed stationary radius (1km-ish)
        Point near = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);
        Point far = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT + 0.01, TECH_SQUARE_LON);

        locationLogRepository.saveAll(List.of(
            new LocationLog(null, now.minusSeconds(30), DEVICE_ID, near, null),
            new LocationLog(null, now.minusSeconds(20), DEVICE_ID, near, null),
            new LocationLog(null, now.minusSeconds(10), DEVICE_ID, near, null),
            new LocationLog(null, now.minusSeconds(5), DEVICE_ID, near, null),
            new LocationLog(null, now.minusSeconds(50), DEVICE_ID, far, null)
        ));
        locationLogRepository.flush();

        // Act
        UserLocationContext ctx = locationAggregationService.aggregateLocationInfo(now, DEVICE_ID);
        System.out.println(ctx);

        // Assert
        assertThat(ctx.stationary()).isFalse();
    }

    @Test
    void aggregateLocationInfo_usesOnlyWindowDuration() {
        // Arrange: known place near Tech Square
        Point techSquare = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);
        KnownPlace kp = new KnownPlace(
            null,
            "Tech Square",
            "Work",
            techSquare,
            Instant.now(),
            null,
            KnownPlaceStatus.ESTABLISHED
        );
        knownPlaceRepository.saveAndFlush(kp);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // Inside window
        Point inside = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);

        // Outside window (older than window)
        long windowSeconds = Constants.WINDOW_DURATION_MINS * 60;
        Instant tooOldTs = now.minusSeconds(windowSeconds + 60);

        locationLogRepository.saveAll(List.of(
            new LocationLog(null, tooOldTs, DEVICE_ID, inside, null),              // should be ignored
            new LocationLog(null, now.minusSeconds(20), DEVICE_ID, inside, null),  // included
            new LocationLog(null, now.minusSeconds(10), DEVICE_ID, inside, null)   // included
        ));
        locationLogRepository.flush();

        // Act
        UserLocationContext ctx = locationAggregationService.aggregateLocationInfo(now, DEVICE_ID);

        // Assert: oldest should be the oldest *inside* the window, not the too-old one
        assertThat(ctx.oldestTimestampInWindow()).isEqualTo(now.minusSeconds(20));
    }

    @Test
    void aggregateLocationInfo_stationary_true_whenFarPointOutOfWindowTimeRange() {
        // Arrange: known place near Tech Square
        Point techSquare = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);
        KnownPlace kp = new KnownPlace(
            null,
            "Tech Square",
            "Work",
            techSquare,
            Instant.now(),
            null,
            KnownPlaceStatus.ESTABLISHED
        );
        knownPlaceRepository.saveAndFlush(kp);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // One point far enough to exceed stationary radius (1km-ish)
        Point near = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);
        Point far = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT + 0.01, TECH_SQUARE_LON);

        locationLogRepository.saveAll(List.of(
            new LocationLog(null, now.minusSeconds(30), DEVICE_ID, near, null),
            new LocationLog(null, now.minusSeconds(20), DEVICE_ID, near, null),
            new LocationLog(null, now.minusSeconds(10), DEVICE_ID, near, null),
            new LocationLog(null, now.minusSeconds(5), DEVICE_ID, near, null),
            //the far point is 50 mins before now, while the max window time range is 45 mins.
            new LocationLog(null, now.minusSeconds((Constants.WINDOW_DURATION_MINS + 5) * 60), DEVICE_ID, far, null)
        ));
        locationLogRepository.flush();

        // Act
        UserLocationContext ctx = locationAggregationService.aggregateLocationInfo(now, DEVICE_ID);
        System.out.println(ctx);

        // Assert
        assertThat(ctx.stationary()).isTrue();
    }

    @Test
    void aggregateLocationInfo_empty_window() {
        // Arrange: known place near Tech Square
        Point techSquare = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);
        KnownPlace kp = new KnownPlace(
            null,
            "Tech Square",
            "Work",
            techSquare,
            Instant.now(),
            null,
            KnownPlaceStatus.ESTABLISHED
        );
        knownPlaceRepository.saveAndFlush(kp);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // One point far enough to exceed stationary radius (1km-ish)

        locationLogRepository.flush();

        // Act
        UserLocationContext ctx = locationAggregationService.aggregateLocationInfo(now, DEVICE_ID);
        System.out.println(ctx);

        // Assert
        assertThat(UserLocationContext.isEmpty(ctx)).isTrue();
    }

    @Test
    void aggregateLocationInfo_usesOnlyMinVisitSubsetForStationaryCheck() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        Point near = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);
        Point far = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT + 0.02, TECH_SQUARE_LON); // far enough to break stationary

        long minVisitSeconds = Constants.MIN_TIME_FOR_VISIT * Constants.MINS_TO_SECONDS;

        // Recent points (inside MIN_VISIT_TIME) are clustered
        LocationLog recent1 = new LocationLog(null, now.minusSeconds(Math.max(1, minVisitSeconds - 30)), DEVICE_ID, near, null);
        LocationLog recent2 = new LocationLog(null, now.minusSeconds(Math.max(1, minVisitSeconds - 20)), DEVICE_ID, near, null);
        LocationLog recent3 = new LocationLog(null, now.minusSeconds(Math.max(1, minVisitSeconds - 10)), DEVICE_ID, near, null);

        // This point is inside WINDOW_DURATION but older than MIN_VISIT_TIME => should be ignored for stationary
        LocationLog oldButInWindow = new LocationLog(null, now.minusSeconds(minVisitSeconds + 60), DEVICE_ID, far, null);

        locationLogRepository.saveAll(List.of(oldButInWindow, recent1, recent2, recent3));
        locationLogRepository.flush();

        UserLocationContext ctx = locationAggregationService.aggregateLocationInfo(now, DEVICE_ID);

        assertThat(UserLocationContext.isEmpty(ctx)).isFalse();
        assertThat(ctx.stationary()).isTrue();
    }

    @Test
    void aggregateLocationInfo_includesPointExactlyAtMinVisitCutoff() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        long minVisitSeconds = Constants.MIN_TIME_FOR_VISIT * Constants.MINS_TO_SECONDS;

        Point p1 = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);
        Point p2 = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT + 0.00001, TECH_SQUARE_LON);
        Point p3 = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON + 0.00001);

        Instant cutoff = now.minusSeconds(minVisitSeconds);

        locationLogRepository.saveAll(List.of(
            new LocationLog(null, cutoff, DEVICE_ID, p1, null),                 // exactly on boundary -> should be included
            new LocationLog(null, now.minusSeconds(Math.max(1, minVisitSeconds - 10)), DEVICE_ID, p2, null),
            new LocationLog(null, now.minusSeconds(Math.max(1, minVisitSeconds - 5)), DEVICE_ID, p3, null)
        ));
        locationLogRepository.flush();

        UserLocationContext ctx = locationAggregationService.aggregateLocationInfo(now, DEVICE_ID);

        assertThat(UserLocationContext.isEmpty(ctx)).isFalse();
        assertThat(ctx.stationary()).isTrue();

        // oldestTimestampInWindow depends on whether your service uses full window or recent subset.
        // With current code (full window), the cutoff point is inside the window, so oldest should be cutoff.
        assertThat(ctx.oldestTimestampInWindow()).isEqualTo(cutoff);
    }

    @Test
    void aggregateLocationInfo_returnsEmptyWhenWindowExistsButMinVisitSubsetIsEmpty() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        Point p = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);

        long minVisitSeconds = Constants.MIN_TIME_FOR_VISIT * Constants.MINS_TO_SECONDS;
        long windowSeconds = Constants.WINDOW_DURATION_MINS * Constants.MINS_TO_SECONDS;

        // Put points inside the overall window, but older than the min-visit cutoff
        Instant t1 = now.minusSeconds(minVisitSeconds + 30);
        Instant t2 = now.minusSeconds(Math.min(windowSeconds - 1, minVisitSeconds + 60));

        // Ensure they're still inside the window
        if (t2.isBefore(now.minusSeconds(windowSeconds))) {
            t2 = now.minusSeconds(windowSeconds - 1);
        }

        locationLogRepository.saveAll(List.of(
            new LocationLog(null, t1, DEVICE_ID, p, null),
            new LocationLog(null, t2, DEVICE_ID, p, null)
        ));
        locationLogRepository.flush();

        UserLocationContext ctx = locationAggregationService.aggregateLocationInfo(now, DEVICE_ID);

        assertThat(UserLocationContext.isEmpty(ctx)).isTrue();
    }

    @Test
    void aggregateLocationInfo_nearestKnownPlace_nullWhenOutsideRadius() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // Known place far away from the cluster
        Point farKnownPlace = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT + 0.05, TECH_SQUARE_LON); // several km
        knownPlaceRepository.saveAndFlush(new KnownPlace(
            null,
            "Far Place",
            "Other",
            farKnownPlace,
            Instant.now(),
            null,
            KnownPlaceStatus.ESTABLISHED
        ));

        Point near1 = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON);
        Point near2 = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT + 0.00001, TECH_SQUARE_LON);
        Point near3 = PointUtil.wgs84FromLatLon(TECH_SQUARE_LAT, TECH_SQUARE_LON + 0.00001);

        locationLogRepository.saveAll(List.of(
            new LocationLog(null, now.minusSeconds(30), DEVICE_ID, near1, null),
            new LocationLog(null, now.minusSeconds(20), DEVICE_ID, near2, null),
            new LocationLog(null, now.minusSeconds(10), DEVICE_ID, near3, null)
        ));
        locationLogRepository.flush();

        UserLocationContext ctx = locationAggregationService.aggregateLocationInfo(now, DEVICE_ID);

        assertThat(UserLocationContext.isEmpty(ctx)).isFalse();
        assertThat(ctx.nearestKnownPlaceInRadius()).isNull();
    }
}
