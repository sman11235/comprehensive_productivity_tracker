package saket.consumer.domain.actions;

import java.time.Instant;

import org.locationtech.jts.geom.Point;

/**
 * defines how StateAction can communicate with other backend infrastructure.
 */
public interface IStateActionRepository {
    /**
     * Creates and returns new place's ID.
     * @return the new place's ID.
     */
    long createNewKnownPlace(Point centroid, Instant now);
    /**
     * Starts a visit at place [placeId] and at time [start].
     * @return the ID of the visit created.
     */
    long startVisit(long placeId, Instant start);
    /**
     * Ends a visit at place [placeId] and at time [start].
     */
    void endVisit(long visitId, Instant end);

    /**
     * Assigns givien visit id to all events processed between start time and end time.
     */
    void assignVisitToEvents(long visitId, Instant start, Instant end);
}
