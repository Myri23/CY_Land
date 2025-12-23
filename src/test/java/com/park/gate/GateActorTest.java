package com.park.gate;

import com.park.actor.core.ActorContext;
import com.park.actor.core.ActorRef;
import com.park.gate.domain.GateActor;
import com.park.gate.domain.ScanTicket;
import com.park.gate.domain.VisitorEntered;
import com.park.gate.messaging.GateEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GateActorTest {
    
    private GateActor gateActor;
    private List<VisitorEntered> publishedEvents;
    private TestActorContext context;
    
    @BeforeEach
    void setUp() {
        publishedEvents = new ArrayList<>();
        GateEventPublisher mockPublisher = new MockGateEventPublisher(publishedEvents);
        gateActor = new GateActor("G1", mockPublisher);
        context = new TestActorContext("G1");
    }
    
    @Test
    @DisplayName("Un ticket valide doit etre accepte et publier un evenement")
    void shouldAcceptValidTicket() {
        gateActor.onReceive(new ScanTicket("TICKET-001"), context);
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0).ticketId()).isEqualTo("TICKET-001");
        assertThat(publishedEvents.get(0).gateId()).isEqualTo("G1");
    }
    
    @Test
    @DisplayName("Un ticket deja scanne doit etre rejete (pas de doublon)")
    void shouldRejectDuplicateTicket() {
        gateActor.onReceive(new ScanTicket("TICKET-001"), context);
        gateActor.onReceive(new ScanTicket("TICKET-001"), context);
        assertThat(publishedEvents).hasSize(1);
    }
    
    @Test
    @DisplayName("Plusieurs tickets differents doivent tous etre acceptes")
    void shouldAcceptMultipleDifferentTickets() {
        gateActor.onReceive(new ScanTicket("TICKET-001"), context);
        gateActor.onReceive(new ScanTicket("TICKET-002"), context);
        gateActor.onReceive(new ScanTicket("TICKET-003"), context);
        assertThat(publishedEvents).hasSize(3);
        assertThat(publishedEvents).extracting(VisitorEntered::ticketId)
                .containsExactly("TICKET-001", "TICKET-002", "TICKET-003");
    }
    
    @Test
    @DisplayName("Le snapshot doit etre appele apres chaque acceptation")
    void shouldSnapshotAfterAcceptingTicket() {
        gateActor.onReceive(new ScanTicket("TICKET-001"), context);
        assertThat(context.snapshotCount).isEqualTo(1);
    }
    
    static class MockGateEventPublisher extends GateEventPublisher {
        private final List<VisitorEntered> events;
        
        MockGateEventPublisher(List<VisitorEntered> events) {
            super(null);
            this.events = events;
        }
        
        @Override
        public void publish(VisitorEntered event) {
            events.add(event);
        }
    }
    
    static class TestActorContext implements ActorContext {
        private final String id;
        int snapshotCount = 0;
        
        TestActorContext(String id) { this.id = id; }
        
        @Override public String id() { return id; }
        @Override public ActorRef self() { return null; }
        @Override public ActorRef lookup(String actorId) { return null; }
        @Override public void snapshot(Object state) { snapshotCount++; }
        @Override public <T> T restore(Class<T> type) { return null; }
    }
}
