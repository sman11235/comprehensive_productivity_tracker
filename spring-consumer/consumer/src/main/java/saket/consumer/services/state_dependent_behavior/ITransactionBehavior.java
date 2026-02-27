package saket.consumer.services.state_dependent_behavior;

import saket.consumer.domain.TransactionLog;

public interface ITransactionBehavior {
    TransactionLog onTransactionEvent(TransactionLog event);
}
