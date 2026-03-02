package saket.consumer.services;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Component;

import saket.consumer.domain.userFSM.UserState;
import saket.consumer.domain.userFSM.states.DiscreteState;

@Component
public class UserStateStore {
    private final AtomicReference<UserState> ref =
        new AtomicReference<>(UserState.initial());

    public UserState get() {
        return ref.get();
    }

    public DiscreteState getState() {
        return ref.get().getState();
    }

    public Optional<Long> getVisitId() {
        return Optional.ofNullable(ref.get().getCurrentVisit());
    }

    public UserState update(UnaryOperator<UserState> fn) {
        return ref.updateAndGet(fn);
    }
}

