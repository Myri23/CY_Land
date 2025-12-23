package com.music.gate.messaging;

import com.music.gate.domain.GateMessages;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Publisher d'événements Gate vers RabbitMQ.
 */
@Component
public class GateEventPublisher {
    
    private static final String BINDING_NAME = "gateEvents-out-0";
    private final StreamBridge streamBridge;
    
    public GateEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }
    
    public void publish(GateMessages.VisitorEntered event) {
        if (event == null) return;
        
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
    
    public void publishRejection(GateMessages.TicketRejected event) {
        if (event == null) return;
        
        var message = MessageBuilder
                .withPayload(event)
                .setHeader("eventType", "TicketRejected")
                .setHeader("gateId", event.gateId())
                .build();
        
        boolean sent = streamBridge.send(BINDING_NAME, message);
        
        if (sent) {
            System.out.printf("[PUBLISH] TicketRejected: ticket=%s, gate=%s, reason=%s%n",
                    event.ticketId(), event.gateId(), event.reason());
        }
    }
}
