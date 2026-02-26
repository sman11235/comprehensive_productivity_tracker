package saket.consumer.domain.userFSM.states;

import saket.consumer.domain.userFSM.StateDecision;
import saket.consumer.domain.userFSM.UserLocationContext;
import saket.consumer.domain.userFSM.UserState;

/**
 * An interface that defines the behavior of a user state.
 */
public interface IUserState {
    /**
     * Returns the corresponding DiscreteState associated with this IUserState.
     * @return the corresponding DiscreteState associated with this IUserState.
     */
    DiscreteState stateName();
    /**
     * Defines the state changes associated with receiving a new location event.
     * @param userContext the state stored about the user.
     * @param locationContext the condensed location history about the user. Contains the centroid calculated by taking the user's previous locations and averaging them. 
     * @return StateDecision: The next DiscreteState, and the associated state changing actions.
     */
    StateDecision next(UserState userContext, UserLocationContext locationContext); //takes care of visit logic, knownplace logic, and location logic.
}
