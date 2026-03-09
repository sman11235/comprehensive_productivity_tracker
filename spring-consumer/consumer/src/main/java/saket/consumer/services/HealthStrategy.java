package saket.consumer.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import saket.consumer.domain.EventDTO;
import saket.consumer.domain.HealthLog;
import saket.consumer.repositories.HealthLogRepository;
import saket.consumer.services.state_dependent_behavior.IHealthBehavior;

/**
 * A class that handles all behaviors related to health stats.
 */
@Slf4j
@Component
public class HealthStrategy implements ITypeStrategy {

    private final ObjectMapper jsonReader;
    private final HealthLogRepository healthLogRepository;
    private final IHealthBehavior healthBehavior;

    public HealthStrategy(
        ObjectMapper jsonReader,
        HealthLogRepository healthLogRepository,
        IHealthBehavior healthBehavior
    ) {
        this.jsonReader = jsonReader;
        this.healthLogRepository = healthLogRepository;
        this.healthBehavior = healthBehavior;
    }

    @Override
    public String getTopicType() {
        return "saket.health";
    }

    @Transactional
    @Override
    public void handle(EventDTO event) {
        HealthLog payload;
        try {
            payload = jsonReader.treeToValue(event.payload(), HealthLog.class);
        } catch (JsonProcessingException e) {
            log.error("JSON payload from EventDTO was malformed in HealthStrategy.");
            return;
        }
        HealthLog completeHealthLog = healthBehavior.onHealthEvent(payload);
        healthLogRepository.save(completeHealthLog);
    }
}
