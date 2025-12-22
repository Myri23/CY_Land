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

/**
 * Tests unitaires du GateActor.
 * Utilise des mocks simples pour isoler la logique métier.
 */
class GateActorTest {
    
    private GateActor gateActor;
    private List<VisitorEntered> publishedEvents;
    private TestActorContext context;
    
    @BeforeEach
    void setUp() {
        publishedEvents = new ArrayList<>();
        
        // Mock du publisher avec une classe anonyme
        GateEventPublisher mockPublisher = new MockGateEventPublisher(publishedEvents);
        
        gateActor = new GateActor("G1", mockPublisher);
        context = new TestActorContext("G1");
    }
    
    @Test
    @DisplayName("Un ticket valide doit être accepté et publier un événement")
    void shouldAcceptValidTicket() {
        // When
        gateActor.onReceive(new ScanTicket("TICKET-001"), context);
        
        // Then
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0).ticketId()).isEqualTo("TICKET-001");
        assertThat(publishedEvents.get(0).gateId()).isEqualTo("G1");
    }
    
    @Test
    @DisplayName("Un ticket déjà scanné doit être rejeté (pas de doublon)")
    void shouldRejectDuplicateTicket() {
        // Given - premier scan accepté
        gateActor.onReceive(new ScanTicket("TICKET-001"), context);
        
        // When - deuxième scan du même ticket
        gateActor.onReceive(new ScanTicket("TICKET-001"), context);
        
        // Then - un seul événement publié
        assertThat(publishedEvents).hasSize(1);
    }
    
    @Test
    @DisplayName("Plusieurs tickets différents doivent tous être acceptés")
    void shouldAcceptMultipleDifferentTickets() {
        // When
        gateActor.onReceive(new ScanTicket("TICKET-001"), context);
        gateActor.onReceive(new ScanTicket("TICKET-002"), context);
        gateActor.onReceive(new ScanTicket("TICKET-003"), context);
        
        // Then
        assertThat(publishedEvents).hasSize(3);
        assertThat(publishedEvents)
                .extracting(VisitorEntered::ticketId)
                .containsExactly("TICKET-001", "TICKET-002", "TICKET-003");
    }
    
    @Test
    @DisplayName("Le snapshot doit être appelé après chaque acceptation")
    void shouldSnapshotAfterAcceptingTicket() {
        // When
        gateActor.onReceive(new ScanTicket("TICKET-001"), context);
        
        // Then
        assertThat(context.snapshotCount).isEqualTo(1);
    }
    
    // === Test doubles ===
    
    /**
     * Mock du GateEventPublisher pour capturer les événements publiés.
     */
    static class MockGateEventPublisher extends GateEventPublisher {
        private final List<VisitorEntered> events;
        
        MockGateEventPublisher(List<VisitorEntered> events) {
            super(null); // StreamBridge non utilisé dans les tests
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
        
        TestActorContext(String id) {
            this.id = id;
        }
        
        @Override public String id() { return id; }
        @Override public ActorRef self() { return null; }
        @Override public ActorRef lookup(String actorId) { return null; }
        @Override public void snapshot(Object state) { snapshotCount++; }
        @Override public <T> T restore(Class<T> type) { return null; }
    }
}
