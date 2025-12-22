package com.park.gate.messaging;

import com.park.gate.domain.VisitorEntered;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Publie les événements Gate vers RabbitMQ via Spring Cloud Stream.
 */
@Component
public class GateEventPublisher {
    
    private static final String BINDING_NAME = "gateEvents-out-0";
    
    private final StreamBridge streamBridge;
    
    public GateEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }
    
    /**
     * Publie un événement VisitorEntered sur le broker.
     */
    public void publish(VisitorEntered event) {
        var message = MessageBuilder
                .withPayload(event)
                .setHeader("eventType", "VisitorEntered")
                .setHeader("gateId", event.gateId())
                .build();
        
        boolean sent = streamBridge.send(BINDING_NAME, message);
        
        if (sent) {
            System.out.printf("[PUBLISH] VisitorEntered: ticket=%s, gate=%s%n", 
                    event.ticketId(), event.gateId());
        } else {
            System.err.printf("[PUBLISH] Failed to send event for ticket=%s%n", event.ticketId());
        }
    }
}
