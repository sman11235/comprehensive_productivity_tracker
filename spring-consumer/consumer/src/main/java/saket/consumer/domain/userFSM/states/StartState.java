package saket.consumer.domain.userFSM.states;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import saket.consumer.domain.actions.CreateKnownPlaceStartVisitAndAssignEvents;
import saket.consumer.domain.actions.StartVisitAndAssignEvents;
import saket.consumer.domain.userFSM.StateDecision;
import saket.consumer.domain.userFSM.UserLocationContext;
import saket.consumer.domain.userFSM.UserState;
import saket.consumer.services.Constants;

/**
 * This class represents the START state of the user.
 * This means that the program does not have a sliding window long enough to make assumptions about the user..
 */
public class StartState implements IUserState {

    @Override
    public DiscreteState stateName() {
        return DiscreteState.START;
    }

    @Override
    public StateDecision next(UserState userContext, UserLocationContext locationContext) {
        long windowLengthMins = Math.abs(
            Duration.between(
                locationContext.timestamp(), 
                locationContext.oldestTimestampInWindow()
            ).toMinutes()
        );
        if (windowLengthMins <= Constants.MIN_TIME_FOR_VISIT) {
            return new StateDecision(DiscreteState.START, 
                List.of()
            );
        }

        if (locationContext.stationary()) {
            Instant visitStart = locationContext.timestamp()
                .minusSeconds(Constants.MIN_TIME_FOR_VISIT * Constants.MINS_TO_SECONDS);
            if (locationContext.nearestKnownPlaceInRadius() == null) {
                return new StateDecision(DiscreteState.VISITING,
                    List.of(
                        new CreateKnownPlaceStartVisitAndAssignEvents(
                            locationContext.centroid(), 
                            locationContext.timestamp()
                                .minusSeconds(Constants.MIN_TIME_FOR_VISIT * Constants.MINS_TO_SECONDS),
                            locationContext.timestamp(),
                            locationContext.locationName()
                        )
                    )
                );
            }

            return new StateDecision(DiscreteState.VISITING, 
                List.of(
                    new StartVisitAndAssignEvents(
                        locationContext.nearestKnownPlaceInRadius().getId(), 
                        visitStart,
                        locationContext.timestamp()
                    )
                )
            );
        }

        return new StateDecision(DiscreteState.MOVING, List.of());
    }
}
