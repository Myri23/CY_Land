package com.music.ride.messaging;

import com.music.actor.core.ActorSystem;
import com.music.ride.domain.RideMessages;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Configuration pour la réception des événements du Gate Service.
 */
@Configuration
public class VisitorEventConsumer {
    
    private final ActorSystem actorSystem;
    
    public VisitorEventConsumer(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }
    
    /**
     * Consomme les événements de visiteurs depuis RabbitMQ.
     */
    @Bean
    public Consumer<Map<String, Object>> visitorEvents() {
        return event -> {
            try {
                String eventType = (String) event.get("eventType");
                
                if ("VisitorEntered".equals(eventType)) {
                    String ticketId = (String) event.get("ticketId");
                    String gateId = (String) event.get("gateId");
                    
                    System.out.printf("[CONSUME] VisitorEntered: ticket=%s, gate=%s%n",
                            ticketId, gateId);
                    
                    // Notifier l'acteur de tracking des visiteurs
                    actorSystem.findActor("visitor-tracker").ifPresent(tracker ->
                            tracker.tell(new RideMessages.VisitorEntryNotification(
                                    ticketId, gateId, Instant.now()
                            ))
                    );
                }
            } catch (Exception e) {
                System.err.printf("[CONSUME] Error processing event: %s%n", e.getMessage());
            }
        };
    }
}
