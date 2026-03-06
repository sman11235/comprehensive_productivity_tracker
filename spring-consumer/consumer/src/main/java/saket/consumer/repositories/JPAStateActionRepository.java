package saket.consumer.repositories;

import java.time.Instant;

import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import saket.consumer.domain.KnownPlace;
import saket.consumer.domain.KnownPlaceStatus;
import saket.consumer.domain.Visit;
import saket.consumer.domain.actions.IStateActionRepository;

/**
 * A repository that connects to JPA Databases.
 */
@Repository
public class JPAStateActionRepository implements IStateActionRepository {
    private final KnownPlaceRepository knownPlaceRepository;
    private final VisitRepository visitRepository;
    private final DevLogRepository devLogRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final HealthLogRepository healthLogRepository;
    private final LocationLogRepository locationLogRepository;

    public JPAStateActionRepository(
        KnownPlaceRepository knownPlaceRepository,
        VisitRepository visitRepository,
        DevLogRepository devLogRepository,
        TransactionLogRepository transactionLogRepository,
        HealthLogRepository healthLogRepository,
        LocationLogRepository locationLogRepository
    ) {
        this.knownPlaceRepository = knownPlaceRepository;
        this.visitRepository = visitRepository;
        this.devLogRepository = devLogRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.healthLogRepository = healthLogRepository;
        this.locationLogRepository = locationLogRepository;
    }
    
    @Transactional
    @Override
    public long createNewKnownPlace(Point centroid, Instant now) {
        KnownPlace newPlace = KnownPlace.builder()
            .category("New")
            .createdAt(now)
            .loc(centroid)
            .name("New Place")
            .status(KnownPlaceStatus.NEW)
            .build();
        newPlace = knownPlaceRepository.save(newPlace);
        return newPlace.getId();
    }

    @Transactional
    @Override
    public long startVisit(long placeId, Instant start) {
        KnownPlace place = knownPlaceRepository.getReferenceById(placeId);
        Visit newVisit = Visit.builder()
            .entryTime(start)
            .place(place)
            .build();
        newVisit = visitRepository.save(newVisit);
        return newVisit.getId();
    }

    @Transactional
    @Override
    public void endVisit(long visitId, Instant end) {
        Visit visit = visitRepository.getReferenceById(visitId);
        visit.endAt(end);
        visitRepository.save(visit);
    }

    @Transactional
    @Override
    public void assignVisitToEvents(long visitId, Instant start, Instant end) {
        Visit visit = visitRepository.getReferenceById(visitId);
        devLogRepository.assignVisit(visit, start, end);
        healthLogRepository.assignVisit(visit, start, end);
        transactionLogRepository.assignVisit(visit, start, end);
        locationLogRepository.assignVisit(visit, start, end);
    }
}
