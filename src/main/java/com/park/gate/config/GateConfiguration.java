package com.park.gate.config;

import com.park.actor.runtime.ActorRuntime;
import com.park.gate.domain.GateActor;
import com.park.gate.messaging.GateEventPublisher;
import com.park.gate.persistence.GateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GateConfiguration {
    
    @Bean
    CommandLineRunner initializeGates(ActorRuntime runtime, GateEventPublisher publisher, GateRepository gateRepository) {
        return args -> {
            gateRepository.findAll().forEach(gate -> {
                runtime.getOrCreate(gate.getGateId(), id -> new GateActor(id, publisher));
            });
            
            long gateCount = gateRepository.count();
            
            System.out.println("===========================================");
            System.out.println("  GATE SERVICE READY");
            System.out.println("  Portes actives: " + gateCount);
            System.out.println();
            System.out.println("  SCAN ENDPOINTS:");
            System.out.println("  - POST /gate/{gateId}/scan?ticketId=XXX");
            System.out.println("  - GET  /gate/{gateId}/status");
            System.out.println();
            System.out.println("  ADMIN ENDPOINTS:");
            System.out.println("  - GET/POST/PUT/DELETE /admin/gates");
            System.out.println("  - GET/POST/DELETE /admin/tickets");
            System.out.println("  - GET /admin/tickets/valid-today");
            System.out.println("  - GET /admin/tickets/{id}/validate?gateType=XXX");
            System.out.println("===========================================");
        };
    }
}
