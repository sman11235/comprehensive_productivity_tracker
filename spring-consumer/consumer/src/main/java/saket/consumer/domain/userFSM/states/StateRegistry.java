package saket.consumer.domain.userFSM.states;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * This class will map DiscreteStates to implementations of IUserState.
 */
public class StateRegistry {
    private final Map<DiscreteState, IUserState> registry;

    /**
     * Constructor for state registry.
     * Will initialize the registry EnumMap with all IUserStates manually.
     */
    public StateRegistry() {
        registry = new EnumMap<>(DiscreteState.class);
        registry.put(DiscreteState.START, new StartState());
        registry.put(DiscreteState.MOVING, new MovingState());
        registry.put(DiscreteState.VISITING, new VisitingState());
    }

    /**
     * fetchs the corresponding IUserState impl that matches the given DiscreteState.
     * @param state the given DiscreteState.
     * @return Optional: null or the corresponding IUserState.
     */
    public Optional<IUserState> get(DiscreteState state) {
        IUserState userState = registry.get(state);
        return Optional.ofNullable(userState);
    }
}
