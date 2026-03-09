package saket.consumer.services;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import saket.consumer.domain.KnownPlace;
import saket.consumer.domain.LocationLog;
import saket.consumer.domain.userFSM.UserLocationContext;
import saket.consumer.repositories.KnownPlaceRepository;
import saket.consumer.repositories.LocationLogRepository;

/**
 * A service that contains the logic that enriches raw LocationLogs into
 * useful UserLocationContexts. 
 */
@Service
public class LocationAggregationService {
    private final LocationLogRepository locationRepo;
    private final KnownPlaceRepository placeRepo;

    public LocationAggregationService(LocationLogRepository l, KnownPlaceRepository k) {
        locationRepo = l;
        placeRepo = k;
    }

    /**
     * Aggregates the raw LocationLog information into an immutable UserLocationContext object.
     * @param currentTime the timestamp of the last inserted LocationLog.
     * @param deviceId the id of the device that sent the location log.
     * @return UserLocationContext
     */
    @Transactional(readOnly = true)
    public UserLocationContext aggregateLocationInfo(Instant currentTime, String deviceId) {
        //the total large window that contains both current points and old point that will 
        //not be considered in the calculation.
        //this is so that we can calculate whether the state machine should be in the start state or not.
        List<LocationLog> window = getWindow(currentTime, Constants.WINDOW_DURATION_MINS);
        if (window.isEmpty()) return UserLocationContext.empty();
        
        Instant cutoff = currentTime.minusSeconds(
            Constants.MIN_TIME_FOR_VISIT * Constants.MINS_TO_SECONDS
        );

        //points for ease of calculation.
        List<Point> points = window.stream()
            .filter(loc -> !loc.getTimestamp().isBefore(cutoff)) //!isBefore for inclusive window (isAfter is exclusive).
            .map(LocationLog::getLoc)
            .filter(java.util.Objects::nonNull)
            .toList();
        
        if (points.isEmpty()) return UserLocationContext.empty();

        Optional<Point> centroidOrNull = PointUtil.centroid(points);
        Point centroid = centroidOrNull.orElseThrow();

        double maxDistanceFromCentroid = maxDistanceFromCentroid(points, centroid);
        boolean stationary = maxDistanceFromCentroid <= Constants.STATIONARY_RADIUS_M;

        KnownPlace closestKnownPlace = getClosestKnownPlaceInRadius(centroid, Constants.KNOWN_PLACE_MATCH_RADIUS_M).orElse(null);

        Instant oldestTimestamp = getOldestTimestampInWindow(window);
        System.out.println("Points: " + points);
        System.out.println("Window: " + window);
        System.out.println("Stationary: " + stationary);

        return new UserLocationContext(
            deviceId, 
            currentTime, 
            centroid, 
            stationary, 
            closestKnownPlace, 
            oldestTimestamp
        );
    }

    /**
     * Gets the locationlog window
     * @param currentTime the timestamp of the last inserted LocationLog.
     * @param windowLengthMins The temporal length of the window.
     * @return List of location logs.
     */
    private List<LocationLog> getWindow(Instant currentTime, long windowLengthMins) {
        long seconds = windowLengthMins * Constants.MINS_TO_SECONDS;
        return locationRepo.findByTimeRange(currentTime.minusSeconds(seconds), currentTime);
    }

    /**
     * Gets the closest established known_place from point centroid, iff the known_place if within radius meters of centroid.
     * @param centroid the central point from which the search will be conducted.
     * @param radius the radius of the search.
     * @return the known_place.
     */
    private Optional<KnownPlace> getClosestKnownPlaceInRadius(Point centroid, double radius) {
        List<KnownPlace> nearby = placeRepo.findNearby(centroid, radius);
        if (nearby.isEmpty()) return Optional.empty();
        return nearby.stream()
            .min(Comparator.comparingDouble(p -> PointUtil.distanceInMeters(p.getLoc(), centroid)));
    }

    /**
     * Gets the distance of the point in window that is furthest away from point. 
     * @param window the list of points to be compared against point.
     * @param point the point that distance will be calculated from.
     * @return the furthest distance in window from point.
     */
    private Double maxDistanceFromCentroid(List<Point> window, Point centroid) {

        double max = 0.0;
        for (Point p : window) {
            if (p == null) continue;
            double d = PointUtil.distanceInMeters(p, centroid);
            if (d > max) max = d;
        }
        return max;
    }

    /**
     * gets the oldests timestamp from the given window.
     * @param window the window of LocationLogs
     * @return the oldest timestamp.
     */
    private Instant getOldestTimestampInWindow(List<LocationLog> window) {
        Instant oldest = Instant.MAX;
        for (LocationLog l : window) {
            if (l.getTimestamp().isBefore(oldest)) {
                oldest = l.getTimestamp();
            }
        }
        return oldest;
    }
}
