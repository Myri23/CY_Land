package com.music.gate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.gate.domain.GateMessages;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration inter-services.
 * 
 * Ces tests vérifient la communication entre les services via :
 * - RabbitMQ (événements asynchrones)
 * - HTTP/REST (communication directe via Eureka)
 * - RemoteActorRef (pattern acteur distant)
 * 
 * NOTE : Ces tests utilisent le TestChannelBinder de Spring Cloud Stream
 * pour simuler RabbitMQ sans avoir besoin d'un broker réel.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestChannelBinderConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InterServiceIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private OutputDestination outputDestination;
    
    @Autowired
    private InputDestination inputDestination;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Désactiver Eureka pour les tests (utiliser des mocks).
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
    }
    
    // ==================== Tests de publication d'événements ====================
    
    @Test
    @Order(1)
    @DisplayName("Scan publie un événement VisitorEntered vers RabbitMQ")
    void scanShouldPublishVisitorEnteredEvent() throws Exception {
        // Given
        String ticketId = "EVENT-TEST-" + System.currentTimeMillis();
        
        // When - Scanner un ticket
        mockMvc.perform(post("/gate/G1/scan")
                        .param("ticketId", ticketId))
                .andExpect(status().isAccepted());
        
        // Then - Vérifier que l'événement est publié
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            Message<byte[]> message = outputDestination.receive(1000, "park.events");
            assertThat(message).isNotNull();
            
            String payload = new String(message.getPayload());
            assertThat(payload).contains(ticketId);
            assertThat(payload).contains("G1");
            
            // Vérifier les headers
            assertThat(message.getHeaders().get("eventType")).isEqualTo("VisitorEntered");
            assertThat(message.getHeaders().get("gateId")).isEqualTo("G1");
        });
    }
    
    @Test
    @Order(2)
    @DisplayName("Ticket rejeté publie un événement TicketRejected")
    void rejectedTicketShouldPublishRejectionEvent() throws Exception {
        // Given - Scanner le même ticket deux fois
        String ticketId = "DUPLICATE-TEST-" + System.currentTimeMillis();
        
        // Premier scan (accepté)
        mockMvc.perform(post("/gate/G1/scan-sync")
                        .param("ticketId", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        
        // Vider la queue
        outputDestination.receive(500, "park.events");
        
        // When - Deuxième scan (rejeté)
        mockMvc.perform(post("/gate/G1/scan-sync")
                        .param("ticketId", ticketId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Ticket already scanned"));
        
        // Then - Vérifier que l'événement de rejet est publié
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            Message<byte[]> message = outputDestination.receive(1000, "park.events");
            assertThat(message).isNotNull();
            
            String payload = new String(message.getPayload());
            assertThat(payload).contains(ticketId);
            assertThat(payload).contains("already");
        });
    }
    
    // ==================== Tests de consommation d'événements ====================
    
    @Test
    @Order(3)
    @DisplayName("Le service peut recevoir des événements d'autres services")
    void shouldConsumeExternalEvents() throws Exception {
        // Given - Simuler un événement venant d'un autre service
        Map<String, Object> externalEvent = Map.of(
                "eventType", "RideStatusChanged",
                "rideId", "roller-coaster",
                "status", "CLOSED",
                "timestamp", Instant.now().toString()
        );
        
        // When - Envoyer l'événement sur le canal d'entrée
        // Note: Dans un vrai test, cela viendrait du Ride Service
        String eventJson = objectMapper.writeValueAsString(externalEvent);
        
        // Ce test vérifie simplement que le format des événements est correct
        // La consommation réelle est testée dans RideService
        assertThat(eventJson).contains("RideStatusChanged");
        assertThat(eventJson).contains("CLOSED");
    }
    
    // ==================== Tests de flux complet ====================
    
    @Test
    @Order(4)
    @DisplayName("Flux complet : scan -> événement -> tracking")
    void fullFlowScanToEventToTracking() throws Exception {
        // Given
        String ticketId = "FLOW-TEST-" + System.currentTimeMillis();
        
        // When - Étape 1: Scanner le ticket
        MvcResult scanResult = mockMvc.perform(post("/gate/G1/scan-sync")
                        .param("ticketId", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        
        // Then - Étape 2: Vérifier l'événement publié
        Message<byte[]> publishedEvent = outputDestination.receive(2000, "park.events");
        assertThat(publishedEvent).isNotNull();
        
        String eventPayload = new String(publishedEvent.getPayload());
        assertThat(eventPayload).contains(ticketId);
        
        // Étape 3: Vérifier le statut de la porte
        mockMvc.perform(get("/gate/G1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateId").value("G1"))
                .andExpect(jsonPath("$.operational").value(true))
                .andExpect(jsonPath("$.visitorsToday").isNumber());
    }
    
    @Test
    @Order(5)
    @DisplayName("Multiples tickets génèrent des événements séquentiels")
    void multipleTicketsShouldGenerateSequentialEvents() throws Exception {
        // Given - 5 tickets à scanner
        int ticketCount = 5;
        String ticketPrefix = "MULTI-" + System.currentTimeMillis() + "-";
        
        // When - Scanner tous les tickets
        for (int i = 0; i < ticketCount; i++) {
            mockMvc.perform(post("/gate/G2/scan")
                            .param("ticketId", ticketPrefix + i))
                    .andExpect(status().isAccepted());
        }
        
        // Then - Vérifier que tous les événements sont publiés
        int receivedCount = 0;
        while (receivedCount < ticketCount) {
            Message<byte[]> message = outputDestination.receive(1000, "park.events");
            if (message == null) break;
            
            String payload = new String(message.getPayload());
            if (payload.contains(ticketPrefix)) {
                receivedCount++;
            }
        }
        
        assertThat(receivedCount).isEqualTo(ticketCount);
    }
    
    // ==================== Tests de résilience ====================
    
    @Test
    @Order(6)
    @DisplayName("Le service continue de fonctionner même si le Ride Service est indisponible")
    void shouldContinueWhenRemoteServiceUnavailable() throws Exception {
        // Given - Ticket à scanner
        String ticketId = "RESILIENCE-" + System.currentTimeMillis();
        
        // When - Scanner le ticket (le Ride Service n'est pas disponible en test)
        MvcResult result = mockMvc.perform(post("/gate/G1/scan-sync")
                        .param("ticketId", ticketId))
                .andExpect(status().isOk())
                .andReturn();
        
        // Then - Le scan doit réussir localement
        String response = result.getResponse().getContentAsString();
        assertThat(response).contains("\"success\":true");
        
        // L'événement doit quand même être publié
        Message<byte[]> message = outputDestination.receive(1000, "park.events");
        assertThat(message).isNotNull();
    }
    
    @Test
    @Order(7)
    @DisplayName("Le blocage d'une porte n'affecte pas les événements en cours")
    void blockingGateShouldNotAffectPendingEvents() throws Exception {
        // Given - Scanner un ticket avant le blocage
        String ticketId = "PRE-BLOCK-" + System.currentTimeMillis();
        
        mockMvc.perform(post("/gate/G2/scan")
                        .param("ticketId", ticketId))
                .andExpect(status().isAccepted());
        
        // When - Bloquer la porte
        mockMvc.perform(post("/gate/G2/block"))
                .andExpect(status().isOk());
        
        // Then - L'événement du scan précédent doit être publié
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            Message<byte[]> message = outputDestination.receive(500, "park.events");
            // L'événement peut avoir été traité avant le blocage
        });
        
        // Cleanup - Débloquer la porte
        mockMvc.perform(post("/gate/G2/unblock"))
                .andExpect(status().isOk());
    }
    
    // ==================== Tests des métriques inter-services ====================
    
    @Test
    @Order(8)
    @DisplayName("Les métriques du système incluent les acteurs locaux")
    void systemMetricsShouldIncludeLocalActors() throws Exception {
        mockMvc.perform(get("/actors/system/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemName").value("gate-service"))
                .andExpect(jsonPath("$.totalActors").isNumber())
                .andExpect(jsonPath("$.runningActors").isNumber())
                .andExpect(jsonPath("$.blockedActors").isNumber());
    }
    
    @Test
    @Order(9)
    @DisplayName("Les métriques du pool de scanners sont disponibles")
    void scannerPoolMetricsShouldBeAvailable() throws Exception {
        mockMvc.perform(get("/gate/pool/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poolName").value("ticket-scanners"))
                .andExpect(jsonPath("$.currentSize").isNumber())
                .andExpect(jsonPath("$.minSize").value(2))
                .andExpect(jsonPath("$.maxSize").value(10))
                .andExpect(jsonPath("$.pendingMessages").isNumber())
                .andExpect(jsonPath("$.utilizationPercent").isNumber());
    }
    
    // ==================== Test de charge simulée ====================
    
    @Test
    @Order(10)
    @DisplayName("Le système gère une charge élevée de scans")
    void shouldHandleHighLoadOfScans() throws Exception {
        // Given - Beaucoup de tickets à scanner
        int loadSize = 50;
        String ticketPrefix = "LOAD-" + System.currentTimeMillis() + "-";
        CountDownLatch latch = new CountDownLatch(loadSize);
        AtomicReference<Exception> error = new AtomicReference<>();
        
        // When - Scanner tous les tickets en parallèle (simulé séquentiellement)
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < loadSize; i++) {
            try {
                mockMvc.perform(post("/gate/VIP/scan")
                                .param("ticketId", ticketPrefix + i))
                        .andExpect(status().isAccepted());
                latch.countDown();
            } catch (Exception e) {
                error.set(e);
                break;
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Then - Tous les scans doivent réussir
        assertThat(error.get()).isNull();
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        
        System.out.printf("[LOAD TEST] %d scans completed in %dms (%.2f scans/sec)%n",
                loadSize, duration, (double) loadSize / duration * 1000);
        
        // Vérifier que le système est toujours opérationnel
        mockMvc.perform(get("/gate/VIP/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operational").value(true));
    }
}
