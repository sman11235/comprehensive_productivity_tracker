package saket.consumer.domain.userFSM.states;

import java.time.Duration;
import java.util.List;

import saket.consumer.domain.actions.AssignVisitToEvents;
import saket.consumer.domain.actions.CreateKnownPlaceAndStartVisitAction;
import saket.consumer.domain.actions.StartVisit;
import saket.consumer.domain.userFSM.StateDecision;
import saket.consumer.domain.userFSM.UserLocationContext;
import saket.consumer.domain.userFSM.UserState;
import saket.consumer.services.Constants;
/**
 * This class represents the MOVING state of the user.
 * This means that the user is traveling, and not visiting a known_place.
 */
public class MovingState implements IUserState {
    
    @Override
    public DiscreteState stateName() {
        return DiscreteState.MOVING;
    }

    @Override
    public StateDecision next(UserState userContext, UserLocationContext locationContext) {

        long windowLengthMins = Math.abs(Duration.between(locationContext.timestamp(), locationContext.oldestTimestampInWindow()).toMinutes());
        if (windowLengthMins <= Constants.MIN_TIME_FOR_VISIT) {
            return new StateDecision(DiscreteState.START, 
                List.of()
            );
        }

        if (locationContext.stationary()) {
            if (locationContext.nearestKnownPlaceInRadius() == null) {
                return new StateDecision(DiscreteState.VISITING, 
                    List.of(
                        new CreateKnownPlaceAndStartVisitAction(
                            locationContext.centroid(),
                            locationContext.timestamp().minusSeconds(Constants.MIN_TIME_FOR_VISIT * Constants.MINS_TO_SECONDS)
                        ),
                        new AssignVisitToEvents(
                            null, 
                            locationContext.timestamp().minusSeconds(Constants.MIN_TIME_FOR_VISIT * Constants.MINS_TO_SECONDS),
                            locationContext.timestamp()
                        )
                    )
                );
            }
            
            return new StateDecision(DiscreteState.VISITING, 
                List.of(
                    new StartVisit(
                        locationContext.nearestKnownPlaceInRadius().getId(), 
                        locationContext.timestamp().minusSeconds(Constants.MIN_TIME_FOR_VISIT * Constants.MINS_TO_SECONDS)
                    ),
                    new AssignVisitToEvents(
                        null, 
                        locationContext.timestamp().minusSeconds(Constants.MIN_TIME_FOR_VISIT * Constants.MINS_TO_SECONDS),
                        locationContext.timestamp()
                    )
                )
            );
        }

        return new StateDecision(DiscreteState.MOVING, List.of());
    }
}
