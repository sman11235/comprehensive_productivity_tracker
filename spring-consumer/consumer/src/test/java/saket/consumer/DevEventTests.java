package saket.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import saket.consumer.domain.DevLog;
import saket.consumer.domain.EventDTO;
import saket.consumer.domain.EventOp;
import saket.consumer.domain.Visit;
import saket.consumer.domain.userFSM.UserState;
import saket.consumer.domain.userFSM.states.DiscreteState;
import saket.consumer.repositories.DevLogRepository;
import saket.consumer.repositories.VisitRepository;
import saket.consumer.services.DevStrategy;
import saket.consumer.services.UserStateStore;

@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@SpringBootTest
public class DevEventTests extends BaseContainerTest {

    @Autowired private DevStrategy devStrategy;
    @Autowired private ObjectMapper om;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private DevLogRepository devLogRepository;
    @Autowired private VisitRepository visitRepository;

    @Autowired private UserStateStore userStateStore;

    // Use REAL persisted visit ids, do not hardcode 14L
    private long visitId;
    private Instant now;

    @BeforeEach
    void reset() {
        jdbcTemplate.execute("""
            TRUNCATE TABLE
              dev_logs,
              transaction_logs,
              health_logs,
              location_logs,
              visits,
              known_places
            RESTART IDENTITY CASCADE
        """);

        userStateStore.update(old -> UserState.initial());

        now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // Create a real Visit row in DB so DevBehavior can attach visit reference
        // NOTE: This assumes exit_time is nullable for an "open visit".
        Visit openVisit = Visit.builder()
            .entryTime(now.minusSeconds(300))
            .exitTime(null) // must be nullable in DB/entity
            .build();

        openVisit = visitRepository.save(openVisit);
        visitId = openVisit.getId();

        assertThat(visitId).isNotNull();
    }

    private EventDTO createDevEvent(String id, Instant observedAt) {
        JsonNode payload = createDevPayload(observedAt);
        return new EventDTO(
            id,
            "iphone",              // deviceId
            "api.github",          // source
            "saket.dev_activity",  // type (routes to DevStrategy)
            EventOp.CREATE,
            observedAt,
            payload,
            null
        );
    }

    private JsonNode createDevPayload(Instant timestamp) {
        try {
            String json = """
                {
                  "timestamp": "%s",
                  "platform": "github",
                  "actionType": "commit",
                  "target": "saket/personal-foundry",
                  "metadata": {
                    "repo": "personal-foundry",
                    "sha": "abc123",
                    "message": "integration test commit",
                    "filesChanged": 3
                  }
                }
                """.formatted(timestamp.toString());

            return om.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build dev payload JsonNode", e);
        }
    }

    @Test
    void visitingState_devLogIsSaved_withVisitIdAttached() {
        // Set user into VISITING with the open visit id we created
        userStateStore.update(old -> UserState.of(DiscreteState.VISITING, Optional.of(visitId)));

        EventDTO e = createDevEvent("dev-1", now);
        devStrategy.handle(e);

        assertThat(devLogRepository.count()).isEqualTo(1);

        DevLog saved = devLogRepository.findAll().get(0);
        assertThat(saved.getPlatform()).isEqualTo("github");
        assertThat(saved.getActionType()).isEqualTo("commit");
        assertThat(saved.getTarget()).isEqualTo("saket/personal-foundry");
        assertThat(saved.getTimestamp()).isEqualTo(now);

        // Important: because visit is LAZY, checking visit.getId() may require a transaction.
        // Instead, verify via SQL:
        Long savedVisitId = jdbcTemplate.queryForObject(
            "SELECT visit_id FROM dev_logs WHERE id = ?",
            Long.class,
            saved.getId()
        );

        assertThat(savedVisitId).isEqualTo(visitId);
    }

    @Test
    void movingState_devLogIsSaved_withVisitAttached_whenEventTimeFallsInsideVisit() {
        // Even though state is MOVING, timestamp-based lookup should attach the visit
        userStateStore.update(old -> UserState.of(DiscreteState.MOVING, Optional.empty()));

        EventDTO e = createDevEvent("dev-moving-in-visit", now);
        devStrategy.handle(e);

        assertThat(devLogRepository.count()).isEqualTo(1);

        DevLog saved = devLogRepository.findAll().get(0);
        Long savedVisitId = jdbcTemplate.queryForObject(
            "SELECT visit_id FROM dev_logs WHERE id = ?",
            Long.class,
            saved.getId()
        );

        assertThat(savedVisitId).isEqualTo(visitId);
    }

    @Test
    void devLogIsSaved_withNullVisitId_whenEventTimeBeforeVisitEntry() {
        userStateStore.update(old -> UserState.of(DiscreteState.VISITING, Optional.of(visitId))); 
        // state shouldn't matter if attachment is timestamp-based

        Instant beforeVisit = now.minusSeconds(301); // visit entry is now - 300s
        EventDTO e = createDevEvent("dev-before-visit", beforeVisit);
        devStrategy.handle(e);

        assertThat(devLogRepository.count()).isEqualTo(1);

        DevLog saved = devLogRepository.findAll().get(0);
        Long savedVisitId = jdbcTemplate.queryForObject(
            "SELECT visit_id FROM dev_logs WHERE id = ?",
            Long.class,
            saved.getId()
        );

        assertThat(savedVisitId).isNull();
    }

    @Test
    void devLogIsSaved_withVisitAttached_whenEventTimeEqualsVisitStart() {
        Visit persisted = visitRepository.findById(visitId).orElseThrow();
        Instant visitStart = persisted.getEntryTime();

        EventDTO e = createDevEvent("dev-at-start", visitStart);
        devStrategy.handle(e);

        assertThat(devLogRepository.count()).isEqualTo(1);

        DevLog saved = devLogRepository.findAll().get(0);
        Long savedVisitId = jdbcTemplate.queryForObject(
            "SELECT visit_id FROM dev_logs WHERE id = ?",
            Long.class,
            saved.getId()
        );

        assertThat(savedVisitId).isEqualTo(visitId);
    }

    @Test
    void closedVisit_attachmentUsesStartInclusiveEndExclusive() {
        // Reset visits and create a CLOSED visit for deterministic boundary checks
        jdbcTemplate.execute("TRUNCATE TABLE dev_logs, visits RESTART IDENTITY CASCADE");

        Instant start = now.minusSeconds(120);
        Instant end = now.minusSeconds(60);

        Visit closedVisit = visitRepository.save(
            Visit.builder()
                .entryTime(start)
                .exitTime(end)
                .build()
        );

        // Inside interval => attach
        devStrategy.handle(createDevEvent("dev-inside-closed", now.minusSeconds(90)));

        DevLog insideSaved = devLogRepository.findAll().stream()
            .filter(d -> "commit".equals(d.getActionType())) // optional, just to get one row
            .reduce((first, second) -> second) // get latest inserted in this test
            .orElseThrow();

        Long insideVisitId = jdbcTemplate.queryForObject(
            "SELECT visit_id FROM dev_logs WHERE id = ?",
            Long.class,
            insideSaved.getId()
        );
        assertThat(insideVisitId).isEqualTo(closedVisit.getId());

        // At exact end => should NOT attach if query is end-exclusive (endTime > :time)
        devStrategy.handle(createDevEvent("dev-at-end", end));

        // Fetch the newest row directly
        Long newestId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM dev_logs", Long.class);
        Long endVisitId = jdbcTemplate.queryForObject(
            "SELECT visit_id FROM dev_logs WHERE id = ?",
            Long.class,
            newestId
        );

        assertThat(endVisitId).isNull();
    }

}
