package saket.consumer.services.state_dependent_behavior;

import saket.consumer.domain.HealthLog;

public interface IHealthBehavior {
    HealthLog onHealthEvent(HealthLog event);
}
