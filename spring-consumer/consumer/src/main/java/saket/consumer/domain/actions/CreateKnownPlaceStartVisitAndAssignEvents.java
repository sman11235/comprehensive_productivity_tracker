package saket.consumer.domain.actions;

import java.time.Instant;

import org.locationtech.jts.geom.Point;

/**
 * Creates a known place, starts a visit there, and backfills unassigned events in the visit window.
 */
public record CreateKnownPlaceStartVisitAndAssignEvents(
    Point centroid,
    Instant start,
    Instant end,
    String locationName
) implements StateAction {
    @Override
    public ActionResult execute(IStateActionRepository context) {
        long placeId = context.createNewKnownPlace(centroid, start, locationName);
        long visitId = context.startVisit(placeId, start);
        context.assignVisitToEvents(visitId, start, end);
        return new ActionResult(visitId, false);
    }
}
