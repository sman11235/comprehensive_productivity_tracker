package saket.consumer.domain.userFSM;

import java.util.List;

import saket.consumer.domain.actions.StateAction;
import saket.consumer.domain.userFSM.states.DiscreteState;

/**
 * Represents the decision of a state change.
 * @param state the next state from the current state.
 * @param actions a list of state altering commands to emit.
 */
public record StateDecision(DiscreteState state, List<StateAction> actions) {

}
