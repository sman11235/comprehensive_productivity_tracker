package saket.consumer.services.state_dependent_behavior;

import saket.consumer.domain.EventDTO;

public interface ILocationBehavior {
    void onLocationEvent(EventDTO event);
}
