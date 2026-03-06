package saket.consumer.services.state_dependent_behavior;

import java.time.Instant;

import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Transactional;

import saket.consumer.domain.HealthLog;
import saket.consumer.repositories.VisitRepository;

@Component
public class HealthBehavior implements IHealthBehavior {
    private final VisitRepository visitRepository;

    public HealthBehavior(VisitRepository vRepo) {
        visitRepository = vRepo;
    }

    @Transactional
    @Override
    public HealthLog onHealthEvent(HealthLog event) {
        Instant eventTime = event.getTimestamp();
        if (eventTime == null) return event;

        visitRepository.findVisitContainingTime(eventTime)
            .ifPresent(visit -> event.setVisit(visit));
        return event;
    }
}
