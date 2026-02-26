package saket.consumer.domain.userFSM.states;

import java.time.Duration;
import java.util.List;

import saket.consumer.domain.actions.EndVisit;
import saket.consumer.domain.userFSM.StateDecision;
import saket.consumer.domain.userFSM.UserLocationContext;
import saket.consumer.domain.userFSM.UserState;
import saket.consumer.services.Constants;

/**
 * This class represents the VISITING state of the user.
 * This means that the user is visiting a certain known_place.
 */
public class VisitingState implements IUserState {

    @Override
    public DiscreteState stateName() {
        return DiscreteState.VISITING;
    }

    @Override
    public StateDecision next(UserState userContext, UserLocationContext locationContext) {
        if (userContext.getCurrentVisit() == null) {
            throw new IllegalStateException("When state is VisitingState, user must be visiting a known_place." +
                                            " Currently, currentVisitID is null (User is not visiting anywhere).");
        }
        long windowLengthMins = Math.abs(Duration.between(locationContext.timestamp(), locationContext.oldestTimestampInWindow()).toMinutes());
        if (windowLengthMins <= Constants.MIN_TIME_FOR_VISIT) {
            return new StateDecision(DiscreteState.START, 
                List.of(new EndVisit(userContext.getCurrentVisit(), locationContext.timestamp()))
            );
        }
        if (locationContext.stationary()) {
            return new StateDecision(DiscreteState.VISITING, 
                List.of()
            );
        }
        return new StateDecision(DiscreteState.MOVING, 
            List.of(new EndVisit(userContext.getCurrentVisit(), locationContext.timestamp()))
        );
    }
}
