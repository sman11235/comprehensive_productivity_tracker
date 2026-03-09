package saket.consumer.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import saket.consumer.domain.DevLog;
import saket.consumer.domain.EventDTO;
import saket.consumer.repositories.DevLogRepository;
import saket.consumer.services.state_dependent_behavior.IDevBehavior;

/**
 * A class that handles all behaviors related to dev activity
 */
@Slf4j
@Component
public class DevStrategy implements ITypeStrategy {

    private final ObjectMapper jsonReader;
    private final DevLogRepository devLogRepository;
    private final IDevBehavior devBehavior;

    public DevStrategy(
        ObjectMapper jReader,
        DevLogRepository devLogRepository,
        IDevBehavior devBehavior
    ) {
        this.jsonReader = jReader;
        this.devLogRepository = devLogRepository;
        this.devBehavior = devBehavior;
    }

    @Override
    public String getTopicType() {
        return "saket.dev_activity";
    }

    @Transactional
    @Override
    public void handle(EventDTO event) {
        DevLog payload;
        try {
            payload = jsonReader.treeToValue(event.payload(), DevLog.class);
        } catch (JsonProcessingException e) {
            log.error("JSON payload from EventDTO was malformed in DevStrategy.");
            return;
        }
        DevLog completeDevLog = devBehavior.onDevEvent(payload);
        devLogRepository.save(completeDevLog);
    }
}
