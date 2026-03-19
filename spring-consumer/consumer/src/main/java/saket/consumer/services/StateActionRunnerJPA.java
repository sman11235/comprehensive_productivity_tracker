package saket.consumer.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import saket.consumer.domain.actions.ActionResult;
import saket.consumer.domain.actions.IStateActionRepository;
import saket.consumer.domain.actions.StateAction;

/**
 * An impl of IStateActionRunner that handles JPA interactions.
 */
@Component
public class StateActionRunnerJPA implements IStateActionRunner {

    @Override
    public List<ActionResult> run(List<StateAction> actions, IStateActionRepository repository) {
        List<ActionResult> results = new ArrayList<>();
        for (StateAction action : actions) {
            results.add(action.execute(repository));
        }
        return results;
    }
}
