package saket.consumer.domain.actions;

/**
 * A interface to define a class that does a single task (such as find nearby known_places.)
 * It uses StateActionContext to interact with infrastructure (like db or redis or kafka).
 */
public interface StateAction {
    /**
     * Executes the action stored within StateAction.
     * @param context The infrastructure context to which the action will change the state of.
     * @return ActionResult. Read its javadocs for more info.
     */
    ActionResult execute(IStateActionRepository context);
}
