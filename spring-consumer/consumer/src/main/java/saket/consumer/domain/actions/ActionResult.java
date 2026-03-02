package saket.consumer.domain.actions;

/**
 * The result of a state changing action action.
 * @param newOpenVisitId if the StateAction results in a open visit, then the visit's ID. else null.
 * @param closeCurrentVisit A bool. If true, it means that the visit has been closed in the DB.
 */
public record ActionResult(Long newOpenVisitId, boolean closeCurrentVisit) {
    public static ActionResult emptyResult() {
        return new ActionResult(null, false);
    }
}
