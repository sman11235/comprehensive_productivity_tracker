package saket.consumer.domain.actions;

import java.time.Instant;

/**
 * Starts a visit at a known place and backfills unassigned events in the visit window.
 */
public record StartVisitAndAssignEvents(long placeId, Instant start, Instant end) implements StateAction {
    @Override
    public ActionResult execute(IStateActionRepository context) {
        long visitId = context.startVisit(placeId, start);
        context.assignVisitToEvents(visitId, start, end);
        return new ActionResult(visitId, false);
    }
}
