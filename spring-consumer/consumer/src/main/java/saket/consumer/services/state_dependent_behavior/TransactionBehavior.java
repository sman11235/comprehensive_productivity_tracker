package saket.consumer.services.state_dependent_behavior;

import java.time.Instant;

import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Transactional;

import saket.consumer.domain.TransactionLog;
import saket.consumer.repositories.VisitRepository;

@Component
public class TransactionBehavior implements ITransactionBehavior{
    private final VisitRepository visitRepository;

    public TransactionBehavior(VisitRepository vRepo) {
        visitRepository = vRepo;
    }

    @Transactional
    @Override
    public TransactionLog onTransactionEvent(TransactionLog event) {
        Instant eventTime = event.getTimestamp();
        if (eventTime == null) return event;

        visitRepository.findVisitContainingTime(eventTime)
            .ifPresent(visit -> event.setVisit(visit));
        return event;
    }
}
