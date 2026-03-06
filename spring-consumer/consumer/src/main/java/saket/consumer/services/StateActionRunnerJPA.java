package saket.consumer.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import saket.consumer.domain.actions.ActionResult;
import saket.consumer.domain.actions.StateAction;
import saket.consumer.exceptions.VisitInjectionError;
import saket.consumer.domain.actions.IStateActionRepository;
import saket.consumer.domain.actions.IVisitInjectable;

@Component
public class StateActionRunnerJPA implements IStateActionRunner {

    @Override
    public List<ActionResult> run(List<StateAction> actions, IStateActionRepository repository) {

        List<ActionResult> results = new ArrayList<>();
        Long visitId = null;

        for (StateAction action : actions) {

            // If visit-dependent, inject immediately (preserves order)
            if (action instanceof IVisitInjectable injectable) {
                if (visitId == null) {
                    throw new VisitInjectionError(
                        "Visit ID not available yet for action: " + action.getClass().getSimpleName()
                    );
                }
                action = injectable.withVisitId(visitId);
            }

            ActionResult result = action.execute(repository);
            results.add(result);

            // Only update visitId if action returned one (don’t overwrite with null)
            if (result.newOpenVisitId() != null) {
                visitId = result.newOpenVisitId();
            }
        }

        return results;
    }
}

