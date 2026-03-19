package saket.consumer.domain.userFSM;

import java.util.Optional;

import saket.consumer.domain.userFSM.states.DiscreteState;
import saket.consumer.exceptions.InvalidStateException;

/**
 * Contains the state of the user.
 * 
 * This is the Source of truth for this entire application, 
 * and the basis for the Finite State Machine the application runs upon.
 */
public class UserState {
    private final DiscreteState state;
    private final Long currentVisit;

    public UserState(DiscreteState s) {
        this(s, null);
    }

    public UserState(DiscreteState s, long currVisitId) {
        this(s, Long.valueOf(currVisitId));
    }

    private UserState(DiscreteState state, Long currentVisit) {
        validate(state, currentVisit);
        this.state = state;
        this.currentVisit = currentVisit;
    }

    public DiscreteState getState() {
        return state;
    }

    public boolean isVisiting() {
        return currentVisit != null;
    }

    public Long getCurrentVisit() {
        return currentVisit;
    }

    public UserState withVisit(Long visit) {
        return new UserState(state, visit);
    }

    public UserState clearVisit() {
        return new UserState(state, null);
    }

    public static final UserState initial() {
        return new UserState(DiscreteState.START);
    } 

    public static final UserState of(DiscreteState state, Optional<Long> visitId) {
        if (visitId.isPresent())
            return new UserState(state, visitId.get());
        return new UserState(state);
    }

    private static void validate(DiscreteState state, Long currentVisit) {
        boolean hasVisit = currentVisit != null;
        if (state == DiscreteState.VISITING && !hasVisit) {
            throw new InvalidStateException("UserState in VISITING must carry a visit id.");
        }
        if (state != DiscreteState.VISITING && hasVisit) {
            throw new InvalidStateException("Only VISITING state may carry a visit id.");
        }
    }
}
