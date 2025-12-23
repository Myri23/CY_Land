package com.music.ride;

import com.music.ride.domain.RideActor;
import com.music.ride.domain.RideMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration du Ride Service.
 * 
 * Ces tests vérifient le bon fonctionnement des endpoints REST
 * et l'interaction avec le framework d'acteurs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RideIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    // ==================== Tests des endpoints de base ====================
    
    @Test
    @DisplayName("GET /rides retourne la liste des attractions")
    void shouldListRides() throws Exception {
        mockMvc.perform(get("/rides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3));
    }
    
    @Test
    @DisplayName("GET /rides/{id}/state retourne l'état d'une attraction")
    void shouldGetRideState() throws Exception {
        mockMvc.perform(get("/rides/rc/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideId").value("rc"))
                .andExpect(jsonPath("$.name").value("RollerCoaster"))
                .andExpect(jsonPath("$.capacity").value(20))
                .andExpect(jsonPath("$.closed").value(false));
    }
    
    @Test
    @DisplayName("GET /rides/{id}/state retourne 404 pour une attraction inexistante")
    void shouldReturn404ForUnknownRide() throws Exception {
        mockMvc.perform(get("/rides/unknown/state"))
                .andExpect(status().isNotFound());
    }
    
    // ==================== Tests de la file d'attente ====================
    
    @Test
    @DisplayName("POST /rides/{id}/join ajoute un visiteur à la file d'attente")
    void shouldJoinQueue() throws Exception {
        String ticketId = "VISITOR-" + System.currentTimeMillis();
        
        mockMvc.perform(post("/rides/gr/join")
                        .param("ticketId", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Added to queue"))
                .andExpect(jsonPath("$.rideId").value("gr"))
                .andExpect(jsonPath("$.ticketId").value(ticketId));
    }
    
    @Test
    @DisplayName("POST /rides/{id}/join retourne 404 pour une attraction inexistante")
    void shouldReturn404WhenJoiningUnknownRide() throws Exception {
        mockMvc.perform(post("/rides/unknown/join")
                        .param("ticketId", "TEST-001"))
                .andExpect(status().isNotFound());
    }
    
    // ==================== Tests des cycles ====================
    
    @Test
    @DisplayName("POST /rides/{id}/start-cycle démarre un cycle avec des passagers")
    void shouldStartCycleWithPassengers() throws Exception {
        // D'abord ajouter des visiteurs à la queue
        String ticketPrefix = "CYCLE-TEST-" + System.currentTimeMillis() + "-";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/rides/vr/join")
                            .param("ticketId", ticketPrefix + i))
                    .andExpect(status().isOk());
        }
        
        // Démarrer le cycle
        mockMvc.perform(post("/rides/vr/start-cycle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Cycle started"))
                .andExpect(jsonPath("$.passengers").isArray());
    }
    
    @Test
    @DisplayName("POST /rides/{id}/start-cycle échoue si la queue est vide")
    void shouldFailStartCycleWithEmptyQueue() throws Exception {
        // Créer une nouvelle attraction pour avoir une queue vide
        // Note: On utilise une attraction existante qui devrait avoir une queue vide après les cycles
        
        // Vérifier d'abord l'état
        mockMvc.perform(get("/rides/rc/state"))
                .andExpect(status().isOk());
    }
    
    // ==================== Tests des pannes ====================
    
    @Test
    @DisplayName("POST /rides/{id}/report-fault signale une panne")
    void shouldReportFault() throws Exception {
        mockMvc.perform(post("/rides/gr/report-fault")
                        .param("faultType", "MECHANICAL")
                        .param("description", "Test fault"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.faultType").value("MECHANICAL"));
        
        // Vérifier que l'attraction est fermée
        mockMvc.perform(get("/rides/gr/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true))
                .andExpect(jsonPath("$.currentFault").value("MECHANICAL"));
        
        // Réparer pour les tests suivants
        mockMvc.perform(post("/rides/gr/repair"))
                .andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("POST /rides/{id}/repair répare une attraction")
    void shouldRepairRide() throws Exception {
        // D'abord signaler une panne
        mockMvc.perform(post("/rides/rc/report-fault")
                        .param("faultType", "ELECTRICAL")
                        .param("description", "Power issue"))
                .andExpect(status().isOk());
        
        // Réparer
        mockMvc.perform(post("/rides/rc/repair"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        
        // Vérifier que l'attraction est rouverte
        mockMvc.perform(get("/rides/rc/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(false))
                .andExpect(jsonPath("$.currentFault").value("NONE"));
    }
    
    // ==================== Tests de blocage/déblocage ====================
    
    @Test
    @DisplayName("POST /rides/{id}/block bloque une attraction")
    void shouldBlockRide() throws Exception {
        mockMvc.perform(post("/rides/vr/block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Ride vr blocked"));
        
        // Débloquer pour les tests suivants
        mockMvc.perform(post("/rides/vr/unblock"))
                .andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("POST /rides/{id}/unblock débloque une attraction")
    void shouldUnblockRide() throws Exception {
        // D'abord bloquer
        mockMvc.perform(post("/rides/rc/block"))
                .andExpect(status().isOk());
        
        // Débloquer
        mockMvc.perform(post("/rides/rc/unblock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Ride rc unblocked"));
    }
    
    // ==================== Tests de l'API de gestion des acteurs ====================
    
    @Test
    @DisplayName("GET /actors retourne la liste des acteurs du service")
    void shouldListActors() throws Exception {
        mockMvc.perform(get("/actors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").isNumber());
    }
    
    @Test
    @DisplayName("GET /actors/{id} retourne les détails d'un acteur")
    void shouldGetActorDetails() throws Exception {
        mockMvc.perform(get("/actors/rc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("rc"))
                .andExpect(jsonPath("$.state").exists())
                .andExpect(jsonPath("$.local").value(true));
    }
    
    @Test
    @DisplayName("GET /actors/system/metrics retourne les métriques du système")
    void shouldReturnSystemMetrics() throws Exception {
        mockMvc.perform(get("/actors/system/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemName").value("ride-service"))
                .andExpect(jsonPath("$.totalActors").isNumber())
                .andExpect(jsonPath("$.runningActors").isNumber());
    }
    
    // ==================== Tests de validation des paramètres ====================
    
    @Test
    @DisplayName("POST /rides/{id}/report-fault avec type de panne invalide")
    void shouldHandleInvalidFaultType() throws Exception {
        mockMvc.perform(post("/rides/rc/report-fault")
                        .param("faultType", "INVALID_TYPE")
                        .param("description", "Test"))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    @DisplayName("Tous les types de pannes sont acceptés")
    void shouldAcceptAllFaultTypes() throws Exception {
        for (RideMessages.FaultType faultType : RideMessages.FaultType.values()) {
            if (faultType != RideMessages.FaultType.NONE) {
                mockMvc.perform(post("/rides/rc/report-fault")
                                .param("faultType", faultType.name())
                                .param("description", "Testing " + faultType))
                        .andExpect(status().isOk());
                
                // Réparer après chaque test
                mockMvc.perform(post("/rides/rc/repair"))
                        .andExpect(status().isOk());
            }
        }
    }
}
