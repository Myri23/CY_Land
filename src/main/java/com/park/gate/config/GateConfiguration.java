package com.park.gate.config;

import com.park.actor.core.ActorRef;
import com.park.actor.runtime.ActorRuntime;
import com.park.gate.domain.GateActor;
import com.park.gate.messaging.GateEventPublisher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration des portes du parc.
 * Pré-crée les acteurs pour les portes principales au démarrage.
 */
@Configuration
public class GateConfiguration {
    
    /**
     * Initialise les portes principales au démarrage de l'application.
     */
    @Bean
    CommandLineRunner initializeGates(ActorRuntime runtime, GateEventPublisher publisher) {
        return args -> {
            // Pré-création des portes G1 et G2
            runtime.getOrCreate("G1", id -> new GateActor(id, publisher));
            runtime.getOrCreate("G2", id -> new GateActor(id, publisher));
            
            System.out.println("===========================================");
            System.out.println("  GATE SERVICE READY");
            System.out.println("  Portes actives: G1, G2");
            System.out.println("  Endpoint: POST /gate/{gateId}/scan?ticketId=XXX");
            System.out.println("===========================================");
        };
    }
}
