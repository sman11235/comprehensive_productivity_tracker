package saket.consumer.services;

import org.springframework.stereotype.Service;

import saket.consumer.domain.userFSM.StateDecision;
import saket.consumer.domain.userFSM.UserLocationContext;
import saket.consumer.domain.userFSM.UserState;
import saket.consumer.domain.userFSM.states.IUserState;
import saket.consumer.domain.userFSM.states.StateRegistry;

/**
 * A class that contains the logic for state changes in the domain.
 * It is stateless.  
 */ 
@Service
public class UserStateMachineService {

    private final StateRegistry stateRegistry;

    public UserStateMachineService() {
        stateRegistry = new StateRegistry();
    }

    /**
     * Gets the next state of the application. 
     * @param userState The information about the current state of the user.
     * @param userLocationContext Location information about the user.
     * @return The state decision (Next state and domain actions).
     */
    public StateDecision nextState(UserState userState, UserLocationContext userLocationContext) {
        IUserState currentState = stateRegistry.get(userState.getState()).orElseThrow();
        return currentState.next(userState, userLocationContext); 
    }
}
