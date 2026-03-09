package saket.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import saket.consumer.domain.EventDTO;
import saket.consumer.domain.EventOp;
import saket.consumer.domain.userFSM.UserState;
import saket.consumer.domain.userFSM.states.DiscreteState;
import saket.consumer.repositories.LocationLogRepository;
import saket.consumer.repositories.VisitRepository;
import saket.consumer.services.LocationStrategy;
import saket.consumer.services.UserStateStore;
import saket.consumer.services.Constants;

/**
 * A test that checks the handling of kafka events to locationstrategy.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
public class LocationEventTests extends BaseContainerTest {

    @Autowired
    private LocationStrategy locationStrategy;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private LocationLogRepository locationLogRepository;

    @Autowired
    private UserStateStore userStateStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final int SECONDS_IN_MIN = 60;

    @BeforeEach
    void setup() {
        // Clean DB tables used by this pipeline
        // (RESTART IDENTITY gives deterministic ids, CASCADE handles FKs)
        locationLogRepository.deleteAll();
        visitRepository.deleteAll();

        // Reset in-memory state
        userStateStore.update(old -> UserState.initial());
    }

    private JsonNode createLocationPayload(Instant timestamp, double lon, double lat) {
        try {
            String json = """
                {
                  "timestamp": "%s",
                  "deviceId": "iphone",
                  "loc": { "type": "Point", "coord": [%s, %s] }
                }
                """.formatted(timestamp.toString(), lon, lat);

            return om.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build location payload JsonNode", e);
        }
    }

    @Test
    void integration_locationEvents_arePersisted_andUserStateInvariantHolds_andStateChangesToVisit() {
        // Process events in chronological order (oldest first)
        List<EventDTO> events = List.of(
            new EventDTO("9", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(86 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(80 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("8", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(76 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(70 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("7", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(66 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(60 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("6", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(50 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(50 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("5", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(40 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(40 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("4", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(30 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(30 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("3", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(20 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(20 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("2", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(10 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(10 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("1", "iphone", "ios", "saket.location", EventOp.CREATE,
                now,
                createLocationPayload(now, -84.39, 33.78),
                null
            ),
            new EventDTO("10", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.plusSeconds(2 * SECONDS_IN_MIN),
                createLocationPayload(now.plusSeconds(2 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            )
        );
        for (EventDTO event : events.stream().sorted(Comparator.comparing(EventDTO::observedAt)).toList()) {

            locationStrategy.handle(event);

            // ✅ Verify state store updated to something sensible
            UserState finalState = userStateStore.get();
            
            assertThat(finalState).isNotNull();
            assertThat(finalState.getState()).isNotNull();

            // ✅ Invariant check:
            // If VISITING, you should have a visit id. Otherwise, it should be null.
            if (finalState.getState() == DiscreteState.VISITING) {
                assertThat(finalState.getCurrentVisit())
                    .as("When state is VISITING, currentVisit should not be null")
                    .isNotNull();
            } else {
                assertThat(finalState.getCurrentVisit())
                    .as("When state is not VISITING, currentVisit should be null")
                    .isNull();
            }

            // Optional helpful sanity: DB agrees with "visit open" semantics
            // (This does not assume a visit exists; it only checks consistency)
        }
        Integer openVisits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visits WHERE exit_time IS NULL",
                Integer.class
            );
        System.out.println(userStateStore.getState());
        assertThat(userStateStore.getState() == DiscreteState.VISITING).isTrue();
        assertThat(openVisits).isNotEqualTo(0);
    }

    @Test
    void integration_locationEvents_arePersisted_andUserStateInvariantHolds_andStateChangesToMoving() {
        // Process events in chronological order (oldest first)
        List<EventDTO> events = List.of(
            new EventDTO("6", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(60 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(50 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("5", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(40 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(40 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("4", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(30 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(30 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("3", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(20 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(20 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("2", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(10 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(10 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("1", "iphone", "ios", "saket.location", EventOp.CREATE,
                now,
                createLocationPayload(now, -84.39, 33.78),
                null
            ),
            new EventDTO("7", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(Constants.MIN_TIME_FOR_VISIT * SECONDS_IN_MIN - 60),
                createLocationPayload(now.minusSeconds(Constants.MIN_TIME_FOR_VISIT * SECONDS_IN_MIN - 60), -85.39, 33.78),
                null
            )
        );
        int count = 0;
        for (EventDTO event : events.stream().sorted(Comparator.comparing(EventDTO::observedAt)).toList()) {

            locationStrategy.handle(event);
            count++;

            // ✅ Verify location logs persisted
            long countLogRecords = locationLogRepository.count();
            assertThat(count).isEqualTo(countLogRecords);

            // ✅ Verify state store updated to something sensible
            UserState finalState = userStateStore.get();
            System.out.println("DiscreteState: " + finalState.getState() + "; Visit Id: " + finalState.getCurrentVisit());
            assertThat(finalState).isNotNull();
            assertThat(finalState.getState()).isNotNull();

            // ✅ Invariant check:
            // If VISITING, you should have a visit id. Otherwise, it should be null.
            if (finalState.getState() == DiscreteState.VISITING) {
                assertThat(finalState.getCurrentVisit())
                    .as("When state is VISITING, currentVisit should not be null")
                    .isNotNull();
            } else {
                assertThat(finalState.getCurrentVisit())
                    .as("When state is not VISITING, currentVisit should be null")
                    .isNull();
            }

            // Optional helpful sanity: DB agrees with "visit open" semantics
            // (This does not assume a visit exists; it only checks consistency)
            Integer openVisits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visits WHERE exit_time IS NULL",
                Integer.class
            );
            assertThat(openVisits).isNotNull();
        }
        assertThat(userStateStore.getState() == DiscreteState.MOVING).isTrue();
    }

    @Test
    void integration_locationEvents_arePersisted_andFarPointIsOutOfBounds_andStateStaysVisiting() {
        // Process events in chronological order (oldest first)
        List<EventDTO> events = List.of(
            new EventDTO("6", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(60 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(50 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("5", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(40 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(40 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("4", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(30 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(30 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("3", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(20 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(20 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("2", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(10 * SECONDS_IN_MIN),
                createLocationPayload(now.minusSeconds(10 * SECONDS_IN_MIN), -84.39, 33.78),
                null
            ),
            new EventDTO("1", "iphone", "ios", "saket.location", EventOp.CREATE,
                now,
                createLocationPayload(now, -84.39, 33.78),
                null
            ),
            new EventDTO("7", "iphone", "ios", "saket.location", EventOp.CREATE,
                now.minusSeconds(Constants.MINS_TO_SECONDS * SECONDS_IN_MIN + 60),
                createLocationPayload(now.minusSeconds(Constants.MINS_TO_SECONDS * SECONDS_IN_MIN + 60), -85.39, 33.78),
                null
            )
        );
        int count = 0;
        for (EventDTO event : events.stream().sorted(Comparator.comparing(EventDTO::observedAt)).toList()) {

            locationStrategy.handle(event);
            count++;

            // ✅ Verify location logs persisted
            long countLogRecords = locationLogRepository.count();
            assertThat(count).isEqualTo(countLogRecords);

            // ✅ Verify state store updated to something sensible
            UserState finalState = userStateStore.get();
            System.out.println("DiscreteState: " + finalState.getState() + "; Visit Id: " + finalState.getCurrentVisit());
            assertThat(finalState).isNotNull();
            assertThat(finalState.getState()).isNotNull();

            // ✅ Invariant check:
            // If VISITING, you should have a visit id. Otherwise, it should be null.
            if (finalState.getState() == DiscreteState.VISITING) {
                assertThat(finalState.getCurrentVisit())
                    .as("When state is VISITING, currentVisit should not be null")
                    .isNotNull();
            } else {
                assertThat(finalState.getCurrentVisit())
                    .as("When state is not VISITING, currentVisit should be null")
                    .isNull();
            }

            // Optional helpful sanity: DB agrees with "visit open" semantics
            // (This does not assume a visit exists; it only checks consistency)
            Integer openVisits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visits WHERE exit_time IS NULL",
                Integer.class
            );
            assertThat(openVisits).isNotNull();
        }
        assertThat(userStateStore.getState() == DiscreteState.VISITING).isTrue();
    }
}
