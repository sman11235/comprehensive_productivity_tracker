package saket.consumer.domain.userFSM;

import java.time.Instant;

import org.locationtech.jts.geom.Point;

import saket.consumer.domain.KnownPlace;

/**
 * An aggregate of information derived from the sliding window of recent previous locations the user was.
 * @param deviceId the string id the device the location was recorded on.
 * @param timestamp the Instant (time) of the last location event.
 * @param centroid the average Point of all points in the sliding window of recent prev locations.
 * @param stationary a boolean that calculates whether the user is stationary or not.
 * @param nearestKnownPlaceIn50m the nearest known_place in a radius.
 * @param oldestTimestampInWindow the oldest point's timestamp in the sliding window.
 */
public record UserLocationContext (
    String deviceId,
    Instant timestamp,
    Point centroid,
    boolean stationary,
    KnownPlace nearestKnownPlaceInRadius,
    Instant oldestTimestampInWindow
) {
    public static UserLocationContext empty() {
        return new UserLocationContext(null, null, null, false, null, null);
    }

    public static boolean isEmpty(UserLocationContext ctx) {
        return 
            ctx.deviceId == null &&
            ctx.timestamp == null &&
            ctx.centroid == null &&
            !ctx.stationary &&
            ctx.nearestKnownPlaceInRadius == null &&
            ctx.oldestTimestampInWindow == null;

    }
}
