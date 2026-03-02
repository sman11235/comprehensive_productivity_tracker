package saket.consumer.domain.actions;

import java.time.Instant;

/**
 * Assigns a visit id to all events that happened between times start and end.
 * Put this after a visit producing action with visit = null for visit id injection.
 */
public record AssignVisitToEvents(Long visit, Instant start, Instant end) implements StateAction, IVisitInjectable {
    @Override
    public StateAction withVisitId(long visitId) {
        return new AssignVisitToEvents(visitId, start, end);
    }

    @Override
    public ActionResult execute(IStateActionRepository context) {
        context.assignVisitToEvents(visit, start, end);
        return ActionResult.emptyResult();
    }
}
