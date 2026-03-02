package saket.consumer.domain.actions;

import java.time.Instant;

import org.locationtech.jts.geom.Point;

/**
 * A command that creates a known place, names it, and starts a visit.
 */
public record CreateKnownPlaceAndStartVisitAction(
		Point centroid, 
		Instant start
	) implements StateAction {
		@Override
		public ActionResult execute(IStateActionRepository ctx) {
			long placeId = ctx.createNewKnownPlace(centroid, start);
			long visitId = ctx.startVisit(placeId, start);
			return new ActionResult(visitId, false);
		}
}

