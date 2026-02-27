package saket.consumer.services.state_dependent_behavior;

import saket.consumer.domain.DevLog;

public interface IDevBehavior {
    DevLog onDevEvent(DevLog event);
}
