package saket.consumer.services.state_dependent_behavior;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import saket.consumer.domain.EventDTO;
import saket.consumer.domain.LocationDTO;
import saket.consumer.domain.LocationLog;
import saket.consumer.domain.Visit;
import saket.consumer.domain.actions.ActionResult;
import saket.consumer.domain.actions.IStateActionRepository;
import saket.consumer.domain.userFSM.StateDecision;
import saket.consumer.domain.userFSM.UserLocationContext;
import saket.consumer.domain.userFSM.UserState;
import saket.consumer.domain.userFSM.states.DiscreteState;
import saket.consumer.repositories.LocationLogRepository;
import saket.consumer.repositories.VisitRepository;
import saket.consumer.services.ILocationDtoMapper;
import saket.consumer.services.IStateActionRunner;
import saket.consumer.services.LocationAggregationService;
import saket.consumer.services.UserStateMachineService;
import saket.consumer.services.UserStateStore;

@Slf4j
@Component
public class LocationBehavior implements ILocationBehavior {

    private final ObjectMapper jsonReader;
    private final LocationLogRepository locationRepo;
    private final LocationAggregationService locationAggregator;
    private final UserStateMachineService userStateService;
    private final UserStateStore userStateStore;
    private final IStateActionRunner actionRunner;
    private final IStateActionRepository actionRepository;
    private final ILocationDtoMapper locationMapper;
    private final VisitRepository visitRepository;

    public LocationBehavior(
        ObjectMapper jReader, 
        LocationLogRepository locationRepo,
        LocationAggregationService locationAggregator,
        UserStateMachineService userStateService,
        UserStateStore userStateStore,
        IStateActionRunner actionRunner,
        IStateActionRepository actionRepository,
        ILocationDtoMapper locationMapper,
        VisitRepository visitRepository
    ) {
        jsonReader = jReader;
        this.locationRepo = locationRepo;
        this.locationAggregator = locationAggregator;
        this.userStateService = userStateService;
        this.userStateStore = userStateStore;
        this.actionRunner = actionRunner;
        this.actionRepository = actionRepository;
        this.locationMapper = locationMapper;
        this.visitRepository = visitRepository;
    }

    @Override
    public void onLocationEvent(EventDTO event) {
        LocationDTO payload = null;
        try {
            payload = jsonReader.treeToValue(event.payload(), LocationDTO.class);
        } catch (JsonProcessingException e) {
            log.error("JSON payload from EventDTO was malformed in LocationStrategy.");
            return;
        }

        LocationLog newLoc = locationMapper.toEntity(payload);
        UserState currentState = userStateStore.get();
        if (currentState.getState() == DiscreteState.VISITING) {
            Visit currentVisit = visitRepository.getReferenceById(currentState.getCurrentVisit());
            newLoc.setVisit(currentVisit);
        }
        newLoc = locationRepo.save(newLoc);
        
        UserLocationContext userLocationCtx = locationAggregator.aggregateLocationInfo(newLoc.getTimestamp(), event.deviceId());

        if (UserLocationContext.isEmpty(userLocationCtx))
            return;

        StateDecision stateDecision = userStateService.nextState(currentState, userLocationCtx);

        List<ActionResult> actionResults = actionRunner.run(stateDecision.actions(), actionRepository);

        Optional<Long> newVisitId = Optional.empty();
        boolean closeCurrentVisit = false;

        for (var actionResult : actionResults) {
            if (newVisitId.isEmpty())
                newVisitId = Optional.ofNullable(actionResult.newOpenVisitId());
            
            if (!closeCurrentVisit)
                closeCurrentVisit = actionResult.closeCurrentVisit();
        }

        UserState newState = UserState.of(stateDecision.state(), newVisitId);

        if (closeCurrentVisit) {
            newState.setVisit(null);
        } else if (newVisitId.isEmpty()) {
            newState.setVisit(currentState.getCurrentVisit());
        }

        userStateStore.update((old) -> {
            return newState;
        });
    }
}
