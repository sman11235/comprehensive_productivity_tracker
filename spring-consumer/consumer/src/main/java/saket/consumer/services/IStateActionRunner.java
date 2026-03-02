package saket.consumer.services;

import java.util.List;

import saket.consumer.domain.actions.ActionResult;
import saket.consumer.domain.actions.StateAction;
import saket.consumer.domain.actions.IStateActionRepository;

/**
 * An interface that defines the behavior of classes that will run StateActions.
 * Implementations should modify the persistence layer.
 */
public interface IStateActionRunner {
    /**
     * The function that modifies the persistence layer of the application based upon inputted StateActions.
     * @param actions Inputted StateActions from StateDecisions.
     * @param repository The repository that defines the changes to the persistence layer.
     * @return the results of all actions
     */
    List<ActionResult> run(List<StateAction> actions, IStateActionRepository repository);
}
