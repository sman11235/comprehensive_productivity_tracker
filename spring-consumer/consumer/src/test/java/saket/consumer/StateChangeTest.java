package saket.consumer;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import saket.consumer.domain.KnownPlace;
import saket.consumer.domain.KnownPlaceStatus;
import saket.consumer.domain.userFSM.StateDecision;
import saket.consumer.domain.userFSM.UserLocationContext;
import saket.consumer.domain.userFSM.UserState;
import saket.consumer.services.UserStateMachineService;
import saket.consumer.domain.userFSM.states.DiscreteState;
import saket.consumer.services.IStateActionRunner;
import saket.consumer.services.PointUtil;
import saket.consumer.services.StateActionRunnerJPA;

public class StateChangeTest {
    private UserStateMachineService stateChange = new UserStateMachineService();
    private IStateActionRunner runner = new StateActionRunnerJPA();

    private static Instant timestamp = Instant.ofEpochMilli(1767273600000L);
    private static Point centroid = PointUtil.wgs84FromLatLon(10, 10);
    private static KnownPlace place = new KnownPlace(100L, 
            "Tech Square", 
            "Public Area", 
            PointUtil.wgs84FromLatLon(10, 10.001), 
            timestamp,
            null,
            KnownPlaceStatus.ESTABLISHED);
    

    @Test
    void startToVisitingWithPlace() {
        System.out.println("startToVisitingWithPlace");
        boolean isStationary = true;
        UserState state = new UserState(DiscreteState.START);
        UserLocationContext context = new UserLocationContext("IPHONE",
                                                              timestamp,
                                                              centroid, 
                                                              isStationary, 
                                                              place,
                                                              timestamp.minusSeconds(60 * 60));
        StateDecision dec = stateChange.nextState(state, context);
        assertThat(dec.state()).isEqualTo(DiscreteState.VISITING);
        runner.run(dec.actions(), new TestStateActionImpl());
        System.out.println("=================");
    }

    @Test
    void startToVisitingNoPlace() {
        System.out.println("startToVisitingNoPlace");
        boolean isStationary = true;
        UserState state = new UserState(DiscreteState.START);
        UserLocationContext context = new UserLocationContext("IPHONE",
                                                              timestamp,
                                                              centroid, 
                                                              isStationary, 
                                                              null,
                                                              timestamp.minusSeconds(60 * 60));
        StateDecision dec = stateChange.nextState(state, context);
        assertThat(dec.state()).isEqualTo(DiscreteState.VISITING);
        runner.run(dec.actions(), new TestStateActionImpl());
        System.out.println("=================");
    }

    @Test
    void startToMovingNoVisit() {
        System.out.println("startToMovingNoVisit");
        boolean isStationary = false;
        UserState state = new UserState(DiscreteState.START);
        UserLocationContext context = new UserLocationContext("IPHONE",
                                                              timestamp,
                                                              centroid, 
                                                              isStationary, 
                                                              place,
                                                              timestamp.minusSeconds(60 * 60));
        StateDecision dec = stateChange.nextState(state, context);
        assertThat(dec.state()).isEqualTo(DiscreteState.MOVING);
        runner.run(dec.actions(), new TestStateActionImpl());
        System.out.println("=================");
    }

    @Test
    void startToMovingWithVisit() {
        System.out.println("startToMovingWithVisit");
        boolean isStationary = false;
        UserState state = new UserState(DiscreteState.START, 15);
        UserLocationContext context = new UserLocationContext("IPHONE",
                                                              timestamp,
                                                              centroid, 
                                                              isStationary, 
                                                              place,
                                                              timestamp.minusSeconds(60 * 60));
        StateDecision dec = stateChange.nextState(state, context);
        assertThat(dec.state()).isEqualTo(DiscreteState.MOVING);
        runner.run(dec.actions(), new TestStateActionImpl());
        System.out.println("=================");
    }

    @Test
    void movingToVisitingWithPlace() {
        System.out.println("movingToVisitingWithPlace");
        boolean isStationary = true;
        UserState state = new UserState(DiscreteState.MOVING);
        UserLocationContext context = new UserLocationContext("IPHONE",
                                                              timestamp,
                                                              centroid, 
                                                              isStationary, 
                                                              place,
                                                              timestamp.minusSeconds(60 * 60));
        StateDecision dec = stateChange.nextState(state, context);
        assertThat(dec.state()).isEqualTo(DiscreteState.VISITING);
        runner.run(dec.actions(), new TestStateActionImpl());
        System.out.println("=================");
    }

    @Test
    void movingToVisitingNoPlace() {
        System.out.println("movingToVisitingNoPlace");
        boolean isStationary = true;
        UserState state = new UserState(DiscreteState.MOVING);
        UserLocationContext context = new UserLocationContext("IPHONE",
                                                              timestamp,
                                                              centroid, 
                                                              isStationary, 
                                                              null,
                                                              timestamp.minusSeconds(60 * 60));
        StateDecision dec = stateChange.nextState(state, context);
        assertThat(dec.state()).isEqualTo(DiscreteState.VISITING);
        runner.run(dec.actions(), new TestStateActionImpl());
        System.out.println("=================");
    }

    @Test
    void visitingToMovingWithNoVisitID() {
        System.out.println("visitingToMovingWithNoVisitID");
        boolean isStationary = false;
        UserState state = new UserState(DiscreteState.VISITING);
        UserLocationContext context = new UserLocationContext("IPHONE",
                                                              timestamp,
                                                              centroid, 
                                                              isStationary, 
                                                              null,
                                                              timestamp.minusSeconds(60 * 60));
        assertThrows(IllegalStateException.class, () -> stateChange.nextState(state, context));
        System.out.println("=================");
    }

    @Test
    void visitingToMovingWithVisitID() {
        System.out.println("visitingToMovingWithVisitID");
        boolean isStationary = false;
        UserState state = new UserState(DiscreteState.VISITING, 10);
        UserLocationContext context = new UserLocationContext("IPHONE",
                                                              timestamp,
                                                              centroid, 
                                                              isStationary, 
                                                              null,
                                                              timestamp.minusSeconds(60 * 60));
        StateDecision dec = stateChange.nextState(state, context);
        assertThat(dec.state()).isEqualTo(DiscreteState.MOVING);
        runner.run(dec.actions(), new TestStateActionImpl());
        System.out.println("=================");
    }
}
