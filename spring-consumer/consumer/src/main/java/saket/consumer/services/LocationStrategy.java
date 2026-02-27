package saket.consumer.services;

import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import saket.consumer.domain.EventDTO;
import saket.consumer.services.state_dependent_behavior.LocationBehavior;

@Slf4j
@Component
public class LocationStrategy implements ITypeStrategy {

    private final LocationBehavior locationBehavior;

    public LocationStrategy(LocationBehavior locationBehavior) {
        this.locationBehavior = locationBehavior;
    }

    @Override
    public String getTopicType() {
        return "saket.location";
    }

    @Transactional
    @Override
    public void handle(EventDTO event) {
        locationBehavior.onLocationEvent(event);
    }
}
