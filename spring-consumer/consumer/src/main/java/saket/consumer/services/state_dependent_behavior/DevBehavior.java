package saket.consumer.services.state_dependent_behavior;

import java.time.Instant;

import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Transactional;
import saket.consumer.domain.DevLog;
import saket.consumer.repositories.VisitRepository;

@Component
public class DevBehavior implements IDevBehavior {
    private final VisitRepository visitRepo;
    
    public DevBehavior(VisitRepository vRepo) {
        visitRepo = vRepo;
    }

    @Transactional
    @Override
    public DevLog onDevEvent(DevLog event) {
        Instant eventTime = event.getTimestamp();
        if (eventTime == null) return event;

        visitRepo.findVisitContainingTime(eventTime)
            .ifPresent(visit -> event.setVisit(visit));
        return event;
    }
}
