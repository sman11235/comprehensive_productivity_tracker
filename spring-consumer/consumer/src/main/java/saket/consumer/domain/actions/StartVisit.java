package saket.consumer.domain.actions;

import java.time.Instant;

/**
 * Starts a visit at known_place with id placeId
 */
public record StartVisit(long placeId, Instant start) implements StateAction {
    @Override
    public ActionResult execute(IStateActionRepository context) {
        long visitId = context.startVisit(placeId, start);
        return new ActionResult(visitId, false);
    }
}
