package com.music.ride.messaging;

import com.music.ride.domain.RideMessages;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Publisher d'événements Ride vers RabbitMQ.
 */
@Component
public class RideEventPublisher {
    
    private static final String BINDING_NAME = "rideEvents-out-0";
    private final StreamBridge streamBridge;
    
    public RideEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }
    
    public void publishCycleStarted(String rideId, List<String> passengers) {
        var event = new RideMessages.CycleStarted(rideId, passengers, Instant.now());
        
        var message = MessageBuilder
                .withPayload(event)
                .setHeader("eventType", "CycleStarted")
                .setHeader("rideId", rideId)
                .build();
        
        boolean sent = streamBridge.send(BINDING_NAME, message);
        
        if (sent) {
            System.out.printf("[PUBLISH] CycleStarted: ride=%s, passengers=%d%n",
                    rideId, passengers.size());
        }
    }
    
    public void publishCycleFinished(String rideId, List<String> passengers) {
        var event = new RideMessages.CycleFinished(rideId, passengers, Instant.now());
        
        var message = MessageBuilder
                .withPayload(event)
                .setHeader("eventType", "CycleFinished")
                .setHeader("rideId", rideId)
                .build();
        
        boolean sent = streamBridge.send(BINDING_NAME, message);
        
        if (sent) {
            System.out.printf("[PUBLISH] CycleFinished: ride=%s, passengers=%d%n",
                    rideId, passengers.size());
        }
    }
}
