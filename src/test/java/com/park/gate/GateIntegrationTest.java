package com.park.gate;

import com.park.gate.domain.VisitorEntered;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de l'API Gate avec Spring Cloud Stream Test Binder.
 * Vérifie le flux complet : HTTP → Actor → Event Publishing
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestChannelBinderConfiguration.class)
class GateIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private OutputDestination outputDestination;
    
    @Test
    @DisplayName("POST /gate/{id}/scan doit retourner 202 Accepted")
    void scanShouldReturn202() throws Exception {
        mockMvc.perform(post("/gate/G1/scan")
                        .param("ticketId", "TEST-001"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SCAN_ACCEPTED"))
                .andExpect(jsonPath("$.gateId").value("G1"))
                .andExpect(jsonPath("$.ticketId").value("TEST-001"));
    }
    
    @Test
    @DisplayName("Un scan valide doit publier un événement VisitorEntered")
    void scanShouldPublishEvent() throws Exception {
        // Given
        String ticketId = "EVENT-TEST-" + System.currentTimeMillis();
        
        // When
        mockMvc.perform(post("/gate/G1/scan")
                        .param("ticketId", ticketId))
                .andExpect(status().isAccepted());
        
        // Then - attendre un peu pour le traitement async
        Thread.sleep(100);
        
        Message<byte[]> message = outputDestination.receive(1000, "park.events");
        assertThat(message).isNotNull();
        
        String payload = new String(message.getPayload());
        assertThat(payload).contains(ticketId);
        assertThat(payload).contains("G1");
    }
    
    @Test
    @DisplayName("Un ticketId vide doit retourner 400 Bad Request")
    void emptyShouldReturn400() throws Exception {
        mockMvc.perform(post("/gate/G1/scan")
                        .param("ticketId", ""))
                .andExpect(status().isBadRequest());
    }
}
