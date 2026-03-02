package saket.consumer.domain.actions;

/**
 * Injects a visit id into an action. 
 */
public interface IVisitInjectable {
    /**
     * Injects a visit into an astion with no resolved visit. 
     * @param visit the visit id to be injected.
     * @return a fully built state action.
     */
    StateAction withVisitId(long visit);
}
