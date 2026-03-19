package saket.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import saket.consumer.domain.userFSM.UserState;
import saket.consumer.domain.userFSM.states.DiscreteState;
import saket.consumer.exceptions.InvalidStateException;

class UserStateTests {

    @Test
    void visitingStateRequiresVisitId() {
        assertThrows(InvalidStateException.class, () -> UserState.of(DiscreteState.VISITING, Optional.empty()));
    }

    @Test
    void nonVisitingStateCannotCarryVisitId() {
        assertThrows(InvalidStateException.class, () -> UserState.of(DiscreteState.MOVING, Optional.of(10L)));
    }
}
