package com.music.gate;

import com.music.gate.domain.GateActor;
import com.music.gate.domain.GateMessages;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration du Gate Service.
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
    @DisplayName("POST /gate/{id}/scan retourne 202 Accepted")
    void scanShouldReturn202() throws Exception {
        mockMvc.perform(post("/gate/G1/scan")
                        .param("ticketId", "TEST-001"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SCAN_ACCEPTED"))
                .andExpect(jsonPath("$.gateId").value("G1"))
                .andExpect(jsonPath("$.ticketId").value("TEST-001"));
    }
    
    @Test
    @DisplayName("POST /gate/{id}/scan-sync retourne le résultat du scan")
    void scanSyncShouldReturnResult() throws Exception {
        String ticketId = "SYNC-TEST-" + System.currentTimeMillis();
        
        mockMvc.perform(post("/gate/G1/scan-sync")
                        .param("ticketId", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.ticketId").value(ticketId));
    }
    
    @Test
    @DisplayName("Scan publie un événement VisitorEntered")
    void scanShouldPublishEvent() throws Exception {
        String ticketId = "EVENT-" + System.currentTimeMillis();
        
        mockMvc.perform(post("/gate/G1/scan")
                        .param("ticketId", ticketId))
                .andExpect(status().isAccepted());
        
        Thread.sleep(200);
        
        Message<byte[]> message = outputDestination.receive(1000, "park.events");
        assertThat(message).isNotNull();
        
        String payload = new String(message.getPayload());
        assertThat(payload).contains(ticketId);
        assertThat(payload).contains("G1");
    }
    
    @Test
    @DisplayName("GET /gate liste les portes")
    void shouldListGates() throws Exception {
        mockMvc.perform(get("/gate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber());
    }
    
    @Test
    @DisplayName("POST /gate/{id}/block bloque une porte")
    void shouldBlockGate() throws Exception {
        mockMvc.perform(post("/gate/G2/block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        
        // Débloquer après le test
        mockMvc.perform(post("/gate/G2/unblock"))
                .andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("Création et suppression d'une porte")
    void shouldCreateAndDeleteGate() throws Exception {
        // Créer
        mockMvc.perform(post("/gate")
                        .param("gateId", "TEST-GATE")
                        .param("name", "Test Gate")
                        .param("type", "MAIN_GATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        
        // Supprimer
        mockMvc.perform(delete("/gate/TEST-GATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
    
    @Test
    @DisplayName("GET /actors retourne la liste des acteurs")
    void shouldListActors() throws Exception {
        mockMvc.perform(get("/actors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
    
    @Test
    @DisplayName("GET /actors/system/metrics retourne les métriques")
    void shouldReturnSystemMetrics() throws Exception {
        mockMvc.perform(get("/actors/system/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemName").exists())
                .andExpect(jsonPath("$.totalActors").isNumber());
    }
}
